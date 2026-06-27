# Mobile Runtime Smoke Checklist

Manual and Maestro smoke against preview `http://41.57.127.235` (Web Preview VM API).

**Where to run runtime smoke:** **Maestro VM** `facility@41.57.127.218 -p 2027` — not on Web Preview VM (235). See [`docs/mobile/MOBILE_ANDROID_SANDBOX.md`](../mobile/MOBILE_ANDROID_SANDBOX.md).

**Closure wave status (2026-06-27):** Maestro VM activated (KVM validated); mobile toolchain install and runtime smoke **NOT RUN** yet. Static typecheck can run on either VM.

```bash
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
```

## Launch & branding

- [ ] NOT RUN 1. App launches without crash
- [ ] NOT RUN 2. Login screen shows Impilo branding (green citizen / blue provider)
- [ ] NOT RUN 3. Citizen vs Provider apps are visually distinct

## Citizen

- [ ] 4. Health ID login lands on Home (My Life)
- [ ] 5. Health tab opens personal dashboard sections
- [ ] 6. Service hub shows PCT/Care with navigation to telehealth
- [ ] 7. Fundo card opens marketplace learning
- [ ] 8. Madi card opens blood donor hub
- [ ] 9. Mushex/Costa card opens finance (Costa shows unavailable if blocked)
- [ ] 10. Impilo Live card opens live discover
- [ ] 11. Nompilo FAB opens compact bottom-sheet assistant

## Provider

- [ ] 12. Provider ID login → activation → facility → lands on **Work** tab
- [ ] 13. **My Professional** tab shows VARAPI profile / verification
- [ ] 14. Work context chips show facility/workspace
- [ ] 15. Service hub: PCT → queue; OROS → lab tools; PACS → imaging tools
- [ ] 16. Madi → blood orders tool tab
- [ ] 17. Simba → supervisor stock/inventory mode
- [ ] 18. Fundo/Training → apps surface
- [ ] 19. Live → Impilo Live clinical tools tab
- [ ] 20. Nompilo FAB opens compact professional assistant

## Resilience

- [ ] 21. Failed API → ErrorState / banner, no crash
- [ ] 22. Offline → NetworkStatusBar / unavailable messaging
- [ ] 23. Missing facility context → work dashboard empty state (not fake data)
- [ ] 24. Unauthorized service → blocked ServiceCard or UnauthorizedState
- [ ] 25. No dashboard card navigates to mock/dead screen
- [ ] 26. No fake counters on service cards (registry metadata only)
- [ ] 27. All visible services wired, deep-linked, or truthfully blocked

## Maestro (Maestro VM — 41.57.127.218)

```bash
ssh facility@41.57.127.218 -p 2027
cd /opt/impilo/repos/Impilo-vNext
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
bash scripts/mobile/verify-maestro-flows.sh
```

Flows under `apps/mobile/maestro/flows/`. Update `reports/mobile/mobile-runtime-smoke.md` after runs.
