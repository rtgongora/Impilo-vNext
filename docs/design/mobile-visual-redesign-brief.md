# Impilo Citizen & Provider — mobile visual redesign brief

**Status:** handed to the session that owns `apps/mobile`. Not implemented by the web lane.
**Source:** product-owner redesign brief §7–§16, plus a code inventory of `apps/mobile`
taken 2026-07-26.
**Companion work:** the web half (§2–§6) landed on
`claude/staging-ux-orchestration-remediation-Yypyl` — see the "one Impilo family"
section below for the palette and surface decisions the web now uses.

---

## What the PO asked for

The mobile surfaces "feel angular, plain and visually unfinished — more like wireframes
than finished products." The ask is a **visual maturity pass**, not a rewrite:

- Consistent, larger corner radii; softer cards; layered backgrounds; light gradients.
- Refined spacing, stronger hierarchy, better typography.
- Subtle gloss and gentle depth — **but do not overuse glass**; readability and speed win.
- Better icon treatments, empty states, status badges, bottom navigation, segmented
  controls, tabs, sheets and drawers.
- The work must reach **deeper screens**, not just Citizen Home and Provider Dashboard.

Acceptance criteria 11–23 and 27 in the PO brief are the gate.

---

## Five findings that change the shape of the work

These came out of the inventory and are worth knowing before any styling starts.

### 1. The token layer is orphaned — adoption is the first task, not colour choice

`packages/mobile-design-system/src/tokens/colors.ts` declares the real brand green
`#009739`. Only **5 of ~290** screen files import it. **225 files** call
`StyleSheet.create` with hardcoded hex. `ThemeProvider` is mounted in both `App.tsx`
but `useTheme()` is never consumed by an app screen, so the dark theme is defined and
unreachable.

**Consequence: editing `tokens/colors.ts` today changes nothing visible.** A palette
decision that isn't preceded by adoption will look like it failed.

### 2. The design system itself hardcodes citizen green, so the apps cannot diverge

`packages/mobile-design-system/src/components/Button.tsx:46-51`:

```ts
primary:     { bg: "#059669", text: "#FFFFFF" },
secondary:   { bg: "#1E40AF", text: "#FFFFFF" },
outline:     { bg: "transparent", text: "#059669", border: "#059669" },
destructive: { bg: "#DC2626", text: "#FFFFFF" },
```

`<Button variant="primary">` therefore renders **citizen green inside the Provider
app**. `Badge.tsx:30-38` and `Card.tsx` do the same. This has to be fixed — most likely
by resolving variants through `useTheme()` — **before** any palette work, or the two
apps physically cannot have different identities.

### 3. Provider really is generic Tailwind blue

Confirmed at:

- `provider-app/src/navigation/ProviderTabs.tsx:27` — `const ACCENT = "#1E40AF"`,
  repeated verbatim in `OutreachTabs.tsx` and `SupervisorTabs.tsx`
- `provider-app/src/screens/LoginScreen.tsx:22-24` — `#1E40AF / #1E3A8A / #DBEAFE`
- `provider-app/src/screens/provider/ProviderDashboardScreen.tsx:48` — `#1E40AF`

Most-used hex in the provider app: `#2563EB` (36), `#1E40AF` (32), `#3B82F6` (30).

Two modes are also off-brand: `OfflineTabs.tsx` `#B45309` (amber),
`CourierTabs.tsx` `#0D9488` (teal).

### 4. Timeline exists but is buried — that is a navigation change, not a styling one

`citizen-app/src/screens/personal/HealthTimelineScreen.tsx` is reached as **one of 48
chips** in a horizontal scroller inside `PersonalScreen` (`PERSONAL_TABS` / `SECTIONS`,
lines 106–207). §11 makes Timeline mandatory and prominent, and §10 puts it in the core
bottom navigation. That is a `CitizenTabs.tsx` change with Maestro consequences (below).

### 5. Risk is asymmetric: restyle freely, re-navigate carefully

`citizen-app/src/__tests__/setup.ts` mocks React Native wholesale and makes
`StyleSheet.create` a pass-through:

```ts
StyleSheet: { create: (s: unknown) => s },   // styles are a no-op
```

**No test anywhere applies a style.** Colour, radius, spacing and shadow changes carry
essentially zero unit-test risk. But 31 Maestro flows in `apps/mobile/maestro/flows/`
plus most unit tests drive by `testID` (`citizen-tabs`, `provider-tabs`, `mode-router`,
`mode-btn-*`, `queue-management-screen`, `facility-directory-screen`, …). Renaming or
restructuring a screen breaks them; restyling does not.

