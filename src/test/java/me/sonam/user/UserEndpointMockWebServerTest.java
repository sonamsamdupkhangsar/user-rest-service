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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This will test the User signup endpoint
 * to test the '/jwtnotrequired/user/signup' endpoint
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

    @Value("${apiKey}")
    private String apiKey;

    private static MockWebServer mockWebServer;

    private UserHandler handler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebTestClient webTestClient;

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
        LOG.info("updated authentication-rest-service and jwt-rest-service properties");
        LOG.info("mockWebServer.port: {}", mockWebServer.getPort());
    }

    @Test
    public void apiKeyWrong() {
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "yakApiKey",
                "dummy123", "pass", "dummy");

        EntityExchangeResult<String> result = webTestClient.post().uri("/jwtnotrequired/user/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("apikey check fail");
    }

    @Test
    public void existingUser() {
        MyUser myUser = new MyUser("firstname", "lastname", "yakApiKey", "yakApiKey");

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));

        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "yakApiKey",
                "dummy123", "pass", apiKey);

        EntityExchangeResult<String> result = webTestClient.post().uri("/jwtnotrequired/user/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("user already exists with email");
    }

    @Test
    public void newUserValidTest() throws InterruptedException {
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "12yakApiKey",
                "dummy123", "pass", apiKey);

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("dummy123"));

        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build();

        EntityExchangeResult<String> result = webTestClient.post().uri("/jwtnotrequired/user/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("start taking request now");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        //the body is empty for some reason.
        String body = new String(request.getBody().getBuffer().readByteArray());
        LOG.info("path: {}", request.getPath());
        LOG.info("request: {}", body);

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/create");
    }
}
