package me.sonam.user;

import me.sonam.user.handler.AccountDeletionHandler;
import me.sonam.user.handler.RegularAccountDeletionService;
import me.sonam.user.security.InternalServiceAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.*;

class AccountDeletionDelegationTest {
    private final RegularAccountDeletionService regularAccountDeletionService = mock(RegularAccountDeletionService.class);
    private final InternalServiceAuthorization internalServiceAuthorization = mock(InternalServiceAuthorization.class);
    private final AccountDeletionHandler accountDeletionHandler = new AccountDeletionHandler(regularAccountDeletionService, internalServiceAuthorization);
    private final WebTestClient webTestClient = WebTestClient
            .bindToRouterFunction(new Router().accountDeletionRoutes(accountDeletionHandler))
            .build();

    @Test
    void untrustedCallerCannotSelectAnAccountForReadOrDeletion() {
        when(internalServiceAuthorization.requireService())
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));

        webTestClient.get()
                .uri("/users/account/internal/another-user/deletion-check")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.delete()
                .uri("/users/account/internal/another-user")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(regularAccountDeletionService);
    }

    @Test
    void eligibilityIsReadOnlyAndSuccessfulDelegationUsesTheGivenServerIdentity() {
        String name = "regular+test@example.com";
        when(internalServiceAuthorization.requireService())
                .thenReturn(Mono.empty());
        when(regularAccountDeletionService.checkByAuthenticationId(name))
                .thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/users/account/internal/{authenticationId}/deletion-check", name)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.eligible").isEqualTo(true);

        verify(regularAccountDeletionService, never()).deleteByAuthenticationId(any());

        when(regularAccountDeletionService.deleteByAuthenticationId(name))
                .thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/users/account/internal/{authenticationId}", name)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(regularAccountDeletionService).deleteByAuthenticationId(name);
    }

    @Test
    void adminConflictDoesNotBecomeSuccessfulDeletion() {
        when(internalServiceAuthorization.requireService())
                .thenReturn(Mono.empty());
        when(regularAccountDeletionService.deleteByAuthenticationId("admin-user"))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Admin roles remain")));

        webTestClient.delete()
                .uri("/users/account/internal/admin-user")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void failedEligibilityDoesNotReturnAnEligibleResponse() {
        when(internalServiceAuthorization.requireService())
                .thenReturn(Mono.empty());
        when(regularAccountDeletionService.checkByAuthenticationId("regular-user"))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)));

        webTestClient.get()
                .uri("/users/account/internal/regular-user/deletion-check")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        verify(regularAccountDeletionService, never()).deleteByAuthenticationId(any());
    }
}
