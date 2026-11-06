package me.sonam.user.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Flux;

public class OrganizationWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String deleteMyOrganization;

    public OrganizationWebClient(WebClient.Builder webClientBuilder,
                                 String deleteMyOrganization) {
        this.webClientBuilder = webClientBuilder;
        this.deleteMyOrganization = deleteMyOrganization;
    }

    public Mono<Void> deletePersonalMemberships(UUID userId) {
        return webClientBuilder.build()
                .delete()
                .uri(deleteMyOrganization + "/internal/accounts/{id}/memberships", userId)
                .exchangeToMono(response -> response.statusCode().value() == 204 ? response.releaseBody()
                        : response.createException().flatMap(Mono::error));
    }

    public Mono<String> deleteUserData(UUID organizationId, UUID userId) {
        final String endpoint = deleteMyOrganization + "/" + organizationId + "/users/" + userId + "/data";
        LOG.info("delete my organization account endpoint: {}", endpoint);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build()
                .delete()
                .uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }

    public Mono<List<UUID>> getOrganizationIds(UUID userId) {
        return webClientBuilder.build()
                .get()
                .uri(deleteMyOrganization + "/users/{userId}/ids", userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UUID>>() {});
    }

    public Mono<UUID> getDefaultOrganizationId(UUID userId, String host) {
        return webClientBuilder.build()
                .get()
                .uri(deleteMyOrganization + "/subdomain/{host}/users/{userId}/default-organization-id", host, userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(body -> body.get("message") == null ? Mono.empty()
                        : Mono.just(UUID.fromString(body.get("message").toString())));
    }

    public Mono<List<UUID>> getMembers(UUID organizationId) {
        return memberPage(organizationId, 0)
                .switchIfEmpty(Mono.error(new IllegalStateException("Unable to verify organization members")))
                .expand(page -> page.number() + 1 < page.totalPages()
                        ? memberPage(organizationId, page.number() + 1)
                            .switchIfEmpty(Mono.error(new IllegalStateException("Unable to verify organization members")))
                        : Mono.empty())
                .collectList()
                .flatMap(pages -> {
                    var ids = pages.stream()
                            .flatMap(page -> page.content().stream())
                            .distinct()
                            .toList();

                    if (pages.stream().anyMatch(page -> page.totalElements() != ids.size())) {
                        return Mono.error(new IllegalStateException("Organization membership changed"));
                    }

                    return Mono.just(ids);
                });
    }

    private Mono<MemberPage> memberPage(UUID organizationId, int page) {
        return webClientBuilder.build()
                .get()
                .uri(deleteMyOrganization + "/{orgId}/users?page={page}&size=100", organizationId, page)
                .retrieve()
                .bodyToMono(MemberPage.class);
    }

    public record MemberPage(List<UUID> content, int number, long totalElements, int totalPages) {
    }
}
