package me.sonam.user.handler;

import me.sonam.user.handler.carrier.User;
import me.sonam.user.repo.entity.MyUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User service for signing up a user
 */
public interface UserService {
    Mono<String> signupUser(Mono<UserTransfer> userMono);
    Mono<String> updateUser(String authenticationId, Mono<UserTransfer> userMono);
    Mono<String> updateProfilePhoto(String authenticationId, Mono<String> profilePhotoUrlMono);
  //  Mono<MyUser> getUserByAuthenticationId(String authenticationId);
    Flux<MyUser> findMatchingName(String firstName, String lastName);
    Mono<String> activateUser(String authenticationId);
    Mono<String> deleteUser(String authentiationId);
    Mono<Map<String, Object>> getUserByAuthenticationId(String authenticationId);
    Mono<Map<String, Object>> getUserByAuthenticationIdForProfileSearch(String authenticationId);
    Mono<Map<String, Object>> getUserForOidcUserInfo(UUID userId);
    Mono<User> getUserById(UUID id);
    Mono<List<User>> getBatchOfUserById(List<UUID> uuids);
}
