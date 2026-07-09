# Impilo vNext — "Future-Realism" Design Style Guide

> One visual language for the whole suite (Web `one-ui-shell`, Citizen app, Provider app).
> High-gloss glassmorphism, dark-first, cinematic accents — **without** ever compromising the four
> non-negotiables of a national Health Operating System.

**Status:** Foundation spec (v0.1). Source of truth for the redesign. Extends the existing token
systems; it does **not** replace them.

**Audience:** anyone touching UI in `ui/one-ui-shell`, `ui/shared-ui`, or
`apps/mobile/**` (both apps + `packages/mobile-design-system`).

---

## 0. TL;DR

- **Extend, don't reinvent.** Web tokens live in `ui/shared-ui/tokens.css` + `ui/shared-ui/tailwind-preset.ts`; mobile tokens in `apps/mobile/packages/mobile-design-system/src/tokens/*` with a `ThemeProvider` that already defines light **and** dark. We add glass/neon/dark layers on top.
- **Dark-first, light always available.** Ship a WCAG-AA high-contrast light theme from day one.
- **Progressive enhancement is law.** A solid, no-blur, no-3D, AA-contrast baseline always renders and is fully usable. Glass, neon glow, motion, and 3D **layer on only** for capable devices and only when `prefers-reduced-motion` / low-bandwidth allow.
- **Gloss never lies.** Glass/neon may never hide missing data, fake success, or reduce the legibility of clinical information. This is a direct extension of the product honesty doctrine.
- **Sovereign, not generic.** Impilo teal (`#009739`) + Zimbabwe gold (`#fce300`) + the African-print heritage motif remain the identity. "Neon" is a *restrained accent/glow*, never the brand.

---

## 1. Vision & the four non-negotiables

The aesthetic is **"Future-Realism"**: translucent frosted-glass surfaces over deep chromatic
gradients, 1px "light-catch" borders, soft depth, and selective cinematic motion. It should feel
premium and modern on a flagship phone and **still be clean, fast, and legible on a $80 Android over
a rural 3G link.**

Every design decision is gated by these, in priority order:

1. **Honest** — Gloss is decoration, never a substitute for truth. No fabricated values, no
   fake-success states, no "shimmer" standing in for real data. If a value is unknown, say so.
2. **Accessible** — WCAG 2.1 **AA** is the floor for all text, and **clinical/decision data must not
   rely on glass or color alone**. Text on glass must pass AA against the *effective* backdrop.
3. **Performant** — Progressive enhancement. The baseline must render < 1s of interaction cost on
   low-tier devices/local networks. Dazzle is opt-in and device-gated.
4. **Sovereign** — Impilo teal + Zimbabwe gold + African-print heritage. The redesign modernizes the
   existing brand; it does not import a generic "cyberpunk neon" identity.

> If a "wow" effect conflicts with 1–3, the effect loses. Always.

---

## 2. Foundations — tokens

### 2.1 Brand palette (unchanged — keep)

| Role | Value | Web var | Mobile token |
|---|---|---|---|
| Primary / Impilo teal | `#009739` | `--impilo-green` | `colors.primary[500]` |
| Zimbabwe gold | `#fce300` | `--impilo-yellow` | `colors.warning.main` (`#FCE300`) |
| Alert red | `#ef3340` | `--impilo-red` | `colors.secondary[500]` |
| Charcoal | `#231f20` | `--impilo-charcoal` | `colors.neutral[900]` (near) |

Semantic colors (`success/warning/danger/info`) and the mobile `clinical.*` category colors
(vitals, diagnosis, prescription, lab, imaging, referral, admission, discharge) are **retained
verbatim** — clinical semantics never change for aesthetics.

### 2.2 New: governed **neon-accent** set (accent & glow only)

Neon is used **only** for glows, focus rings, active-state edges, and hero accents — **never** for
body text, never for clinical status, never as a large fill. Derived from brand, not imported.

