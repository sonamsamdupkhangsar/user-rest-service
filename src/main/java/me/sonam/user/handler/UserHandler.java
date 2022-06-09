package me.sonam.user.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Service
public class UserHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UserHandler.class);

    @Autowired
    private UserService userService;

    public Mono<ServerResponse> signupUser(ServerRequest serverRequest) {
        LOG.info("authenticate user");

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .body(userService.signupUser(serverRequest.bodyToMono(UserTransfer.class)),
                        String.class)
                .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
                        .fromValue(e.getMessage())));
    }
}
