/**
 * Spacing Tokens — Consistent spacing scale for mobile layouts.
 *
 * Based on 4px grid system.
 */

export const spacing = {
  /** 0px */
  none: 0,
  /** 2px */
  xxs: 2,
  /** 4px */
  xs: 4,
  /** 8px */
  sm: 8,
  /** 12px */
  md: 12,
  /** 16px */
  lg: 16,
  /** 20px */
  xl: 20,
  /** 24px */
  xxl: 24,
  /** 32px */
  xxxl: 32,
  /** 40px */
  huge: 40,
  /** 48px */
  massive: 48,
  /** 64px */
  giant: 64,
} as const;

export type SpacingToken = keyof typeof spacing;
