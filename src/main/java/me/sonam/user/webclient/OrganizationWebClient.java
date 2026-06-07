package me.sonam.user.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class OrganizationWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String deleteMyOrganization;

    public OrganizationWebClient(WebClient.Builder webClientBuilder,
                                 String deleteMyOrganization) {
        this.webClientBuilder = webClientBuilder;
        this.deleteMyOrganization = deleteMyOrganization;
    }

    public Mono<String> deleteUserData(UUID organizationId, UUID userId) {
        final String endpoint = deleteMyOrganization + "/" + organizationId + "/users/" + userId + "/data";
        LOG.info("delete my organization account endpoint: {}", endpoint);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }
}
