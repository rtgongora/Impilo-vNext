# Health OS shell (Experience)

Impilo vNext’s **One Experience** layer includes an OS-style shell: taskbar, Start launcher, global search palette, task manager, and file/document explorer. This document summarizes the domain model, persistence, and extension points implemented under `ui/experience`.

## Domain model

- **AppDefinition** — Curated catalog in `src/lib/shell/app-registry.ts` (`SHELL_APPS`). Role visibility uses `matchesRequiredRole` / `requiredRole` aligned with route guards.
- **RunningTask** — Open workspaces keyed by route; status `open` | `minimized`. Synced from navigation via `ShellRouteSync` and `route-task-meta.ts`. Session persistence: `sessionStorage` key `exp:shell_tasks`.
- **Pinned apps** — `pinnedAppCodes` in `useShellStore`; `exp:shell_pins`.
- **RecentItem** — Shell recents for Start/search/file catalog; `exp:shell_recent`.
- **ShellCommand** — Quick actions in the search palette (`SHELL_COMMANDS`).
- **FileResource** — Unified row type for the file explorer; built from personal vault documents and document-like recents in `src/lib/shell/file-resources.ts`.

## Search

- **Platform index** — `GET /internal/v1/search` (debounced in `ShellSearchPalette`).
- **Fusion hints** — `GET /internal/v1/intelligence-plane/shell-suggest` via `fetchShellFusionHints`; normalized by `normalizeFusionHints` (`src/lib/shell/normalize-fusion-hints.ts`).
- **Open search command** — `focusSearchPalette()` keeps the palette open and refocuses the input (`searchPaletteFocusTick`).

## Task manager

- **Clear minimized** — `clearClosedTasks()` drops tasks in `minimized` status from the session task list (taskbar shell state).

## Event bus (client)

- `src/lib/shell/shell-events.ts` — `subscribeShellEvents` / `emitShellEvent` for `app_launched`, `task_opened`, `task_activated`, `task_closed`, pin/unpin, `search_executed`, `fusion_hints_loaded`, `task_cleared_minimized`. Intended for future analytics, tray, or assistant hooks—not a substitute for server audit.

## Server persistence (pins / recents)

- **GET/PUT** `/internal/v1/shell/workspace-state` — Redis-backed document per `tenantId` + `X-Actor-ID`, TTL from `impilo.shell-workspace.redis-ttl-days` (default 120).
- **Tshepo** — Optional PDP via `impilo.shell-workspace.require-tshepo-authorize` using synthetic path `/internal/v1/shell/workspace-state` (`TshepoAuthzServiceClient.shellWorkspaceStateAllowed`). Policy migrations and enablement steps: [tshepo-shell-workspace-policy.md](./tshepo-shell-workspace-policy.md).
- **ABAC-style filtering** — `ShellRecentItemVisibilityFilter` removes clinical / patient-scoped recents when obligations indicate aggregate-only or blocked clinical access.
- **UI** — `ShellWorkspaceRemoteSync` (mounted from `Providers`) hydrates from session, **merges** server pins/recents with the hydrated session (`mergeRemoteWorkspaceWithSession`: pin union, recents by `refKey` with newer `recordedAt` winning; tie keeps local), then debounces PUTs after local changes.

## File catalog (reports + certificates)

- **GET** `/internal/v1/shell/file-catalog?facility_id=` — merges **report tenant runs** (reporting-service `GET /internal/v1/reports/tenant-runs`, export visibility respected) and **facility certificates** parsed from Tuso regulatory profile when `facility_id` is supplied.
- **Admin jobs** — `GET /internal/v1/admin/reports/jobs` now proxies the same reporting tenant-runs feed (JSON:API-ish rows for the data-export UI).

## Taskbar tray / live stream

- **Shell events** — `ShellNotificationTray` subscribes to `subscribeShellEvents` (noisy search events skipped) and shows synthetic tray rows for launches, tasks, pins, etc.
- **WebSocket** — If `NEXT_PUBLIC_EXP_NOTIFICATIONS_WS` is set, `useOptionalNotificationWebSocket` connects with backoff; inbound JSON is normalized via `normalizeLiveNotificationPayload` (`title` / `message` / `body` / nested `data.notification`, `alertTitle` / `msg`, etc.) then emitted as `live_notification` for the tray.

## Assumptions and gaps

- Cross-service search beyond BFF index + shell-suggest remains **governed by backend** authorization; the UI does not synthesize clinical rows without href.
- **Inventory** and **Intelligence** apps are catalog entries; route guards (facility, JWT) still apply when navigating.
- File catalog **unifies** personal bridge documents and shell recents; deep links into every module-specific document viewer are not auto-generated without index/BFF href hints.
- WebSocket payload contract remains **environment-specific**; normalization covers common envelope shapes — unknown fields still appear in a short JSON preview body.

## Manual testing

1. Sign in, open Experience with shell chrome; confirm bottom taskbar, Start, search icon.
2. Open global search (Ctrl+K), type ≥2 characters: platform hits load; fused hints load when BFF intelligence plane is available.
3. Run quick action **Open search** from the palette: palette stays open and input refocuses.
4. Open two routes, minimize one from task manager, click **Clear minimized from taskbar**: minimized row disappears; active task falls back to an open task.
5. Open **File manager** (`/shell/file-manager`), tab **All sources**: unified table lists personal docs and document-like recents after vault/recents activity.

## Next wave

- **Workspace conflict merge** — today the client merges GET results with session (pins union, recents by `refKey`); richer per-field conflict resolution remains future work if multiple devices write concurrently.
- Richer **FileResource** from additional workflow/export surfaces beyond reporting + Tuso certificates.
- Deeper tray analytics or server-originated notification catalog (beyond optional WebSocket + local shell events).
