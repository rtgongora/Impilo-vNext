# Spec Conflict Log — v3 + Tech Companion Spec 2.0 Alignment

Date: 2026-03-11
Branch: claude/review-project-manifest-jb5O0

## Resolved Conflicts

### 1. V11HeaderFilterTest asserted only 2 hard-required headers

| Field | Value |
|-------|-------|
| **Area** | libs/tech-companion (V11HeaderFilterTest.java) |
| **What spec says** | ALL FOUR headers (X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID) are hard-required on every v1.1 request |
| **What repo had** | Test asserted `HARD_REQUIRED.length == 2` (only Tenant-ID, Pod-ID) |
| **Resolution** | Fixed test to assert `length == 4` with all four header names verified |

### 2. GoldenContractSuite lacked individual tests for X-Request-ID and X-Correlation-ID

| Field | Value |
|-------|-------|
| **Area** | libs/tech-companion-harness (GoldenContractSuite.java) |
| **What spec says** | Missing any single header must return 400 MISSING_REQUIRED_HEADER |
| **What repo had** | Only tested missing X-Tenant-ID and X-Pod-ID individually |
| **Resolution** | Added `missingRequestIdReturns400`, `missingCorrelationIdReturns400`, and `missingAllFourHeadersReturns400` tests |

### 3. IdempotencyFilter only covered /internal/v1/** paths

| Field | Value |
|-------|-------|
| **Area** | libs/tech-companion (IdempotencyFilter.java) |
| **What spec says** | All create/update command endpoints MUST accept Idempotency-Key |
| **What repo had** | Only enforced on `/internal/v1/**` paths, not `/external/v1/**` |
| **Resolution** | Changed to use `V11HeaderFilter.isV11Path()` which covers both internal and external v1 paths |

### 4. GoldenContractSuite assertErrorEnvelope didn't check `details` field

| Field | Value |
|-------|-------|
| **Area** | libs/tech-companion-harness (GoldenContractSuite.java) |
| **What spec says** | Error envelope must include: code, message, details, request_id, correlation_id |
| **What repo had** | `assertErrorEnvelope` checked code, message, request_id, correlation_id but NOT details |
| **Resolution** | Added `details` assertion to `assertErrorEnvelope` + new `assertMissingHeaderInDetails` helper |

## Open Conflicts (Require Stakeholder Input)

### 5. Attached spec documents not accessible in repository

| Field | Value |
|-------|-------|
| **Area** | Project-wide |
| **What spec says** | vNext V3.docx and vNext Tech Companion Spec 2.0.docx are canonical inputs |
| **What repo has** | No .docx files found in the repository |
| **What's missing** | The spec documents were referenced as "attached" but not present in the repo file system |
| **Question** | Should the canonical spec documents be committed to docs/specs/ for traceability? |

### 6. Authorization header enforcement scope

| Field | Value |
|-------|-------|
| **Area** | libs/tech-companion (CompanionHeaders.java, V11HeaderFilter.java) |
| **What spec says** | CompanionHeaders Javadoc lists Authorization as "mandatory" alongside the 4 headers |
| **What repo has** | Authorization is NOT in HARD_REQUIRED array; it's treated as optional by the filter |
| **What's missing** | Clarification on whether Authorization should be a 5th hard-required header or remains gateway-injected |
| **Question** | Is Authorization always present after Envoy ext_authz, making filter enforcement redundant? |

### 7. Runtime verification unexecuted

| Field | Value |
|-------|-------|
| **Area** | All services |
| **What spec says** | Tests should pass end-to-end |
| **What repo has** | All tests written and statically verified, but Maven cannot run in this environment |
| **What's missing** | Runtime test execution proof |
| **Question** | N/A — requires CI pipeline or local Maven environment to execute |
