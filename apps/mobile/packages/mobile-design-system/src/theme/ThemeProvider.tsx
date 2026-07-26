/**
 * Theme Provider — Light/dark mode support with tenant-configurable accent color.
 *
 * Wraps the app root and provides theme context to all components.
 */

import React, { createContext, useContext, useState, useCallback, useMemo } from "react";
import { colors } from "../tokens/colors";

export type ThemeMode = "light" | "dark" | "system";

export interface Theme {
  mode: ThemeMode;
  resolvedMode: "light" | "dark";
  colors: {
    background: string;
    surface: string;
    surfaceVariant: string;
    text: string;
    textSecondary: string;
    textTertiary: string;
    border: string;
    divider: string;
    primary: string;
    primaryContainer: string;
    onPrimary: string;
    secondary: string;
    error: string;
    errorContainer: string;
    success: string;
    warning: string;
    info: string;
    overlay: string;
  };
  accentColor?: string;
}

export const lightTheme: Theme = {
  mode: "light",
  resolvedMode: "light",
  colors: {
    background: colors.neutral[0],
    surface: colors.neutral[0],
    surfaceVariant: colors.neutral[50],
    text: colors.neutral[900],
    textSecondary: colors.neutral[600],
    textTertiary: colors.neutral[500],
    border: colors.neutral[300],
    divider: colors.neutral[200],
    primary: colors.primary[600],
    primaryContainer: colors.primary[50],
    onPrimary: colors.neutral[0],
    secondary: colors.secondary[600],
    error: colors.error.main,
    errorContainer: colors.error.light,
    success: colors.success.main,
    warning: colors.warning.main,
    info: colors.info.main,
    overlay: "rgba(0, 0, 0, 0.5)",
  },
};

export const darkTheme: Theme = {
  mode: "dark",
  resolvedMode: "dark",
  colors: {
    background: colors.neutral[900],
    surface: colors.neutral[800],
    surfaceVariant: colors.neutral[700],
    text: colors.neutral[50],
    textSecondary: colors.neutral[400],
    textTertiary: colors.neutral[500],
    border: colors.neutral[600],
    divider: colors.neutral[700],
    primary: colors.primary[300],
    primaryContainer: colors.primary[900],
    onPrimary: colors.neutral[900],
    secondary: colors.secondary[300],
    error: colors.error.light,
    errorContainer: colors.error.dark,
    success: colors.success.light,
    warning: colors.warning.light,
    info: colors.info.light,
    overlay: "rgba(0, 0, 0, 0.7)",
  },
};

interface ThemeContextValue {
  theme: Theme;
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  setAccentColor: (color: string) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({
  children,
  defaultMode = "light",
  mode,
  accentColor,
}: {
  children: React.ReactNode;
  defaultMode?: ThemeMode;
  /**
   * Back-compat alias for `defaultMode` (older app roots).
   * Prefer `defaultMode`.
   */
  mode?: ThemeMode;
  accentColor?: string;
}) {
  const [modeState, setMode] = useState<ThemeMode>(mode ?? defaultMode);
  const [accent, setAccentColor] = useState<string | undefined>(accentColor);

  const resolvedMode = modeState === "system" ? "light" : modeState; // System detection delegated to native

  const theme = useMemo<Theme>(() => {
    const base = resolvedMode === "dark" ? darkTheme : lightTheme;
    if (accent) {
      return {
        ...base,
        accentColor: accent,
        colors: {
          ...base.colors,
          primary: accent,
          // Light tint of the accent for badge/chip containers, so an app's
          // brand color flows into every primary-tagged element, not just
          // solid-fill buttons. "1F" is ~12% alpha appended to a 6-digit hex.
          primaryContainer: `${accent}1F`,
          onPrimary: "#FFFFFF",
        },
      };
    }
    return base;
  }, [resolvedMode, accent]);

  const value = useMemo<ThemeContextValue>(
    () => ({ theme, mode: modeState, setMode, setAccentColor }),
    [theme, modeState]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error("useTheme must be used within a ThemeProvider");
  }
  return ctx;
}

const NOOP_SET_MODE: ThemeContextValue["setMode"] = () => {};
const NOOP_SET_ACCENT: ThemeContextValue["setAccentColor"] = () => {};

/**
 * Same shape as useTheme(), but falls back to lightTheme instead of throwing
 * when there is no ancestor ThemeProvider. Design-system PRIMITIVES (Button,
 * Badge, Card) use this rather than useTheme() so that:
 *   - inside an app (App.tsx mounts ThemeProvider) they pick up the real,
 *     possibly per-app accentColor, and
 *   - in isolated unit tests that render a component directly, with no
 *     provider in the tree, they degrade to the same default theme rather
 *     than crashing every test that touches a Button/Badge/Card.
 * Real screen-level consumers that need a hard guarantee of provider presence
 * should keep using useTheme().
 */
export function useOptionalTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (ctx) return ctx;
  return { theme: lightTheme, mode: "light", setMode: NOOP_SET_MODE, setAccentColor: NOOP_SET_ACCENT };
}
