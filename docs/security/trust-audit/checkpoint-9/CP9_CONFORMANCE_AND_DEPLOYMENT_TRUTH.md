# Checkpoint 9 — Final conformance and deployment truth

Branch `claude/tshepo-trust-completion-Yypyl`. Preview namespace `impilo-full-preview`.
All runtime figures measured live on 2026-08-02, not carried forward from earlier checkpoints.

Truth layers are the CP1 four: `SOURCE_IMPLEMENTED`, `TEST_PROVEN`, `PREVIEW_DEPLOYED`,
`PREVIEW_ENFORCED`. **They are declared separately and are never cumulative.** A facet may be
source-complete and test-proven and still change nothing for any user.

---

## Live estate, measured

| Fact | Value |
|---|---|
| Workloads in namespace | 114 |
| **Running with the OAuth bypass ON** | **95** |
| Running with it OFF | 3 — `tshepo-authz-service`, `tshepo-audit-service`, `workforce-governance-service` |
| ServiceAccounts | 108 (was 2 before CP4) |
| NetworkPolicies | 1 (cohort-1 only; enforcement substrate restored in CP4.6) |
| OPA mode | `SHADOW` |
| Envoy ext_authz | **still off** — the two matches in the running config are comments |
| Services open with no bypass flag to retire | 16 |

Of the three enforcing services, two predate this programme. **CP7 retired exactly one bypass.**

---

## Facet truth matrix

| Facet | SOURCE | TEST | DEPLOYED | ENFORCED |
|---|:--:|:--:|:--:|:--:|
| Authentication (Keycloak, PKCE, browser + mobile) | ✅ | ✅ | ✅ | ⚠️ 3 of 98 |
| Identity assurance / step-up decisioning | ✅ | ✅ | ✅ | ❌ |
| Workload identity — registry (130 rows) | ✅ | ✅ | ✅ | n/a |
| Workload identity — ServiceAccounts | ✅ | ✅ | ✅ 108 | ❌ not yet bound as identity |
| Workload credentials (audience-restricted tokens) | ✅ | ✅ | ⚠️ cohort only | ⚠️ cohort only |
| Transport (strict mTLS) | ❌ | ❌ | ❌ | ❌ |
| Network containment | ✅ | ✅ | ⚠️ 1 policy | ⚠️ cohort 1 only |
| Work context binding | ✅ | ✅ | ✅ | ❌ `SHADOW` |
| Authority resolution (appointment ≠ context) | ✅ | ✅ | ✅ | ❌ |
| Consent — capture and evaluation | ✅ | ✅ | ✅ | ⚠️ PDP off-path |
| Lawful basis beyond consent | ✅ | ✅ | ✅ | ❌ |
| Policy evaluation — PolicyEngine | ✅ | ✅ | ✅ | ⚠️ callers only |
| Policy evaluation — OPA parity | ✅ | ✅ | ✅ | ❌ `SHADOW`; `ENFORCE` structurally unreachable |
| Envoy edge enforcement | ✅ | ✅ | ⚠️ Stage 1 | ❌ ext_authz off |
| Application enforcement (bypass retirement) | ✅ | ✅ | ✅ | ❌ 95 of 98 bypassed |
| Trust challenge experience — BFF | ✅ | ✅ | ✅ | ✅ live over HTTP |
| Trust challenge experience — shell | ✅ | ✅ | ❌ | ❌ |
| Trust challenge experience — mobile | ✅ | ✅ | ❌ | ❌ |
| Constrained recovery | ✅ | ✅ | ✅ | ✅ |
| Audit correlation | ✅ | ✅ | ✅ | ⚠️ not reconstruction-tested |
| Header containment (strip/regenerate) | ✅ | ✅ | ✅ | ⚠️ config-verified only |

---

## What this programme actually changed

Most of CP4–CP9 was not building. It was **finding controls that read as present and did
nothing**, and the same defect recurred at every layer:

1. **Fourteen BFF governance checks had always denied.** The client sent `:method`/`:path` —
   unsendable over HTTP/1.1. Every call threw; the fail-closed catch-all made a thrown check
   indistinguishable from a policy decision. *Only a deployed probe found it.*
2. **A PDP outage and a refusal were the same boolean.** Users would be told they lack
   permissions when the policy service was simply down.
3. **Mobile's step-up branch was unreachable on both wires** — it read `decision`; ext_authz emits
   `verdict` and the BFF nests the outcome. Mobile has never prompted for verification.
4. **Sixteen services are open with no bypass flag**, so retiring all 95 declared bypasses would
   be truthfully reported while biometrics, the audit ledger and identity matching stayed open.
5. **NetworkPolicies could not be enforced at all** for 3+ days — unloaded `ip_set_hash_*` kernel
   modules, not the documented cause. 1270 iptables rules referenced sets that never existed.
6. **The consent wire had five simultaneous mismatches** and its fail-closed catch-all hid a call
   that could never have succeeded.
7. **93 of 100 workloads ran code that was not on the branch** — "merged in all 97 services" was
   running in 9.

Every one was a check that could not fail, or a measurement that lied. None was a wrong opinion
about the code.

---

## Gates from the brief, honestly scored

| Gate | Status |
|---|---|
| Zero unexplained legitimate shadow denials | ✅ 0 REAL divergences over 44 compared |
| Unique workload identity per workload | ⚠️ registry + SAs exist; not bound as identity |
| Stable credential refresh | ✅ cohort |
| Known direct routes | ❌ 38 services have unenumerated non-BFF callers |
| Proven headers and policies | ⚠️ config-verified, not runtime-proven |
| Backups and recorded rollback digests | ✅ every wave |
| Captured performance baseline | ❌ not captured |
| No per-hop synchronous Keycloak dependency | ✅ cached provider |
| Zero fail-open decisions | ❌ `tshepoPdpFallbackAllow` still fails open |

Performance gates (OPA p95 ≤10 ms, trust overhead p95 ≤20 ms) were **not measured**. Reporting
them as met on the basis of a shadow deployment would be a fabrication.

---

## Terminal status

# SERVICE TRUST PARTIALLY ENFORCED

One cohort of one service is genuinely enforced end to end (authentication, audience restriction,
network containment, workload identity). The trust *decision* path is real and live: the BFF now
reaches the PDP for the first time and returns canonical challenges over HTTP. The trust
*experience* is built and tested on every platform and deployed on none of the user-facing ones.

**95 of 98 flag-bearing workloads still run with the bypass on, and 16 more were never covered by
the bypass framing at all.** Envoy ext_authz is off, OPA is in shadow, work-context binding is in
shadow, and strict mTLS has no substrate.

This is the status the evidence supports. It is not "Tshepo complete", and no facet above should
be quoted without its layer.

---

## Ranked next actions

1. **Write security chains for the 16 open services.** No flag closes them. `abis-service`,
   `audit-ledger-service` and `matcher-engine` first.
2. **Enforce CP8 cohort 2** — 30 candidates are gate-passed and ready; the flip was blocked by an
   environment permission, not by evidence.
3. **Deploy `one-ui-shell`** and capture the Playwright and Redroid proofs (queued for the next
   fullboot).
4. **Rebuild the ~90 stale services**, without which source fixes never become runtime fixes.
5. **Retire `tshepoPdpFallbackAllow`** — the last known fail-open.
6. **Capture the performance baseline** before ext_authz Stage 2.
