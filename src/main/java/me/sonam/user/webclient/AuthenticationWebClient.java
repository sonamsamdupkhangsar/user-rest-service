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

    public Mono<String> create(String authenticationId, String password, UUID userId) {
        LOG.info("call authentication endpoint to create Authentication record {}", authenticationEndpoint);

        Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put("authenticationId", authenticationId);
        payloadMap.put("password",password);
        payloadMap.put("userId", userId.toString());

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().post().uri(authenticationEndpoint).bodyValue(payloadMap).retrieve();

        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {}).map(map -> {
            LOG.info("got back authenticationId from service call: {}", map.get("message"));
            return map.get("message");
        }).onErrorResume(throwable -> {
            LOG.error("authentication rest call failed: {}", throwable.getMessage());

            LOG.info("rollback userRepository by deleting authenticationId");
            return userRepository.deleteByAuthenticationId(authenticationId).then(
                    Mono.error(new SignupException("Authentication api call failed with error: " + throwable.getMessage())));
        });
    }

    public Mono<String> deleteMyAccount() {
        LOG.info("delete my authentication account endpoint: {}", authenticationEndpoint);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(authenticationEndpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }
}