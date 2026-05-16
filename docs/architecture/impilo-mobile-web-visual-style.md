# Impilo Mobile + Web Visual Style

## Principle

Web and mobile should share visual identity and semantic states while preserving context-specific density and ergonomics.

## Web Style

- Warm background with rounded white work surfaces.
- Role-aware navigation and contextual chips in shell.
- Nompilo command bar as global command/search/voice entry.
- Core transaction states shown with semantic status chips.

## Mobile Style Direction

The current web shell and mobile-facing flows are aligned through component styling patterns:

- Large touch targets (`rounded-2xl` to `rounded-3xl`).
- Pill chips and action buttons.
- Compact but legible status badges.
- Voice-friendly command interactions via Nompilo and dictation controls.

## Kiosk and Assisted Desk

`/kiosk` was updated with:

- clearer visual hierarchy;
- larger rounded action controls;
- stronger consent readability;
- calm high-trust color palette;
- warning/success emphasis using semantic colors.

## Responsive Guidance

- Do not squeeze desktop structures into mobile.
- Keep horizontal complexity in side panels optional/collapsible.
- Prioritize card stacking, single-column reading flow, and action clustering.

## Accessibility in Mobile/Web

- Existing accessibility toolbar toggles preserved and visually improved.
- Tokenized contrast-safe color choices for labels, warnings, and state chips.
- Reduced motion + large text + high contrast classes retained.
