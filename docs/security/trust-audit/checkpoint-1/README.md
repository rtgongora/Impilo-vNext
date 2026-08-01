# Trust-plane truth audit — Checkpoint 1

**Branch:** `claude/tshepo-trust-cp1-truth-audit`  
**Foundation commit (MFA):** `f190318e1` — authentication assurance foundation only; **not** "Tshepo complete".  
**Canonical doctrine:** [`docs/doctrine/tshepo-trust-plane-doctrine.md`](../../../doctrine/tshepo-trust-plane-doctrine.md)

## Deliverables

| Artifact | Path |
|---|---|
| Capability matrix | [TRUST_CAPABILITY_MATRIX.md](TRUST_CAPABILITY_MATRIX.md) |
| Trust journeys (≥20 + S2S classes) | [TRUST_JOURNEYS.md](TRUST_JOURNEYS.md) |
| East–west graph | [EAST_WEST_GRAPH.md](EAST_WEST_GRAPH.md) |
| PDP/PEP map | [PDP_PEP_ENFORCEMENT_MAP.md](PDP_PEP_ENFORCEMENT_MAP.md) |
| mTLS report | [MTLS_REPORT.md](MTLS_REPORT.md) |
| Threat model + root causes | [THREAT_MODEL_AND_ROOT_CAUSES.md](THREAT_MODEL_AND_ROOT_CAUSES.md) |
| Runtime evidence | [runtime-evidence/](runtime-evidence/) |
| Source audits | [source-audits/](source-audits/) |
| User-token propagation | [USER_TOKEN_PROPAGATION.md](USER_TOKEN_PROPAGATION.md) |
| Mvumo / consent contract map | [MVUMO_CONSENT_CONTRACT_MAP.md](MVUMO_CONSENT_CONTRACT_MAP.md) |

## Gate

No enforcement, OAuth expansion, Envoy cutover, OPA enforce, or work-context ENFORCE flip starts from this branch until this checkpoint is reviewed. Next programme checkpoint: **Canonical contracts and compatibility adapters**.
