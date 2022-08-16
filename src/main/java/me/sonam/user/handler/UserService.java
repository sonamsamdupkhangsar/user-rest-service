package me.sonam.user.handler;

import me.sonam.user.repo.entity.MyUser;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * User service for signing up a user
 */
public interface UserService {
    Mono<String> signupUser(Mono<UserTransfer> userMono);
    Mono<String> updateUser(String authenticationId, Mono<UserTransfer> userMono);
    Mono<String> updateProfilePhoto(String authenticationId, Mono<String> profilePhotoUrlMono);
    Mono<MyUser> getUserByAuthenticationId(String authenticationId);
    Flux<MyUser> findMatchingName(String firstName, String lastName);
    Mono<String> activateUser(String authenticationId);
}
