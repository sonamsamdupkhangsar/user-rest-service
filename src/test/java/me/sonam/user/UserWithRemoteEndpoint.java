package me.sonam.user;

import me.sonam.user.handler.AuthTransfer;
import me.sonam.user.handler.UserTransfer;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

public class UserWithRemoteEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(UserWithRemoteEndpoint.class);

    private final String apiKey = "";

    private WebClient webClient = WebClient.builder().build();

    @Test
    public void findByNames() {
        final String jwt = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkdW1teTEyMzQiLCJpc3MiOiJzb25hbS5jbG91ZCIsImF1ZCI6InNvbmFtLmNsb3VkIiwiZXhwIjoxNjU3NDU2MjYzLCJqdGkiOiI0Y2Y4ZWYxZi1lZjM3LTRkMTctOGEzNC00YTRkNmNjNzVjZjcifQ.laKyiskryOrrFZfrwvc_F3-AnEcT5MO6s9j4iILdjBbOqbB7Evkxqqm00j3wu-MDVWkvWTI4NKFSHeF0R1I-Bw";

        WebClient.ResponseSpec responseSpec = webClient.get().uri("https://user-rest-service.sonam.cloud/names/dommy/thecat")
                .headers(httpHeaders -> httpHeaders.setBearerAuth(jwt))
                .retrieve();

        LOG.info("httpHeaders: {}", responseSpec.toBodilessEntity().block().getHeaders());
       // responseSpec.bodyToMono(String.class).subscribe(s -> LOG.info("jwt: {}", s));
       // responseSpec.toEntity(String.class).subscribe(stringResponseEntity -> LOG.info("string: {}", stringResponseEntity.getBody()));

       // LOG.info("body: {}", responseSpec.bodyToMono(String.class).block());
    }

    @Test
    public void signup() {
        LOG.info("signup user");
        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "dummy15",
                "dummy15", "pass", apiKey);

        WebClient.ResponseSpec responseSpec  = webClient.post().uri("https://user-rest-service.sonam.cloud/jwtnotrequired/signup")
                .bodyValue(userTransfer)
                .retrieve();

        LOG.info("response: {}", responseSpec.toBodilessEntity().block().getBody());

    }
}
