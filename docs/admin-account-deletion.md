# Admin-account deletion backend

## Status

`AdminAccountDeletionService` contains the eligibility rules and orchestration.
`ServiceAdminAccountDeletionOperations` is the concrete implementation of its operations:
it reads and mutates the organization, role, and authorization services through guarded
internal endpoints, and deletes user-photo objects through the async S3 client.

The coordinator and adapter are registered only with `account-deletion.admin-enabled=true`
in user-rest-service. This defaults to false. The signed-in authorization page at
`/account/admin/delete` uses service-only user-service Router endpoints for eligibility and deletion.
Admin console Delete My Data links point to that page, never directly to internal APIs.
POST requires CSRF and `DELETE MY ACCOUNT AND ORGANIZATIONS`; the username comes from the session.
Existing regular-user deletion remains unchanged and
still blocks administrative assignments. The older bulk `DELETE /clients` API is not used.

Nothing has been enabled or deployed in the live environment. Tests use in-memory
repositories/databases, in-process HTTP routing, a simulated HTTP transport, and a mocked
S3 client; they do not delete live data.

## Calls and ownership

- User service combines the authoritative inventories and repeats the complete account
  inventory before the first mutation. It rechecks relevant state before each stage.
- Organization service inventories owned, joined, and default-selected organizations;
  cleanup rechecks sole membership, creator identity, and protection, and removes positions,
  default selections, memberships, and subdomain associations before the organization.
  A stale default selection owned by another user or a position referenced by another
  organization blocks cleanup. Memberships are removed before their positions. The
  shared subdomain is preserved.
- Role service inventories all administrative assignments and personal client-role
  relationships. Unknown administrative roles/scopes block cleanup. Deletion rechecks
  OrgAdmin permission, other administrators/users, cross-organization role usage, and
  protection. Global OrgAdmin/SubdomainAdmin definitions are never deleted.
- Authorization service inventories client-user relationships and organization
  clients. It locates each client in the registered issuer databases, rejecting missing
  or ambiguous locations. Cleanup rechecks protection, organization sharing, client-user associations,
  and other-user grants/consents, then deletes OAuth data and client associations.
- User service adds client-role relationships to the authorization inventory, so a client
  shared through role assignments also blocks cleanup. Orphan relationships fail closed.

Other organization members must be removed manually before retrying. The requester must
be the creator and sole member/OrgAdmin of every affected organization and must have no
SubdomainAdmin or unresolved administrative responsibility. Creating a client is not a
blocker: owned, unshared clients are removed automatically.

## Internal APIs

All APIs below require the existing shared internal OAuth client identity from the
configured internal issuer, without an end-user `userId` claim. JWT validation remains
in each application's security filter chain. The new reactive-service routes are in the
existing Router configurations; the authorization application retains its servlet
controller pattern.

| Service | Method and path |
| --- | --- |
| Organization | GET `/organizations/internal/admin-deletion/users/{userId}` |
| Organization | GET/DELETE `/organizations/internal/admin-deletion/users/{userId}/organizations/{organizationId}` |
| Role | GET `/roles/internal/admin-deletion/users/{userId}` |
| Role | GET/DELETE `/roles/internal/admin-deletion/users/{userId}/organizations/{organizationId}` |
| Role | GET `/roles/internal/admin-deletion/clients/{clientId}` |
| Authorization | GET `/clients/internal/admin-deletion/users/{userId}` |
| Authorization | GET `/clients/internal/admin-deletion/users/{userId}/organizations/{organizationId}` |
| Authorization | DELETE `/clients/internal/admin-deletion/users/{userId}/organizations/{organizationId}/clients/{clientId}` |

Authorization organization/client calls also require the canonical `authenticationId`
query parameter. DELETE requires the issuer returned by inventory, not a browser-selected
issuer. The adapter URL is configured on the server; it never sends network requests to
an issuer URL supplied in a deletion plan.

## Required protection configuration

Before enabling the coordinator, review the protected platform/seed organizations and
infrastructure clients. In **organization, role, and authorization** services, configure:

- `account-deletion.protection-policy-configured=true` only after that review.
- `account-deletion.protected-organization-ids`: the same reviewed, comma-separated UUID
  list in all three services. Missing configuration is not an empty reviewed list.
- In authorization, also set `account-deletion.protected-client-ids`: reviewed client UUIDs
  or OAuth client identifiers. The shared internal OAuth client is always protected,
  even if omitted from this list.

Missing policy/list configuration blocks deletion. Do not guess these values or turn on
the review flag without identifying the resources to preserve. An explicitly empty string
means a reviewed empty list, not permission to skip review. Malformed UUIDs fail startup.

Deploy the new role, organization, and authorization APIs before enabling the user-service
adapter. Configure protection independently in each affected namespace. No Terraform,
Helm values, live secrets, or running deployments were changed as part of this implementation.

In user service, `account-deletion.authorization-url` defaults to
`${auth-server.root}/clients/internal/admin-deletion`. Override it only with the trusted
internal authorization-service address if deployment routing requires it. Existing
organization/role URLs and `serviceWebClientBuilder` are reused. That builder's token
filter obtains the shared service JWT and requires the application's reactive request
context; this is not a standalone scheduled-job API.

## Cleanup sequence and limitations

After checking every organization and revalidating the complete account snapshot:

1. Remove each approved organization's clients, grants/consents, and client associations.
2. Remove its application-role definitions and role/admin assignments.
3. Remove its organization records, including positions and associations.
4. Remove the user's photo/thumbnail objects under their exact UUID-specific prefix.
5. Invoke regular-user cleanup, repeating its zero-admin check and deleting the user
   profile last.

Photo cleanup uses paginated S3 listing, awaits every delete, validates the returned keys
against the exact user prefix, and rejects unsuccessful storage responses. It does not
use the older library folder helper, which does not await its individual deletes.

The authorization page performs current-issuer passkey/user-grant/consent cleanup and logout
after success. It derives identity from the session, enforces CSRF and informed confirmation,
and displays read-only eligibility blockers. A failure stops later work and warns about partial
cleanup. Live rollout and protection configuration remain explicit operator actions.

Deletion is not distributed-transactional. Role and organization writes have local
transactions. OAuth tenant data and central client associations have separate transactions.
An error stops later work but cannot roll back earlier service changes. Concurrent
membership/role changes remain the previously accepted known limitation; rechecks are
not a distributed lock. There is no automatic recovery job. Self-contained tokens,
backups, previous S3 object versions in versioned buckets, and other application sessions
are not guaranteed erased or revoked immediately.

## Verification

Run the full user, role, and organization test suites with `./gradlew test`. In authorization:

```bash
./gradlew test --tests '*AdminClientDeletionTest' \\
  --tests '*AccountDeletionControllerTest' \\
  --tests '*AccountSecurityCleanupTest' \\
  --tests '*ClientCleanupSafetyTest' \\
  --tests '*ClientRestServiceIntegTest'
```
