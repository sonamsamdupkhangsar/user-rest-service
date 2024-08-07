package me.sonam.user.webclient;

import me.sonam.user.handler.SignupException;
import me.sonam.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.UUID;

public class AccountWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(AccountWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String accountEndpoint;

    private UserRepository userRepository;

    public AccountWebClient(WebClient.Builder webClientBuilder,
                            String accountEndpoint, UserRepository userRepository) {
        this.webClientBuilder = webClientBuilder;
        this.accountEndpoint = accountEndpoint;
        this.userRepository = userRepository;
    }

    public Mono<String> createAccount(String authenticationId, UUID userId, String email) {
        StringBuilder stringBuilder = new StringBuilder(accountEndpoint).append("/").append(userId).append("/")
                .append(authenticationId)
                .append("/").append(email);

        LOG.info("create Account record with http call on endpoint: {}", stringBuilder);
        WebClient.ResponseSpec spec = webClientBuilder.build().post().uri(stringBuilder.toString()).retrieve();

        return spec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {}).doOnNext(map -> {
            LOG.info("account has been created with response: {}", map.get("message"));
        }).then(userRepository.updatedUserAuthAccountCreatedTrue(authenticationId))
                .thenReturn("account created")
                .onErrorResume(throwable -> {
                    LOG.debug("exception occured when calling create account endpoint", throwable);
                    LOG.error("create account rest call failed: {}", throwable.getMessage());
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                    LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());

                    return userRepository.deleteByAuthenticationId(authenticationId)
                            .then(
                                    Mono.error(new SignupException("Account api call failed with error: " +
                                            webClientResponseException.getResponseBodyAsString())));
                }
                else {
                    return Mono.error(new SignupException("Account api call failed with error: " +throwable.getMessage()));
                }
        });
    }

    public Mono<? extends String> deleteAccountByEmail(String email) {
        LOG.info("call delete account check");

        String encodedEmail = URLEncoder.encode(email, Charset.defaultCharset());
        final StringBuilder stringBuilder = new StringBuilder(accountEndpoint).append("/email/").append(encodedEmail);
        LOG.info("accountEp: {}", stringBuilder.toString());

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(stringBuilder.toString()).retrieve();

        return responseSpec.bodyToMono(String.class).map(string -> {//Map.class).map(map -> {
            LOG.info("got back response from account deletion service call: {}", string);//map.get("message"));
            return true; //map.get("message");
        }).onErrorResume(throwable -> {
            LOG.error("account deletion rest call failed: {}", throwable.getMessage());
            return Mono.just(true);
        }).then(Mono.just("done calling account delete check"));

    }

    public Mono<String> deleteMyAccount() {
        LOG.info("delete my account endpoint: {}", accountEndpoint);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(accountEndpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }
}