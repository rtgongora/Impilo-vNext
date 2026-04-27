# Mobile parity — traceability

**Classification:** P = provider, C = citizen, B = both, W = web-only, F = future.

| Capability | P | C | BFF path | Parity | Gap |
|------------|---|---|----------|--------|-----|
| Tariff library browse | P (finance) | C (billing status only) | `costa-intel` | **Web done** | Native screens TBD |
| Consent (Mvumo) | B | B | BFF | Partial | Full capture/refusal on mobile |
| PCT queue | P | — | PCT | Partial | `apps/mobile` routes TBD |
| OROS results | P | C (limited) | OROS | Partial | — |
| Telemedicine 7-step | B | B | multiple | **Gap** | Mobile-first flows |
| SOS / Comms / Support | B | B | shell | Partial | push notifications |

**Next step:** Map `apps/mobile` (if not in workspace, note in build docs) and align auth headers with BFF v1.2.
