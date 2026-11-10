package me.sonam.user;

import me.sonam.user.handler.AdminAccountDeletionOperations;
import me.sonam.user.handler.AdminAccountDeletionOperations.AccountInventory;
import me.sonam.user.handler.AdminAccountDeletionOperations.ClientInventory;
import me.sonam.user.handler.AdminAccountDeletionOperations.OrganizationInventory;
import me.sonam.user.handler.AdminAccountDeletionService;
import me.sonam.user.handler.AdminAccountDeletionService.Blocker;
import me.sonam.user.handler.AdminAccountDeletionService.DeletionBlockedException;
import me.sonam.user.handler.RegularAccountDeletionService;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccountDeletionServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final String authenticationId = "owner+free@example.com";
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminAccountDeletionOperations adminAccountDeletionOperations = mock(AdminAccountDeletionOperations.class);
    private final RegularAccountDeletionService regularAccountDeletionService = mock(RegularAccountDeletionService.class);
    private final AdminAccountDeletionService adminAccountDeletionService = new AdminAccountDeletionService(
            userRepository,
            adminAccountDeletionOperations,
            regularAccountDeletionService);

    AdminAccountDeletionServiceTest() {
        MyUser user = new MyUser(userId, "Owner", "User", authenticationId, authenticationId, true);
        when(userRepository.findByAuthenticationIdIgnoreCase(authenticationId))
                .thenReturn(Mono.just(user));
    }

    @Test
    void eligibilityCheckIsReadOnlyAndAllowsOwnedOauthClients() {
        when(adminAccountDeletionOperations.loadAccountDeletionInventory(userId))
                .thenReturn(Mono.just(eligibleAccount()));

        var deletionCheck = adminAccountDeletionService.checkByAuthenticationId(authenticationId)
                .block();

        assertThat(deletionCheck.eligible()).isTrue();
        assertThat(deletionCheck.blockers()).isEmpty();
        verify(adminAccountDeletionOperations).loadAccountDeletionInventory(userId);
        verifyNoMoreInteractions(adminAccountDeletionOperations);
        verifyNoInteractions(regularAccountDeletionService);
    }

    @Test
    void deletionRechecksThenDeletesClientRolesOrganizationFilesAndAccountInOrder() {
        AccountInventory accountInventory = eligibleAccount();
        stubSuccessfulDeletion(accountInventory);

        adminAccountDeletionService.deleteByAuthenticationId(authenticationId, AdminAccountDeletionService.CONFIRMATION)
                .block();

        var inOrder = inOrder(adminAccountDeletionOperations, regularAccountDeletionService);
        inOrder.verify(adminAccountDeletionOperations).loadAccountDeletionInventory(userId);
        inOrder.verify(adminAccountDeletionOperations).revalidateAccount(userId, eligibleAccount());
        inOrder.verify(adminAccountDeletionOperations).deleteClientData(userId, organizationId, eligibleClient());
        inOrder.verify(adminAccountDeletionOperations).deleteOrganizationRoleData(userId, organizationId);
        inOrder.verify(adminAccountDeletionOperations).deleteOrganizationData(userId, organizationId);
        inOrder.verify(adminAccountDeletionOperations).deletePersonalFiles(userId);
        inOrder.verify(regularAccountDeletionService).deleteByAuthenticationId(authenticationId);
        verify(userRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void requiresExplicitOrganizationAndAccountConfirmation() {
        assertThatThrownBy(() -> adminAccountDeletionService.deleteByAuthenticationId(authenticationId, "DELETE")
                .block())
                .hasMessageContaining("Confirm deletion");

        verifyNoInteractions(userRepository, adminAccountDeletionOperations, regularAccountDeletionService);
    }

    @Test
    void subdomainAdminIsBlockedBeforeAnyCleanup() {
        AccountInventory accountInventory = new AccountInventory(
                userId, true, true, true, false, Set.of(organizationId), List.of(eligibleOrganization()));

        assertBlocked(accountInventory, "SUBDOMAIN_ADMIN");
    }

    @Test
    void unknownAdminResponsibilitiesAreBlocked() {
        AccountInventory accountInventory = new AccountInventory(
                userId, true, true, false, true, Set.of(organizationId), List.of(eligibleOrganization()));

        assertBlocked(accountInventory, "OTHER_ADMIN_RESPONSIBILITIES");
    }

    @Test
    void otherOrganizationMembersMustBeRemovedManually() {
        OrganizationInventory organizationInventory = new OrganizationInventory(
                organizationId, userId, true, false, Set.of(userId, otherUserId), Set.of(userId), List.of(eligibleClient()));

        assertBlocked(accountWith(organizationInventory), "OTHER_OR_INCONSISTENT_MEMBERS");
    }

    @Test
    void otherAdminEvenWithoutMembershipBlocksDeletion() {
        OrganizationInventory organizationInventory = new OrganizationInventory(
                organizationId, userId, true, false, Set.of(userId), Set.of(userId, otherUserId), List.of(eligibleClient()));

        assertBlocked(accountWith(organizationInventory), "OTHER_OR_MISSING_ADMIN");
    }

    @Test
    void soleMembershipDoesNotGrantOrgAdminPermission() {
        OrganizationInventory organizationInventory = new OrganizationInventory(
                organizationId, userId, true, false, Set.of(userId), Set.of(), List.of(eligibleClient()));

        assertBlocked(accountWith(organizationInventory), "OTHER_OR_MISSING_ADMIN");
    }

    @Test
    void anotherUsersOrganizationCannotBeDeleted() {
        OrganizationInventory organizationInventory = new OrganizationInventory(
                organizationId, otherUserId, true, false, Set.of(userId), Set.of(userId), List.of(eligibleClient()));

        assertBlocked(accountWith(organizationInventory), "OTHER_CREATOR");
    }

    @Test
    void protectedPlatformOrganizationIsBlocked() {
        OrganizationInventory organizationInventory = new OrganizationInventory(
                organizationId, userId, true, true, Set.of(userId), Set.of(userId), List.of(eligibleClient()));

        assertBlocked(accountWith(organizationInventory), "PROTECTED_ORGANIZATION");
    }

    @Test
    void protectionPolicyMustBeExplicitlyVerified() {
        AccountInventory accountInventory = new AccountInventory(
                userId, true, false, false, false, Set.of(organizationId), List.of(eligibleOrganization()));

        assertBlocked(accountInventory, "PROTECTION_UNVERIFIED");
    }

    @Test
    void protectedInfrastructureClientIsBlocked() {
        ClientInventory clientInventory = new ClientInventory(
                clientId, "https://free.example/issuer", true, true, Set.of(organizationId), Set.of(), false);

        assertBlocked(accountWithClient(clientInventory), "PROTECTED_CLIENT");
    }

    @Test
    void clientsSharedWithOtherOrganizationsAreBlocked() {
        ClientInventory clientInventory = new ClientInventory(
                clientId, "https://free.example/issuer", true, false,
                Set.of(organizationId, UUID.randomUUID()), Set.of(), false);

        assertBlocked(accountWithClient(clientInventory), "SHARED_CLIENT");
    }

    @Test
    void otherUsersClientAssignmentsAreBlockedEvenWithoutOrgMembership() {
        ClientInventory clientInventory = new ClientInventory(
                clientId, "https://free.example/issuer", true, false, Set.of(organizationId), Set.of(otherUserId), false);

        assertBlocked(accountWithClient(clientInventory), "OTHER_CLIENT_USERS");
    }

    @Test
    void otherUsersStoredAuthorizationsAreBlocked() {
        ClientInventory clientInventory = new ClientInventory(
                clientId, "https://free.example/issuer", true, false, Set.of(organizationId), Set.of(), true);

        assertBlocked(accountWithClient(clientInventory), "OTHER_CLIENT_USERS");
    }

    @Test
    void missingOrganizationInInventoryBlocksAllDeletion() {
        AccountInventory accountInventory = new AccountInventory(
                userId, true, true, false, false,
                Set.of(organizationId, UUID.randomUUID()), List.of(eligibleOrganization()));

        assertBlocked(accountInventory, "INCOMPLETE_INVENTORY");
    }

    @Test
    void secondSharedOrganizationBlocksCleanupOfTheFirstOneToo() {
        UUID secondOrganizationId = UUID.randomUUID();
        OrganizationInventory secondOrganizationInventory = new OrganizationInventory(
                secondOrganizationId, userId, true, false, Set.of(userId, otherUserId), Set.of(userId), List.of());
        AccountInventory accountInventory = new AccountInventory(
                userId, true, true, false, false,
                Set.of(organizationId, secondOrganizationId), List.of(eligibleOrganization(), secondOrganizationInventory));

        assertBlocked(accountInventory, "OTHER_OR_INCONSISTENT_MEMBERS");
    }

    @Test
    void incompleteClientInventoryFailsClosed() {
        ClientInventory clientInventory = new ClientInventory(
                clientId, "https://free.example/issuer", false, false, Set.of(organizationId), Set.of(), false);

        assertBlocked(accountWithClient(clientInventory), "INCOMPLETE_CLIENT");
    }

    @Test
    void unavailableInventoryProducesReadOnlyBlocker() {
        when(adminAccountDeletionOperations.loadAccountDeletionInventory(userId))
                .thenReturn(Mono.error(new IllegalStateException("downstream unavailable")));

        var deletionCheck = adminAccountDeletionService.checkByAuthenticationId(authenticationId)
                .block();

        assertThat(deletionCheck.eligible()).isFalse();
        assertThat(deletionCheck.blockers())
                .extracting(Blocker::code)
                .contains("VERIFICATION_UNAVAILABLE");
        assertNoCleanup();
    }

    @Test
    void emptyInventoryBlocksDeletion() {
        when(adminAccountDeletionOperations.loadAccountDeletionInventory(userId))
                .thenReturn(Mono.empty());

        assertThatThrownBy(() -> adminAccountDeletionService.deleteByAuthenticationId(
                        authenticationId, AdminAccountDeletionService.CONFIRMATION)
                .block())
                .isInstanceOf(DeletionBlockedException.class);
        assertNoCleanup();
    }

    @Test
    void wrongAccountInventoryCannotDeleteSomeoneElse() {
        AccountInventory accountInventory = new AccountInventory(
                otherUserId, true, true, false, false, Set.of(organizationId), List.of(eligibleOrganization()));

        assertBlocked(accountInventory, "INCOMPLETE_INVENTORY");
    }

    @Test
    void deleteDoesNotTrustAnEarlierSuccessfulEligibilityCheck() {
        AccountInventory blockedInventory = new AccountInventory(
                userId, true, true, true, false, Set.of(organizationId), List.of(eligibleOrganization()));
        when(adminAccountDeletionOperations.loadAccountDeletionInventory(userId))
                .thenReturn(Mono.just(eligibleAccount()), Mono.just(blockedInventory));

        assertThat(adminAccountDeletionService.checkByAuthenticationId(authenticationId)
                .block().eligible())
                .isTrue();
        assertThatThrownBy(() -> adminAccountDeletionService.deleteByAuthenticationId(
                        authenticationId, AdminAccountDeletionService.CONFIRMATION)
                .block())
                .isInstanceOf(DeletionBlockedException.class);
        assertNoCleanup();
    }

    @Test
    void changedOrMissingRevalidationStopsAllCleanup() {
        when(adminAccountDeletionOperations.loadAccountDeletionInventory(userId))
                .thenReturn(Mono.just(eligibleAccount()));
        when(adminAccountDeletionOperations.revalidateAccount(userId, eligibleAccount()))
                .thenReturn(Mono.just(false), Mono.empty());

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> adminAccountDeletionService.deleteByAuthenticationId(
                            authenticationId, AdminAccountDeletionService.CONFIRMATION)
                    .block())
                    .hasMessageContaining("relationships changed");
        }

        assertNoCleanup();
    }

    @Test
    void clientCleanupFailureStopsRoleOrganizationAndAccountDeletion() {
        stubSuccessfulDeletion(eligibleAccount());
        when(adminAccountDeletionOperations.deleteClientData(userId, organizationId, eligibleClient()))
                .thenReturn(Mono.error(new IllegalStateException("client cleanup failed")));

        assertThatThrownBy(() -> adminAccountDeletionService.deleteByAuthenticationId(
                        authenticationId, AdminAccountDeletionService.CONFIRMATION)
                .block())
                .hasMessage("client cleanup failed");

        verify(adminAccountDeletionOperations, never()).deleteOrganizationRoleData(any(), any());
        verify(adminAccountDeletionOperations, never()).deleteOrganizationData(any(), any());
        verify(adminAccountDeletionOperations, never()).deletePersonalFiles(any());
        verifyNoInteractions(regularAccountDeletionService);
    }

    @Test
    void photoFileCleanupFailureKeepsPersonalAccount() {
        stubSuccessfulDeletion(eligibleAccount());
        when(adminAccountDeletionOperations.deletePersonalFiles(userId))
                .thenReturn(Mono.error(new IllegalStateException("file cleanup failed")));

        assertThatThrownBy(() -> adminAccountDeletionService.deleteByAuthenticationId(
                        authenticationId, AdminAccountDeletionService.CONFIRMATION)
                .block())
                .hasMessage("file cleanup failed");

        verifyNoInteractions(regularAccountDeletionService);
    }

    private void assertBlocked(AccountInventory accountInventory, String expectedCode) {
        when(adminAccountDeletionOperations.loadAccountDeletionInventory(userId))
                .thenReturn(Mono.just(accountInventory));

        var deletionCheck = adminAccountDeletionService.checkByAuthenticationId(authenticationId)
                .block();

        assertThat(deletionCheck.eligible()).isFalse();
        assertThat(deletionCheck.blockers())
                .extracting(Blocker::code)
                .contains(expectedCode);
        assertThatThrownBy(() -> adminAccountDeletionService.deleteByAuthenticationId(
                        authenticationId, AdminAccountDeletionService.CONFIRMATION)
                .block())
                .isInstanceOf(DeletionBlockedException.class);
        assertNoCleanup();
    }

    private void assertNoCleanup() {
        verify(adminAccountDeletionOperations, never()).deleteClientData(any(), any(), any());
        verify(adminAccountDeletionOperations, never()).deleteOrganizationRoleData(any(), any());
        verify(adminAccountDeletionOperations, never()).deleteOrganizationData(any(), any());
        verify(adminAccountDeletionOperations, never()).deletePersonalFiles(any());
        verifyNoInteractions(regularAccountDeletionService);
        verify(userRepository, never()).deleteById(any(UUID.class));
    }

    private void stubSuccessfulDeletion(AccountInventory accountInventory) {
        when(adminAccountDeletionOperations.loadAccountDeletionInventory(userId))
                .thenReturn(Mono.just(accountInventory));
        when(adminAccountDeletionOperations.revalidateAccount(userId, eligibleAccount()))
                .thenReturn(Mono.just(true));
        when(adminAccountDeletionOperations.deleteClientData(userId, organizationId, eligibleClient()))
                .thenReturn(Mono.empty());
        when(adminAccountDeletionOperations.deleteOrganizationRoleData(userId, organizationId))
                .thenReturn(Mono.empty());
        when(adminAccountDeletionOperations.deleteOrganizationData(userId, organizationId))
                .thenReturn(Mono.empty());
        when(adminAccountDeletionOperations.deletePersonalFiles(userId))
                .thenReturn(Mono.empty());
        when(regularAccountDeletionService.deleteByAuthenticationId(authenticationId))
                .thenReturn(Mono.empty());
    }

    private AccountInventory eligibleAccount() {
        return accountWith(eligibleOrganization());
    }

    private AccountInventory accountWith(OrganizationInventory organizationInventory) {
        return new AccountInventory(
                userId, true, true, false, false, Set.of(organizationId), List.of(organizationInventory));
    }

    private AccountInventory accountWithClient(ClientInventory clientInventory) {
        return accountWith(new OrganizationInventory(
                organizationId, userId, true, false, Set.of(userId), Set.of(userId), List.of(clientInventory)));
    }

    private OrganizationInventory eligibleOrganization() {
        return new OrganizationInventory(
                organizationId, userId, true, false, Set.of(userId), Set.of(userId), List.of(eligibleClient()));
    }

    private ClientInventory eligibleClient() {
        return new ClientInventory(
                clientId, "https://free.example/issuer", true, false, Set.of(organizationId), Set.of(userId), false);
    }
}
