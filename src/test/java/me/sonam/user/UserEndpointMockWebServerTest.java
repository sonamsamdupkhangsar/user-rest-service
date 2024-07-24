package me.sonam.user;

import me.sonam.user.handler.UserHandler;
import me.sonam.user.handler.UserTransfer;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication;

/**
 * This will test the User signup endpoint
 * to test the '/public/user/signup' endpoint
 * For '/authentication' callout from the {@link me.sonam.user.handler.UserSignupService}
 * it will test that service using a MockWebServer for
 * returning a mocked response.  See {@link Router} for endpoints.
 */
@EnableAutoConfiguration
//@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Application.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(MockitoExtension.class)
public class UserEndpointMockWebServerTest {
    private static final Logger LOG = LoggerFactory.getLogger(UserEndpointMockWebServerTest.class);

    private static String authEndpoint = "http://localhost:{port}/authentications";
    private static String jwtRestServiceAccesstoken = "http://localhost:{port}/jwts/accesstoken";
    private static String accountEp = "http://localhost:{port}/accounts";

    private static MockWebServer mockWebServer;

    private UserHandler handler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    @Autowired
    ApplicationContext context;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        this.webTestClient = WebTestClient
                .bindToApplicationContext(this.context)
                // add Spring Security test Support
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    final String clientCredentialResponse = "{" +
            "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
            "    \"scope\": \"message.read message.write\"," +
            "    \"token_type\": \"Bearer\"," +
            "    \"expires_in\": 299" +
            "}";

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
        r.add("authentication-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
        r.add("account-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
        r.add("organization-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
        r.add("role-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
        r.add("auth-server.root", () -> "http://localhost:"+ mockWebServer.getPort());
    }

    @BeforeEach
    public void checkRequest() throws InterruptedException {
        LOG.info("requestCount: {}", mockWebServer.getRequestCount());
    }

    @Test
    public void signupUser() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String email = "signupUser@some1.company";

        LOG.info("try to POST with the same email/authId");
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));


        //2
        final String msg = "{\"error\": \"no account with email\"}";
        //Http 500 will throw a Exception in the webclient call, exectuing the onError block
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(500).setBody(msg));

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String authMessage = "Authentication created successfully for authenticationId: " + authenticationId;
        final String authenticationCreatedResponse = " {\"message\":\""+ authMessage +"\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(authenticationCreatedResponse));

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String accountCreatedResponse = " {\"message\":\"Account created successfully.  Check email for activating account\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(accountCreatedResponse));
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                authenticationId, "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("message")).isEqualTo("user signup succcessful");

