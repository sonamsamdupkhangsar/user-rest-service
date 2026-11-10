package me.sonam.user.handler;

import me.sonam.user.handler.AdminAccountDeletionOperations.AccountInventory;
import me.sonam.user.handler.AdminAccountDeletionOperations.ClientInventory;
import me.sonam.user.handler.AdminAccountDeletionOperations.OrganizationInventory;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates closing an OrgAdmin's sole-member organizations and personal account.
 * This is separate from the personal-only {@link RegularAccountDeletionService}.
 *
 * <p>The read-only check explains blockers. Deletion always creates a fresh assessment;
 * a successful earlier check is not authorization to delete. All organizations must
 * pass before cleanup starts. Merely having OAuth clients is allowed: eligible clients
 * are deleted automatically before their organization-owned roles and organization.
 *
 * <p>The caller must derive the authentication name from the signed-in identity and
 * enforce internal-service authorization, CSRF protection, and explicit UI confirmation.
 * Never expose these methods with a browser-selected target account.
 *
 * <p>Registered by AdminAccountDeletionConfig only when explicitly enabled, using the
 * concrete internal-service adapter. The authorization server's signed-in admin deletion page
 * delegates through guarded internal Router endpoints. Regular-user deletion remains separate.
 *
 * <p>Cross-service deletion is not atomic. Failure stops later work without rolling
 * back completed deletions. Rechecks reduce stale-plan risk but do not solve the known
 * concurrent membership/role-change race. Authorization-server passkey/grant cleanup
 * and logout must still run after this coordinator succeeds, as in the regular flow.
 */
public class AdminAccountDeletionService {
    private static final Logger LOG = LoggerFactory.getLogger(AdminAccountDeletionService.class);

    public static final String CONFIRMATION = "DELETE MY ACCOUNT AND ORGANIZATIONS";

    private final UserRepository userRepository;
    private final AdminAccountDeletionOperations adminAccountDeletionOperations;
    private final RegularAccountDeletionService regularAccountDeletionService;

    public AdminAccountDeletionService(
            UserRepository userRepository,
            AdminAccountDeletionOperations adminAccountDeletionOperations,
            RegularAccountDeletionService regularAccountDeletionService) {
        this.userRepository = userRepository;
        this.adminAccountDeletionOperations = adminAccountDeletionOperations;
        this.regularAccountDeletionService = regularAccountDeletionService;
    }

    /** Returns dialog-ready blockers without making any changes. */
    public Mono<DeletionCheck> checkByAuthenticationId(String authenticationId) {
        return findUser(authenticationId)
                .flatMap(this::assessDeletionEligibility)
                .map(Assessment::deletionCheck);
    }

    /** Rechecks all eligibility rules, closes eligible organizations, then deletes the account. */
    public Mono<Void> deleteByAuthenticationId(String authenticationId, String confirmation) {
        if (!CONFIRMATION.equals(confirmation)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Confirm deletion of your account, organizations, and OAuth clients"));
        }

        return findUser(authenticationId)
                .flatMap(user -> assessDeletionEligibility(user)
                        .flatMap(assessment -> {
                            if (!assessment.deletionCheck().eligible()) {
                                return Mono.error(new DeletionBlockedException(assessment.deletionCheck()));
                            }

                            return revalidateAll(user.getId(), assessment.accountInventory())
                                    .then(closeOrganizations(user.getId(), assessment.accountInventory()))
                                    .then(Mono.defer(() ->
                                            adminAccountDeletionOperations.deletePersonalFiles(user.getId())))
                                    // This repeats the zero-admin check after organization admin assignments are removed.
                                    .then(Mono.defer(() -> regularAccountDeletionService
                                            .deleteByAuthenticationId(user.getAuthenticationId())));
                        }));
    }

