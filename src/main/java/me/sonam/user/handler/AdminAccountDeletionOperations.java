package me.sonam.user.handler;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Trusted backend operations needed by {@link AdminAccountDeletionService}.
 *
 * <p>This is not a browser-supplied deletion plan. An implementation must read the
 * authoritative organization, role, and authorization services with internal service
 * credentials. Empty HTTP responses, incomplete pagination, and missing protection policy
 * must never be interpreted as permission to delete.
 *
 * <p>{@link ServiceAdminAccountDeletionOperations} implements these operations through
 * guarded internal APIs. It is registered only when admin-account deletion is explicitly
 * enabled. Internal Router endpoints accept delegation from the authorization server's signed-in admin deletion page.
 */
public interface AdminAccountDeletionOperations {

    /**
     * Inventories every organization the user belongs to, created, or administers,
     * including organizations outside the user's default organization. Include all
     * administrative assignments; unknown role/scope types are unresolved responsibilities.
     * Client users must include both client-role assignments and client-user associations.
     * Protection verification must explicitly cover seeded/platform organizations and
     * infrastructure clients, not merely rely on an organization's member count.
     */
    Mono<AccountInventory> loadAccountDeletionInventory(UUID userId);

    /**
     * Rechecks the entire account's permissions, administrative responsibilities,
     * membership, protection policy, client inventory, and sharing against this snapshot.
     * Returns true only if all checks still hold. This is not a distributed lock.
     */
    Mono<Boolean> revalidateAccount(UUID userId, AccountInventory accountInventory);

    /**
     * Deletes the specified client, its organization/client-user associations, and its
     * stored authorizations and consents in the authoritative issuer. Must repeat local
     * sharing/protection checks before mutation and reject an unexpected client.
     */
    Mono<Void> deleteClientData(UUID userId, UUID organizationId, ClientInventory clientInventory);

    /**
     * Deletes organization-owned role definitions, client-role assignments, and admin
     * assignments only after rechecking that no other users/admins depend on them.
     * Never deletes the global OrgAdmin/SubdomainAdmin role definitions.
     */
    Mono<Void> deleteOrganizationRoleData(UUID userId, UUID organizationId);

    /**
     * Deletes the organization, positions, memberships, default selections, and its
     * subdomain associations. Does not delete the shared subdomain itself. Must repeat
     * local sole-member, creator/admin, and protected-resource checks.
     */
    Mono<Void> deleteOrganizationData(UUID userId, UUID organizationId);

    /**
     * Deletes only this user's uploaded profile photo/thumbnail objects. No-op if none
     * exist; an error must stop account deletion rather than silently orphan objects.
     */
    Mono<Void> deletePersonalFiles(UUID userId);

    record AccountInventory(
            UUID userId,
            boolean accountInventoryLoaded,
            boolean protectionPolicyVerified,
            boolean hasSubdomainAdminResponsibilities,
            boolean hasUnresolvedAdminResponsibilities,
            Set<UUID> relatedOrganizationIds,
            List<OrganizationInventory> organizations) {
        public AccountInventory {
            relatedOrganizationIds = relatedOrganizationIds == null ? null : Set.copyOf(relatedOrganizationIds);
            organizations = organizations == null ? null : List.copyOf(organizations);
        }
    }

    record OrganizationInventory(
            UUID organizationId,
            UUID creatorUserId,
            boolean organizationInspectionPassed,
            boolean protectedResource,
            Set<UUID> memberUserIds,
            Set<UUID> orgAdminUserIds,
            List<ClientInventory> clients) {
        public OrganizationInventory {
            memberUserIds = memberUserIds == null ? null : Set.copyOf(memberUserIds);
            orgAdminUserIds = orgAdminUserIds == null ? null : Set.copyOf(orgAdminUserIds);
            clients = clients == null ? null : List.copyOf(clients);
        }
    }

    record ClientInventory(
            UUID clientId,
            String issuer,
            boolean clientInspectionPassed,
            boolean protectedResource,
            Set<UUID> organizationIds,
            Set<UUID> assignedUserIds,
            boolean hasOtherUserAuthorizations) {
        public ClientInventory {
            organizationIds = organizationIds == null ? null : Set.copyOf(organizationIds);
            assignedUserIds = assignedUserIds == null ? null : Set.copyOf(assignedUserIds);
        }
    }
}
