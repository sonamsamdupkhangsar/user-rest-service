package me.sonam.user.handler;

import cloud.sonam.s3.config.S3ClientConfigurationProperties;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Uses guarded internal APIs, never the retired user-deletion or legacy bulk-client endpoints. */
@Service
@ConditionalOnProperty(name = "account-deletion.admin-enabled", havingValue = "true")
public class ServiceAdminAccountDeletionOperations implements AdminAccountDeletionOperations {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceAdminAccountDeletionOperations.class);

    private final UserRepository userRepository;
    private final WebClient webClient;
    private final S3AsyncClient s3AsyncClient;
    private final String bucket;
    private final String organizationEndpoint;
    private final String roleEndpoint;
    private final String authorizationEndpoint;
    private final String profilePrefix;

    public ServiceAdminAccountDeletionOperations(
            UserRepository userRepository,
            @Qualifier("serviceWebClientBuilder") WebClient.Builder webClientBuilder,
            S3AsyncClient s3AsyncClient,
            S3ClientConfigurationProperties s3ClientConfigurationProperties,
            @Value("${organization-rest-service.context}") String organizationEndpoint,
            @Value("${role-rest-service.context}") String roleEndpoint,
            @Value("${account-deletion.authorization-url:${auth-server.root}/clients/internal/admin-deletion}") String authorizationEndpoint,
            @Value("${profilePhotoFolder:profile/}") String profilePhotoFolder) {
        this.userRepository = userRepository;
        this.webClient = webClientBuilder.build();
        this.s3AsyncClient = s3AsyncClient;
        this.bucket = Objects.requireNonNull(s3ClientConfigurationProperties.getBucket());
        this.organizationEndpoint = organizationEndpoint + "/internal/admin-deletion";
        this.roleEndpoint = roleEndpoint + "/internal/admin-deletion";
        this.authorizationEndpoint = authorizationEndpoint;
        this.profilePrefix = Objects.requireNonNull(s3ClientConfigurationProperties.getRootPath())
                + Objects.requireNonNull(s3ClientConfigurationProperties.getPhotoPath()) + profilePhotoFolder;

        if (profilePhotoFolder.isBlank() || profilePrefix.contains("..")) {
            throw new IllegalArgumentException("A safe user-photo prefix is required for admin-account deletion");
        }
    }

    /**
     * Loads the user's roles, organizations, and client relationships for deletion eligibility checks.
     * Does not modify or delete data.
     */
    @Override
    public Mono<AccountInventory> loadAccountDeletionInventory(UUID userId) {
        // Load the canonical user record; stop if the account does not exist.
        return findUser(userId)
                // Combine role responsibilities, organization relationships, and client-linked organizations.
                .flatMap(user -> Mono.zip(
                                get(roleEndpoint + "/users/" + userId, AccountRoles.class),
                                get(organizationEndpoint + "/users/" + userId, RelatedOrganizations.class),
                                get(authorizationEndpoint + "/users/" + userId, RelatedOrganizations.class))
                        .flatMap(states -> {
                            // T1: role inventory; T2: organization-service links; T3: authorization-service client links.
                            AccountRoles accountRoles = states.getT1();
                            RelatedOrganizations organizations = states.getT2();
                            RelatedOrganizations clients = states.getT3();

                            // Require successful lookups for this same user before trusting any returned relationships.
                            requireVerifiedAccountRelationships(accountRoles.roleInventoryLoaded() && organizations.organizationLookupCompleted() && clients.organizationLookupCompleted()
                                    && userId.equals(accountRoles.userId()) && userId.equals(organizations.userId())
                                    && userId.equals(clients.userId()));

                            // Collect every organization connected to this user into one set, without duplicates.
                            Set<UUID> organizationIds = new HashSet<>(accountRoles.organizationIds());
                            organizationIds.addAll(accountRoles.relatedOrganizationIds());
                            organizationIds.addAll(organizations.organizationIds());
                            organizationIds.addAll(clients.organizationIds());

                            // Inspect each organization sequentially in a stable order, including its members,
                            // administrators, clients, and protection settings. Collect every result before proceeding.
                            return Flux.fromIterable(organizationIds.stream().sorted()
                                    .toList())
                                    .concatMap(organizationId -> loadOrganizationDeletionInventory(user, organizationId))
                                    .collectList()
                                    // Build the read-only inventory, preserving admin blockers and requiring
                                    // protection policy verification from every inspection. Eligibility is checked separately.
                                    .map(inspections -> new AccountInventory(
                                            userId,
                                            true,
                                            accountRoles.protectionPolicyVerified()
                                                    && inspections.stream().allMatch(Inspection::protectionPolicyVerified),
                                            accountRoles.hasSubdomainAdminResponsibilities(),
                                            accountRoles.hasUnresolvedAdminResponsibilities(),
                                            organizationIds,
                                            inspections.stream()
                                                    .map(Inspection::organizationInventory)
                                                    .toList()));
                        }))
                // Log lookup or inspection failures without swallowing the error or permitting deletion.
                .doOnError(exception -> LOG.error(
                        "Unable to load account deletion inventory for user {}", userId, exception));
    }

    @Override
    public Mono<Boolean> revalidateAccount(UUID userId, AccountInventory accountInventory) {
        return loadAccountDeletionInventory(userId)
                .map(accountInventory::equals);
    }

    @Override
    public Mono<Void> deleteClientData(UUID userId, UUID organizationId, ClientInventory clientInventory) {
        return eligibleOrganization(userId, organizationId)
                .flatMap(organizationInventory -> {
                    if (!organizationInventory.clients().contains(clientInventory)) {
                        return Mono.error(conflict("Client relationships changed before cleanup"));
                    }

                    return findUser(userId);
                })
                .flatMap(user -> delete(
                        authorizationEndpoint + "/users/{userId}/organizations/{organizationId}/clients/{clientId}"
                                + "?authenticationId={name}&issuer={issuer}",
                        userId, organizationId, clientInventory.clientId(), user.getAuthenticationId(), clientInventory.issuer()));
    }

    @Override
    public Mono<Void> deleteOrganizationRoleData(UUID userId, UUID organizationId) {
        return eligibleOrganization(userId, organizationId)
                .flatMap(organizationInventory -> {
                    if (!organizationInventory.clients()
                            .isEmpty()) {
                        return Mono.error(conflict("Clients must be removed before organization roles"));
                    }

                    return delete(roleEndpoint + "/users/{userId}/organizations/{organizationId}", userId, organizationId);
                });
    }

    @Override
    public Mono<Void> deleteOrganizationData(UUID userId, UUID organizationId) {
        // The role step already removed OrgAdmin assignments; recheck the remaining local ownership/member guards.
        return Mono.zip(
                        get(organizationEndpoint + "/users/" + userId + "/organizations/" + organizationId, OrganizationState.class),
                        get(roleEndpoint + "/users/" + userId + "/organizations/" + organizationId, OrganizationRoles.class),
                        get(roleEndpoint + "/users/" + userId, AccountRoles.class),
                        findUser(userId))
                .flatMap(states -> {
                    OrganizationState organizationState = states.getT1();
                    OrganizationRoles organizationRoles = states.getT2();
                    AccountRoles accountRoles = states.getT3();
                    requireVerifiedAccountRelationships(organizationState.relationshipChecksPassed() && organizationRoles.adminRoleAssignmentsValid() && accountRoles.roleInventoryLoaded()
                            && organizationId.equals(organizationState.organizationId())
                            && organizationId.equals(organizationRoles.organizationId()) && userId.equals(accountRoles.userId()));

                    if (!organizationState.protectionPolicyVerified() || !accountRoles.protectionPolicyVerified()
                            || organizationState.protectedResource() || organizationRoles.protectedResource()
                            || !userId.equals(organizationState.creatorUserId())
                            || !organizationState.memberUserIds().equals(Set.of(userId))
                            || !organizationRoles.orgAdminUserIds()
                                    .isEmpty() || !organizationRoles.clientIds()
                                    .isEmpty()
                            || accountRoles.hasSubdomainAdminResponsibilities() || accountRoles.hasUnresolvedAdminResponsibilities()) {
                        return Mono.error(conflict("Organization relationships changed before cleanup"));
                    }

                    return getClients(states.getT4(), organizationId);
                })
                .flatMap(clientState -> {
                    if (!clientState.clientInventoryLoaded() || !clientState.protectionPolicyVerified() || !clientState.clients()
                            .isEmpty()) {
                        return Mono.error(conflict("Organization clients remain or could not be verified"));
                    }

                    return delete(organizationEndpoint + "/users/{userId}/organizations/{organizationId}", userId, organizationId);
                });
    }

    @Override
    public Mono<Void> deletePersonalFiles(UUID userId) {
        return findUser(userId)
                .flatMap(user -> {
                    String prefix = profilePrefix + user.getId() + "/";
                    ListObjectsV2Request request = ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .build();

                    return Flux.defer(() -> Flux.from(s3AsyncClient.listObjectsV2Paginator(request)))
                            .concatMap(page -> {
                                if (page.sdkHttpResponse() == null || !page.sdkHttpResponse().isSuccessful()) {
                                    return Flux.error(new IllegalStateException("Unable to list profile photo objects"));
                                }

                                return Flux.fromIterable(page.contents());
                            })
                            .concatMap(object -> {
                                if (object.key() == null || !object.key().startsWith(prefix)) {
                                    return Mono.error(new IllegalStateException("Unexpected profile object key"));
                                }

                                return Mono.defer(() -> Mono.fromFuture(s3AsyncClient.deleteObject(DeleteObjectRequest.builder()
                                                .bucket(bucket)
                                                .key(object.key())
                                                .build())))
                                        .flatMap(response -> {
                                            if (response.sdkHttpResponse() == null || !response.sdkHttpResponse().isSuccessful()) {
                                                return Mono.error(new IllegalStateException("Profile object deletion failed"));
                                            }

                                            return Mono.just(true);
                                        });
                            })
                            .then();
                })
                .then();
    }

    private Mono<OrganizationInventory> eligibleOrganization(UUID userId, UUID organizationId) {
        return findUser(userId)
                .flatMap(user -> Mono.zip(
                        loadOrganizationDeletionInventory(user, organizationId),
                        get(roleEndpoint + "/users/" + userId, AccountRoles.class)))
                .map(states -> {
                    OrganizationInventory organizationInventory = states.getT1().organizationInventory();
                    AccountRoles accountRoles = states.getT2();
                    requireVerifiedAccountRelationships(accountRoles.roleInventoryLoaded() && userId.equals(accountRoles.userId()));

                    if (!states.getT1().protectionPolicyVerified() || !accountRoles.protectionPolicyVerified()
                            || accountRoles.hasSubdomainAdminResponsibilities() || accountRoles.hasUnresolvedAdminResponsibilities()
                            || !accountRoles.organizationIds().contains(organizationId) || !organizationInventory.organizationInspectionPassed()
                            || organizationInventory.protectedResource() || !userId.equals(organizationInventory.creatorUserId())
                            || !organizationInventory.memberUserIds().equals(Set.of(userId))
                            || !organizationInventory.orgAdminUserIds().equals(Set.of(userId))) {
                        throw conflict("Organization responsibilities, protection, or membership prevent cleanup");
                    }

                    for (ClientInventory clientInventory : organizationInventory.clients()) {
                        if (!clientInventory.clientInspectionPassed() || clientInventory.protectedResource()
                                || !clientInventory.organizationIds().equals(Set.of(organizationId))
                                || !Set.of(userId).containsAll(clientInventory.assignedUserIds())
                                || clientInventory.hasOtherUserAuthorizations()) {
                            throw conflict("Client protection or shared relationships prevent cleanup");
                        }
                    }

                    return organizationInventory;
                });
    }

    /**
     * Combines organization, role, and OAuth client relationships into one read-only inventory.
     * Example: Sonam wants to delete their account and is associated with AlexLab.
     * <pre>
     * Organization service:
     *   Creator: Sonam
     *   Members: [Sonam]
     *   Relationship checks passed: true
     *   Protected: false
     *
     * Role service:
     *   OrgAdmins: [Sonam]
     *   Clients referenced by role assignments: [client-A]
     *   Role checks passed: true
     *
     * Authorization service:
     *   OAuth clients: [client-A, client-B]
     *   client-A: associated with AlexLab, assigned to Sonam
     *   client-B: associated with AlexLab, no assigned users
     *   Other users' authorizations: none
     * </pre>
     * Cross-checks these responses and adds each client's role relationships to the inventory.
     * If members are [Sonam, Alex], both remain in the inventory so the coordinator can block
     * deletion. This method gathers and validates data; it does not decide eligibility or delete it.
     */
    private Mono<Inspection> loadOrganizationDeletionInventory(MyUser user, UUID organizationId) {
        return Mono.zip(
                        // Get the organization's creator, members, relationship-check results, and protection settings.
                        get(organizationEndpoint + "/users/" + user.getId() + "/organizations/" + organizationId,
                                OrganizationState.class),
                        // Get its admin user IDs, client IDs referenced by role assignments, and role-validation results.
                        get(roleEndpoint + "/users/" + user.getId() + "/organizations/" + organizationId,
                                OrganizationRoles.class),
                        // Get its OAuth clients and relationships, using this user's identity to detect other users' authorizations.
                        getClients(user, organizationId))
                .flatMap(states -> {
                    OrganizationState organizationState = states.getT1();
                    OrganizationRoles organizationRoles = states.getT2();
                    ClientState clientState = states.getT3();

                    // Require successful checks and confirm every response describes the requested organization.
                    requireVerifiedAccountRelationships(
                            organizationState.relationshipChecksPassed()
                            && organizationRoles.adminRoleAssignmentsValid()
                            && clientState.clientInventoryLoaded()
                            && organizationId.equals(organizationState.organizationId())
                            && organizationId.equals(organizationRoles.organizationId())
                            && organizationId.equals(clientState.organizationId()));

                    // Gather unique client IDs from the authorization service's inventory.
                    Set<UUID> clientIds = clientState.clients()
                            .stream()
                            .map(ClientInventory::clientId)
                            .collect(java.util.stream.Collectors.toSet());

                    // Reject duplicate client IDs or role assignments referencing a client missing from that inventory.
                    requireVerifiedAccountRelationships(
                            clientIds.size() == clientState.clients().size()
                            && clientIds.containsAll(organizationRoles.clientIds()));

                    // Inspect each client's role assignments one at a time to uncover other users or organizations.
                    return Flux.fromIterable(clientState.clients())
                            .concatMap(clientInventory -> get(
                                            roleEndpoint + "/clients/" + clientInventory.clientId(),
                                            ClientRoles.class)
                                    .map(clientRoles -> {
                                        // Trust role relationships only after confirming the lookup succeeded for this client.
                                        requireVerifiedAccountRelationships(
                                                clientRoles.clientRoleAssignmentsLoaded()
                                                && clientInventory.clientId().equals(clientRoles.clientId()));

                                        // Combine organization and user IDs from both services without duplicates.
                                        Set<UUID> organizationIds = new HashSet<>(clientInventory.organizationIds());
                                        organizationIds.addAll(clientRoles.organizationIds());

                                        Set<UUID> userIds = new HashSet<>(clientInventory.assignedUserIds());
                                        userIds.addAll(clientRoles.userIds());

                                        // Preserve the client's details while adding the combined relationships.
                                        return new ClientInventory(
                                                clientInventory.clientId(),
                                                clientInventory.issuer(),
                                                clientInventory.clientInspectionPassed(),
                                                clientInventory.protectedResource(),
                                                organizationIds,
                                                userIds,
                                                clientInventory.hasOtherUserAuthorizations());
                                    }))
                            // Keep clients in a stable order so a later inventory comparison detects real changes.
                            .sort(Comparator.comparing(ClientInventory::clientId))
                            .collectList()
                            // Return the read-only organization inventory and combined protection-policy status.
                            // Either service can mark the organization protected; both policies must be configured.
                            .map(clients -> {
                                boolean protectedResource = organizationState.protectedResource()
                                        || organizationRoles.protectedResource();
                                boolean protectionPolicyVerified = organizationState.protectionPolicyVerified()
                                        && clientState.protectionPolicyVerified();

                                OrganizationInventory organizationInventory = new OrganizationInventory(
                                        organizationId,
                                        organizationState.creatorUserId(),
                                        true,
                                        protectedResource,
                                        organizationState.memberUserIds(),
                                        organizationRoles.orgAdminUserIds(),
                                        clients);

                                return new Inspection(organizationInventory, protectionPolicyVerified);
                            });
                });
    }

    private Mono<ClientState> getClients(MyUser user, UUID organizationId) {
        return read(webClient.get()
                .uri(authorizationEndpoint + "/users/{userId}/organizations/{organizationId}?authenticationId={name}",
                        user.getId(), organizationId, user.getAuthenticationId()), ClientState.class);
    }

    private Mono<MyUser> findUser(UUID userId) {
        return Mono.defer(() -> userRepository.findById(userId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")));
    }

    /**
     * Creates a GET request and reads its JSON response as the supplied type, such as OrganizationState.
     */
    private <T> Mono<T> get(String endpoint, Class<T> responseType) {
        return read(webClient.get()
                .uri(endpoint), responseType);
    }

    /**
     * Requests JSON, requires HTTP 200, and converts the response body to the supplied type.
     * Propagates non-200 responses as errors and rejects an empty body with HTTP 503 so missing
     * inventory data cannot allow account deletion to proceed.
     */
    private <T> Mono<T> read(WebClient.RequestHeadersSpec<?> request, Class<T> responseType) {
        return request.accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(response -> {
                    if (response.statusCode()
                            .value() != 200) {
                        return response.createException()
                                .flatMap(Mono::error);
                    }

                    return response.bodyToMono(responseType)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.SERVICE_UNAVAILABLE, "Deletion inventory response was empty")));
                });
    }

    private Mono<Void> delete(String endpoint, Object... variables) {
        return webClient.delete()
                .uri(endpoint, variables)
                .exchangeToMono(response -> {
                    if (response.statusCode()
                            .value() != 204) {
                        return response.createException()
                                .flatMap(Mono::error);
                    }

                    return response.releaseBody();
                });
    }

    private void requireVerifiedAccountRelationships(boolean inspectionValid) {
        if (!inspectionValid) {
            LOG.warn("Account deletion inspection failed: required relationship data or consistency checks did not pass; returning HTTP 503");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account relationships could not be verified");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record Inspection(OrganizationInventory organizationInventory, boolean protectionPolicyVerified) {
    }

    public record RelatedOrganizations(UUID userId, boolean organizationLookupCompleted, Set<UUID> organizationIds) {
    }

    public record AccountRoles(UUID userId, boolean roleInventoryLoaded, boolean protectionPolicyVerified,
                               boolean hasSubdomainAdminResponsibilities, boolean hasUnresolvedAdminResponsibilities,
                               Set<UUID> organizationIds, Set<UUID> relatedOrganizationIds) {
    }

    /**
     * @param organizationId organization being inspected
     * @param creatorUserId user who originally created the organization
     * @param relationshipChecksPassed true when every default selection belongs to the creator
     *                                 and no owned position is referenced by another organization's membership;
     *                                 this alone does not mean deletion is allowed
     * @param protectionPolicyVerified true when the protected-organization policy was explicitly configured
     * @param protectedResource true when this organization is protected from account-deletion cleanup
     * @param memberUserIds unique IDs of all current organization members, including the requester if a member
     */
    public record OrganizationState(UUID organizationId, UUID creatorUserId, boolean relationshipChecksPassed,
                                    boolean protectionPolicyVerified, boolean protectedResource, Set<UUID> memberUserIds) {
    }

    /**
     * adminRoleAssignmentsValid requires recognized OrgAdmin assignments and no cross-organization
     * use of organization-owned roles. It does not establish that an administrator exists.
     */
    public record OrganizationRoles(UUID organizationId, boolean adminRoleAssignmentsValid, Set<UUID> orgAdminUserIds,
                                    Set<UUID> clientIds, boolean protectedResource) {
    }

    public record ClientState(UUID organizationId, boolean clientInventoryLoaded, boolean protectionPolicyVerified, List<ClientInventory> clients) {
    }

    public record ClientRoles(UUID clientId, boolean clientRoleAssignmentsLoaded, Set<UUID> organizationIds, Set<UUID> userIds) {
    }
}
