package me.sonam.user;

import me.sonam.user.handler.UserTransfer;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    @AfterEach
    public void deleteUserRepo() {
        userRepository.deleteAll().subscribe();
    }

    @Test
    public void actuatorEndpoint() {
        LOG.info("access actuator health endpoint");
        EntityExchangeResult<String> result = webTestClient.get().uri("/users")
                .exchange().expectStatus().is4xxClientError().expectBody(String.class).returnResult();

        LOG.info("result: {}", result.getResponseBody());

    }

    @Test
    public void updateUser() throws InterruptedException, IOException {
        LOG.info("make rest call to save user and create authentication record");

        final String authenticationId = "dave";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        MyUser myUser2 = new MyUser("Dommy", "thecat", "dommy@cat.email",
                authenticationId);

        userRepository.save(myUser2).subscribe();

        UserTransfer userTransfer = new UserTransfer();

        userTransfer.setFirstName("Josey");
        userTransfer.setLastName("Cat");
        userTransfer.setEmail("josey.cat@@cat.emmail");

        LOG.info("update user fields with jwt in auth bearer token");
        EntityExchangeResult<String> result = webTestClient.put().uri("/users")
                .bodyValue(userTransfer)
                //.headers(httpHeaders -> httpHeaders.set("authId", "dommy@cat.email"))
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("result: {}", result.getResponseBody());

        userRepository.findByAuthenticationId("dave").as(StepVerifier::create)
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

        final String authenticationId = "dommy@cat.email";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        MyUser myUser2 = new MyUser("Dommy", "thecat", "dommy@cat.email", authenticationId);

        userRepository.save(myUser2).subscribe();

        LOG.info("get user by auth id");

        Flux<MyUser> myUserFlux = webTestClient.get().uri("/users/"+authenticationId)
                .headers(addJwt(jwt)).exchange().expectStatus().isOk()
                .returnResult(MyUser.class).getResponseBody();

        StepVerifier.create(myUserFlux)
                .assertNext(myUser -> {
                    LOG.info("asserting found user by authId: {}", myUser);
                    assertThat(myUser.getLastName()).isEqualTo("thecat");
                    assertThat(myUser.getEmail()).isEqualTo("dommy@cat.email");
                })
                .verifyComplete();

        Flux<Map> result = webTestClient.get().uri("/users/dummy-123")
                .headers(addJwt(jwt)).exchange().expectStatus().isBadRequest()
                .returnResult(Map.class).getResponseBody();

        StepVerifier.create(result)
                .assertNext(map -> {
                    LOG.info("user not found {}", map.get("error"));

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

        final String authenticationId = "dommymacn@cat.email";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        Flux<MyUser> myUserFlux = webTestClient.get().uri("/users/names/dommy/thecat")
                .headers(addJwt(jwt))
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
        final String authenticationId = "dommy@cat.email";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));


        Flux<String> myUserFlux = webTestClient.put().uri("/users/profilephoto")
                .bodyValue("http://spaces.sonam.us/myapp/app/someimage.png")
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody();

        StepVerifier.create(myUserFlux)
                .assertNext(s -> { LOG.info("string response is {}", s); assertThat(s).isEqualTo("photo updated"); })
                .verifyComplete();
    }

    private Jwt jwt(String subjectName) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName));
    }

    private Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }
}
