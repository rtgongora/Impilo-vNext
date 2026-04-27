# Start menu / command palette / search — coverage map

**Source of truth:** `SHELL_APPS` and `SHELL_COMMANDS` in:
- `ui/experience/src/lib/shell/app-registry.ts`
- `ui/one-ui-shell/src/lib/shell/app-registry.ts`

## Finance / COSTA (added 2026-04-23)

| Command id | Label | Keywords | Route | Role |
|------------|-------|----------|-------|------|
| `cmd-finance-tariff-library` | Tariff library | tariff, costing, costa, price list, schedule, reference tariff, billing tariff | `/finance/tariffs` | FINANCE |
| `cmd-finance-costa` | COSTA & MusheX tools | costa, cost estimate, charge sheet, settlement, mushex handoff | `/finance/costa` | FINANCE |
| `cmd-finance-settlements` (shell) | MusheX settlements | settlements, mushex, receivables | `/finance/settlements` | FINANCE |

## Known gaps

- Mvumo / consent: add dedicated command entries (consent, DNR, withdrawal) where product confirms labels.
- Telemedicine: add stage-based aliases to command palette.
- PCT: `cmd-queue` exists; add “control tower” alias if route stabilises.
