# Full Boot Runtime Completeness Report

> All of vNext is accountable. One estate means all deployable vNext services. Waves are sequencing, not optionality.

**Estate status:** `PARTIAL_WAVE_PASS`
**Estate reason:** required spine healthy but estate only 99/102 ready (missing/not-ready: 3) - waves are sequencing, not full estate
**Runtime estate ready:** 99/102 (missing/not-ready: 3)
**Legacy full-boot status (alias):** `FULL_BOOT_PASS` — images, helm, and runtime healthy

| Phase | State |
|-------|-------|
| Images ready | True |
| Helm deployability ready | True (22/22) |
| Namespace deployed | True (impilo-full-preview) |
| Runtime healthy | True |

| Metric | Value |
|--------|-------|
| Total discovered | 160 |
| Required full boot | 22 |
| Image pass / fail | 2 / 0 |
| Helm ready / missing / partial | 22 / 0 / 0 |
| Deployed in full boot | 113 |
| Pods ready / total | 116 / 120 |

