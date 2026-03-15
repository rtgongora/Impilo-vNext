/**
 * Color Tokens — Impilo brand palette for mobile.
 *
 * Mirrors ui/shared-ui/tokens.css color variables.
 * Supports light and dark mode.
 */

export const colors = {
  // Primary brand
  primary: {
    50: "#E8F5E9",
    100: "#C8E6C9",
    200: "#A5D6A7",
    300: "#81C784",
    400: "#66BB6A",
    500: "#4CAF50",
    600: "#43A047",
    700: "#388E3C",
    800: "#2E7D32",
    900: "#1B5E20",
  },

  // Secondary / accent
  secondary: {
    50: "#E3F2FD",
    100: "#BBDEFB",
    200: "#90CAF9",
    300: "#64B5F6",
    400: "#42A5F5",
    500: "#2196F3",
    600: "#1E88E5",
    700: "#1976D2",
    800: "#1565C0",
    900: "#0D47A1",
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
  success: { light: "#E8F5E9", main: "#4CAF50", dark: "#2E7D32", contrast: "#FFFFFF" },
  warning: { light: "#FFF3E0", main: "#FF9800", dark: "#E65100", contrast: "#000000" },
  error: { light: "#FFEBEE", main: "#F44336", dark: "#C62828", contrast: "#FFFFFF" },
  info: { light: "#E3F2FD", main: "#2196F3", dark: "#1565C0", contrast: "#FFFFFF" },

  // Clinical
  clinical: {
    vitals: "#4CAF50",
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