    private Mono<MyUser> findUser(String authenticationId) {
        return Mono.defer(() -> userRepository.findByAuthenticationIdIgnoreCase(authenticationId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found")));
    }

    private Mono<Assessment> assessDeletionEligibility(MyUser user) {
        return Mono.defer(() -> adminAccountDeletionOperations.loadAccountDeletionInventory(user.getId()))
                .switchIfEmpty(Mono.error(new IllegalStateException("Account inventory was not returned")))
                .map(accountInventory -> new Assessment(accountInventory, validate(user.getId(), accountInventory)))
                .onErrorResume(error -> {
                    LOG.warn("Unable to verify admin-account deletion relationships for user {}", user.getId(), error);

                    return Mono.just(new Assessment(
                            null,
                            new DeletionCheck(List.of(new Blocker(
                                    "VERIFICATION_UNAVAILABLE",
                                    "We could not verify all account relationships. No deletion can start.",
                                    null,
                                    null)))));
                });
    }

    private DeletionCheck validate(UUID userId, AccountInventory accountInventory) {
        List<Blocker> blockers = new ArrayList<>();

        if (!userId.equals(accountInventory.userId()) || !accountInventory.accountInventoryLoaded()
                || accountInventory.relatedOrganizationIds() == null || accountInventory.organizations() == null) {
            blockers.add(blocker("INCOMPLETE_INVENTORY", "All account relationships must be verified first.", null, null));
            return new DeletionCheck(blockers);
        }

        if (!accountInventory.protectionPolicyVerified()) {
            blockers.add(blocker("PROTECTION_UNVERIFIED", "Platform and infrastructure resource protection must be verified.", null, null));
        }

        if (accountInventory.hasSubdomainAdminResponsibilities()) {
            blockers.add(blocker("SUBDOMAIN_ADMIN", "Transfer or remove your SubdomainAdmin responsibilities first.", null, null));
        }

        if (accountInventory.hasUnresolvedAdminResponsibilities()) {
            blockers.add(blocker("OTHER_ADMIN_RESPONSIBILITIES", "Resolve your other administrative responsibilities first.", null, null));
        }

        Set<UUID> inspectedOrganizationIds = new HashSet<>();
        Set<UUID> inspectedClientIds = new HashSet<>();

        for (OrganizationInventory organizationInventory : accountInventory.organizations()) {
            if (organizationInventory == null || organizationInventory.organizationId() == null
                    || !inspectedOrganizationIds.add(organizationInventory.organizationId())) {
                blockers.add(blocker("INCOMPLETE_INVENTORY", "The organization inventory is inconsistent.", null, null));
                continue;
            }

            validateOrganization(userId, organizationInventory, inspectedClientIds, blockers);
        }

        if (inspectedOrganizationIds.isEmpty()
                || !inspectedOrganizationIds.equals(accountInventory.relatedOrganizationIds())) {
            blockers.add(blocker("INCOMPLETE_INVENTORY", "Every owned, joined, or administered organization must be checked.", null, null));
        }

        return new DeletionCheck(blockers);
    }

    private void validateOrganization(
            UUID userId,
            OrganizationInventory organizationInventory,
            Set<UUID> inspectedClientIds,
            List<Blocker> blockers) {
        UUID organizationId = organizationInventory.organizationId();

        if (!organizationInventory.organizationInspectionPassed() || organizationInventory.memberUserIds() == null
                || organizationInventory.orgAdminUserIds() == null || organizationInventory.clients() == null) {
            blockers.add(blocker("INCOMPLETE_ORGANIZATION", "Organization relationships could not be verified.", organizationId, null));
            return;
        }

        if (organizationInventory.protectedResource()) {
            blockers.add(blocker("PROTECTED_ORGANIZATION", "A platform or protected organization cannot be closed here.", organizationId, null));
        }

        if (!userId.equals(organizationInventory.creatorUserId())) {
            blockers.add(blocker("OTHER_CREATOR", "An organization created by another user must be resolved manually.", organizationId, null));
        }

        if (!organizationInventory.memberUserIds().equals(Set.of(userId))) {
            blockers.add(blocker("OTHER_OR_INCONSISTENT_MEMBERS", "Remove other members manually; you must be the organization's sole member.", organizationId, null));
        }

        if (!organizationInventory.orgAdminUserIds().equals(Set.of(userId))) {
            blockers.add(blocker("OTHER_OR_MISSING_ADMIN", "You must be the sole OrgAdmin; resolve any other administrator assignments first.", organizationId, null));
        }

        for (ClientInventory clientInventory : organizationInventory.clients()) {
            if (clientInventory == null || clientInventory.clientId() == null
                    || !inspectedClientIds.add(clientInventory.clientId()) || !clientInventory.clientInspectionPassed()
                    || clientInventory.issuer() == null || clientInventory.issuer().isBlank()
                    || clientInventory.organizationIds() == null || clientInventory.assignedUserIds() == null) {
                blockers.add(blocker("INCOMPLETE_CLIENT", "Every client and its relationships must be verified.", organizationId, null));
                continue;
            }

            if (clientInventory.protectedResource()) {
                blockers.add(blocker("PROTECTED_CLIENT", "An infrastructure or protected client cannot be deleted here.", organizationId, clientInventory.clientId()));
            }

            if (!clientInventory.organizationIds().equals(Set.of(organizationId))) {
                blockers.add(blocker("SHARED_CLIENT", "Resolve clients shared with another organization first.", organizationId, clientInventory.clientId()));
            }

            if (!Set.of(userId).containsAll(clientInventory.assignedUserIds())
                    || clientInventory.hasOtherUserAuthorizations()) {
                blockers.add(blocker("OTHER_CLIENT_USERS", "Remove other users' client access and grants first.", organizationId, clientInventory.clientId()));
            }
        }
    }

    private Mono<Void> revalidateAll(UUID userId, AccountInventory accountInventory) {
        return Mono.defer(() -> adminAccountDeletionOperations.revalidateAccount(userId, accountInventory))
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Account relationships changed. Review deletion again before retrying.")))
                .then();
    }

    private Mono<Void> closeOrganizations(UUID userId, AccountInventory accountInventory) {
        return Flux.fromIterable(accountInventory.organizations())
                .concatMap(organizationInventory -> Flux.fromIterable(organizationInventory.clients())
                        .concatMap(clientInventory -> Mono.defer(() -> adminAccountDeletionOperations.deleteClientData(
                                userId,
                                organizationInventory.organizationId(),
                                clientInventory)))
                        .then(Mono.defer(() -> adminAccountDeletionOperations.deleteOrganizationRoleData(
                                userId,
                                organizationInventory.organizationId())))
                        .then(Mono.defer(() -> adminAccountDeletionOperations.deleteOrganizationData(
                                userId,
                                organizationInventory.organizationId()))))
                .then();
    }

    private Blocker blocker(String code, String message, UUID organizationId, UUID clientId) {
        return new Blocker(code, message, organizationId, clientId);
    }

    public record Blocker(String code, String message, UUID organizationId, UUID clientId) {
    }

    public record DeletionCheck(List<Blocker> blockers) {
        public DeletionCheck {
            blockers = List.copyOf(Objects.requireNonNull(blockers));
        }

        public boolean eligible() {
            return blockers.isEmpty();
        }
    }

    private record Assessment(AccountInventory accountInventory, DeletionCheck deletionCheck) {
    }

    public static class DeletionBlockedException extends ResponseStatusException {
        private final DeletionCheck deletionCheck;

        public DeletionBlockedException(DeletionCheck deletionCheck) {
            super(HttpStatus.CONFLICT, "Resolve account-deletion blockers before deleting your account");
            this.deletionCheck = deletionCheck;
        }

        public DeletionCheck getDeletionCheck() {
            return deletionCheck;
        }
    }
}
