/**
 * Typography Tokens — Font sizes, weights, and line heights.
 */

export const typography = {
  fontFamily: {
    sans: "System",
    mono: "Courier",
  },

  fontSize: {
    xs: 11,
    sm: 13,
    base: 15,
    md: 17,
    lg: 20,
    xl: 24,
    xxl: 30,
    xxxl: 36,
    display: 48,
  },

  fontWeight: {
    regular: "400" as const,
    medium: "500" as const,
    semibold: "600" as const,
    bold: "700" as const,
  },

  lineHeight: {
    tight: 1.2,
    normal: 1.5,
    relaxed: 1.75,
  },
} as const;

export const textStyles = {
  displayLarge: { fontSize: typography.fontSize.display, fontWeight: typography.fontWeight.bold, lineHeight: typography.lineHeight.tight },
  displayMedium: { fontSize: typography.fontSize.xxxl, fontWeight: typography.fontWeight.bold, lineHeight: typography.lineHeight.tight },
  headlineLarge: { fontSize: typography.fontSize.xxl, fontWeight: typography.fontWeight.semibold, lineHeight: typography.lineHeight.tight },
  headlineMedium: { fontSize: typography.fontSize.xl, fontWeight: typography.fontWeight.semibold, lineHeight: typography.lineHeight.tight },
  headlineSmall: { fontSize: typography.fontSize.lg, fontWeight: typography.fontWeight.semibold, lineHeight: typography.lineHeight.normal },
  titleLarge: { fontSize: typography.fontSize.md, fontWeight: typography.fontWeight.semibold, lineHeight: typography.lineHeight.normal },
  titleMedium: { fontSize: typography.fontSize.base, fontWeight: typography.fontWeight.medium, lineHeight: typography.lineHeight.normal },
  titleSmall: { fontSize: typography.fontSize.sm, fontWeight: typography.fontWeight.medium, lineHeight: typography.lineHeight.normal },
  bodyLarge: { fontSize: typography.fontSize.base, fontWeight: typography.fontWeight.regular, lineHeight: typography.lineHeight.normal },
  bodyMedium: { fontSize: typography.fontSize.sm, fontWeight: typography.fontWeight.regular, lineHeight: typography.lineHeight.normal },
  bodySmall: { fontSize: typography.fontSize.xs, fontWeight: typography.fontWeight.regular, lineHeight: typography.lineHeight.normal },
  labelLarge: { fontSize: typography.fontSize.sm, fontWeight: typography.fontWeight.semibold, lineHeight: typography.lineHeight.tight },
  labelMedium: { fontSize: typography.fontSize.xs, fontWeight: typography.fontWeight.medium, lineHeight: typography.lineHeight.tight },
  caption: { fontSize: typography.fontSize.xs, fontWeight: typography.fontWeight.regular, lineHeight: typography.lineHeight.normal },
} as const;

export type TextStyle = keyof typeof textStyles;
