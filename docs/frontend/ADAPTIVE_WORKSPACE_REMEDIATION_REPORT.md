# Adaptive Workspace & Screen Real-Estate Remediation — Report

Date: 2026-07-12 · Branch: `claude/staging-ux-orchestration-remediation-Yypyl`
Scope: `ui/shared-ui`, one-ui-shell shell/layout/navigation/Nompilo chrome, layout-only page
conversions, and the separate `zimttech/impilo-website` repo.

Doctrine anchor: *one experience shell … spacious when space is available, compact when tasks
demand focus.* Screen space is treated as a limited operational resource; the active task gets the
largest practical workspace on phones, tablets, laptops and large monitors.

## 1. Inventory reviewed

- **Shell chrome**: `AppLayout`, `EHRLayout`, `PageShell`/`ModuleWorkspaceHero`,
  `ExperienceSidebar` (off-canvas drawer), `ShellChrome`, `ShellTaskbar`, `ShellRouteSync`,
  `useShellStore`.
- **Nompilo surfaces**: `ProactiveAssistant`, `NompiloContextualGuidance` (already
  whisper-pattern, in-flow — left as-is), `FloatingClinicalAssist` (click-to-open — left as-is),
  `NompiloContextPanel`, `NompiloGuidanceOrchestrationRail`.
- **Design system**: `shared-ui` tokens/preset/components, `useTier` progressive enhancement.
- **Service-delivery workflows** (very-thorough fan-out scan of `src/app/**`): EHR encounter /
  vitals / orders / discharge, VITO registration wizard, diagnostics order composer, Nhume
  new-delivery, pharmacy dispense/prescriptions (found to be list pages, not form targets),
  telemedicine intake (already split-pane + step nav), registry intake, id-services, citizen
  signup, the 13 config-driven `OnboardFlowWizard` governance flows, Fundo studio (small,
  low-traffic), facility-lifecycle & coverage (already heavily gridded).
- **Public surfaces**: `welcome/**`, `discover/**` (deliberately untouched — hot files owned by
  the parallel gateway session; see §10), portal/self-service, and the separate public website
  repo.

## 2. Reusable layout primitives created / changed

All in `ui/shared-ui` (exported from `index.ts`), each with vitest coverage in
`one-ui-shell/src/test/adaptive-layout-primitives.test.tsx` (15 cases):

| Primitive | Purpose |
|---|---|
| `FormGrid` / `FormField` / `FormSection` | Responsive 2/3/4-column form grids; single column on phones; `span="full"` narrative fields; consistent label/hint/`role=alert` error rendering; subtle section boundaries instead of giant cards |
| `StickyActionBar` | One compact terminal action row per screen; sticks above the shell taskbar (`--shell-taskbar-height`) and mobile safe areas; `role=toolbar` |
| `Stepper` | Horizontal stepper ≥sm with clickable completed steps (policy-gated), automatic compact step-counter + progressbar variant on phones — never forces a wide stepper into a small viewport |
| `MoreBelow` | Overflow cue: compact chip while content remains below the fold, disappears at the end, tap scrolls forward; button (not hover) so it works on touch |
| `SplitView` | Work + supporting panel; side-by-side ≥`splitAt`, stacked on narrow viewports with the primary pane first in reading order |
| `FullHeightWorkspace` + `WorkspaceScrollPane` | Full-height app workspaces with exactly ONE internal scroll region (no nested/competing scrollbars); keyboard-reachable scroll region |
| `AdaptiveGrid` | `auto-fit` minmax dashboard grid — tiles size to content, no oversized fixed design grids |
| `Card density="compact"` (+ `padding="xs"`, `CardHeader density`, `CardTitle size`) | Content-sized compact card variants for dense operational views |
| Tailwind preset | `xs` (480px) and `3xl` (1920px) breakpoints added for small-phone and large-monitor differentiation |

Shell-level primitives (one-ui-shell):

- `useLayoutPrefsStore` — persisted nav-rail preference + taskbar minimise (localStorage),
  session-only focus mode. Pure chrome state, never workflow state.
- `src/lib/shell/workspace-context.ts` — pure route classifier: navigating/landing surfaces vs
  focused-work surfaces; `resolveNavRailMode` (focus mode > manual preference > auto).
