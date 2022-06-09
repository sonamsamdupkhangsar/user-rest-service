package me.sonam.user.handler;

import reactor.core.publisher.Mono;

/**
 * User service for signing up a user
 */
public interface UserService {
    Mono<String> signupUser(Mono<UserTransfer> userMono);
}
