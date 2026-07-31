# Emergency pack — completion register

**Convention: PARTIAL is never counted as delivered.** A capability is DONE only when nothing
named in its row is outstanding. Template style: adult-medicine `completion-register.md`.

| Area | Status | Outstanding |
|------|--------|-------------|
| Episode spine (pct V200–V211) | **DONE** | — |
| ED registration mints episode (W15a) | **DONE** | Silent-open when disposition map cannot satisfy R12 — see gap register; reconcile CTA on ED visit |
| Alerts ack/respond/close + sweeps | **DONE** | — |
| Handover + expiry + Rito case open | **DONE** | MH auto-intake on MENTAL_HEALTH handover (BFF) |
| Observation stay / 15-type disposition | **DONE** | — |
| BFF reachability (W15c seven surfaces) | **DONE** | Diagnostics list + resus depth under `/ed/resuscitation` |
| Honesty envelope (flat error) | **PARTIAL** | 31 ED routes still emit nested `HTTP_ERROR` shape |
| Command / board / spine / pre-arrival UI | **DONE** | Thin-UI closure + durable diagnostics + disposition reconcile |
| Mental-health service + clinical UI | **PARTIAL** | Web + mobile UI complete; compose.runtime wired; Helm digest + authorize deploy still required |
| Offline Tier B + SW (W16b) | **DONE** | Web outbox + Tier A IITT; mobile outbox + NOT_TRIAGEABLE_OFFLINE advisory |
| Indicators / DSEC (W17) | **PARTIAL** | 17/47 core mapped; acuity PARTIAL; resus/observation/critical-result NOT_COMPUTABLE |
| Content tranches W14 (~140 syndromes) | **NOT BUILT** | Sourcing blocker — see gap register |
| Realtime phase 2 (W19) | **DONE** | Gateway + bridge + UI; Helm/compose env keys set (deploy apply still authorize) |
| Mobile emergency / ED / MH / SOS | **DONE** | Emergency tab + hub; full spine; guest SOS callback; parity matrix updated |
| Envoy public MH cluster | **NOT BUILT** | Deliberate — BFF→MH in-cluster only; compose.runtime MH **wired** |
| Inter-facility ambulance | **OUT OF SCOPE** | Named in gap register |

## Totals (capabilities above)

**12 DONE · 3 PARTIAL · 2 NOT BUILT · 1 OUT OF SCOPE**

Wave evidence: [`implementation-report.md`](implementation-report.md),
[`docs/audits/emergency-pack-honest-gap-register.md`](../../audits/emergency-pack-honest-gap-register.md).
