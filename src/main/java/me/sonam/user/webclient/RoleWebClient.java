package me.sonam.user.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class RoleWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(RoleWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String deleteMyRole;

    public RoleWebClient(WebClient.Builder webClientBuilder,
                         String deleteMyRole) {
        this.webClientBuilder = webClientBuilder;
        this.deleteMyRole = deleteMyRole;
    }

    public Mono<String> deleteMyAccount() {
        LOG.info("delete my role endpoint: {}", deleteMyRole);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(deleteMyRole)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }
}