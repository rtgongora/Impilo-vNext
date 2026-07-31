# Emergency pack — analytics coverage

National emergency indicators run in **reporting-service** against the Kafka projection
`rpt_emergency_episode_metric` — the Theatre pattern. They do **not** run inside PCT and they
must never name `pct.*` tables from reporting's own database (that failure mode is documented
in adult-medicine `analytics-coverage.md`).

## Register

Authoritative element-by-element mapping:

[`dsec-element-mapping.json`](dsec-element-mapping.json)

| Band | Count |
|------|-------|
| Core MAPPED | 17 / 47 |
| Core PARTIAL | 8 / 47 |
| Core UNMAPPED | 22 / 47 |
| Extended PARTIAL | 2 / 31 |
| Extended UNMAPPED | 29 / 31 |
| Indicators IMPLEMENTED | 3 |
| Indicators PARTIAL | 2 |
| Indicators NOT_COMPUTABLE | 3 |

## Live report keys

| Key | Status |
|-----|--------|
| `emergency-episode-summary` | IMPLEMENTED |
| `emergency-disposition-mix` | IMPLEMENTED |
| `emergency-acuity-distribution` | PARTIAL — `NOT_YET_TRIAGED` when acuity absent |
| `emergency-episode-register` | IMPLEMENTED |

## Rules every indicator obeys

1. **Unit is stated** — episodes, not people-unless-distinct.
2. **Empty denominator → null rate**, never a fabricated 0%.
3. **Absent acuity is not Green** — bucket as `NOT_YET_TRIAGED`.
4. **UI shows no zeros for NOT_COMPUTABLE measures** — named absence only.

Decision write-up: [`w17-indicators.md`](w17-indicators.md).