```css
/* ui/shared-ui/tokens.css — ADD */
--neon-teal:      #23e6a0;   /* brand teal, lifted for glow */
--neon-gold:      #ffe94d;   /* Zimbabwe gold, lifted */
--glow-teal:      0 0 24px rgba(35, 230, 160, 0.45);
--glow-gold:      0 0 22px rgba(255, 233, 77, 0.40);
--glow-danger:    0 0 20px rgba(239, 51, 64, 0.45);   /* reserved for emergency/SOS only */
```

```ts
// apps/mobile/packages/mobile-design-system/src/tokens/colors.ts — ADD (glow used via elevation/skia)
export const neon = {
  teal: "#23E6A0",
  gold: "#FFE94D",
  glowAlpha: { rest: 0.0, active: 0.45 }, // drives shadow/skia glow strength
} as const;
```

**Restraint rule:** at most **one** neon glow visible per viewport region at rest; multiple glows are
reserved for a single focal "wow" moment (e.g. the SOS confirm, the Health-ID hero).

### 2.3 New: **glass** tokens

```css
/* ui/shared-ui/tokens.css — ADD */
--glass-blur-strong: 25px;   /* hero / modal surfaces (high tier only) */
--glass-blur-soft:   12px;   /* cards / docks */
--glass-fill-dark:   rgba(20, 28, 24, 0.55);   /* dark theme translucent surface */
--glass-fill-light:  rgba(255, 255, 255, 0.62);/* light theme translucent surface */
--glass-border:      rgba(255, 255, 255, 0.16);/* 1px "light-catch" edge */
--glass-fallback-dark:  #141c18;  /* SOLID surface when blur is disabled */
--glass-fallback-light: #f4f7f5;
```

Mobile mirrors these as `tokens.glass.{blurSoft,blurStrong,fillDark,fillLight,border,fallback*}`.

### 2.4 Dark + light theme tables (dark-first)

**Web is the gap to fill** — today dark is backend-preference + scattered `dark:` classes with **no
centralized dark tokens and no `darkMode` config**. Introduce a `.dark` root class
(`darkMode: 'class'` in the tailwind preset) and a paired token block:

```css
:root { /* LIGHT (high-contrast, AA) — default when user/system prefers light */
  --bg-base:      #e9efe9;   --bg-deep: #dbe4dd;
  --surface:      #ffffff;   --surface-2: #f3f7f4;
  --text:         #1a1f1b;   --text-muted: #55635a;
  --border:       #cdd8d0;
}
.dark { /* DARK — default */
  --bg-base:      #0c110f;   --bg-deep: #070b09;   /* deep teal-black gradient base */
  --surface:      #131b17;   --surface-2: #17211c;
  --text:         #eef4f0;   --text-muted: #9fb2a8;
  --border:       #24312b;
}
```

Deep-gradient backdrop (behind frosted glass): a soft radial of teal→charcoal in dark, teal-tint→white
in light. Gold is a *low-opacity accent glow* in the gradient, never a large fill.

**Mobile:** the `ThemeProvider` already defines `light` and `dark` themes — flip both apps to
`mode="system"` (defaulting dark) and **fill the dark token values** above. Then complete section 7's
refactor so components actually read `useTheme()`.

### 2.5 Type, spacing, radius, elevation — reuse

Keep the existing type ramps (web Inter/Source-Serif/SF-Pro-Rounded; mobile `textStyles`), 4px spacing
grid, radii (12–32px web, `sm..full` mobile). Add one **glass elevation** guideline: glass surfaces use
`--shadow-floating` (web) / `shadows.xl` (mobile) plus the 1px `--glass-border`, and **never** stack
more than two glass layers (depth without soup).

---

## 3. Glassmorphism system

**The recipe (web):**

