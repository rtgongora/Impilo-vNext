# Provider Workspace Reality Check

## Status Overview

| Surface | Web | Mobile | Classification |
|---|---|---|---|
| Queue/triage/worklist | Real | Real | `WEB_REAL_MOBILE_REAL` |
| Provider workspace doctrine page | Fixture | Missing dedicated equivalent | `WEB_MOCK_MOBILE_MISSING` |
| Provider messaging | Real/partial | Real with potential endpoint mismatch | `WEB_REAL_MOBILE_PARTIAL` |
| Provider telemedicine | Real | Real/partial | `WEB_REAL_MOBILE_REAL` (partial depth) |

## Key Findings

- High-value provider operational routes are mostly wired on both platforms.
- The named doctrine “provider workspace” route itself is fixture-backed and should not be treated as live source-of-truth workflow.

## Recommendations

1. Migrate doctrine provider workspace to live core transaction APIs.
2. Verify and normalize provider mobile messaging endpoint family usage.
3. Keep fixture labeling until full backend orchestration is wired.
