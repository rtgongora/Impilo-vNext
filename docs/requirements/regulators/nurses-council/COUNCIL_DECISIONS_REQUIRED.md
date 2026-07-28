# Nurses Council — decisions required

Every item here is classified `POLICY_CONFIRMATION_REQUIRED`. For each one the platform **builds the
effective-dated configuration seam and leaves the value unset**. None of them is invented, defaulted or
inferred, and none is activated until the Council confirms it.

**Implementation readiness and policy activation readiness are different things.** A capability may be
fully built, tested and deployed while the rule it enforces remains unset — in that state the seam
exists, the value is absent, and the platform says so honestly rather than guessing.

| ID | Decision required | Why the platform cannot decide it | Configuration seam | Blocks |
|---|---|---|---|---|
| `NCZ-DEC-001` | **Student index-number format** | A registration identifier is a statutory artefact; a guessed format would be issued to real people and then be unchangeable | `numbering_policy` definition — reserve/issue, uniqueness and no-reuse are built; the *format* is a parameter | Issuance only. The number policy, concurrency safety and audit are built and testable with a provisional format. |
| `NCZ-DEC-002` | **Student index fee amount and currency** | Fees are set by instrument, not by software | `fee_schedule` definition version, amount `NULL`, status `PENDING_REGULATOR_APPROVAL` (mirrors the tuso V021 discipline) | Invoice *value*. The fee gate, invoice, payment and reconciliation rails are built; an unconfigured fee surfaces as `NOT_CONFIGURED` rather than silently skipping the gate. |
| `NCZ-DEC-003` | **Renewal penalty formula** | Penalty accrual is a legal calculation with a defined basis and cap | `penalty_policy` definition, pinned at liability assessment | Penalty amounts. Accrual timing, pinning and audit are built. |
| `NCZ-DEC-004` | **"Failing more than twice" — precise interpretation** | Attempt counting, reset conditions and the discontinuation trigger are each ambiguous in the source, and the consequence for a candidate is severe | `examination` definition — attempt limit, reset rule, discontinuation trigger | Automatic discontinuation. Attempt history is recorded regardless. |
| `NCZ-DEC-005` | **Examination attempt timing and reset rules** | Not specified | `examination` definition | As above |
| `NCZ-DEC-006` | **Supervised practice — interruption and part-time calculation** | Three years of supervised work is stated, but how interruption, part-time service and concurrent placements accumulate is not; the answer decides when a nurse may migrate to the Main Register | `supervised_practice` definition — duration basis, interruption handling, part-time factor, supervisor approval | Migration eligibility *verdict*. Placement, supervisor and duration recording are built. |
| `NCZ-DEC-007` | **Provisional registration — licensure examination rule** | The source rule is incomplete | `application_type` + `eligibility` definitions | Whether the examination is compulsory for a given cohort |
| `NCZ-DEC-008` | **Nurse-led institution documentary requirements and stages** | Not defined | `evidence_requirement` + `workflow` definitions | Institution application content |
| `NCZ-DEC-009` | **Certificate wording and templates** | A certificate is a legal instrument; its wording is not a design choice | `certificate_template` definition, pinned at issuance | Certificate issuance |
| `NCZ-DEC-010` | **Approval levels / thresholds** | Who may finally approve which decision is a governance matter | `approval_matrix` in the role/workspace definition; four-eyes rail already exists | Final approval routing |
| `NCZ-DEC-011` | **Result review or appeal process** | Not stated | `workflow` definition | Result appeals |
| `NCZ-DEC-012` | **CPD cycle, required credits and mandatory categories** | Out of BRD scope and not confirmed; the council — not Fundo — is authoritative on whether CPD is satisfied | `cpd_rules` definition; Fundo evidence seam already wired | CPD gating of renewal. **Not activated.** |
| `NCZ-DEC-013` | **Statutory register names** | The four registers are confirmed by the brief, but their exact statutory titles are not; nine org files carry ~101 `TO_CONFIRM` items of this kind | `register` definitions | Display and certificate wording only |
| `NCZ-DEC-014` | **Multi-currency policy** | The brief requires multiple currencies, but the estate has **no FX or exchange-rate machinery at all**, and no rate source is nominated. Which currencies are accepted, at what rate, set by whom, and whether cross-currency settlement is permitted are all council/treasury decisions | `fee_schedule` currency + a currency policy definition | Cross-currency invoicing and settlement. Single-currency invoicing works without it. |
| `NCZ-DEC-015` | **Payment allocation priority and carry-forward rules** | "Debt may be settled automatically" and "excess payments may create carry-forward balances" are stated as capabilities; the *priority order* and the treatment of credits are accounting policy | `fee_schedule` allocation rules | Automatic allocation and carry-forward. Explicit 1:1 settlement works without it. |

## How to add an entry

Reference the source id (a `PO-NCZ-*` interim id now; the original BRD id after reconciliation), state
plainly why the platform cannot decide it, name the configuration seam that is built and waiting, and
say precisely what is blocked — distinguishing the value from the mechanism. Do not record a decision
here and then default it in code.