The corollary is uncomfortable and worth stating: **a green suite will not tell you the
redesign looks right.** Screenshots are the only evidence for §18's visual criteria.

---

## Colour direction

### Citizen — warm, personal, hopeful (§8)

Today: `#059669` accent (`CitizenTabs.tsx:24`, `HomeScreen.tsx:37`,
`LoginScreen.tsx:22-24`), Tailwind greys `#6B7280` (188), `#111827` (111), `#9CA3AF` (96),
with blue leaking in (`#2563EB` ×27, `#1E40AF` ×12).

Direction: Impilo green, soft mint, pale teal, warm white. Supportive blue/purple/gold/
coral **only** for category distinction. Red reserved for emergency and danger — the
`#DC2626` currently used for ordinary destructive actions should be reviewed against that.

### Provider — green-near-blue, precise and calm (§8)

Not an ordinary blue. Deep teal, blue-green, cyan-teal accents, dark professional
green-teal, pale aqua supporting surfaces — related to the brand green, clearly distinct
from citizen emerald so a clinician never mistakes which app they are in.

The two off-brand mode accents (offline amber, courier teal) should be re-derived from
the provider ramp rather than picked independently.

---

## Navigation load (§10, §12, §13)

Not colour, but it is the reason the apps read as unfinished:

- **Citizen bottom bar carries 8 tabs** at `fontSize: 10` in a `space-around` row
  (`TabBar.tsx:104-150`) — about 47px per tab on a 375pt device. "Messages" and
  "Khuluma" sit adjacent as two separate chat destinations.
  §10 wants Home / Care / Find / Timeline / Messages / My Impilo plus a structured More.
- **Provider carries 10–11 tabs** plus an always-visible `ModeSwitcher` pill row above
  them, consuming vertical space on every screen.
- **Both apps have a 48-entry horizontal scroller** as a third navigation layer
  (`ClinicalToolsScreen.tsx`, `PersonalScreen.tsx`).
- **No React Navigation** despite the dependency being declared — navigation is a
  `switch` over Zustand state, so there are no transitions, no back stack and no header
  chrome to inherit. Any "polished sheets and drawers" work has to supply its own.

## Loose ends worth a decision

- `citizen-app/src/screens/caregiving/DelegationSection.tsx` (dependants) is imported by
  nothing — §9 lists dependants as a screen to refine, so it needs wiring or removing.
- Seven provider screens are built but unreferenced: `ActivityFeedScreen`,
  `BudgetSummaryScreen`, `DaidzaiFieldMissionScreen`,
  `AssistedCommunicationPreferencesScreen`, `outreach/FollowUpScreen`,
  `equipment/EquipmentToolsScreen` (and its 3 children), `ConfirmDeathScreen`.
- `wellnessSocial` has a `renderContent` case in `ProviderTabs` but no tab entry.
- Missing primitives a polish pass will want: no `Chip`/`Pill`, `SegmentedControl`,
  `ListItem`, `Divider`, `Toast`, `Modal`, `Accordion`, `Stack`/`Box`, `Icon` wrapper or
  `SearchBar` — every screen rolls its own `TextInput`.

---

## One Impilo family (§24)

What the web now does, so mobile can rhyme with it rather than guess:

- **Hero canvas** runs near-white behind the Impilo wordmark, deepens downward with the
  richest teal pooled toward the lower left, and settles on a dark teal-green floor.
- **Ministry identity appears once**, in the top bar — never repeated in the hero.
- **Glass is used sparingly and always with a fallback**: `shared-ui/GlassSurface`
  carries `supports-[not(backdrop-filter:blur(0px))]` and a `.low-blur` escape. The
  mobile equivalent is `mobile-design-system/GlassSurface.tsx` + `glassStyle.ts`,
  currently consumed by exactly one file (`FloatingSosButton.tsx`). §7's "do not overuse
  glass effects" and the web's own restraint agree here.
- **Nothing renders that isn't backed.** The web landing's continue-your-journey section
  returns `null` rather than showing an empty shell, and the virtual-care section says
  plainly that no virtual services are published rather than inventing cards. Mobile
  empty states should hold the same line — §7 lists "improved empty states" as a goal,
  and the honest version is the better product, not a compromise.

---

## Known backend gap that affects mobile too

TUSO holds **21 virtual services, all `CONFIGURED`, none `ACTIVE`**. The public discovery
lane and any citizen virtual-care surface will correctly show nothing until someone with
registry authority activates them. This is a data/governance gap, not a UI defect — do
not work around it by seeding or by relaxing the `ACTIVE` filter.
