package me.sonam.user;

import me.sonam.user.handler.UserHandler;
import me.sonam.user.handler.UserService;
import me.sonam.user.handler.UserTransfer;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.reactive.function.server.support.ServerRequestWrapper;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class MyUserMockRestServiceTest {
    private static final Logger LOG = LoggerFactory.getLogger(MyUserMockRestServiceTest.class);

    private final ServerRequest mockServerRequest = mock(ServerRequest.class);
    private final ServerRequestWrapper mockServerRequestWrapper = new ServerRequestWrapper(
            mockServerRequest);

    private WebTestClient webTestClient;

    @InjectMocks
    private UserHandler handler;

    @Mock
    private UserService service;

    @Before
    public void setUp() {
        LOG.info("setup mock");
        MockitoAnnotations.openMocks(this);
        RouterFunction<ServerResponse> routerFunction = RouterFunctions
                .route(RequestPredicates.POST("/jwtnotrequired/signup"),
                        handler::signupUser);
        this.webTestClient = WebTestClient.bindToRouterFunction(routerFunction).build();
    }

    @Test
    public void signup() {
        when(service.signupUser(Mockito.any())).thenReturn(Mono.just("user created with id: dummy with authId: dummyId"));

        assertThat(webTestClient).isNotNull();

        LOG.info("signup user");
        UserTransfer userTransfer = new UserTransfer("firstname", "lastname", "yakApiKey", "authId", "pass", "yakApiKey");

        webTestClient.post().uri("/jwtnotrequired/signup")
                .bodyValue(userTransfer)
                .exchange().expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(stringEntityExchangeResult -> LOG.info("result: {}", stringEntityExchangeResult.getResponseBody()));
    }


}
