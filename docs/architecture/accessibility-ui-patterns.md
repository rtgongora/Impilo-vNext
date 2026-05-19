# Accessibility UI Patterns

## Added Pattern

- `AccessibilityToolbar` in `ui/one-ui-shell/src/components/accessibility/AccessibilityToolbar.tsx`.
- Mounted in `AppLayout` near Nompilo command bar and role navigation.

## Controls

- Simple Language toggle.
- High Contrast toggle.
- Large Text toggle.
- Low Bandwidth toggle.
- Read Aloud action.
- Keyboard hint shortcut.
- Caregiver assist entry point.

## Technical Notes

- Uses `lib/accessibility.ts` announcements.
- Persists choices in session storage.
- Applies CSS classes at document root:
  - `impilo-high-contrast`
  - `impilo-large-text`
  - `impilo-low-bandwidth`

