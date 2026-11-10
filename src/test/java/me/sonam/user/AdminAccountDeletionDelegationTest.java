package me.sonam.user;

import me.sonam.user.handler.AdminAccountDeletionHandler;
import me.sonam.user.handler.AdminAccountDeletionService;
import me.sonam.user.security.InternalServiceAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class AdminAccountDeletionDelegationTest {
    private final AdminAccountDeletionService adminAccountDeletionService = mock(AdminAccountDeletionService.class);
    private final InternalServiceAuthorization internalServiceAuthorization = mock(InternalServiceAuthorization.class);
    private final WebTestClient webTestClient = WebTestClient.bindToRouterFunction(new Router().adminAccountDeletionRoutes(
            new AdminAccountDeletionHandler(adminAccountDeletionService, internalServiceAuthorization))).build();
    private final String endpoint = "/users/account/internal/admin+test@example.com";

    @Test
    void untrustedRequestsCannotCheckOrDelete() {
        when(internalServiceAuthorization.requireInternalServiceIdentity())
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));
        webTestClient.get().uri(endpoint + "/admin-deletion-check").accept(MediaType.APPLICATION_JSON)
                .exchange().expectStatus().isForbidden();
        webTestClient.post().uri(endpoint + "/admin-deletion").accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("confirmation", "DELETE MY ACCOUNT AND ORGANIZATIONS"))
                .exchange().expectStatus().isForbidden();
        verifyNoInteractions(adminAccountDeletionService);
    }

    @Test
    void checkIsReadOnlyAndDeletionPassesConfirmationToCoordinator() {
        when(internalServiceAuthorization.requireInternalServiceIdentity()).thenReturn(Mono.empty());
        when(adminAccountDeletionService.checkByAuthenticationId("admin+test@example.com"))
                .thenReturn(Mono.just(new AdminAccountDeletionService.DeletionCheck(List.of())));
        webTestClient.get().uri(endpoint + "/admin-deletion-check").accept(MediaType.APPLICATION_JSON)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.blockers").isEmpty();
        verify(adminAccountDeletionService, never()).deleteByAuthenticationId(any(), any());
        when(adminAccountDeletionService.deleteByAuthenticationId("admin+test@example.com", "DELETE MY ACCOUNT AND ORGANIZATIONS"))
                .thenReturn(Mono.empty());
        webTestClient.post().uri(endpoint + "/admin-deletion").accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("confirmation", "DELETE MY ACCOUNT AND ORGANIZATIONS"))
                .exchange().expectStatus().isNoContent();
        verify(adminAccountDeletionService).deleteByAuthenticationId("admin+test@example.com", "DELETE MY ACCOUNT AND ORGANIZATIONS");
    }
}
