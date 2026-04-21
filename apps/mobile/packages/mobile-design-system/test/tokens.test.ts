import { describe, it, expect } from "vitest";
import { colors, spacing, typography, textStyles, borderRadius, shadows, tokens } from "../src/tokens/index";

describe("Design Tokens", () => {
  it("colors has primary palette with 10 shades", () => {
    expect(Object.keys(colors.primary)).toHaveLength(10);
    expect(colors.primary[500]).toBe("#009739");
  });

  it("colors has semantic colors", () => {
    expect(colors.success.main).toBeTruthy();
    expect(colors.warning.main).toBeTruthy();
    expect(colors.error.main).toBeTruthy();
    expect(colors.info.main).toBeTruthy();
  });

  it("colors has clinical colors", () => {
    expect(colors.clinical.vitals).toBeTruthy();
    expect(colors.clinical.diagnosis).toBeTruthy();
    expect(colors.clinical.prescription).toBeTruthy();
    expect(colors.clinical.labResult).toBeTruthy();
  });

  it("spacing follows 4px grid", () => {
    expect(spacing.xs).toBe(4);
    expect(spacing.sm).toBe(8);
    expect(spacing.md).toBe(12);
    expect(spacing.lg).toBe(16);
  });

  it("typography has font sizes", () => {
    expect(typography.fontSize.base).toBe(15);
    expect(typography.fontSize.sm).toBe(13);
  });

  it("textStyles has standard text style definitions", () => {
    expect(textStyles.headlineLarge.fontSize).toBe(typography.fontSize.xxl);
    expect(textStyles.bodyLarge.fontSize).toBe(typography.fontSize.base);
  });

  it("borderRadius has progressive values", () => {
    expect(borderRadius.none).toBe(0);
    expect(borderRadius.sm).toBeLessThan(borderRadius.md);
    expect(borderRadius.md).toBeLessThan(borderRadius.lg);
    expect(borderRadius.full).toBe(9999);
  });

  it("shadows has elevation levels", () => {
    expect(shadows.none.elevation).toBe(0);
    expect(shadows.sm.elevation).toBeLessThan(shadows.md.elevation);
    expect(shadows.lg.elevation).toBeLessThan(shadows.xl.elevation);
  });

  it("tokens aggregates all token categories", () => {
    expect(tokens.colors).toBe(colors);
    expect(tokens.spacing).toBe(spacing);
    expect(tokens.typography).toBe(typography);
  });
});
