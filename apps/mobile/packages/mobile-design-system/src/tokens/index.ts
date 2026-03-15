/**
 * Design Tokens — Re-exports all token categories.
 */

export { colors } from "./colors";
export type { ColorToken } from "./colors";

export { spacing } from "./spacing";
export type { SpacingToken } from "./spacing";

export { typography, textStyles } from "./typography";
export type { TextStyle } from "./typography";

export const borderRadius = {
  none: 0,
  sm: 4,
  md: 8,
  lg: 12,
  xl: 16,
  full: 9999,
} as const;

export const shadows = {
  none: { shadowColor: "transparent", shadowOffset: { width: 0, height: 0 }, shadowOpacity: 0, shadowRadius: 0, elevation: 0 },
  sm: { shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 2, elevation: 1 },
  md: { shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.1, shadowRadius: 4, elevation: 3 },
  lg: { shadowColor: "#000", shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.15, shadowRadius: 8, elevation: 6 },
  xl: { shadowColor: "#000", shadowOffset: { width: 0, height: 8 }, shadowOpacity: 0.2, shadowRadius: 16, elevation: 12 },
} as const;

export const tokens = {
  colors,
  spacing,
  typography,
  textStyles,
  borderRadius,
  shadows,
} as const;
