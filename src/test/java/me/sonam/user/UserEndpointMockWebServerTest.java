package me.sonam.user;

import me.sonam.user.handler.UserHandler;
import me.sonam.user.handler.UserTransfer;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * This will test the User signup endpoint
 * to test the '/public/user/signup' endpoint
 * For '/authentication' callout from the {@link me.sonam.user.handler.UserSignupService}
 * it will test that service using a MockWebServer for
 * returning a mocked response.  See {@link Router} for endpoints.
 */
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(MockitoExtension.class)
public class UserEndpointMockWebServerTest {
    private static final Logger LOG = LoggerFactory.getLogger(UserEndpointMockWebServerTest.class);

    private static String authEndpoint = "http://localhost:{port}/create";
    private static String jwtEndpoint = "http://localhost:{port}/validate";
    private static String accountEp = "http://localhost:{port}/accounts";

    @Value("${apiKey}")
    private String apiKey;

    private static MockWebServer mockWebServer;

    private UserHandler handler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    @Before
    public void setUp() {
        LOG.info("setup mock");
        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void deleteUserRepo() {
        userRepository.deleteAll().subscribe();
    }

    @BeforeAll
    static void setupMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        LOG.info("host: {}, port: {}", mockWebServer.getHostName(), mockWebServer.getPort());
    }

    @AfterAll
    public static void shutdownMockWebServer() throws IOException {
        LOG.info("shutdown and close mockWebServer");
        mockWebServer.shutdown();
        mockWebServer.close();
    }

