package me.sonam.user.handler;

import me.sonam.user.repo.entity.MyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class UserHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UserHandler.class);

    @Autowired
    private UserService userService;

    public Mono<ServerResponse> signupUser(ServerRequest serverRequest) {
        LOG.info("authenticate user");
       return userService.signupUser(serverRequest.bodyToMono(UserTransfer.class))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable ->
                        ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(throwable.getMessage()));
    }

    public Mono<ServerResponse> update(ServerRequest serverRequest) {
        LOG.info("authenticate user");
        return userService.updateUser(serverRequest.headers().firstHeader("authId"), serverRequest.bodyToMono(UserTransfer.class))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {LOG.info("got exception in update: {}", throwable.getMessage());
                       return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(throwable.getMessage());
                });
    }
    //updateProfilePhoto
    public Mono<ServerResponse> updateProfilePhoto(ServerRequest serverRequest) {
        LOG.info("update profile photo");
        return userService.updateProfilePhoto(serverRequest.headers().firstHeader("authId"), serverRequest.bodyToMono(String.class))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {LOG.info("got exception in updateProfilePhoto: {}", throwable.getMessage());
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }

    public Mono<ServerResponse> findMatchingFirstNameAndLastName(ServerRequest serverRequest) {
        LOG.info("authenticate user");
        LOG.info("http headers: {}", serverRequest.headers());
        return userService.findMatchingName(serverRequest.pathVariable("firstName"), serverRequest.pathVariable("lastName"))
                .collectList().flatMap(myUsers -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(myUsers))
                .onErrorResume(throwable ->
                        ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(throwable.getMessage()));
    }

    public Mono<ServerResponse> getUserById(ServerRequest serverRequest) {
        LOG.info("authenticate user");
        if (serverRequest.pathVariable("id") == null || serverRequest.pathVariable("id").isEmpty()) {
            return Mono.error(new UserException("id is null"));
        }
        try {
            UUID.fromString(serverRequest.pathVariable("id"));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            LOG.error("id is not a UUID type");
            return Mono.error(new UserException("id is not a UUID"));
        }

        return userService.getUserByAuthenticationId(serverRequest.pathVariable("authId"))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable ->
                        ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(throwable.getMessage()));
    }
}
