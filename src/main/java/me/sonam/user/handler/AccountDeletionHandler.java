package me.sonam.user.handler;

import me.sonam.user.security.InternalServiceAuthorization;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import java.util.Map;

@Service
public class AccountDeletionHandler {
    private final RegularAccountDeletionService regularAccountDeletionService;
    private final InternalServiceAuthorization internalServiceAuthorization;

    public AccountDeletionHandler(
            RegularAccountDeletionService regularAccountDeletionService,
            InternalServiceAuthorization internalServiceAuthorization) {
        this.regularAccountDeletionService = regularAccountDeletionService;
        this.internalServiceAuthorization = internalServiceAuthorization;
    }

    // The configured internal client delegates the authenticated user's identity.
    public Mono<ServerResponse> checkDelegated(ServerRequest request) {
        String authenticationId = request.pathVariable("authenticationId");

        return internalServiceAuthorization.requireInternalServiceIdentity()
                .then(Mono.defer(() -> regularAccountDeletionService.checkByAuthenticationId(authenticationId)))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("eligible", true)));
    }

    public Mono<ServerResponse> deleteDelegated(ServerRequest request) {
        String authenticationId = request.pathVariable("authenticationId");

        return internalServiceAuthorization.requireInternalServiceIdentity()
                .then(Mono.defer(() -> regularAccountDeletionService.deleteByAuthenticationId(authenticationId)))
                .then(ServerResponse.noContent().build());
    }
}
