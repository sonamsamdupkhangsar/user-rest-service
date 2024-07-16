package me.sonam.user.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class OrganizationWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String deleteMyAccount;

    public OrganizationWebClient(WebClient.Builder webClientBuilder,
                                 String deleteMyAccount) {
        this.webClientBuilder = webClientBuilder;
        this.deleteMyAccount = deleteMyAccount;
    }

    public Mono<String> deleteMyAccount() {
        LOG.info("delete my account");
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(deleteMyAccount)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }
}
