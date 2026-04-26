# One UI Shell — Navigation Doctrine

## Purpose

The **One UI Shell** is the governed **utility layer** for Impilo vNext. It must keep users **oriented**, **safe**, and **able to reach authorised work** without turning the shell into a second application menu.

## Core doctrine

1. **The Shell is not a module menu.**  
   Persistent chrome exposes **Start**, **Search / Command**, **Context**, **Comms**, **Help**, **System Support**, **SOS**, **Notifications**, and **Profile** — not every clinical or ERP module.

2. **Menus belong inside active modules and workflows.**  
   EHR encounter steps, facility operations subflows, and ERP planes may use **local** sidebars, tabs, steppers, or drawers **after** the user has entered that module.

3. **Discoverability must not depend on a permanent global sidebar.**  
   Former sidebar destinations are reachable via **Start (launcher)**, **Command palette**, **Home** role-aware tiles, **breadcrumbs**, **local module nav**, and **deep links** (notifications, tasks).

4. **No URL-only critical paths.**  
   Anything that was in the global navigation must remain findable through at least one **visible** path (Start, Search, Home, or in-module nav).

## Why we avoid a permanent global sidebar

- It **consumes horizontal space** needed for clinical forms, queues, and imaging.
- It **duplicates** the Start / Search model and confuses “shell” vs “module”.
- It encourages **flat lists** of 30+ links that do not scale by role or facility.

## What belongs on the Shell Taskbar

Only **universal shell actions** (compact, labelled where possible, keyboard-accessible):

| Action | Role |
|--------|------|
| Start / Launcher | Role-aware app and domain launcher |
| Search / Command | Primary fast navigation (`Ctrl+K` / `Meta+K`) |
| Context | Facility / workspace / shift entry (links to selection flows) |
| Comms | Secure messaging / comms hub |
| Help | Contextual guidance (`/guidance`) |
| Nompilo / Ask | Intelligent assistance (`/ask`) |
| System Support | Tickets and operational help (`/support`) |
| SOS | Emergency / escalation workflow (dialog + events) |
| Notifications | Real tray only (no fabricated counts) |
| Profile | Account, preferences, sign-out entry |

Pinned apps and running tasks remain **secondary** to the above; they are not substitutes for module menus.

## What belongs in Start / Launcher

- **Role-filtered** apps from `SHELL_APPS` and curated domain groups.
- **Pinned** and **recent** items (from shell store / future BFF workspace API).
- **“Full navigation map”** entry that opens the **drawer** carrying the legacy zone map (Work / Professional / Life) for users who browse hierarchically.

## What belongs in the Command Palette

- **Navigation** to modules (keywords aligned with clinical and ERP language).
- **Commands** (open Start, open task manager, go Home, …).
- **Authorised** platform / patient / facility search hits from BFF (existing fusion path).
- **No** unauthorised patient or financial leakage — server-side enforcement remains authoritative; the UI filters by role.

## When local module sidebars are allowed

- **Inside** EHR, encounter wizards, facility operations hubs, ERP workspaces, admin curation flows, etc.
- They must be **scoped** to the active module and **dismiss** when leaving the module.

## Breadcrumbs and orientation

- Every major **app layout** route shows **ModuleBreadcrumb**: `Domain > Section > Page` derived from `matchRouteDefinition` and path.
- Breadcrumb links only navigate to **parent segments the user is allowed to see**; unknown routes omit sensitive parents.

## Home / Start page rules

- Home answers: **What now?**, **What needs attention?**, **What was I doing?**, **Where am I?**, **Where next?**
- **No fake production metrics** — empty states and “connect integration” messaging when APIs are absent.

## SOS doctrine

- SOS is **always visible** on the taskbar (except deliberate full-screen modes such as DICOM viewers — future flag).
- Types: clinical emergency, security, outage, privacy, connectivity, equipment, facility disaster, safeguarding.
- Emits **shell events** for local analytics; **server audit** when API exists; **safe fallback** UI if offline.

## Accessibility and low-training usability

- **Text labels** alongside icons on `sm+` where space allows.
- **Tooltips** on icon-only collapse breakpoints.
- **Keyboard**: `Ctrl+K` search, `Escape` closes overlays.
- **Empty states** explain how to open Start or Search.

## Responsive behaviour

- **Drawer navigation** replaces permanent sidebar at **all** breakpoints; wide screens gain canvas space.
- Taskbar may collapse labels on very small widths; **Start and Search** remain reachable.

## Related artefacts

- `docs/audits/one-ui-sidebar-dependency-audit.md` — inventory of former sidebar items.
- `docs/audits/one-ui-sidebar-route-replacement-map.md` — route → replacement path matrix.
- `docs/audits/one-ui-navigation-accessibility-risk-register.md` — risks and mitigations.
