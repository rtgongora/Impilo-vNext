# Keycloak workload identity and the missing audience — Checkpoint 4

**Captured:** 2026-08-02 · **Realm:** `impilo` · **Namespace:** `impilo-full-preview`

## Two gaps, both measured

### 1. No per-workload clients

The realm has **11 clients**, and `impilo-backend` is shared by `experience-bff` *and*
`pct-service`. A callee cannot tell one caller from another, which is the defect the trust
doctrine names directly: each workload must authenticate as itself.

### 2. No audience mapper anywhere in the realm

Checkpoint 1 recorded *"JWT audience validation: ABSENT"* as though it were an application gap.
It is not. Inspecting every client and every client scope: **no `oidc-audience-mapper` exists
anywhere in the realm.** Keycloak mints no `aud` claim at all.

So there is nothing for a resource server to validate. A token minted for any client is accepted
by every service that trusts the issuer — **cross-service token replay by default**. And a service
that switched audience validation on today would reject *every* token in the estate, because none
of them carries an audience.

That is why the fix has to be sequenced, and why the two halves are shipped together but activated
separately.

## What is built

| Half | Artefact | State |
|---|---|---|
| Mint the claim | `scripts/keycloak/provision-workload-clients.sh` | ready, **blocked on admin credential** |
| Validate the claim | `WorkloadAudienceValidator` (shared-core), 7 tests | ready, opt-in per service |
| Present a credential | `WorkloadTokenProvider` + interceptor, 9 tests | ready, opt-in per workload |

The provisioning script creates confidential clients with service accounts and **nothing else** —
no standard flow (a workload is not a browser client), no direct access grant (a workload must
never use a password grant), no implicit flow — plus an `oidc-audience-mapper` naming the
**callee's** audience, on the access token only.

`WorkloadAudienceValidator` passes when no audience is configured. A service that has not been
told its audience has not opted in, and rejecting all its traffic would be a self-inflicted outage
rather than a control. The moment an audience *is* configured, a token lacking it is refused.

## Blocker: no usable Keycloak admin credential

`impilo-app-secrets:keycloak-admin-password` does not match the live master-realm admin:

```
POST /realms/master/protocol/openid-connect/token  →  {"error":"invalid_grant",
                                                       "error_description":"Invalid user credentials"}
```

The realm itself is healthy (`/realms/master/.well-known/openid-configuration` → `200`), so this
is credential drift, not an outage — most likely from the H2→Postgres migration, since
`KC_BOOTSTRAP_ADMIN_*` only seeds an admin when none exists.

Every service account is **403 on client management**, verified individually:

| Client | Token | List clients | Create client |
|---|---|---|---|
| `impilo-admin-cli` | OK | 403 | 403 |
| `impilo-user-admin` | OK | 403 | 403 |
| `impilo-backend` | OK | 403 | 403 |

**That 403 is correct and must stay.** The reconciler suite explicitly asserts *"no service
account received manage-realm or manage-clients"*.

### Two workarounds, both refused

- **Reset the master admin password** — modification of an existing human credential, which this
  programme is explicitly not authorised to do.
- **Grant `manage-clients` to a service account** — would widen a least-privilege posture that a
  test suite exists to protect, to save an operator one action.

The script fails with this explanation rather than doing either.

## To unblock

Supply a working admin credential (update `impilo-app-secrets:keycloak-admin-password`, or export
`KC_ADMIN_USER`/`KC_ADMIN_PASSWORD`), then:

```bash
scripts/keycloak/provision-workload-clients.sh --dry-run   # inspect first
scripts/keycloak/provision-workload-clients.sh
```

Then, in order — the sequence is the point:

1. Provision clients + audience mappers (above).
2. Copy each client secret into `impilo-app-secrets` and set `IMPILO_S2S_*` on the caller.
3. Confirm a minted token **actually carries `aud`** before trusting anything downstream.
4. Only then set the callee's required audience and flip
   `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=false`.

Reversing 3 and 4 rejects every call in the cohort.
