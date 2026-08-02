# Checkpoint 7 — first enforced cohort

**Cohort:** `workforce-governance-service` · **Captured:** 2026-08-02
**Namespace:** `impilo-full-preview` · **Branch:** `claude/tshepo-trust-completion-Yypyl`

## Status: ENFORCED — application authentication AND network containment, both proven by controls.

## Measured enforcement

| Request | Result |
|---|---|
| Anonymous → `/v1/internal/governance` | **401** |
| Garbage bearer token | **401** |
| Valid token (`impilo-backend`, what the BFF sends) | **404** — past the gate |
| Valid token (`vashandi-workforce-service`, audience-bearing) | past the gate |
| `/actuator/health` (kubelet probe path) | **200** — deliberately open |

Both enumerated callers healthy, **zero auth errors** in either since the flip.

## The flag was never the enforcement seam

`impilo.security.disable-oauth-for-tests=false` is the documented way to "enable OAuth" across
96 services. On this service it would have changed **nothing measurable**: the filter chain ended
with `.anyRequest().permitAll()` unconditionally, so the resource server validated a token *if one
was presented* while no endpoint ever required one. An anonymous caller was still served.

A cohort declared ENFORCED on the strength of that flag would have been enforcing nothing. The
real seam is `.anyRequest().authenticated()`. **This applies to all 96 services** — every one needs
its actual seam checked, not its flag flipped.

Two further corrections were required for a token to be accepted at all:

- **Issuer.** `issuer-uri` defaulted to the in-cluster Keycloak address, but Keycloak advertises
  the **public** issuer even when reached internally, so every token's `iss` is the public URL and
  validating against the internal one rejects all of them.
- **JWK source.** The key set must still be fetched internally — this host cannot reach its own
  public address (hairpin NAT), so discovery against the public issuer hangs.

## Gates, and how each was satisfied

| Gate | Evidence |
|---|---|
| Callers enumerated | `experience-bff` + `vashandi-workforce-service`, both `RUNTIME_ENV` (live container env, not a source default) |
| Cohort `IN_BRANCH` | both rebuilt and re-verified by `audit-deployed-provenance.py` — was `STALE` |
| Caller can authenticate | `vashandi` had **no credential at all**; given its own Keycloak client + `WorkloadTokenProvider` |
| Audience claim exists | realm had **no `oidc-audience-mapper` anywhere**; created — `aud` now minted and verified |
| Rollback recorded | prior digests + prior `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` captured before any change |

## Network containment: blocked, and it broke enforcement cluster-wide

The cohort NetworkPolicy is written (`deploy/networkpolicy/cohort-1-workforce-governance.yaml`)
but is **not applied**. Two failures, both caught by controls rather than reasoning:

**1. The first version allowed the whole pod CIDR.** `10.42.0.0/24` was added "for kubelet probes"
— but every pod lives in that range, so it admitted the entire cluster. The negative control
caught it immediately: `oros-service`, not a caller, still got `200`. Narrowed to the cni0 bridge
address and the node address as `/32`s.

**2. A single `ipBlock` policy disabled NetworkPolicy enforcement cluster-wide.** `ipBlock` CIDRs
require the `hash:net` ipset type. Only `ip_set_hash_ip` is loaded, so kube-router hit
*"set type not supported"* — and it **aborts the entire sync** on one bad set type. The enforcement
probe regressed from exit 0 to exit 1 while this policy existed.

Proven causally: deleting the policy restored the probe to **exit 0, `NetworkPolicy IS ENFORCED`**.

**One policy using an unsupported set type silently disables enforcement for every other policy
in the cluster.** That is a far sharper hazard than "NetworkPolicy is inert", and it only shows up
if the enforcement probe is re-run after each policy is added.

### To unblock

```bash
sudo modprobe ip_set_hash_net
```

(`/etc/modules-load.d/ipset-kube-router.conf` already lists it, so it will load on the next boot
regardless.) Then re-apply the policy and re-run **both** controls: the probe must stay exit 0, a
non-caller must be blocked, and both enumerated callers plus the kubelet probe must still succeed.

## Honest status

**`CHECKPOINT 7 PARTIAL`** — application-layer authentication is genuinely enforced for the first
service in the estate, with anonymous access refused and both real callers working. Network
containment is written, proven wrong twice by its own controls, and withheld pending one kernel
module. Audience *validation* is deliberately not switched on yet: the BFF still presents an
`impilo-backend` token that carries no audience, so enabling it would reject a legitimate caller.
That is the next increment, not a silent gap.


---

# Containment closed 2026-08-02

`sudo modprobe ip_set_hash_net` loaded the missing set type. The cohort policy was re-applied and
**all four controls pass**:

| Control | Result |
|---|---|
| 1. Enforcement survives the `ipBlock` policy | probe **exit 0** — `NetworkPolicy IS ENFORCED` |
| 2. Negative — non-callers blocked | `oros-service`, `pct-service`, `tuso-service` → **exit 7 (connection blocked)** |
| 3. Positive — enumerated callers reach it | `experience-bff` **200**, `vashandi-workforce-service` **200** |
| 4. Kubelet probes survive | pod `ready=true`, `restarts=0` |

Application layer re-verified from an allowed caller: anonymous **401**, valid token past the gate.
Estate: 117 pods Running, authenticated browser proof **12/12**.

## The cohort now has, simultaneously

- **Authentication** — anonymous and forged tokens refused at the application (`401`)
- **Containment** — only the two enumerated callers can reach the port at all
- **Workload identity** — `vashandi` holds its own Keycloak client and mints its own token
- **A minted audience** — `aud` exists for the first time in this realm

Containment and authentication are independent layers, and this is the first service in the estate
to have both. Reachability alone was the entire east-west control until now.

## Deliberately still open

**Audience validation is not switched on.** The BFF presents an `impilo-backend` token that carries
no audience, so enabling it would reject a legitimate caller. Closing it means giving
`experience-bff` its own client with a mapper — the next increment, named rather than hidden.

## The hazard this cohort discovered

**One NetworkPolicy using an unsupported ipset type silently disables enforcement for every other
policy in the cluster.** kube-router aborts the *entire* sync on a single bad set type. It was only
visible because the enforcement probe was re-run after adding the policy, and it was proven
causally by deleting the policy and watching the probe recover.

Any future cohort policy must re-run `scripts/guard/probe-network-policy-enforcement.sh` **after**
being applied. A policy that looks correct can take the whole cluster's isolation down with it.