```html
<div class="rounded-3xl border border-[color:var(--glass-border)]
            bg-[color:var(--glass-fill-dark)] backdrop-blur-[var(--glass-blur-soft)]
            shadow-[var(--shadow-floating)]
            supports-[not(backdrop-filter:blur(0))]:bg-[color:var(--glass-fallback-dark)]
            low-blur:bg-[color:var(--glass-fallback-dark)] low-blur:backdrop-blur-none">
```

- The `supports-[...]` and `.low-blur` (a device-tier class, §6) both force the **solid fallback** —
  the surface is always opaque enough to keep text AA-legible. `backdrop-blur` is *additive polish*.
- Reference implementation already in the repo: `ui/one-ui-shell/src/components/shell/ShellTaskbar.tsx`
  (`backdrop-blur-xl` + `/92` surface) and the `AuthHero*` glass chips.

**The recipe (mobile):** add `expo-blur`; wrap content in `<BlurView tint={mode} intensity={tier==='high'?60:0} />`
over a translucent `View`. When `intensity={0}` (low tier) it degrades to the solid `--glass-fallback-*`
fill. Ship a `GlassSurface` component in `mobile-design-system` so screens never hand-roll it.

**Contrast rule (hard):** text/icons on a glass surface must pass **AA against the solid fallback
color**, not against the blurred image. This guarantees legibility regardless of what's behind.

---

## 4. Motion & cinematics

- **Web:** `framer-motion` is already installed and under-used — use it for entrance/stagger,
  layout transitions, and the SOS/hero focal moments. Reuse the existing afro-futurist keyframes
  (`globals.css` `auth-hero-*`: weave-shimmer, route-flow, heartbeat-pulse, node-glow, orb-drift) as
  the house motion grammar for hero zones.
- **Mobile:** RN `Animated` (native driver, already used in `SkeletonLoader`) for the baseline; add
  `react-native-reanimated` **only** when a screen needs gesture-driven 60fps motion.
- **Motion tiers:** `essential` (feedback, always on) → `expressive` (entrance/hero, high tier) →
  `cinematic` (3D/particle, high tier + opt-in).
- **Hard gates:** every non-essential animation must be disabled by `@media (prefers-reduced-motion:
  reduce)` (web) / `AccessibilityInfo.isReduceMotionEnabled()` (mobile) **and** by the existing
  `.impilo-low-bandwidth` global kill-switch. These are already respected on the auth hero — extend
  the same discipline everywhere.

---

## 5. 3D / "wow" strategy (honest & phased)

| Brief "wow" | Approach | Phase |
|---|---|---|
| Web **3D National Map** of Zimbabwe | **maplibre 3D extrusion / tilt** on the existing `Ndila` map (`ui/one-ui-shell/src/components/ndila/*`). **No three.js.** Glowing "data streams" = animated line layers; translucent provinces = fill-extrusion. | Near-term (reuses installed `maplibre-gl`) |
| Citizen **Personal Health Hologram** (pulsing heart) | **Interim:** SVG/Skia 2D "holographic" vitals card with glow + subtle motion. **Later:** true 3D via `expo-three` behind a high-tier + opt-in gate. Never on the critical path. | Interim now, 3D later |
| Provider **Cinematic Diagnostic Scans** | Reuse the DICOM canvas viewer (`dwv`) + `VitalsTrendChart` SVG sparklines with neon vital-wave styling; cinematic *transitions* via framer-motion. Not new 3D. | Near-term |

**Rule:** 3D and WebGL are **progressive enhancement**. Core function must never depend on them, and
they must be gated (§6). A device that can't render them shows the accessible baseline, not a broken
scene.

---

## 6. Progressive-enhancement & device-tier model

Define three tiers and resolve one at runtime:

- **`baseline`** — solid surfaces, no backdrop blur, essential motion only, no 3D. **Always shippable,
  always AA.** This is what low-end Android / reduced-motion / low-bandwidth / `save-data` get.
- **`enhanced`** — glass blur (soft), expressive motion, glow accents.
- **`cinematic`** — strong blur, maplibre 3D, hologram/particle effects.

