# Impilo Rounded Healthcare Design Language

## Why Rounded

Impilo surfaces must reduce institutional coldness while preserving clinical clarity, trust, and speed.

## Radius Rules

- Cards/panels: `rounded-3xl`
- Inputs/command bars: rounded-pill or `rounded-2xl`
- Buttons/chips: `rounded-full`
- Kiosk primary actions: large rounded (`rounded-3xl`)
- Modals/sheets: `rounded-3xl`

## Elevation Rules

- Use soft green-tinted elevation:
  - card: `--shadow-card`
  - floating elements: `--shadow-floating`
  - Nompilo: `--shadow-nompilo`
- Avoid hard black shadows and noisy glass effects.

## Density Rules

- Provider workflows may be denser, but not compressed.
- Client/wellness surfaces should be more spacious and warm.
- Manager/operations dashboards should be data-rich but still grouped with rounded containers.

## Updated Areas

- App shell header strip + role navigation.
- Sidebar shell and active item treatment.
- Nompilo command bar.
- Accessibility toolbar.
- Page shell.
- Core transaction card system.
- Timeline cards and status chips.
- Kiosk interactions.

## Remaining Gaps

- Additional route-level migrations needed for long-tail pages still using legacy utility classes.
- Future pass should align table-heavy screens to rounded wrappers + semantic row states.
