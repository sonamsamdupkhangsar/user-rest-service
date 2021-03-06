package me.sonam.user;

import me.sonam.user.handler.UserTransfer;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.junit.jupiter.api.AfterEach;
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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This will test the response from the Rest endpoint, the business service
 * , and the data repository interfaces.
 * For the `/public/user/signup` endpoint see {@link UserEndpointMockWebServerTest}
 * will test that endpoint which will mock the response to the Authentication endpoint.
 */
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserEndpointTest {
    private static final Logger LOG = LoggerFactory.getLogger(UserEndpointTest.class);

    @Value("${apiKey}")
    private String apiKey;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebTestClient webTestClient;

    @AfterEach
    public void deleteUserRepo() {
        userRepository.deleteAll().subscribe();
    }

    @Test
    public void updateUser() throws InterruptedException, IOException {
        LOG.info("make rest call to save user and create authentication record");

        MyUser myUser2 = new MyUser("Dommy", "thecat", "dommy@cat.email", "dommy@cat.email");

        userRepository.save(myUser2).subscribe();

        UserTransfer userTransfer = new UserTransfer("Josey", "Cat", "dommy@cat.email",
                "dommy@cat.email", "pass", apiKey);

        userTransfer.setFirstName("Josey");
        userTransfer.setLastName("Cat");
        userTransfer.setEmail("josey.cat@@cat.emmail");

        LOG.info("update user fields with jwt in auth bearer token");
        EntityExchangeResult<String> result = webTestClient.put().uri("/user")
                .bodyValue(userTransfer)
                .headers(httpHeaders -> httpHeaders.set("authId", "dommy@cat.email"))
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("result: {}", result.getResponseBody());

        userRepository.findByAuthenticationId("dommy@cat.email").as(StepVerifier::create)
                .expectNextMatches(myUser -> {
                    LOG.info("do expectNextMatches");
                   return myUser.getEmail().equals("josey.cat@@cat.emmail");
                }
                )
                .expectComplete().verify();

    }
    
    @Test
    public void getUserByAuthId() {
        LOG.info("make rest call to save user and create authentication record");

        MyUser myUser2 = new MyUser("Dommy", "thecat", "dommy@cat.email", "dommy@cat.email");

        userRepository.save(myUser2).subscribe();

        LOG.info("get user by auth id");

        Flux<MyUser> myUserFlux = webTestClient.get().uri("/user/"+"dommy@cat.email").exchange().expectStatus().isOk()
                .returnResult(MyUser.class).getResponseBody();

        StepVerifier.create(myUserFlux)
                .assertNext(myUser -> {
                    LOG.info("asserting found user by authId");
                    assertThat(myUser.getLastName()).isEqualTo("thecat");
                    assertThat(myUser.getEmail()).isEqualTo("dommy@cat.email");
                })
                .verifyComplete();
    }

    @Test
    public void findMatchingFirstNameAndLastName() {
        LOG.info("test find by firstName and lastName matching");
        MyUser myUser = new MyUser("Dommy", "thecat", "dommy@cat.email", "dommy@cat.email");

        userRepository.save(myUser).subscribe();

        myUser = new MyUser("Dommy", "thecatman", "dommythecatman@cat.email", "dommythecatman@cat.email");

        userRepository.save(myUser).subscribe();

        myUser = new MyUser("Dommy", "mac", "dommymacn@cat.email", "dommymacn@cat.email");

        userRepository.save(myUser).subscribe();

        Flux<MyUser> myUserFlux = webTestClient.get().uri("/user/names/dommy/thecat")
                .exchange().expectStatus().isOk()
                .returnResult(MyUser.class).getResponseBody();

        StepVerifier.create(myUserFlux)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    public void updateProfilePhoto() {
        LOG.info("test find by firstName and lastName matching");
        MyUser myUser = new MyUser("Dommy", "thecat", "dommy@cat.email", "dommy@cat.email");

        userRepository.save(myUser).subscribe();

        Flux<String> myUserFlux = webTestClient.put().uri("/user/profilephoto")
                .bodyValue("http://spaces.sonam.us/myapp/app/someimage.png")
                .headers(httpHeaders -> httpHeaders.set("authId", "dommy@cat.email"))
                .exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody();

        StepVerifier.create(myUserFlux)
                .assertNext(s -> { LOG.info("string response is {}", s); assertThat(s).isEqualTo("photo updated"); })
                .verifyComplete();
    }
}
