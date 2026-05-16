# Impilo UI Visual Design System

## Scope

This design refresh extends the existing One UI Shell implementation. It does not create a parallel UI framework, route tree, or contract model.

## Existing Primitives Reused

The implementation keeps the current composition model and upgrades shared shell-level surfaces:

- `src/components/AppLayout.tsx`
- `src/components/navigation/ExperienceSidebar.tsx`
- `src/components/navigation/RoleJourneyNavigation.tsx`
- `src/components/intelligent/NompiloGlobalCommandBar.tsx`
- `src/components/accessibility/AccessibilityToolbar.tsx`
- `src/components/PageShell.tsx`
- `src/features/core-transaction/components.tsx`
- `src/components/workflow/WorkflowHeader.tsx`
- `src/components/timeline/PatientTimeline.tsx`
- `src/components/payment/PaymentMethodPicker.tsx`
- `src/components/help/HelpMenu.tsx`
- `src/components/intelligence/IntelligenceResultPanel.tsx`
- `src/app/kiosk/page.tsx`

## Visual Language

- Rounded-by-default controls and cards.
- Warm healthcare surfaces (`background/surface/surface-soft/surface-warm`).
- Brand-led green primary actions.
- Controlled yellow/red usage for status and accents only.
- Nompilo visual identity in soft indigo/purple with restrained Impilo accent dots.

## Updated Surface Hierarchy

1. **App background**: soft, warm, low-noise gradients.
2. **Primary workspace card**: white, rounded, soft border + shadow.
3. **Context strips/chips**: soft semantic pills.
4. **Alerts/status**: semantic status chip families.
5. **Assistant surfaces**: Nompilo tone with companion contrast.

## Component Design Rules

- Buttons: pill/rounded-full where possible.
- Inputs: rounded-pill command + filter fields.
- Cards/panels: `rounded-3xl`, soft borders, layered spacing.
- Timelines: rounded event cards with semantic status markers.
- Sidebars/nav: lighter, warm-surface navigation with clear active affordance.
- Kiosk: larger rounded controls and strong hierarchy for assisted use.

## Visual Debt Still Present

- Some page-level components still use direct `gray/slate/*` classes.
- `shared-ui/tokens.css` and shell runtime tokens still need one final canonical merge path.
- Some deep route pages remain functional but not yet migrated to utility token classes.
