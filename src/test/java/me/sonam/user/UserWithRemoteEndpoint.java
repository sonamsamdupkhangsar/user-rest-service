package me.sonam.user;

import me.sonam.user.handler.UserTransfer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

public class UserWithRemoteEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(UserWithRemoteEndpoint.class);

    private final String apiKey = "";

    private WebClient webClient = WebClient.builder().build();

    private WebTestClient webTestClient = WebTestClient.bindToServer().build();

   // @Test
    public void findByNames() {
        final String jwt = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkdW1teTEyMzQiLCJpc3MiOiJzb25hbS5jbG91ZCIsImF1ZCI6InNvbmFtLmNsb3VkIiwiZXhwIjoxNjU3NjY5NDUxLCJqdGkiOiI2OGVmODYwMC02YjcyLTRhZjMtOTUwOS1jNDliYjc2ZDA0NTgifQ.Ku_5CHVXOZpbdaBl9P7tNEQqdbYq5qu87VjquimFzGWBlg7uvylwZ3eQMC8wq-r6EpSrsRaLDB9WxIB0o49G3Q";

        WebClient.ResponseSpec responseSpec = webClient.get().uri("https://user-rest-service.sonam.cloud/user/names/dommy/thecat")
                .headers(httpHeaders -> httpHeaders.setBearerAuth(jwt))
                .retrieve();

        LOG.info("httpHeaders: {}", responseSpec.toBodilessEntity().block().getHeaders());
       // responseSpec.bodyToMono(String.class).subscribe(s -> LOG.info("jwt: {}", s));
       // responseSpec.toEntity(String.class).subscribe(stringResponseEntity -> LOG.info("string: {}", stringResponseEntity.getBody()));

       // LOG.info("body: {}", responseSpec.bodyToMono(String.class).block());
    }
    //now try on Aug 26 9am with a signup using the same email but different userid like apple

    //change authenticationId to 'apple' to test deletion from user-rest-service to account-rest-service calling auth and user services
    @Test
    public void signup() {
        LOG.info("signup user");
        final String id = UUID.randomUUID().toString().replace("-", "");
        UserTransfer userTransfer = new UserTransfer("sonam", "", "mex@sonam.co",
                "apple", "", apiKey);

        webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build();

        webTestClient.post().uri("https://user-rest-service.sonam.cloud/public/user/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isCreated().expectBody(String.class)
                .consumeWith(stringEntityExchangeResult ->
                    LOG.info("body: {}, status: {}", stringEntityExchangeResult.getResponseBody(),
                            stringEntityExchangeResult.getStatus()));
    }

    @Test
    public void authenticate() {
        LOG.info("authenticate user");
        final String id = UUID.randomUUID().toString().replace("-", "");
        UserTransfer userTransfer = new UserTransfer(null, null, null,
                "sonam", "50bucks$", null);

        webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build();

        webTestClient.post().uri("https://authentication-rest-service.sonam.cloud/public/authentications/authenticate")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isOk().expectBody(String.class)
                .consumeWith(stringEntityExchangeResult ->
                        LOG.info("body: {}, status: {}", stringEntityExchangeResult.getResponseBody(),
                                stringEntityExchangeResult.getStatus()));
    }
}
