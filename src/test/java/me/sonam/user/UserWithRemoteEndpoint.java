package me.sonam.user;

import me.sonam.user.handler.AuthTransfer;
import me.sonam.user.handler.UserTransfer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

public class UserWithRemoteEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(UserWithRemoteEndpoint.class);

    private WebClient webClient = WebClient.builder().build();

    //@Test
    public void updateUser() {
        final String jwt = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkdW1teTEyMzQiLCJpc3MiOiJzb25hbS5jbG91ZCIsImF1ZCI6InNvbmFtLmNsb3VkIiwiZXhwIjoxNjU3NDU2MjYzLCJqdGkiOiI0Y2Y4ZWYxZi1lZjM3LTRkMTctOGEzNC00YTRkNmNjNzVjZjcifQ.laKyiskryOrrFZfrwvc_F3-AnEcT5MO6s9j4iILdjBbOqbB7Evkxqqm00j3wu-MDVWkvWTI4NKFSHeF0R1I-Bw";

        UserTransfer userTransfer = new UserTransfer("Tashi", "Tsering", "",
                "", "", "");

        WebClient.ResponseSpec responseSpec = webClient.put().uri("http://localhost:8001/user")
                .headers(httpHeaders -> httpHeaders.setBearerAuth(jwt))
                .bodyValue(userTransfer)
                .retrieve();

        LOG.info("httpHeaders: {}", responseSpec.toBodilessEntity().block().getHeaders());
        responseSpec.bodyToMono(String.class).subscribe(s -> LOG.info("jwt: {}", s));
        responseSpec.toEntity(String.class).subscribe(stringResponseEntity -> LOG.info("string: {}", stringResponseEntity.getBody()));

        LOG.info("body: {}", responseSpec.bodyToMono(String.class).block());
    }
}