    /**
     * this method will update the 'jwt-rest-service' endpoint address to the mockWebServer port
     * so that it can be mocked.
     *
     * @param r
     * @throws IOException
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws IOException {
        r.add("authentication-rest-service", () -> authEndpoint.replace("{port}", mockWebServer.getPort() + ""));
        r.add("jwt-rest-service", () -> jwtEndpoint.replace("{port}", mockWebServer.getPort() + ""));
        r.add("account-rest-service", () -> accountEp.replace("{port}", mockWebServer.getPort() + ""));
        LOG.info("updated authentication-rest-service and jwt-rest-service properties");
        LOG.info("mockWebServer.port: {}", mockWebServer.getPort());
    }

    @Test
    public void signupUser() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String email = "signupUser@some.company";

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(authenticationId));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(authenticationId));

        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                authenticationId, "pass", apiKey);

        EntityExchangeResult<String> result = webTestClient.post().uri("/users/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isCreated().expectBody(String.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("user signup succcessful");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        LOG.info("response: {}", result.getResponseBody());
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        StepVerifier.create(userRepository.findByAuthenticationId(authenticationId))
            .assertNext(myUser1 -> {
                LOG.info("assert update of userAuthAccountCreated field to true");
                assertThat(myUser1.getUserAuthAccountCreated()).isTrue();
            })
            .verifyComplete();
    }

    @Test
    public void signupUserWithExistingEmail() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String email = "signupUser@some.company";

        MyUser myUser = new MyUser("firstname", "lastname", email, authenticationId);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));
        LOG.info("make rest call to save user and create authentication record");

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("deleted authenticationId that is active false"));

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                authenticationId, "pass", apiKey);

        EntityExchangeResult<String> result = webTestClient.post().uri("/users/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("a user with this email already exists");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();

        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        recordedRequest = mockWebServer.takeRequest();

        assertThat(recordedRequest.getMethod()).isEqualTo("DELETE");

        StepVerifier.create(userRepository.findByAuthenticationId(authenticationId))
                .assertNext(myUser1 -> {
                    LOG.info("assert update of userAuthAccountCreated field to true");
                    assertThat(myUser1.getUserAuthAccountCreated()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    public void signupUserUserAuthAccountCreated() throws InterruptedException {
        MyUser myUser = new MyUser("firstname", "lastname", "yakApiKey", "existingUser");
        myUser.setUserAuthAccountCreated(true);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));

        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "yakApiKey",
                "existingUser", "pass", apiKey);

        EntityExchangeResult<String> result = webTestClient.post().uri("/users/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("User account has already been created for that id, check to activate it by email");
    }

    @Test
    public void signupUserWhenActiveIsTrue() throws InterruptedException {
        MyUser myUser = new MyUser("firstname", "lastname", "yakApiKey", "existingUser");
        myUser.setActive(true);
        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));

        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "yakApiKey",
                "existingUser", "pass", apiKey);

        EntityExchangeResult<String> result = webTestClient.post().uri("/users/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("User is already active with authenticationId");
    }

    @Test
    public void newUserValidTest() throws InterruptedException {
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "12yakApiKey",
                "dummy123", "pass", apiKey);

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("dummy123"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("email sent"));

       // webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build();

        EntityExchangeResult<String> result = webTestClient.post().uri("/users/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isCreated().expectBody(String.class).returnResult();

        LOG.info("start taking request now");
        RecordedRequest request = mockWebServer.takeRequest();
        LOG.info("response: {}", result.getResponseBody());


       assertThat(result.getResponseBody()).isEqualTo("user signup succcessful");

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getMethod()).isEqualTo("POST");

        //the body is empty for some reason.
        String body = new String(request.getBody().getBuffer().readByteArray());
        LOG.info("path: {}", request.getPath());
        LOG.info("request: {}", body);

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/create");
    }

    @Test
    public void activateAccount() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccount";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        MyUser myUser = new MyUser("firstname", "lastname", "yakApiKey", authenticationId);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));

        userRepository.findByAuthenticationId(authenticationId).as(StepVerifier::create).
                assertNext(myUser1 -> {
                    LOG.info("assert active is false");
                    assertThat(myUser1.getActive()).isFalse();
                })
                .verifyComplete();

        LOG.info("activate user authId: {}", id);
        EntityExchangeResult<String> result = webTestClient.put().uri("/users/activate")
                .headers(addJwt(jwt)).exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("activated: "+authenticationId);

        userRepository.findByAuthenticationId(authenticationId).as(StepVerifier::create).
                assertNext(myUser1 -> {
                    LOG.info("assert active is now true");
                    assertThat(myUser1.getActive()).isTrue();
                })
                .verifyComplete();

    }

    @Test
    public void deleteUserWhenUserFalse() {
        UUID id = UUID.randomUUID();
        final String authenticationId = "deleteUserWhenUserFalse";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        MyUser myUser = new MyUser("firstname", "lastname", "somemeail@email.com", authenticationId);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));

        userMono.as(StepVerifier::create).assertNext(myUser1 -> {
            LOG.info("assert active is false");
            assertThat(myUser1.getActive()).isFalse();
        });

        userRepository.findByAuthenticationId(authenticationId).as(StepVerifier::create).
                assertNext(myUser1 -> {
                    LOG.info("assert active is false");
                    assertThat(myUser1.getActive()).isFalse();
                })
                .verifyComplete();

        LOG.info("activate user authId: {}", id);
        EntityExchangeResult<String> result = webTestClient.delete().uri("/users")
                .headers(addJwt(jwt)).exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("deleted: "+authenticationId);

        userRepository.existsByAuthenticationId(authenticationId).subscribe(aBoolean -> LOG.info("exists should be false: {}", aBoolean));
    }

    @Test
    public void deleteUserWhenUserActive() throws InterruptedException {
        final String authenticationId = "deleteUserWhenUserFalse";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        UUID id = UUID.randomUUID();
        //final String authenticationId = "deleteUserWhenUserFalse";

        MyUser myUser = new MyUser("firstname", "lastname", "somemeail@email.com", authenticationId);
        myUser.setActive(true);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));

        userMono.as(StepVerifier::create).assertNext(myUser1 -> {
            LOG.info("assert active is True");
            assertThat(myUser1.getActive()).isTrue();
        });

        userRepository.findByAuthenticationId(authenticationId).as(StepVerifier::create).
                assertNext(myUser1 -> {
                    LOG.info("assert active is True");
                    assertThat(myUser1.getActive()).isTrue();
                })
                .verifyComplete();

        LOG.info("activate user authId: {}", id);
        EntityExchangeResult<String> result = webTestClient.delete().uri("/users")
                .headers(addJwt(jwt)).exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("user is active, cannot delete");

        userRepository.existsByAuthenticationId(authenticationId).subscribe(aBoolean -> LOG.info("exists should be true: {}", aBoolean));
    }

    private Jwt jwt(String subjectName) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName));
    }

    private Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }

}
