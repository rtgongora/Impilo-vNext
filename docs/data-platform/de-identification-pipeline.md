# De-identification pipeline — design (Identity Contract §15)

**Status:** DESIGN (Wave E4). Not built. Registered gap.
**Owner:** data platform (data-governance-service + analytics-pipeline-service),
under Identity Trust Core governance.

## Why this exists

The operational SHR is **pseudonymised, not anonymised** (Identity Contract §15):
clinical data keys on the CPID, a pseudonym reversible through the authorised,
audited trust-core reverse-resolution path (§7.4). Analytics and research
datasets must therefore be produced by a **separate de-identification pipeline** —
never by shipping CPID-keyed clinical data downstream and calling it
"de-identified". Today no service performs any de-identification transform
(verified: `national-data-repository`, `data-warehouse`, `analytics-pipeline`,
`surveillance` contain no k-anonymity / tokenisation / suppression logic); the
NDR README's "de-identified" claim has been corrected to say so.

## Principles

1. **Dataset-specific tokens, never the CPID.** Each dataset gets its own
   irreversible subject token = `HMAC(dataset_secret, cpid)` with a
   **per-dataset** key held only by the de-id service. The CPID must not appear
   in any dataset. Because the key is per-dataset, the same person is
   unlinkable across datasets (no cross-dataset join key), and no dataset token
   is reversible to a CPID without the trust core.
2. **Direct-identifier removal.** Names, national IDs, phone, address, exact
   dates, free-text notes, and any VITO-plane field are dropped before a record
   enters a dataset. (The SHR already excludes these — `PiiPreventionInterceptor`
   — so the main risk is quasi-identifiers, below.)
3. **Quasi-identifier reduction.** Generalise DOB→age-band, location→district,
   rare categoricals→"other"; drop or coarsen fields that make a row unique.
4. **Small-cell suppression.** Any aggregate cell (and any k-anonymity class)
   with count < *k* (default k=5, policy-configurable per dataset) is suppressed
   or merged; complementary suppression prevents back-calculation.
5. **Policy-gated release.** A dataset is only materialised under an approved
   research/public-health data-use policy (purpose, requester, retention,
   re-identification prohibition). The gate is enforced, audited, and revocable.
6. **One-way, downstream-only.** De-identification runs downstream of the SHR;
   it never writes back, and the pipeline holds no reverse map. Re-identification
   (if ever legally required) goes through the trust core with its own
   authorisation, not through this pipeline.

## Proposed shape

```text
SHR / clinical events (CPID-keyed, pseudonymised)
        │
        ▼
De-identification service (new module in data-governance-service)
  · per-dataset HMAC tokeniser (dataset_secret via tshepo-keys custody)
  · direct-identifier stripper (allowlist columns per dataset schema)
  · quasi-identifier generaliser (DOB→band, geo→district, rare→other)
  · k-anonymity + small-cell suppressor (k per dataset policy)
  · data-use policy gate (approved policy ref required to materialise)
  · full audit (dataset, policy, row counts in/out, suppression stats)
        │
        ▼
NDR / data-warehouse datasets (dataset-token-keyed, de-identified)
```

Tables (indicative): `deid_dataset` (id, policy_ref, k, dataset_secret_ref,
schema_json, status), `deid_run` (dataset_id, source_watermark, rows_in,
rows_out, cells_suppressed, ran_at), `deid_release_policy` (purpose, requester,
retention, reid_prohibited).

## Test bar (when built)

- No CPID value appears in any produced dataset (audit query, mirrors
  `scripts/identity/audit-no-healthid-in-clinical.sh`).
- The same person yields **different** tokens in two different datasets
  (per-dataset key isolation).
- Every k-anonymity class in a released dataset has count ≥ k.
- Materialising a dataset without an approved policy ref is rejected + audited.

## Sequencing

Design only in this wave (E4). Build is a later data-platform wave; until then
the boundary is documentation, and downstream stores must not claim
de-identification (NDR README corrected accordingly).