- `NavRail` — persistent collapsible navigation rail (see §4).
- `useAssistantUiStore` — Nompilo panel/conversation state (see §6).

## 3. Pages converted from inefficient vertical layouts

Layout-only conversions (no handler/query/state changes; all ids/testids/labels preserved):

- `diagnostics/orders/new` — narrow `max-w-2xl` single column → responsive 2/3-column grid,
  full-width clinical-notes field; form now fits a laptop viewport without scrolling.
- `operations/vito/registration/new` — demographics grid widened to 3 columns at `lg`,
  full-width notes, wizard actions moved into `StickyActionBar` with step status.
- `nhume/deliveries/new` — 7-section dispatch form: terminal Create/Cancel actions in
  `StickyActionBar` (no longer buried below the fold).
- `auth/register` (citizen signup) — wider auth card (`AuthLayout width="xl"`), name/identifier
  and password/confirm paired at ≥sm.
- `OnboardFlowWizard` (shared by 13 governance onboarding flows) — vertical stack of one-card-per-
  step with repeated boilerplate → single horizontal `Stepper` + one shared note line.
- EHR `encounter`, `vitals`, `discharge` — short clinical fields grouped into responsive grids,
  terminal actions in `StickyActionBar` (details in the batch-2 commit).
- Assessed and deliberately NOT churned: telemedicine intake (already split-pane with step nav),
  pharmacy dispense/prescriptions (list/action pages), facility-lifecycle & coverage (already
  gridded), Fundo studio forms (3–4 fields, small).

## 4. Navigation-collapse behaviour

Previously: the zone navigation was an off-canvas drawer at **all** breakpoints — every page
started nav-less, and opening navigation covered the workspace.

Now (`NavRail`, ≥`lg` viewports):

- **Expanded** (icon + label, 240px) on landing/hub surfaces where the user is primarily
  navigating: `/home/**`, `/welcome/**`, `/discover/**`, citizen/professional/social surfaces,
  and application hub landings (`/clinical`, `/pharmacy`, `/learning`, …).
- **Compact** (icon-only, 64px, tooltips + `aria-label`s) automatically when an application,
  record, or operational drill-down is opened (`isFocusedWorkspaceRoute`), or when the user
  manually collapses. Manual preference persists (localStorage) and wins over route context.
- **Hidden** in focus mode, and below `lg` (phones/tablet-portrait) where the off-canvas drawer +
  bottom taskbar serve navigation without reserving horizontal space.
- The full drawer (zones, context spotlight, experience-entry state) remains one tap away from
  the rail's menu button and the header hamburger on all viewports.
- Zone config + role gating extracted to `sidebar-zones.ts` — drawer and rail render the same
  governed items; behaviour unchanged.

## 5. Taskbar visibility rules

- Dock minimise button (and `Ctrl+Alt+B`, which also works during field entry) collapses the
  bottom taskbar into a small restore handle (bottom-right pill, ≥44px touch target,
  screen-reader labelled, safe-area aware).
- Minimised or focus-mode taskbar publishes `--shell-taskbar-height: 0px` so every page's
  reserved bottom padding is reclaimed by the workspace.
- Focus mode implies a minimised taskbar; tapping the handle restores both.
- SOS dialog stays mounted while minimised (command-palette SOS and the persistent Emergency
  Help button are unaffected — gateway doctrine §7 preserved).

## 6. Nompilo visibility & interruption rules

- Assistant panel/conversation state moved to `useAssistantUiStore`: minimising the assistant or
  navigating between routes never loses the conversation (in-memory only, never persisted).
- On focused-work routes and in focus mode the launcher shrinks to an unobtrusive edge control,
  raised above sticky action bars so it cannot cover Save/Continue/clinical actions.
- The panel opens ONLY on deliberate tap (`aria-expanded` reflected); no auto-open over forms.
- Interruption rule: while the user is actively typing in any field (4s window), non-urgent
  badge animation is suppressed. CRITICAL clinical alerts keep their always-visible top banner —
  safety-critical content still interrupts.
