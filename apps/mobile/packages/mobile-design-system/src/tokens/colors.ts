/**
 * Color Tokens — Impilo brand palette for mobile.
 *
 * Mirrors ui/shared-ui/tokens.css color variables.
 * Supports light and dark mode.
 */

export const colors = {
  // Primary brand
  primary: {
    50: "#E6F5EC",
    100: "#CDEDD9",
    200: "#9DDBB6",
    300: "#6CC992",
    400: "#33B56A",
    500: "#009739",
    600: "#008A34",
    700: "#006F2A",
    800: "#005420",
    900: "#003A16",
  },

  // Secondary / accent
  secondary: {
    50: "#FDEAEC",
    100: "#FAD0D5",
    200: "#F5A6AE",
    300: "#F07C88",
    400: "#EB5262",
    500: "#EF3340",
    600: "#D62E39",
    700: "#AB252D",
    800: "#801C22",
    900: "#551316",
  },

  // Neutral / gray
  neutral: {
    0: "#FFFFFF",
    50: "#FAFAFA",
    100: "#F5F5F5",
    200: "#EEEEEE",
    300: "#E0E0E0",
    400: "#BDBDBD",
    500: "#9E9E9E",
    600: "#757575",
    700: "#616161",
    800: "#424242",
    900: "#212121",
    1000: "#000000",
  },

  // Semantic
  success: { light: "#E6F5EC", main: "#009739", dark: "#005420", contrast: "#FFFFFF" },
  warning: { light: "#FFFADB", main: "#FCE300", dark: "#D0B800", contrast: "#231F20" },
  error: { light: "#FFEBEE", main: "#F44336", dark: "#C62828", contrast: "#FFFFFF" },
  info: { light: "#E3F2FD", main: "#2196F3", dark: "#1565C0", contrast: "#FFFFFF" },

  // Gray scale (Tailwind-compatible), ADDITIVE to `neutral` above.
  //
  // `neutral` is a Material-style gray that NOTHING in apps/mobile actually
  // renders. A 2026-07-26 audit of ~218 screen files found the real,
  // consistently-used gray is Tailwind's default scale — every value below
  // was extracted from actual hardcoded literals in the codebase, not
  // invented. `gray` exists so token adoption can be a PURE refactor (name an
  // already-used value) rather than a silent repaint (swap it for a value
  // that merely looks similar). Do not "consolidate" this into `neutral` —
  // the values are genuinely different grays, and doing so would recolor
  // every screen in the product without anyone deciding to.
  gray: {
    50: "#F9FAFB",
    100: "#F3F4F6",
    200: "#E5E7EB",
    300: "#D1D5DB",
    400: "#9CA3AF",
    500: "#6B7280",
    600: "#4B5563",
    700: "#374151",
    800: "#1F2937",
    900: "#111827",
  },

  /**
   * UI semantic colors ADDITIVE to `success`/`warning`/`error`/`info` above.
   *
   * Those four exist in this file but are used almost nowhere — the same
   * 2026-07-26 audit found every screen's actual warning/error/success color
   * is a DIFFERENT, Tailwind-derived hex (e.g. warning.main here is #FCE300,
   * a near-neon yellow; every screen that renders a warning uses #F59E0B, a
   * muted amber — visibly different colors). `ui` records what is actually
   * on screen today, cross-checked against Badge.tsx's own inline palette
   * (light/text pairs match exactly). Token adoption must preserve these
   * values, not silently repaint every alert/badge/status chip in the
   * product onto the unused palette above.
   */
  ui: {
    error:   { main: "#DC2626", light: "#FEE2E2", text: "#991B1B" },
    warning: { main: "#F59E0B", light: "#FEF3C7", text: "#92400E" },
    success: { main: "#22C55E", light: "#D1FAE5", text: "#065F46" },
    info:    { main: "#2563EB", light: "#DBEAFE", text: "#1E40AF" },
  },

  // Clinical
  clinical: {
    vitals: "#009739",
    diagnosis: "#FF9800",
    prescription: "#2196F3",
    labResult: "#9C27B0",
    imaging: "#00BCD4",
    referral: "#795548",
    admission: "#F44336",
    discharge: "#8BC34A",
  },
} as const;

export type ColorToken = keyof typeof colors;