**Resolution signals:**
- Web: `prefers-reduced-motion`, `prefers-reduced-transparency`, `navigator.connection.saveData`/
  `effectiveType`, `deviceMemory`, plus the existing `.impilo-low-bandwidth` opt-out. Emit a `.tier-*`
  / `.low-blur` class on `<html>` (a small `TierProvider` alongside the theme class).
- Mobile: `react-native`'s `AccessibilityInfo` (reduce-motion/transparency), `expo-device` tier
  heuristic (RAM/year-class), and a user setting. Expose via a `useTier()` hook in
  `mobile-design-system`.

Components read the tier through the theme/tier context — they **never** query hardware directly.

---

## 7. Component variants

Add `glass` and `neon` **variants** (not new components) to the shared primitives, and finish the
theme refactor.

- **Web** (`ui/shared-ui/components/*`): extend `Card`/`Button`/`Badge` etc. with a `surface="glass"`
  option and a `glow` prop (uses `--glow-*`). Keep Radix primitives; style via the tokens above.
- **Mobile** (`mobile-design-system`): add `variant="glass"` to `Card`/`Screen`/`BottomSheet` (built on
  the new `GlassSurface`), and a `glow` prop on `Button`/FAB.
- **Debt to pay:** most mobile components currently **hardcode hex** instead of `useTheme()`. Migrating
  them to consume the theme is a prerequisite for dark mode working at all — do it as part of the
  variant work, component by component, with the token tests in `mobile-design-system` guarding values.

**Do / Don't:**
- ✅ Glass for containers, docks, sheets, hero cards. ✅ Neon glow for one focal accent / focus ring.
- ❌ Glass behind dense tables or long clinical text. ❌ Neon as text color. ❌ Glow on every card.
- ❌ Reducing contrast of a vital sign, dose, or alert for the sake of a look.

---

## 8. Per-surface application (brief → real code)

### Visual temperature is context-graduated (not one global dark switch)

Glass is invisible on a *flat* surface — it needs a backdrop to refract. But the register of that
backdrop must track the context, per "many contexts, many experience modes". Two canonical stage
primitives (both in `ui/shared-ui`, both tier-degrading, both keep text AA):

- **`CinematicStage`** — deep teal-charcoal gradient + teal/gold aurora; carries `.dark` on its own
  subtree (no global `<html>` flip). Reserved for **operational command** surfaces — analytics,
  monitoring, national ops — where a mission-control feel is purposeful. *Applied:*
  `EnterpriseResourceDashboard`, `monitoring/provider-dashboard`.
- **`LuminousStage`** — luminous near-white gradient + soft teal/gold tint washes; stays **light**.
  For **wellness / citizen / clinical-care** surfaces, which want a light, warm, airy, human
  register. Dark would be the wrong emotional temperature there. *Applied:* `wellness` hub.

Rule of thumb: if the surface is someone *operating the estate*, it may go dark; if it is someone
*living their health or receiving care*, it stays luminous-light.

### Citizen app (`apps/mobile/citizen-app`)
- **Iridescent Digital Health ID** → re-skin `HealthIdSection` as a glass card with a gold-teal
  gradient sheen + subtle tilt (gyroscope, high tier only, reduced-motion-off).
- **Gamified wellness rings** → `WellnessJourneysSection` — animated progress rings (RN Animated / Skia),
  essential-motion baseline = static rings with values.
- **Floating SOS (flagship "wow" slice)** → a `GlassSurface` FAB mounted next to `NompiloLauncher` in
  `citizen-app/src/navigation/AppNavigator.tsx`, wired to the **real** `emergencyService.createSos`
  (Daidzai BFF) → navigate to `TrackEmergencyScreen`. Confirm-before-send (accidental-trigger guard),
  `--glow-danger` accent, honest states throughout. *(This is the concrete first build after tokens.)*

