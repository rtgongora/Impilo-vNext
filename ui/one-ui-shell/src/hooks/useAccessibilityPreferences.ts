"use client";

import { useEffect, useMemo, useState } from "react";
import { announce } from "@/lib/accessibility";

const STORAGE_KEY = "impilo:a11y-toolbar";

export interface AccessibilityPreferences {
  simpleLanguage: boolean;
  highContrast: boolean;
  largeText: boolean;
  lowBandwidth: boolean;
}

export const ACCESSIBILITY_TOGGLES: Array<{ key: keyof AccessibilityPreferences; label: string }> = [
  { key: "simpleLanguage", label: "Simple Language" },
  { key: "highContrast", label: "High Contrast" },
  { key: "largeText", label: "Large Text" },
  { key: "lowBandwidth", label: "Low Bandwidth" },
];

function applyFlags(state: AccessibilityPreferences) {
  const root = document.documentElement;
  root.classList.toggle("impilo-high-contrast", state.highContrast);
  root.classList.toggle("impilo-large-text", state.largeText);
  root.classList.toggle("impilo-low-bandwidth", state.lowBandwidth);
}

export function useAccessibilityPreferences() {
  const [state, setState] = useState<AccessibilityPreferences>({
    simpleLanguage: false,
    highContrast: false,
    largeText: false,
    lowBandwidth: false,
  });

  useEffect(() => {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      if (!stored) return;
      const parsed = JSON.parse(stored) as AccessibilityPreferences;
      setState(parsed);
      applyFlags(parsed);
    } catch {
      // Ignore invalid stored data.
    }
  }, []);

  useEffect(() => {
    applyFlags(state);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }, [state]);

  function toggle(key: keyof AccessibilityPreferences) {
    setState((previous) => {
      const next = { ...previous, [key]: !previous[key] };
      const label = ACCESSIBILITY_TOGGLES.find((item) => item.key === key)?.label ?? key;
      announce(`${label} ${next[key] ? "enabled" : "disabled"}`);
      return next;
    });
  }

  const toggles = useMemo(() => ACCESSIBILITY_TOGGLES, []);

  return { state, toggles, toggle };
}
