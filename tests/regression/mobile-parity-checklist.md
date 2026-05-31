# Mobile parity regression checklist

Manual/automated checklist for citizen + provider apps. Run on device or emulator against preview BFF.

## Per capability (minimum)

| Capability | Citizen screen | Provider screen | Real API | Not placeholder |
|------------|----------------|-----------------|----------|-----------------|
| Vito / registry | Health ID / profile | Registry ops | ☐ | ☐ |
| Varapi | — | Professional profile | ☐ | ☐ |
| Tuso | — | Facility context | ☐ | ☐ |
| Butano / SHR | Personal health | Clinical summary | ☐ | ☐ |
| Nompilo | — | — | ☐ | ☐ |
| Fundo | Learning tab | Learning service | ☐ | ☐ |
| Ndila | — | Field maps | ☐ | ☐ |
| Nhume | — | Dispatch ops | ☐ | ☐ |
| MusheX | Wallet/payments | Finance | ☐ | ☐ |
| Telemedicine | — | TelemedicineScreen | ☐ | ☐ |
| Comms | Notifications | Channels hub | ☐ | ☐ |

## Automated gates (VM/CI)

```bash
bash scripts/guard/check-mobile-parity.sh
bash scripts/guard/check-backend-frontend-parity.sh
```

## Notes

- iOS full build: advisory until macOS/TestFlight available.
- Android APK: advisory until EAS preview profile stabilized.