### Provider app (`apps/mobile/provider-app`)
- **Tactile Glass Triage Hub** → glass cards + "plasticity" press states on the triage/queue screens.
- **Neon vital waves** → style `VitalCard` / `VitalsTrendChart` sparklines with a restrained neon glow;
  values and ranges stay full-contrast and never glass-obscured.

### Web admin/national (`ui/one-ui-shell`, admin/national role views)
- **Floating Analytics Command Center** → glass panels (extend `ShellTaskbar`'s live pattern) over the
  deep-gradient dark backdrop; framer-motion panel transitions.
- **Interactive 3D National Map** → maplibre 3D extrusion + animated data-stream line layers on the
  existing `Ndila` map. Translucent provinces via fill-extrusion; tier-gated tilt/animation.

---

## 9. Accessibility & governance guardrails

- **Contrast:** body/label text ≥ 4.5:1, large text ≥ 3:1, **against the solid fallback**. Clinical
  numbers, doses, alerts: treat as large-text-critical, never below 4.5:1, never glass-blurred.
- **Never color/gloss alone:** status also carries icon + label (existing `StatusBadge`/`StatusIndicator`
  patterns already do this — keep it).
- **Focus & SR:** visible focus rings (neon glow is fine *in addition to* a solid ring), preserved
  `aria-*`/`accessibilityRole` (web has 514+ aria usages; mobile components already set roles/labels).
- **Reduced transparency:** honor `prefers-reduced-transparency` → force solid surfaces.
- **Honesty tie-in:** a glass/skeleton/shimmer state must resolve to real data or an honest empty/error
  state — it is never the final rendered "value." Same rule the product-truth gate enforces for the app.

---

## 10. Adoption roadmap

1. **This guide** (done) — approved source of truth.
2. **Token-extension PR** — add §2 neon/glass/dark tokens to `ui/shared-ui/tokens.css` +
   `tailwind-preset.ts` (`darkMode: 'class'`), and to `mobile-design-system` tokens + fill the dark
   `ThemeProvider` values. Guarded by the existing token unit tests; web verified with `tsc`/build.
3. **Primitives** — `GlassSurface` + `glass`/`neon` variants + the mobile `useTheme()` migration
   (§7), with `useTier()` / web `.tier-*` gating (§6).
4. **Proof surface — Citizen app first**: activate dark theme, apply glass primitives, and ship the
   **floating SOS button** (§8) wired to `emergencyService.createSos` as the first tangible "wow" slice.
5. **Expand** — Provider triage hub + web admin/national command views (incl. maplibre 3D map).

**Verification per phase:** web — `tsc --noEmit` + `vitest` in `ui/one-ui-shell`/`ui/shared-ui` + a
build; mobile — token unit tests + structural review (Expo apps can't be built/run in this
environment). The SOS proof slice is verified on-device when built.

---

### Appendix — key files referenced

- Web tokens: `ui/shared-ui/tokens.css`, `ui/shared-ui/tailwind-preset.ts`, `ui/one-ui-shell/src/styles/globals.css`
- Web glass/motion refs: `ui/one-ui-shell/src/components/shell/ShellTaskbar.tsx`, `ui/one-ui-shell/src/components/auth/AuthHeroAfroFuturistBackground.tsx`
- Web map: `ui/one-ui-shell/src/components/ndila/*`, `ui/one-ui-shell/src/lib/ndila/build-ndila-map-style.ts`
- Mobile tokens/theme: `apps/mobile/packages/mobile-design-system/src/tokens/*`, `.../theme/ThemeProvider.tsx`
- Mobile FAB/motion refs: `.../components/NompiloLauncher.tsx`, `.../feedback/SkeletonLoader.tsx`
- Citizen SOS: `apps/mobile/citizen-app/src/navigation/AppNavigator.tsx`, `.../services/emergencyService.ts`, `.../screens/emergency/{SosScreen,TrackEmergencyScreen}.tsx`
