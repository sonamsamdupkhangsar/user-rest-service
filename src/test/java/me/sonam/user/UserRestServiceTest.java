package me.sonam.user;


import me.sonam.user.handler.UserTransfer;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;


// to run this test por-forward or run authentication-rest-service
//  kubectl port-forward authentication-rest-service-mychart-233323h 8001:8080
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserRestServiceTest {
    private static final Logger LOG = LoggerFactory.getLogger(UserRestServiceTest.class);

    @Autowired
    private WebTestClient client;

    @Autowired
    private UserRepository userRepository;

    @Value("${apiKey}")
    private String apiKey;

    @Test
    public void hello() {
        LOG.info("dummy test method for now");
    }

    @Test
    public void apiKeyWrong() {
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "yakApiKey",
                "dummy123", "pass", "dummy");

        EntityExchangeResult<String> result = client.post().uri("/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("signup fail: apikey check fail");
    }

    @Test
    public void existingUser() {
        User user = new User("firstname", "lastname", "yakApiKey");

        Mono<User> userMono = userRepository.save(user);
        userMono.subscribe(user1 -> LOG.info("save user first"));

        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "yakApiKey",
                "dummy123", "pass", apiKey);

        EntityExchangeResult<String> result = client.post().uri("/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("signup fail: user already exists with email");
    }

    @Test
    public void newUser() {
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "12yakApiKey",
                "dummy123", "pass", apiKey);

        EntityExchangeResult<String> result = client.post().uri("/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        //since the remote api will not be available in this test, we will get a connection refused.
        assertThat(result.getResponseBody()).startsWith("Authentication api call failed with error: Connection refused:");
    }
}
