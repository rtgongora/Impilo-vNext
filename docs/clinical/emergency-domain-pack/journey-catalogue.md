# Emergency pack — journey catalogue (`J-EP-*`)

Journey IDs use the pack's live convention **`J-EP-*`**. Do not invent `J-EM-*`.

## Pathway integrity (CKP content)

Script: `scripts/runtime-proof/emergency-pathway-integrity.sh`

| ID | Assertion |
|----|-----------|
| **J-EP-1** | Every `ED_*` pathway_definition has ≥1 pathway_step (no stepless shell) |
| **J-EP-2** | No `ED_*` pathway cites UTI / Fever / chronic-asthma sections |
| **J-EP-3** | `ED_SEPSIS` step 1 does not screen on qSOFA; screens via EWS |
| **J-EP-4** | `ED_ECTOPIC` gates on reproductive age until pregnancy excluded |

## Episode spine (API / estate)

Script: `scripts/runtime-proof/emergency-episode-journeys.sh`  
Requires the drive estate (`bash scripts/dev/emergency-drive-rig.sh up`) or equivalent PCT+BFF.

| ID | Assertion |
|----|-----------|
| **J-EP-5** | Opening an episode (activation POST) creates one `emergency_episode` row and returns 201 |
| **J-EP-6** | Command summary for a facility returns items (or an honesty failure) — never a silent empty board shaped as success without meta |
| **J-EP-7** | Disposition GET for an undisposed episode is 200 with null/empty data (absence ≠ 404 unreadability) |
| **J-EP-8** | Incomplete disposition mapping leaves the episode open (W15a honest-open) — proven by unit suite; estate probe documents the contract |
| **J-EP-9** | Analytics page exposes live report keys and named NOT_COMPUTABLE measures (no fabricated zeros) |

## Browser full-chain

| ID | Spec |
|----|------|
| **J-EP-B1** | `e2e/emergency-pack-w15.spec.ts` — command, activation→spine, observation, disposition, pre-arrival, analytics, MH |
| **J-EP-B2** | `e2e/emergency-pack-w18.spec.ts` — consolidated full-chain drive (same estate) |

Boot and evidence: [`browser-drive.md`](browser-drive.md).