        LOG.info("after post requestCount: {}", mockWebServer.getRequestCount());

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/accounts");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/authentications");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/accounts");

        LOG.info("end requestCount: {}", mockWebServer.getRequestCount());

        StepVerifier.create(userRepository.findByAuthenticationId(authenticationId))
            .assertNext(myUser1 -> {
                LOG.info("assert update of userAuthAccountCreated field to true");
                assertThat(myUser1.getUserAuthAccountCreated()).isTrue();
            })
            .verifyComplete();
    }

    @Test
    public void signupUserBadResponseFromAuthentication() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String email = "signupUser@some1.company";

        LOG.info("try to POST with the same email/authId");
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        //2
        final String msg = "{\"error\": \"no account with email\"}";
        //Http 500 will throw a Exception in the webclient call, exectuing the onError block
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(500).setBody(msg));

        //3
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String authMessage = "Authentication api call failed with error: " + authenticationId;
        final String authenticationCreatedResponse = " {\"error\":\""+ authMessage +"\"}";

        //4
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(400).setBody(authenticationCreatedResponse));

        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                authenticationId, "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        assertThat(result.getResponseBody().get("error")).isNotNull();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/accounts");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/authentications");
        assertThat(result.getResponseBody().get("error")).isEqualTo("user signup failed with error: Authentication api call failed with error: 400 Bad Request from POST http://localhost:"+mockWebServer.getPort()+"/authentications");


        StepVerifier.create(userRepository.existsByAuthenticationId(authenticationId))
                .assertNext(aBoolean -> {
                    LOG.info("assert no user exists with the failed authenticationId");
                    assertThat(aBoolean).isFalse();
                })
                .verifyComplete();
    }

    @Test
    public void signupUserBadResponseFromAccount() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String email = "signupUser@some1.company";

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));
        //2
        final String msg = "{\"error\": \"no account with email\"}";
        //Http 500 will throw a Exception in the webclient call, exectuing the onError block
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(500).setBody(msg));

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String authMessage = "Authentication created successfully for authenticationId: " + authenticationId;
        final String authenticationCreatedResponse = " {\"message\":\""+ authMessage +"\"}";

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(authenticationCreatedResponse));

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String accountCreatedResponse = "{\"message\":\"Account create failed\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(400).setBody(accountCreatedResponse));
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                authenticationId, "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("error")).isEqualTo("user signup failed with error: Account api call failed with error: {\"message\":\"Account create failed\"}");

        LOG.info("after post requestCount: {}", mockWebServer.getRequestCount());

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/accounts");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/authentications");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/accounts");

        LOG.info("end requestCount: {}", mockWebServer.getRequestCount());

        StepVerifier.create(userRepository.existsByAuthenticationId(authenticationId))
                .assertNext(aBoolean -> {
                    LOG.info("assert update of userAuthAccountCreated field to true");
                    assertThat(aBoolean).isFalse();
                })
                .verifyComplete();
    }

    @Test
    public void signupUserWithAnotherUserWithEmailActiveFalse() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String email = "signupUser@some1.company";

        MyUser myUser = new MyUser("firstname", "lastname", email, "johnny");

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first: {}", user1.getEmail()));
        LOG.info("make rest call to save user and create authentication record");


        LOG.info("try to POST with the same email/authId");
        //1
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        //2
        final String msg = "{\"error\": \"no account with email\"}";
        //Http 500 will throw a Exception in the webclient call, exectuing the onError block
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(500).setBody(msg));

        //3
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String authMessage = "Authentication created successfully for authenticationId: " + authenticationId;
        final String authenticationCreatedResponse = " {\"message\":\""+ authMessage +"\"}";
        //4
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(authenticationCreatedResponse));

        //5
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String accountCreatedResponse = " {\"message\":\"Account created successfully.  Check email for activating account\"}";
        //6
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(accountCreatedResponse));
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                authenticationId, "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")

                .bodyValue(userTransfer)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("message")).isEqualTo("user signup succcessful");

        LOG.info("after post requestCount: {}", mockWebServer.getRequestCount());

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/accounts");
        LOG.info("response: {}", result.getResponseBody());

        request = mockWebServer.takeRequest();
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");
        LOG.info("response: {}", result.getResponseBody());

        request = mockWebServer.takeRequest();
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/authentications");

        request = mockWebServer.takeRequest();
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");
        LOG.info("response: {}", result.getResponseBody());

        request = mockWebServer.takeRequest();
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/accounts");

        LOG.info("end requestCount: {}", mockWebServer.getRequestCount());

        StepVerifier.create(userRepository.findByAuthenticationId(authenticationId))
                .assertNext(myUser1 -> {
                    LOG.info("assert update of userAuthAccountCreated field to true");
                    assertThat(myUser1.getUserAuthAccountCreated()).isTrue();
                })
                .verifyComplete();
    }

    //test with a existing email account with UserAuthAccountCreated and try adding a new user
    // with new AuthenticationId and existing email.
    @Test
    public void signupUserWithExistingEmailAndUserAuthAccountCreatedTrue() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String newAuthenticationId = "signupUser1";
        final String email = "signupUser@some2.company";

        MyUser myUser = new MyUser("firstname", "lastname", email, authenticationId);
        myUser.setUserAuthAccountCreated(true);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                newAuthenticationId, "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("error")).isEqualTo("user signup failed with error: User account has already been created for that email, check to activate it by email");
        LOG.info("mockWebServer: {}", mockWebServer.getRequestCount());

        StepVerifier.create(userRepository.existsByAuthenticationId(newAuthenticationId))
                .assertNext(aBoolean -> {
                    LOG.info("assert newAuth didn't get created");
                    assertThat(aBoolean).isFalse();
                })
                .verifyComplete();
    }

    //test with a existing email account with UserAuthAccountCreated and try adding a new user
    // with same AuthenticationId and existing email.
    @Test
    public void signupUserWithExistingEmailAndUserAuthAccountCreatedTrueAndSameAuthId() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String email = "signupUser@some2.company";

        MyUser myUser = new MyUser("firstname", "lastname", email, authenticationId);
        myUser.setUserAuthAccountCreated(true);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                authenticationId, "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("error")).isEqualTo("user signup failed with error: User account has already been created for that username, check to activate it by email");
        LOG.info("mockWebServer: {}", mockWebServer.getRequestCount());

        StepVerifier.create(userRepository.existsByAuthenticationId(authenticationId))
                .assertNext(aBoolean -> {
                    LOG.info("assert existing user created is true");
                    assertThat(aBoolean).isTrue();
                })
                .verifyComplete();
    }

    //test with a existing email account with isActive True and try adding a new user
    // with same email.
    @Test
    public void signupUserWithExistingEmailAndActiveTrueAndSameEmail() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String newAuthenticationId = authenticationId+"1";
        final String email = "signupUser@some2.company";

        MyUser myUser = new MyUser("firstname", "lastname", email, authenticationId);
        myUser.setActive(true);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                newAuthenticationId, "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("error")).isEqualTo("user signup failed with error: User account is active for that email");
        LOG.info("mockWebServer: {}", mockWebServer.getRequestCount());

        StepVerifier.create(userRepository.existsByAuthenticationId(newAuthenticationId))
                .assertNext(aBoolean -> {
                    LOG.info("assert newAuth didn't get created");
                    assertThat(aBoolean).isFalse();
                })
                .verifyComplete();
    }

    //test with a existing email account with isActive True and try adding a new user
    // with same email.
    @Test
    public void signupUserExistingAuthIdAndActiveTrue() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String email = "signupUser@some2.company";

        MyUser myUser = new MyUser("firstname", "lastname", email, authenticationId);
        myUser.setActive(true);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                authenticationId, "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("error")).isEqualTo("user signup failed with error: User is already active with that username (authenticationId)");
        LOG.info("mockWebServer: {}", mockWebServer.getRequestCount());

        StepVerifier.create(userRepository.existsByAuthenticationId(authenticationId))
                .assertNext(aBoolean -> {
                    LOG.info("assert existing authId still exists");
                    assertThat(aBoolean).isTrue();
                })
                .verifyComplete();
    }

    /**
     * Add a user that is not active and UserAuthAccountCreated is false.
     * Add another user that has the same email only and different authId
     * Account service will throw a exception indicating there is no account with email
     * But this should allow to create a new user account with the same email since that email acount was not created
     * successfully.
     * @throws InterruptedException
     */
    @Test
    public void signupUserWithExistingEmailThrowAccountServiceException() throws InterruptedException {
        final String authenticationId = "signupUser";
        final String newAuthenticationId = "signupUser2";
        final String email = "signupUser@some2.company";

        MyUser myUser = new MyUser("firstname", "lastname", email, authenticationId);

        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));
        LOG.info("make rest call to save user and create authentication record");

        //1
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));
        //2
        final String msg = "{\"error\": \"no account with email\"}";
        //Http 500 will throw a Exception in the webclient call, exectuing the onError block
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(500).setBody(msg));

        //3
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String authMessage = "Authentication created successfully for authenticationId: " + authenticationId;
        final String authenticationCreatedResponse = " {\"message\":\""+ authMessage +"\"}";

        //4
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(authenticationCreatedResponse));

        //5
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String accountCreatedResponse = " {\"message\":\"Account created successfully.  Check email for activating account\"}";
        //6
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(accountCreatedResponse));
        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", email,
                newAuthenticationId, "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("message")).isEqualTo("user signup succcessful");

        LOG.info("after post requestCount: {}", mockWebServer.getRequestCount());

        //1
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        //2
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/accounts");

        LOG.info("response: {}", result.getResponseBody());

        //3
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        //4
        request = mockWebServer.takeRequest();
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/authentications");
        LOG.info("response: {}", result.getResponseBody());

        request = mockWebServer.takeRequest();
        //5
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        //6
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/accounts");

        LOG.info("end requestCount: {}", mockWebServer.getRequestCount());

        StepVerifier.create(userRepository.findByAuthenticationId(newAuthenticationId))
                .assertNext(myUser1 -> {
                    LOG.info("assert update of userAuthAccountCreated field to true");
                    assertThat(myUser1.getUserAuthAccountCreated()).isTrue();
                })
                .verifyComplete();

        StepVerifier.create(userRepository.existsByAuthenticationId(authenticationId))
                .assertNext(aBoolean -> {
                    LOG.info("assert previous authentiationId record does not exist anymore");
                    assertThat(aBoolean).isFalse();
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
                "existingUser", "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("error")).isEqualTo("user signup failed with error: User account has already been created for that username, check to activate it by email");
    }

    @Test
    public void signupUserWhenActiveIsTrue() throws InterruptedException {
        MyUser myUser = new MyUser("firstname", "lastname", "yakApiKey", "existingUser");
        myUser.setActive(true);
        Mono<MyUser> userMono = userRepository.save(myUser);
        userMono.subscribe(user1 -> LOG.info("save user first"));

        LOG.info("make rest call to save user and create authentication record");

        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "yakApiKey",
                "existingUser", "pass");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("assert result contains authId: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("error")).isEqualTo("user signup failed with error: User is already active with that username (authenticationId)");
    }

    @Test
    public void newUserValidTest() throws InterruptedException {
        LOG.info("make rest call to save user and create authentication record");
        final String authenticationId = "dummy123";
        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "12yakApiKey",
                authenticationId, "pass");

        LOG.info("try to POST with the same email/authId");
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));
        //2
        final String msg = "{\"error\": \"no account with email\"}";
        //Http 500 will throw a Exception in the webclient call, exectuing the onError block
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(500).setBody(msg));

        //3
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));
        final String authMessage = "Authentication created successfully for authenticationId: " + userTransfer.getAuthenticationId();
        final String authenticationCreatedResponse = " {\"message\":\""+ authMessage +"\"}";

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(authenticationCreatedResponse));

        LOG.info("add the same token again as another account webservice is called after the Authentication one");
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String accountCreatedResponse = " {\"message\":\"Account created successfully.  Check email for activating account\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(accountCreatedResponse));

        EntityExchangeResult<Map> result = webTestClient.post().uri("/users")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        LOG.info("start taking request now");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/accounts");


        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/authentications");

        request = mockWebServer.takeRequest();
        LOG.info("path: {}", request.getPath());
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/accounts");

        LOG.info("end requestCount: {}", mockWebServer.getRequestCount());

        StepVerifier.create(userRepository.findByAuthenticationId(authenticationId))
                .assertNext(myUser1 -> {
                    LOG.info("assert update of userAuthAccountCreated field to true");
                    assertThat(myUser1.getUserAuthAccountCreated()).isTrue();
                })
                .verifyComplete();

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
        EntityExchangeResult<String> result = webTestClient.put().uri("/users/"+authenticationId+"/active")
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
    public void deleteMyInfo() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "deleteUserWhenUserFalse";

        MyUser myUser = new MyUser("firstname", "lastname", "somemeail@email.com", authenticationId);
        assertThat(myUser.getId()).isNotNull();

        Jwt jwt = jwt(authenticationId, myUser.getId());
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

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

        final String msg1 = "{\"message\": \"deleted account\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(msg1));

        final String msg2 = "{\"message\": \"deleted authentication\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(msg2));

        final String msg3 = "{\"message\": \"deleted organization\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(msg3));

        final String msg4 = "{\"message\": \"deleted role\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(msg4));
        final String jwtString= "eyJraWQiOiJlOGQ3MjIzMC1iMDgwLTRhZjEtODFkOC0zMzE3NmNhMTM5ODIiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiI3NzI1ZjZmZC1kMzk2LTQwYWYtOTg4Ni1jYTg4YzZlOGZjZDgiLCJhdWQiOiI3NzI1ZjZmZC1kMzk2LTQwYWYtOTg4Ni1jYTg4YzZlOGZjZDgiLCJuYmYiOjE3MTQ3NTY2ODIsImlzcyI6Imh0dHA6Ly9teS1zZXJ2ZXI6OTAwMSIsImV4cCI6MTcxNDc1Njk4MiwiaWF0IjoxNzE0NzU2NjgyLCJqdGkiOiI0NDBlZDY0My00MzdkLTRjOTMtYTZkMi1jNzYxNjFlNDRlZjUifQ.fjqgoczZbbmcnvYpVN4yakpbplp7EkDyxslvar5nXBFa6mgIFcZa29fwIKfcie3oUMQ8MDWxayak5PZ_QIuHwTvKSWHs0WL91ljf-GT1sPi1b4gDKf0rJOwi0ClcoTCRIx9-WGR6t2BBR1Rk6RGF2MW7xKw8M-RMac2A2mPEPJqoh4Pky1KgxhZpEXixegpAdQIvBgc0KBZeQme-ZzTYugB8EPUmGpMlfd-zX_vcR1ijxi8e-LRRJMqmGkc9GXfrH7MOKNQ_nu6pc6Gish2v_iuUEcpPHXrfqzGb9IHCLvfuLSaTDcYKYjQaEUAp-1uDW8-5posjiUV2eBiU48ajYg";

        LOG.info("activate user authId: {}", id);
        EntityExchangeResult<String> result = webTestClient.mutateWith(mockJwt().jwt(jwt)).delete().uri("/users")
                .headers(httpHeaders -> httpHeaders.setBearerAuth(jwtString))
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("delete my account success for user id: "+myUser.getId());

        userRepository.existsByAuthenticationId(authenticationId).subscribe(aBoolean -> LOG.info("exists should be false: {}", aBoolean));

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/accounts");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/authentications");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/organizations");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/roles");


    }

    //@Test
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

    //@Test
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
    private Jwt jwt(String subjectName, UUID userId) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName, "userId", userId.toString()));
    }
    private Jwt jwt(String subjectName) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName));
    }

    private Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }

}
