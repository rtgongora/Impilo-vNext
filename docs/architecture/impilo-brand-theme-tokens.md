# Impilo Brand Theme Tokens

## Purpose

This document defines the canonical visual token layer for `ui/one-ui-shell` so design updates remain additive to the existing platform, routes, contracts, and component architecture.

## Current Theme Sources Inspected

- `ui/one-ui-shell/tailwind.config.ts`
- `ui/one-ui-shell/src/styles/globals.css`
- `ui/shared-ui/tokens.css`

## Token Consolidation Decision

`one-ui-shell` now treats `src/styles/globals.css` as the runtime source for brand + semantic tokens and maps Tailwind keys to those CSS variables. This avoids parallel token drift in feature components.

## Core Brand Tokens

- `--impilo-green: #009739`
- `--impilo-green-soft-logo: #0F9848`
- `--impilo-yellow: #FCE300`
- `--impilo-red: #EF3340`
- `--impilo-charcoal: #221F20`

## Semantic Tokens

- Surfaces:
  - `--background`, `--surface`, `--surface-soft`, `--surface-warm`
- Brand actions:
  - `--primary`, `--primary-hover`, `--primary-soft`, `--primary-muted`
- Accents:
  - `--accent-yellow`, `--accent-yellow-soft`, `--accent-red`, `--accent-red-soft`
- Text/border:
  - `--text-primary`, `--text-secondary`, `--text-muted`
  - `--border-soft`, `--border-strong`
- States:
  - `--info`, `--info-soft`, `--warning`, `--warning-soft`
  - `--success`, `--success-soft`, `--danger`, `--danger-soft`
- Assistant:
  - `--nompilo`, `--nompilo-soft`

## Tailwind Exposure

Tailwind now exposes:

- Brand namespace: `impilo.green`, `impilo.greenSoft`, `impilo.yellow`, `impilo.yellowSoft`, `impilo.red`, `impilo.redSoft`, `impilo.charcoal`, `impilo.surface`, `impilo.background`, `impilo.border`, `impilo.nompilo`, `impilo.nompiloSoft`
- Semantic aliases: `background`, `foreground`, `card`, `border`, `primary`, `success`, `warning`, `danger`, `muted`, `assistant`

## Radius and Elevation

- Rounded scale aligned to design language:
  - `sm 0.5rem`, `md 0.875rem`, `lg 1.25rem`, `xl 1.5rem`, `2xl 1.75rem`, `3xl 2rem`
- Shadows:
  - `--shadow-soft`
  - `--shadow-card`
  - `--shadow-floating`
  - `--shadow-nompilo`

## Component Utility Classes

Added reusable utility classes in `globals.css`:

- Surface:
  - `.impilo-surface-card`
  - `.impilo-surface-soft`
  - `.impilo-surface-warm`
- Controls:
  - `.impilo-pill-input`
  - `.impilo-btn-primary`
  - `.impilo-btn-secondary`
  - `.impilo-chip`
- Status:
  - `.impilo-status-success`
  - `.impilo-status-warning`
  - `.impilo-status-danger`
  - `.impilo-status-info`

## Migration Guidance

1. Prefer semantic tokens and utility classes over hard-coded color classes.
2. Keep high-risk clinical states explicit with semantic status chips.
3. Only use direct brand accents for focused emphasis (actions, tiny dots, badges).
4. Avoid large yellow/red backgrounds and white-on-yellow text.
