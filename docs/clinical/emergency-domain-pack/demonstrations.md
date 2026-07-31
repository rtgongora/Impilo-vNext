# Emergency pack — demonstrations

What has been proven on a real chain, and what has only been unit-tested.

## Proven live (browser + estate)

| Demonstration | Evidence |
|---------------|----------|
| Command board reads pct tallies; blind spots named | W15 Playwright + screenshots `reports/journeys/emergency-pack-w15/` |
| Activation POST creates `emergency_episode` and opens spine | W15 drive |
| Observation absence ≠ unreadability | W15 drive |
| Disposition form when none recorded (200 empty) | W15 drive + disposition Optional fix |
| Analytics named absence / live keys | W15 + W17 UI |
| Offline Tier B `NOT_TRIAGEABLE_OFFLINE` | W16b Playwright prod build |
| TeaVM IITT corpus in browser | W16a GO — 23/23, ~149 KiB |

Boot: [`browser-drive.md`](browser-drive.md).

## Proven at API / DB / unit layer

| Demonstration | Evidence |
|---------------|----------|
| J-EP-1..4 pathway integrity | `scripts/runtime-proof/emergency-pathway-integrity.sh` |
| J-EP-5..9 episode spine contracts | `scripts/runtime-proof/emergency-episode-journeys.sh` |
| jsonb column binding | `scripts/guard/check-jsonb-column-binding.sh` |
| Reporting projection consumer | `EmergencyReportingConsumerTest` |
| W15a honest-open on incomplete disposition map | `EdVisitServiceEmergencyEpisodeTest` |

## Not demonstrated (named)

| Gap | Why |
|-----|-----|
| Mental-health clinical UI against a **deployed** MH pod | Service never imaged/deployed |
| Full-boot preview with MH on public ingress | Digests / Envoy / compose omissions |
| W14 syndrome content | Sourcing blocker |
| Inter-facility ambulance end-to-end | Scoped out |
| Tier A offline IITT scoring in the ED UI | W16a GO; UI still Tier B |

Full gap list: [`docs/audits/emergency-pack-honest-gap-register.md`](../../audits/emergency-pack-honest-gap-register.md).
