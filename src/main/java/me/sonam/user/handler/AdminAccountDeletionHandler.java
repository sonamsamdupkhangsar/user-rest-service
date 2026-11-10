package me.sonam.user.handler;

import me.sonam.user.security.InternalServiceAuthorization;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Accepts only a trusted service's delegation of the signed-in user's identity. */
@Service
@ConditionalOnProperty(name = "account-deletion.admin-enabled", havingValue = "true")
public class AdminAccountDeletionHandler {
    private final AdminAccountDeletionService adminAccountDeletionService;
    private final InternalServiceAuthorization internalServiceAuthorization;

    public AdminAccountDeletionHandler(AdminAccountDeletionService adminAccountDeletionService,
            InternalServiceAuthorization internalServiceAuthorization) {
        this.adminAccountDeletionService = adminAccountDeletionService;
        this.internalServiceAuthorization = internalServiceAuthorization;
    }

    public Mono<ServerResponse> checkDelegated(ServerRequest request) {
        return internalServiceAuthorization.requireInternalServiceIdentity()
                .then(Mono.defer(() -> adminAccountDeletionService
                        .checkByAuthenticationId(request.pathVariable("authenticationId"))))
                .flatMap(check -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(check));
    }

    public Mono<ServerResponse> deleteDelegated(ServerRequest request) {
        return internalServiceAuthorization.requireInternalServiceIdentity()
                .then(Mono.defer(() -> request.bodyToMono(Confirmation.class)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation is required")))
                .flatMap(confirmation -> adminAccountDeletionService.deleteByAuthenticationId(
                        request.pathVariable("authenticationId"), confirmation.confirmation()))
                .then(ServerResponse.noContent().build())
                .onErrorResume(AdminAccountDeletionService.DeletionBlockedException.class,
                        error -> ServerResponse.status(HttpStatus.CONFLICT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(error.getDeletionCheck()));
    }

    public record Confirmation(String confirmation) {
    }
}
