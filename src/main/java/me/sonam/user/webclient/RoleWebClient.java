package me.sonam.user.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;

public class RoleWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(RoleWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String deleteMyRole;

    public RoleWebClient(WebClient.Builder webClientBuilder,
                         String deleteMyRole) {
        this.webClientBuilder = webClientBuilder;
        this.deleteMyRole = deleteMyRole;
    }

    public Mono<Long> adminAssignmentCount(UUID userId) {
        return webClientBuilder.build()
                .get()
                .uri(deleteMyRole + "/internal/accounts/{id}/admin-count", userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Long>>() {})
                .flatMap(body -> body.get("count") == null ? Mono.error(new IllegalStateException("Unable to verify admin roles"))
                        : Mono.just(body.get("count")))
                .switchIfEmpty(Mono.error(new IllegalStateException("Unable to verify admin roles")));
    }

    public Mono<Void> deletePersonalAssignments(UUID userId) {
        return webClientBuilder.build()
                .delete()
                .uri(deleteMyRole + "/internal/accounts/{id}/assignments", userId)
                .exchangeToMono(response -> response.statusCode().value() == 204 ? response.releaseBody()
                        : response.createException().flatMap(Mono::error));
    }

    public Mono<Boolean> hasOtherAdminScopes(String accessToken) {
        return adminScopeCount(accessToken, "organizations")
                .zipWith(adminScopeCount(accessToken, "subdomains"))
                .map(counts -> counts.getT1() != 1 || counts.getT2() != 0);
    }

    private Mono<Integer> adminScopeCount(String accessToken, String scope) {
        return webClientBuilder.build()
                .get()
                .uri(deleteMyRole + "/authzmanagerroles/users/" + scope + "/count")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Integer>>() {})
                .flatMap(body -> body.get("message") == null ? Mono.error(new IllegalStateException("Unable to verify admin assignments"))
                        : Mono.just(body.get("message")))
                .switchIfEmpty(Mono.error(new IllegalStateException("Unable to verify admin assignments")));
    }

    public Mono<String> deleteUserData(UUID organizationId, UUID userId) {
        return deleteUserData(organizationId, userId, true);
    }

    public Mono<String> deleteUserData(UUID organizationId, UUID userId, boolean preserveOrganization) {
        final String endpoint = deleteMyRole + "/organizations/" + organizationId + "/users/" + userId
                + "?preserveOrganization=" + preserveOrganization;
        LOG.info("delete my role endpoint: {}", endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build()
                .delete()
                .uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }

    public Mono<Boolean> isOrgAdmin(String accessToken, UUID userId, UUID organizationId) {
        return webClientBuilder.build()
                .get()
                .uri(deleteMyRole + "/authzmanagerroles/users/{userId}/organizations/{orgId}/org-admin", userId, organizationId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {})
                .map(body -> Boolean.TRUE.equals(body.get("message")))
                .defaultIfEmpty(false);
    }
}
