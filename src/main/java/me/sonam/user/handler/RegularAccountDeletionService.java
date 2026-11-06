package me.sonam.user.handler;

import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import me.sonam.user.webclient.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.UUID;

/** Personal records only: no organization, OAuth client, or shared role-definition deletion. */
@Service
public class RegularAccountDeletionService {
    private final UserRepository userRepository;
    private final RoleWebClient roleWebClient;
    private final OrganizationWebClient organizationWebClient;
    private final AccountWebClient accountWebClient;
    private final AuthenticationWebClient authenticationWebClient;

    public RegularAccountDeletionService(
            UserRepository userRepository,
            RoleWebClient roleWebClient,
            OrganizationWebClient organizationWebClient,
            AccountWebClient accountWebClient,
            AuthenticationWebClient authenticationWebClient) {
        this.userRepository = userRepository;
        this.roleWebClient = roleWebClient;
        this.organizationWebClient = organizationWebClient;
        this.accountWebClient = accountWebClient;
        this.authenticationWebClient = authenticationWebClient;
    }

    public Mono<Void> checkByAuthenticationId(String name) {
        return byName(name)
                .flatMap(user -> requireNoAdminRoles(user.getId()));
    }

    public Mono<Void> deleteByAuthenticationId(String name) {
        return byName(name)
                .flatMap(this::delete);
    }

    private Mono<MyUser> byName(String name) {
        return userRepository.findByAuthenticationIdIgnoreCase(name)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")));
    }

    private Mono<Void> requireNoAdminRoles(UUID userId) {
        return roleWebClient.adminAssignmentCount(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify admin roles")))
                .flatMap(count -> count == 0 ? Mono.<Void>empty() : Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Transfer or remove all organization and subdomain admin roles before deleting your account")));
    }

    private Mono<Void> delete(MyUser user) {
        UUID id = user.getId();

        return requireNoAdminRoles(id)
                .then(Mono.defer(() -> roleWebClient.deletePersonalAssignments(id)))
                .then(Mono.defer(() -> organizationWebClient.deletePersonalMemberships(id)))
                .then(Mono.defer(() -> accountWebClient.deleteUserData(id)))
                .then(Mono.defer(() -> authenticationWebClient.deleteUserData(id)))
                .then(Mono.defer(() -> userRepository.deleteById(id)));
    }
}
