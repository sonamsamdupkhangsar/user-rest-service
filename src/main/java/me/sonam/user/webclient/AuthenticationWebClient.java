package me.sonam.user.webclient;

import me.sonam.user.handler.SignupException;
import me.sonam.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthenticationWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String authenticationEndpoint;
    private UserRepository userRepository;

    public AuthenticationWebClient(WebClient.Builder webClientBuilder,
                                   String authenticationEndpoint, UserRepository userRepository) {
        this.webClientBuilder = webClientBuilder;
        this.authenticationEndpoint = authenticationEndpoint;
        this.userRepository = userRepository;
    }

    public Mono<String> create(String authenticationId, String password, UUID userId, boolean active) {
        LOG.info("call authentication endpoint to create Authentication record {}", authenticationEndpoint);

        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("authenticationId", authenticationId);
        payloadMap.put("password",password);
        payloadMap.put("userId", userId.toString());
        payloadMap.put("active", String.valueOf(active));

        LOG.debug("map.active {} vs active {}", payloadMap.get("active"), active);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().post().uri(authenticationEndpoint).bodyValue(payloadMap).retrieve();

        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {}).map(map -> {
            LOG.info("got back authenticationId from service call: {}", map.get("message"));
            return map.get("message");
        }).onErrorResume(throwable -> {
            LOG.error("authentication rest call failed: {}", throwable.getMessage());

            LOG.info("rollback userRepository by deleting authenticationId");
            return userRepository.deleteByAuthenticationIdIgnoreCase(authenticationId).then(
                    Mono.error(new SignupException("Authentication api call failed with error: " + throwable.getMessage())));
        });
    }

    public Mono<Map<String, String>> deleteByAuthenticationId(String authenticationId) {
        String deleteByAuthenticationIdEndpoint = authenticationEndpoint+"/"+authenticationId;
        LOG.info("delete authentication by authenticationId endpoint: {}", deleteByAuthenticationIdEndpoint);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(deleteByAuthenticationIdEndpoint)
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {}).doOnNext(map -> {
            LOG.debug("got response back: {}", map);
        }).onErrorResume(throwable -> {
            LOG.error("error occurred in calling deleteByAuthenticationId endpoint {}", deleteByAuthenticationIdEndpoint, throwable);
            return Mono.error(throwable);
        });
    }

    public Mono<String> deleteMyAccount() {
        LOG.info("delete my authentication account endpoint: {}", authenticationEndpoint);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(authenticationEndpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class).doOnNext(s -> {
            LOG.debug("got response for deleteMyAccount call to endpoint{} {}", authenticationEndpoint, s);
        }).onErrorResume(throwable -> {
            LOG.error("error occurred in calling deleteMyAccount endpoint {}", authenticationEndpoint, throwable);
            return Mono.error(throwable);
        });
    }
}