- `NompiloContextualGuidance` already implemented the "announce → resting whisper" pattern
  in-flow (not an overlay) and was verified, not changed.

## 7. Responsive behaviour by device category

- **Small/large phones (<640px)**: single-column forms (FormGrid collapses), compact mobile
  stepper (counter + progressbar), nav via drawer + taskbar, no persistent rail, sticky actions
  respect `env(safe-area-inset-bottom)`, no horizontal page scroll (e2e-asserted at 393px).
- **Tablets portrait (<1024px)**: same as phones for nav (drawer); FormGrid engages 2 columns
  from 640px; touch-first controls (no hover-only affordances anywhere in the new chrome).
- **Tablets landscape / laptops (≥1024px)**: persistent NavRail (auto expanded/compact by
  context), 2–3-column form grids, SplitView side panels engage at `lg`.
- **Desktops (≥1280px) / large screens (≥1920px, new `3xl`)**: 3–4-column grids for short
  fields, AdaptiveGrid packs more content-sized tiles per row; `xs`/`3xl` breakpoints available
  to all workspaces via the shared preset.
- `useTier` progressive enhancement untouched: baseline tier keeps solid surfaces (StickyActionBar
  and MoreBelow carry explicit `low-blur` fallbacks).

## 8. Before/after evidence (representative workflows)

- `e2e/adaptive-workspace.spec.ts` (new, runs in the standard chromium project) walks:
  rail expand→compact across navigation vs application routes; manual preference persistence
  across reload; taskbar minimise → reserved-height reclaim → restore; focus mode with the
  workflow-state hard gate (entered CPID survives layout changes); phone-viewport horizontal-
  overflow guards on the three converted forms. **Final run: 5/5 passed.**
- The spec caught two real overlay defects before they shipped: the floating dock covered the
  rail's bottom edge (fixed: rail reserves `--shell-taskbar-height`), and the gateway session's
  fixed Emergency Help button (doctrine §7, must stay) covered the rail's bottom toggle (fixed:
  the collapse toggle moved to the top of the rail — the bottom edge is contested territory).
- Golden-journey specs (`e2e/journeys/**`, screenshot/video evidence project) remain the live
  estate harness — selectors/testids/routes were kept stable throughout (§12).

## 9. Automated & manual test results

- shared-ui `tsc --noEmit`: clean.
- one-ui-shell `tsc --noEmit`: clean.
- one-ui-shell vitest: **1679 passed / 0 failed** (incl. 15 new primitive cases, 7 new
  workspace-context/layout-prefs cases, 4 new Nompilo behaviour cases).
- `test:routes` (route parity) and `test:launchers` (dead-end guard): green.
- `e2e/adaptive-workspace.spec.ts`: **5/5 passed** against the dev server (desktop 1440×900 and
  phone 393×851 viewports).
- Regression check on the wider chromium e2e set covering every touched surface (responsive,
  auth, citizen-signup, citizen/provider onboarding, dispatch, clinical-flow): these specs carry
  pre-existing failures on this branch (mostly strict-mode duplicate-text violations against the
  taskbar/drawer chrome). Honest baseline comparison at the pre-remediation merge-base
  (`3c4ff3066`, clean temp worktree): **20 failed / 5 passed before → 16 failed / 8 passed
  after** — no regression introduced; three additional specs now pass.
- Website repo: `npm run build` green after every one of its 12 commits; lint clean.

## 10. Remaining exceptions — where vertical scrolling stays, and why

- **Clinical record surfaces (EHR chart, vitals history)**: clinically visible information must
  not be collapsed or hidden (brief §4 hard rule) — long histories still scroll; grids reduce the
  length, `StickyActionBar` keeps actions reachable.
- **`welcome/**` / `discover/**`** (deferred — conflict risk, not a defect): hot files actively
  receiving gateway-intent functional work from the parallel session; on re-check they are already
  responsibly built (responsive `sm:grid-cols-2 lg:grid-cols-3` pillar/reference grids, sensible
  spacing). Layout churn there buys little and risks rebase conflicts against in-flight functional
  commits, so it is intentionally left to the gateway session.
