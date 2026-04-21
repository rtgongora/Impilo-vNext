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
