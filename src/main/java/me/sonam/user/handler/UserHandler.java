package me.sonam.user.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class UserHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UserHandler.class);
    private static final String AUTHENTICATION_ID = "authenticationId";
    @Autowired
    private UserService userService;

    public Mono<ServerResponse> signupUser(ServerRequest serverRequest) {
        LOG.info("signup user");
        LOG.info("printing myname header value {}", serverRequest.headers().firstHeader("myname"));

       return userService.signupUser(serverRequest.bodyToMono(UserTransfer.class))
                .flatMap(s ->  ServerResponse.created(URI.create("/users/"))
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(getMap(Pair.of("message", s))))
                .onErrorResume(throwable -> {
                    LOG.debug("exception occurred in signupUser", throwable);
                    LOG.error("signup user failed {}", throwable.getMessage());
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "user signup failed with error: " + throwable.getMessage()));
                });
    }

    public Mono<ServerResponse> update(ServerRequest serverRequest) {

        return serverRequest.principal().flatMap(principal ->
                userService.updateUser(principal.getName(), serverRequest.bodyToMono(UserTransfer.class))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.info("user update failed: ", throwable);
                       return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(throwable.getMessage());
                })
        );
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
        LOG.info("get user by authId");

        return userService.getUserByAuthenticationId(serverRequest.pathVariable(AUTHENTICATION_ID))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("get user by authid failed, {}", throwable.getMessage());

                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", throwable.getMessage()));
                });
    }

    public Mono<ServerResponse> getUserByAuthIdProfileSearch(ServerRequest serverRequest) {
        LOG.info("get user by authId for profileSearch");

        return userService.getUserByAuthenticationIdForProfileSearch(serverRequest.pathVariable(AUTHENTICATION_ID))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("get user by authid failed, {}", throwable.getMessage());

                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", throwable.getMessage()));
                });
    }

    public Mono<ServerResponse> getUserById(ServerRequest serverRequest) {
        LOG.info("get user by id");
        UUID id;
        try {
             id = UUID.fromString(serverRequest.pathVariable("id"));
        }
        catch (Exception e) {
            LOG.error("id is not a UUID type, error: {}", e.getMessage());
            return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("error", "id should be a UUID type"));
        }

        return userService.getUserById(id)
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("get user by id failed: {}", throwable.getMessage());

                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "no user found with id"));
                });
    }

    /**
     * allow a user to get user id
     * @param serverRequest
     * @return
     */
    public Mono<ServerResponse> getBatchOfUserById(ServerRequest serverRequest) {
        LOG.info("authenticate user");

        List<UUID> uuidList = Arrays.stream(serverRequest.pathVariable("ids").split(",")).map(UUID::fromString).toList();

        return userService.getBatchOfUserById(uuidList)
                .flatMap(userList -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(userList))
                .onErrorResume(throwable -> {
                    LOG.error("get user by authid failed", throwable);

                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", throwable.getMessage()));
                });
    }

    // The account-rest-service will pass the JWT token to identify the user using security
    public Mono<ServerResponse> activateUser(ServerRequest serverRequest) {
        LOG.info("activate user");

        return userService.activateUser(serverRequest.pathVariable("authenticationId"))
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("activate user failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }

    public Mono<ServerResponse> deleteUser(ServerRequest serverRequest) {
        LOG.info("delete user");

        return serverRequest.principal().flatMap(principal -> userService.deleteUser(principal.getName())
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("delete user failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                })
        );
    }

    public Mono<ServerResponse> deleteMyAccount(ServerRequest serverRequest) {
        LOG.info("delete my account");

        return userService.deleteMyAccount()
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("delete user failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }
/*

    public Mono<ServerResponse> uploadProfilePhoto(ServerRequest serverRequest) {
        LOG.info("upload profile photo");

        serverRequest.multipartData()
                .map(parts -> parts.get("image"))
                .cast(FilePart.class)
                .flatMap(filePart -> {
                    Path tempFile = null;
                    try {
                        tempFile = Files.createTempFile("image-", filePart.filename());
                        return filePart.transferTo(tempFile).then(Mono.just(tempFile.toString()));
                    } catch (IOException e) {
                        LOG.error("failed to create temp file for uploading profile photo", e);
                    }
                    return null;
                })
                .
        return userService.uploadProfilePhoto()
                .flatMap(s ->  ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(throwable -> {
                    LOG.error("profile photo upload failed", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(throwable.getMessage());
                });
    }
*/

    @SafeVarargs
    public static Map<String, String> getMap(Pair<String, String>... pairs){
        Map<String, String> map = new HashMap<>();
        for(Pair<String, String> pair: pairs) {
            map.put(pair.getFirst(), pair.getSecond());
        }
        return map;
    }

}