- **`ui/portal` / `ui/self-service`** (assessed — correct as-is): the citizen self-service flows
  (Health-ID request, card pickup, ID recovery, side-effect report, credential verify) are
  deliberately narrow single-column (`max-w-lg`/`max-w-xl`). That is the *right* shape for a
  low-literacy, one-task-at-a-time, low-cost-Android audience (brief §2 phones + §11 device
  priority); multi-column gridding would be a regression, so none was applied.
- **`registry/intake`** (assessed — already responsive): an operator dashboard of tool cards on a
  `md:grid-cols-2` grid with wide cards spanning two columns — not the section-wizard an early
  scan suggested; no conversion warranted.
- **Telemedicine intake**: already a split-pane step-nav wizard — no conversion needed.
- **List/queue pages (pharmacy dispense, dispatch queues)**: vertical lists are the correct shape
  for compare-and-act work; they use compact rows already.
- **EHRLayout**: has its own focused chrome (own header/sidebar); only inherits the taskbar-height
  reclaim. Deeper unification deferred.

**Scope discipline note:** the space-compaction mandate targets operational/professional forms
completed under time pressure (health workers entering data), not linear citizen flows — those
stay single-column by design. After converting the genuine operational offenders (§3), the
remaining surfaces were each verified rather than assumed, and found to be either already
responsive or correctly narrow, so no further churn was introduced onto the shared branch.

## 11. Website repo (`zimttech/impilo-website`)

Branch `adaptive-layout-remediation` off `public-gateway-vnext-handoff`, pushed to origin
(tip `0168b18`, 12 atomic commits, `npm run build` green after each; lint clean; copy law
preserved — "National Health Operating System" intact, zero occurrences of the deprecated
phrase). Highlights:

- `PageLayout` banner and `GatewayHero` compacted — the next section now peeks above the fold
  (no more 100vh-style walls); duplicate full-size in-body `h1`s demoted to `h2`.
- HomePage: section padding py-24→py-16; 256px square tech-marquee tiles → 144–160px
  content-sized tiles; partner grid compacted.
- Community page mobile blocker fixed: a fixed-width `940px` Google Forms iframe forced
  horizontal scroll at 360px — now fluid with an accessible title.
- CoP sign-up + Contact forms: short-field pairs from ≥sm, 44px touch targets, autocomplete
  hints, category radios in a 2-column grid; Contact's half-empty 2-column grid (other cell was
  a commented-out form) now full-width with side-by-side contact channels.
- Footer/Navbar: 2-column footer from sm, 44px mobile menu toggle.
- Deliberately untouched: the commented-out Contact form (no submit handler — re-enabling it
  would add a dead production path), full-reload anchor links (routing out of scope).

## 12. Modified files & compatibility confirmation

Monorepo commits (this remediation, in order):

1. `feat(shared-ui): adaptive workspace layout primitives (W0)`
2. `feat(shell): adaptive workspace shell — collapsible NavRail, focus mode, taskbar minimise (W1)`
3. `feat(nompilo): context-aware assistant — minimise during focused work, retain conversation, interruption rules (W2)`
4. `refactor(layout): compact responsive form layouts — registration, diagnostics order, dispatch, signup (W3 batch 1)`
5. W3 batch 2: EHR encounter/vitals/discharge grids + OnboardFlowWizard stepper + this report +
   `e2e/adaptive-workspace.spec.ts`.

Confirmation:

- **No workflow state broken**: all layout state lives in dedicated stores
  (`useLayoutPrefsStore`, `useAssistantUiStore`); page conversions changed markup/classes only —
  every input kept its exact wiring; e2e hard-gate test proves entered data survives
  collapse/minimise/focus transitions.
- **No accessibility regressions**: icon-only rail keeps `aria-label`s + tooltips; steppers
  announce `aria-current="step"` and progress; sticky bars are labelled toolbars; scroll panes
  are keyboard-reachable; no control is hover-only; critical-alert interruption preserved.
- **No business logic touched**: `services/**`, experience-bff, API contracts, migrations,
  `apps/mobile/**` untouched; golden-journey testids/selectors/routes stable (one test mock
  extended for the new `usePathname` use in `PageShell`).
- **Design law**: CinematicStage/LuminousStage context-graduation and `useTier` untouched; no
  global dark mode introduced.
