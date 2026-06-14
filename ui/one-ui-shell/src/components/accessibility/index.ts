/**
 * Accessibility preferences live on the shell taskbar (`ShellAccessibilityMenu`).
 * Use `useAccessibilityPreferences` for shared state — do not add inline page strips.
 */
export { useAccessibilityPreferences, ACCESSIBILITY_TOGGLES } from "@/hooks/useAccessibilityPreferences";
export type { AccessibilityPreferences } from "@/hooks/useAccessibilityPreferences";
export { ShellAccessibilityMenu } from "@/components/shell/ShellAccessibilityMenu";
