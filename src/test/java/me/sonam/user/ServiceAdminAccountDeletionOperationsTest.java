package me.sonam.user;

import cloud.sonam.s3.config.S3ClientConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.sonam.user.handler.AdminAccountDeletionOperations.AccountInventory;
import me.sonam.user.handler.AdminAccountDeletionOperations.ClientInventory;
import me.sonam.user.handler.AdminAccountDeletionService;
import me.sonam.user.handler.RegularAccountDeletionService;
import me.sonam.user.handler.ServiceAdminAccountDeletionOperations;
import me.sonam.user.handler.ServiceAdminAccountDeletionOperations.*;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceAdminAccountDeletionOperationsTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final String authenticationId = "owner+free@example.com";
    private final String issuer = "https://free.example/issuer";
    private final String orgPath = "/organizations/internal/admin-deletion/users/" + userId + "/organizations/" + organizationId;
    private final String rolePath = "/roles/internal/admin-deletion/users/" + userId + "/organizations/" + organizationId;
    private final String clientPath = "/clients/internal/admin-deletion/users/" + userId + "/organizations/" + organizationId;
    private final String photoPrefix = "authzmanager/photos/profile/" + userId + "/";
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RegularAccountDeletionService regularAccountDeletionService = mock(RegularAccountDeletionService.class);
    private final S3AsyncClient s3AsyncClient = mock(S3AsyncClient.class);
    private final S3ClientConfigurationProperties s3ClientConfigurationProperties = mock(S3ClientConfigurationProperties.class);
    private final List<ClientRequest> requests = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ServiceAdminAccountDeletionOperations serviceAdminAccountDeletionOperations;
    private boolean clientsPresent = true;
    private boolean rolesPresent = true;
    private boolean orgPresent = true;
    private boolean otherMember;
    private boolean protectedPolicyVerified = true;
    private boolean otherClientUser;
    private boolean orphanRoleClient;
    private int forcedReadStatus = 200;
    private int forcedDeleteStatus = 204;

    ServiceAdminAccountDeletionOperationsTest() {
        MyUser user = new MyUser(userId, "Owner", "User", authenticationId, authenticationId, true);
        when(userRepository.findById(userId))
                .thenReturn(Mono.just(user));
        when(userRepository.findByAuthenticationIdIgnoreCase(authenticationId))
                .thenReturn(Mono.just(user));
        when(regularAccountDeletionService.deleteByAuthenticationId(authenticationId))
                .thenReturn(Mono.empty());
        when(s3ClientConfigurationProperties.getRootPath())
                .thenReturn("authzmanager/");
        when(s3ClientConfigurationProperties.getPhotoPath())
                .thenReturn("photos/");
        when(s3ClientConfigurationProperties.getBucket())
                .thenReturn("test-bucket");
        when(s3AsyncClient.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenAnswer(invocation -> new ListObjectsV2Publisher(s3AsyncClient, invocation.getArgument(0)));
        when(s3AsyncClient.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(page(false, null)));
        when(s3AsyncClient.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(deletedResponse(204)));

        WebClient.Builder webClientBuilder = WebClient.builder()
                .defaultHeader("Authorization", "Bearer internal-test-token")
                .exchangeFunction(request -> {
                    requests.add(request);
                    return response(request);
                });
        serviceAdminAccountDeletionOperations = new ServiceAdminAccountDeletionOperations(
                userRepository, webClientBuilder, s3AsyncClient, s3ClientConfigurationProperties,
                "http://organization/organizations", "http://role/roles",
                "http://authorization/clients/internal/admin-deletion", "profile/");
    }

    @Test
    void realAdapterCombinesOrganizationRoleAndLegacyClientReadsWithoutDeleting() {
        AccountInventory accountInventory = serviceAdminAccountDeletionOperations.loadAccountDeletionInventory(userId).block();

        assertThat(accountInventory.accountInventoryLoaded()).isTrue();
        assertThat(accountInventory.relatedOrganizationIds()).containsExactly(organizationId);
        assertThat(accountInventory.organizations()).hasSize(1);
        assertThat(accountInventory.organizations().getFirst().clients()).containsExactly(clientInventory());
        assertThat(requests).allMatch(request -> request.method() == HttpMethod.GET);
        assertThat(requests).allMatch(request -> "Bearer internal-test-token".equals(request.headers().getFirst("Authorization")));
        assertThat(requests.stream()
                .filter(request -> request.url().getPath().equals(clientPath))
                .findFirst()
                .orElseThrow()
                .url().getRawQuery()).contains("owner%2Bfree%40example.com");
    }

    @Test
    void concreteAdapterAndCoordinatorPerformCleanupInOrder() {
        AdminAccountDeletionService adminAccountDeletionService = new AdminAccountDeletionService(
                userRepository, serviceAdminAccountDeletionOperations, regularAccountDeletionService);

        adminAccountDeletionService.deleteByAuthenticationId(authenticationId, AdminAccountDeletionService.CONFIRMATION).block();

        assertThat(requests.stream()
                .filter(request -> request.method() == HttpMethod.DELETE)
                .map(request -> request.url().getPath())
                .toList())
                .containsExactly(clientPath + "/clients/" + clientId, rolePath, orgPath);
        verify(s3AsyncClient).listObjectsV2(argThat((ListObjectsV2Request request) -> request.prefix().equals(photoPrefix)));
        verify(regularAccountDeletionService).deleteByAuthenticationId(authenticationId);
    }

    @Test
    void revalidationDetectsMembershipChanges() {
        AccountInventory accountInventory = serviceAdminAccountDeletionOperations.loadAccountDeletionInventory(userId).block();
        otherMember = true;

        assertThat(serviceAdminAccountDeletionOperations.revalidateAccount(userId, accountInventory).block()).isFalse();
        assertThat(requests).noneMatch(request -> request.method() == HttpMethod.DELETE);
    }

    @Test
    void organizationWithoutOauthClientsCanBeClosed() {
        clientsPresent = false;
        AdminAccountDeletionService adminAccountDeletionService = new AdminAccountDeletionService(
                userRepository, serviceAdminAccountDeletionOperations, regularAccountDeletionService);

        adminAccountDeletionService.deleteByAuthenticationId(authenticationId, AdminAccountDeletionService.CONFIRMATION).block();

        assertThat(requests.stream()
                .filter(request -> request.method() == HttpMethod.DELETE)
                .map(request -> request.url().getPath())
                .toList())
                .containsExactly(rolePath, orgPath);
        verify(regularAccountDeletionService).deleteByAuthenticationId(authenticationId);
    }

    @Test
    void protectedPolicyAndOtherClientUsersBlockDirectCleanupOperations() {
        protectedPolicyVerified = false;
        assertThatThrownBy(() -> serviceAdminAccountDeletionOperations.deleteClientData(userId, organizationId, clientInventory()).block())
                .hasMessageContaining("protection");

        protectedPolicyVerified = true;
        otherClientUser = true;
        assertThatThrownBy(() -> serviceAdminAccountDeletionOperations.deleteClientData(userId, organizationId, clientInventory()).block())
                .hasMessageContaining("Client protection");

        assertThat(requests).noneMatch(request -> request.method() == HttpMethod.DELETE);
    }

    @Test
    void roleOnlyClientMissingFromAuthorizationInventoryFailsClosed() {
        clientsPresent = false;
        orphanRoleClient = true;

        assertThatThrownBy(() -> serviceAdminAccountDeletionOperations.loadAccountDeletionInventory(userId).block())
                .hasMessageContaining("could not be verified");
        assertThat(requests).noneMatch(request -> request.method() == HttpMethod.DELETE);
    }

    @Test
    void redirectsAreNotAcceptedAsInventoryOrDeletionSuccess() {
        forcedReadStatus = 302;
        assertThatThrownBy(() -> serviceAdminAccountDeletionOperations.loadAccountDeletionInventory(userId).block())
                .hasMessageContaining("302");

        forcedReadStatus = 200;
        forcedDeleteStatus = 200;
        assertThatThrownBy(() -> serviceAdminAccountDeletionOperations.deleteClientData(userId, organizationId, clientInventory()).block())
                .hasMessageContaining("200");
        assertThat(clientsPresent).isTrue();
    }

    @Test
    void storageCleanupPaginatesAndAwaitsBothObjectDeletes() {
        when(s3AsyncClient.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenAnswer(invocation -> {
                    ListObjectsV2Request request = invocation.getArgument(0);
                    return CompletableFuture.completedFuture(request.continuationToken() == null
                            ? page(true, "next", photoPrefix + "photo.png")
                            : page(false, null, photoPrefix + "thumbnail.png"));
                });

        serviceAdminAccountDeletionOperations.deletePersonalFiles(userId).block();

        verify(s3AsyncClient).deleteObject(argThat((DeleteObjectRequest request) -> request.key().equals(photoPrefix + "photo.png")));
        verify(s3AsyncClient).deleteObject(argThat((DeleteObjectRequest request) -> request.key().equals(photoPrefix + "thumbnail.png")));
    }

    @Test
    void storageDeletionFailureIsNotReportedAsSuccess() {
        when(s3AsyncClient.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(page(false, null, photoPrefix + "photo.png")));
        when(s3AsyncClient.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(deletedResponse(500)));

        assertThatThrownBy(() -> serviceAdminAccountDeletionOperations.deletePersonalFiles(userId).block())
                .hasMessageContaining("object deletion failed");
    }

    @Test
    void storageKeyOutsideExactUserPrefixIsNeverDeleted() {
        when(s3AsyncClient.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(page(false, null, "authzmanager/photos/profile/another-user/photo.png")));

        assertThatThrownBy(() -> serviceAdminAccountDeletionOperations.deletePersonalFiles(userId).block())
                .hasMessageContaining("Unexpected profile object key");
        verify(s3AsyncClient, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    private Mono<ClientResponse> response(ClientRequest request) {
        String path = request.url().getPath();

        if (request.method() == HttpMethod.DELETE) {
            if (forcedDeleteStatus != 204) {
                return Mono.just(ClientResponse.create(org.springframework.http.HttpStatusCode.valueOf(forcedDeleteStatus))
                        .build());
            }

            if (path.equals(clientPath + "/clients/" + clientId)) {
                clientsPresent = false;
            } else if (path.equals(rolePath)) {
                rolesPresent = false;
            } else if (path.equals(orgPath)) {
                orgPresent = false;
            } else {
                return Mono.error(new AssertionError("Unexpected deletion path: " + path));
            }

            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.NO_CONTENT)
                    .build());
        }

        if (forcedReadStatus != 200) {
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatusCode.valueOf(forcedReadStatus))
                    .build());
        }

        Object body;
        if (path.equals("/roles/internal/admin-deletion/users/" + userId)) {
            body = new AccountRoles(userId, true, protectedPolicyVerified, false, false,
                    rolesPresent ? Set.of(organizationId) : Set.of(), Set.of());
        } else if (path.equals("/organizations/internal/admin-deletion/users/" + userId)) {
            body = new RelatedOrganizations(userId, true, orgPresent ? Set.of(organizationId) : Set.of());
        } else if (path.equals("/clients/internal/admin-deletion/users/" + userId)) {
            body = new RelatedOrganizations(userId, true, clientsPresent ? Set.of(organizationId) : Set.of());
        } else if (path.equals(orgPath)) {
            body = new OrganizationState(organizationId, userId, true, protectedPolicyVerified, false,
                    otherMember ? Set.of(userId, UUID.randomUUID()) : Set.of(userId));
        } else if (path.equals(rolePath)) {
            body = new OrganizationRoles(organizationId, true, rolesPresent ? Set.of(userId) : Set.of(),
                    clientsPresent || orphanRoleClient ? Set.of(clientId) : Set.of(), false);
        } else if (path.equals(clientPath)) {
            body = new ClientState(organizationId, true, protectedPolicyVerified,
                    clientsPresent ? List.of(clientInventory()) : List.of());
        } else if (path.equals("/roles/internal/admin-deletion/clients/" + clientId)) {
            body = new ClientRoles(clientId, true, Set.of(organizationId),
                    otherClientUser ? Set.of(UUID.randomUUID()) : Set.of(userId));
        } else {
            return Mono.error(new AssertionError("Unexpected read path: " + path));
        }

        try {
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(objectMapper.writeValueAsString(body))
                    .build());
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    private ClientInventory clientInventory() {
        return new ClientInventory(clientId, issuer, true, false, Set.of(organizationId), Set.of(userId), false);
    }

    private ListObjectsV2Response page(boolean truncated, String token, String... keys) {
        ListObjectsV2Response.Builder builder = ListObjectsV2Response.builder()
                .isTruncated(truncated)
                .nextContinuationToken(token)
                .contents(java.util.Arrays.stream(keys)
                .map(key -> S3Object.builder().key(key)
                .build())
                .toList());
        builder.sdkHttpResponse(SdkHttpResponse.builder().statusCode(200)
                .build());

        return builder.build();
    }

    private DeleteObjectResponse deletedResponse(int status) {
        DeleteObjectResponse.Builder builder = DeleteObjectResponse.builder();
        builder.sdkHttpResponse(SdkHttpResponse.builder().statusCode(status)
                .build());

        return builder.build();
    }
}
