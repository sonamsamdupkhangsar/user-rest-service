package me.sonam.user;

import me.sonam.user.handler.RegularAccountDeletionService;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import me.sonam.user.webclient.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelfServiceDeletionTest {
    private final UUID id = UUID.randomUUID();
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleWebClient roleWebClient = mock(RoleWebClient.class);
    private final OrganizationWebClient organizationWebClient = mock(OrganizationWebClient.class);
    private final AccountWebClient accountWebClient = mock(AccountWebClient.class);
    private final AuthenticationWebClient authenticationWebClient = mock(AuthenticationWebClient.class);
    private final RegularAccountDeletionService regularAccountDeletionService = new RegularAccountDeletionService(userRepository, roleWebClient, organizationWebClient, accountWebClient, authenticationWebClient);

    SelfServiceDeletionTest() {
        MyUser user = new MyUser(id, "Regular", "User", "user@example.com", "user+test@example.com", true);
        when(userRepository.findById(id))
                .thenReturn(Mono.just(user));
        when(userRepository.findByAuthenticationIdIgnoreCase("user+test@example.com"))
                .thenReturn(Mono.just(user));
    }

    private void run() {
        regularAccountDeletionService.deleteByAuthenticationId("user+test@example.com")
                .block();
    }

    private void stubCleanup() {
        when(roleWebClient.adminAssignmentCount(id))
                .thenReturn(Mono.just(0L));
        when(roleWebClient.deletePersonalAssignments(id))
                .thenReturn(Mono.empty());
        when(organizationWebClient.deletePersonalMemberships(id))
                .thenReturn(Mono.empty());
        when(accountWebClient.deleteUserData(id))
                .thenReturn(Mono.just("deleted"));
        when(authenticationWebClient.deleteUserData(id))
                .thenReturn(Mono.just("deleted"));
        when(userRepository.deleteById(id))
                .thenReturn(Mono.empty());
    }

    @Test
    void nonAdminRemovesOnlyPersonalDataAndDeletesProfileLast() {
        stubCleanup();
        run();
        var order = inOrder(roleWebClient, organizationWebClient, accountWebClient, authenticationWebClient, userRepository);
        order.verify(roleWebClient)
                .deletePersonalAssignments(id);
        order.verify(organizationWebClient)
                .deletePersonalMemberships(id);
        order.verify(accountWebClient)
                .deleteUserData(id);
        order.verify(authenticationWebClient)
                .deleteUserData(id);
        order.verify(userRepository)
                .deleteById(id);
        verify(organizationWebClient, never())
                .deleteUserData(any(), any());
        verify(roleWebClient, never())
                .deleteUserData(any(), any(), anyBoolean());
    }

    @Test
    void adminCannotDeleteAnyRecords() {
        when(roleWebClient.adminAssignmentCount(id))
                .thenReturn(Mono.just(1L));
        assertThatThrownBy(this::run)
                .hasMessageContaining("admin roles");
        noDeletion();
    }

    @Test
    void unavailableRoleCheckFailsClosed() {
        when(roleWebClient.adminAssignmentCount(id))
                .thenReturn(Mono.error(new IllegalStateException("unavailable")));
        assertThatThrownBy(this::run)
                .hasMessage("unavailable");
        noDeletion();
    }

    @Test
    void missingRoleCheckFailsClosed() {
        when(roleWebClient.adminAssignmentCount(id))
                .thenReturn(Mono.empty());
        assertThatThrownBy(this::run)
                .hasMessageContaining("verify admin roles");
        noDeletion();
    }

    @Test
    void authenticatedDelegationUsesCanonicalUsername() {
        stubCleanup();
        regularAccountDeletionService.deleteByAuthenticationId("user+test@example.com")
                .block();
        verify(userRepository)
                .deleteById(id);
    }

    @Test
    void roleCleanupFailureStopsEverythingAfterIt() {
        when(roleWebClient.adminAssignmentCount(id))
                .thenReturn(Mono.just(0L));
        when(roleWebClient.deletePersonalAssignments(id))
                .thenReturn(Mono.error(new IllegalStateException("failed")));
        assertThatThrownBy(this::run)
                .hasMessage("failed");
        verifyNoInteractions(organizationWebClient, accountWebClient, authenticationWebClient);
        verify(userRepository, never())
                .deleteById(any(UUID.class));
    }

    @Test
    void eligibilityCheckDoesNotDeleteAnything() {
        when(roleWebClient.adminAssignmentCount(id))
                .thenReturn(Mono.just(0L));
        regularAccountDeletionService.checkByAuthenticationId("user+test@example.com")
                .block();
        noDeletion();
    }

    private void noDeletion() {
        verify(roleWebClient, never())
                .deletePersonalAssignments(any());
        verifyNoInteractions(organizationWebClient, accountWebClient, authenticationWebClient);
        verify(userRepository, never())
                .deleteById(any(UUID.class));
    }
}
