package me.sonam.user.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class RoleWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(RoleWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String deleteMyRole;

    public RoleWebClient(WebClient.Builder webClientBuilder,
                         String deleteMyRole) {
        this.webClientBuilder = webClientBuilder;
        this.deleteMyRole = deleteMyRole;
    }

    public Mono<String> deleteUserData(UUID organizationId, UUID userId) {
        final String endpoint = deleteMyRole + "/organizations/" + organizationId + "/users/" + userId;
        LOG.info("delete my role endpoint: {}", endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }
}
