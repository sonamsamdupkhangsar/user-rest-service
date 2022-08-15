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

import java.net.URI;
import java.util.UUID;

@Service
public class UserHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UserHandler.class);

    @Autowired
    private UserService userService;

    public Mono<ServerResponse> signupUser(ServerRequest serverRequest) {
        LOG.info("authenticate user");
       return userService.signupUser(serverRequest.bodyToMono(UserTransfer.class))
                .flatMap(s ->  ServerResponse.created(URI.create("/user/")).contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("signup user failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }

    public Mono<ServerResponse> update(ServerRequest serverRequest) {
        LOG.info("authenticate user");
        return userService.updateUser(serverRequest.headers().firstHeader("authId"), serverRequest.bodyToMono(UserTransfer.class))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.info("user update failed", throwable);
                       return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(throwable.getMessage());
                });
    }
    //updateProfilePhoto
    public Mono<ServerResponse> updateProfilePhoto(ServerRequest serverRequest) {
        LOG.info("update profile photo");
        return userService.updateProfilePhoto(serverRequest.headers().firstHeader("authId"), serverRequest.bodyToMono(String.class))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.info("update profile photo failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }

    public Mono<ServerResponse> findMatchingFirstNameAndLastName(ServerRequest serverRequest) {
        LOG.info("authenticate user");
        LOG.info("http headers: {}", serverRequest.headers());

        return userService.findMatchingName(serverRequest.pathVariable("firstName"), serverRequest.pathVariable("lastName"))
                .collectList().flatMap(myUsers -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(myUsers))
                .onErrorResume(throwable -> {
                    LOG.error("find matching firstname and lastname", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }

    public Mono<ServerResponse> getUserByAuthId(ServerRequest serverRequest) {
        LOG.info("authenticate user");
        if (serverRequest.pathVariable("authId") == null || serverRequest.pathVariable("authId").isEmpty()) {
            return Mono.error(new UserException("authenticationId is null"));
        }

        return userService.getUserByAuthenticationId(serverRequest.pathVariable("authId"))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("get user by authid failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }

    public Mono<ServerResponse> activateUser(ServerRequest serverRequest) {
        LOG.info("authenticate user");

        return userService.activateUser(serverRequest.pathVariable("authenticationId"))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("activate user failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }
}
