# Trust-plane truth audit — Checkpoint 1

**Branch:** `claude/tshepo-trust-cp1-truth-audit`  
**Foundation commit (MFA):** `f190318e1` — authentication assurance foundation only; **not** "Tshepo complete".  
**Canonical doctrine:** [`docs/doctrine/tshepo-trust-plane-doctrine.md`](../../../doctrine/tshepo-trust-plane-doctrine.md)

## Deliverables

| Artifact | Path |
|---|---|
| Capability matrix (posture) | [TRUST_CAPABILITY_MATRIX.md](TRUST_CAPABILITY_MATRIX.md) |
| **Layered truth** (SOURCE/TEST/DEPLOYED/ENFORCED) | [CAPABILITY_TRUTH_LAYERS.md](CAPABILITY_TRUTH_LAYERS.md) |
| **MFA foundation split** | [MFA_FOUNDATION_TRUTH.md](MFA_FOUNDATION_TRUTH.md) |
| Trust journeys (≥20 + S2S classes) | [TRUST_JOURNEYS.md](TRUST_JOURNEYS.md) |
| East–west graph (summary) | [EAST_WEST_GRAPH.md](EAST_WEST_GRAPH.md) |
| **East–west full coverage** | [EAST_WEST_GRAPH_COVERAGE.md](EAST_WEST_GRAPH_COVERAGE.md) |
| PDP/PEP map | [PDP_PEP_ENFORCEMENT_MAP.md](PDP_PEP_ENFORCEMENT_MAP.md) |
| mTLS report | [MTLS_REPORT.md](MTLS_REPORT.md) |
| Threat model + root causes | [THREAT_MODEL_AND_ROOT_CAUSES.md](THREAT_MODEL_AND_ROOT_CAUSES.md) |
| Runtime evidence | [runtime-evidence/](runtime-evidence/) |
| **Image digest provenance** | [runtime-evidence/IMAGE_DIGEST_PROVENANCE.md](runtime-evidence/IMAGE_DIGEST_PROVENANCE.md) |
| **Expanded bypass inventory** | [runtime-evidence/BYPASS_INVENTORY_EXPANDED.md](runtime-evidence/BYPASS_INVENTORY_EXPANDED.md) |
| Source audits | [source-audits/](source-audits/) |
| User-token propagation | [USER_TOKEN_PROPAGATION.md](USER_TOKEN_PROPAGATION.md) |
| Mvumo / consent contract map | [MVUMO_CONSENT_CONTRACT_MAP.md](MVUMO_CONSENT_CONTRACT_MAP.md) |
| **Consent POST/GET detail** | [CONSENT_CONTRACT_INCOMPATIBILITY.md](CONSENT_CONTRACT_INCOMPATIBILITY.md) |
| **Recovery-code proof** | [RECOVERY_CODE_PROOF.md](RECOVERY_CODE_PROOF.md) |
| **Sensitive-data scan** | [SENSITIVE_DATA_SCAN.md](SENSITIVE_DATA_SCAN.md) |
| Doc mislabel corrections | [DOC_MISLABEL_CORRECTIONS.md](DOC_MISLABEL_CORRECTIONS.md) |

## Closure corrections (high signal)

1. Every capability now has layered SOURCE/TEST/DEPLOYED/ENFORCED truth — single `ENFORCED` cells are not sufficient.
2. MFA claim split: browser session PREVIEW_ENFORCED; Keycloak **26.7 + PostgreSQL deployed**; mobile PREVIEW_ENFORCED UNKNOWN; workforce not activated.
3. Graph lists all 129 controller nodes; UNKNOWN edges retained where unproven.
4. Digest→commit mapped only when OCI labels exist; otherwise UNKNOWN (not inferred from this branch).
5. Bypass inventory expanded per service; consumer proof remains PARTIAL/INSUFFICIENT_EVIDENCE.
6. Consent evaluate POST≠GET fully documented; no silent replacement chosen.
7. Recovery-code → ordinary AAL2 is SOURCE_CONFIRMED, not PREVIEW_ENFORCED.
8. Evidence pack scanned clean of secrets/PII.

## Gate

No enforcement, OAuth expansion, Envoy cutover, OPA enforce, or work-context ENFORCE flip starts from this branch until this checkpoint is reviewed. Next programme checkpoint: **Canonical contracts and compatibility adapters**.
