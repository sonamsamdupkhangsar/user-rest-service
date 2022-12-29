package me.sonam.user.handler;

import me.sonam.user.repo.entity.MyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private static final String AUTHENTICATION_ID = "authenticationId";
    @Autowired
    private UserService userService;

    public Mono<ServerResponse> signupUser(ServerRequest serverRequest) {
        LOG.info("signup user");
       return userService.signupUser(serverRequest.bodyToMono(UserTransfer.class))
                .flatMap(s ->  ServerResponse.created(URI.create("/user/")).contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("signup user failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }

    public Mono<ServerResponse> update(ServerRequest serverRequest) {
        String authenticationId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        LOG.info("authenticate user for authId: {}", authenticationId);

        return userService.updateUser(authenticationId, serverRequest.bodyToMono(UserTransfer.class))
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
        String authenticationId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();

        return userService.updateProfilePhoto(authenticationId, serverRequest.bodyToMono(String.class))
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

    /**
     * allow a user to get user information by authenticationId
     * @param serverRequest
     * @return
     */
    public Mono<ServerResponse> getUserByAuthId(ServerRequest serverRequest) {
        LOG.info("authenticate user");

        return userService.getUserByAuthenticationId(serverRequest.pathVariable(AUTHENTICATION_ID))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("get user by authid failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }

    // The account-rest-service will pass the JWT token to identify the user using security
    public Mono<ServerResponse> activateUser(ServerRequest serverRequest) {
        LOG.info("activate user");
        String authenticationId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();

        return userService.activateUser(authenticationId)
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("activate user failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }

    public Mono<ServerResponse> deleteUser(ServerRequest serverRequest) {
        LOG.info("delete user");
        String authenticationId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();

        return userService.deleteUser(authenticationId)
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("delete user failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }


}
