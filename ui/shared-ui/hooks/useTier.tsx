"use client";

import React, { createContext, useContext, useEffect, useState } from "react";

/**
 * Progressive-enhancement device/preference tier (see the Future-Realism style guide §6).
 * - `baseline`   — solid surfaces, no backdrop blur, essential motion only, no 3D. Always AA.
 * - `enhanced`   — glass blur (soft), expressive motion, glow accents.
 * - `cinematic`  — strong blur, maplibre 3D, hologram/particle effects.
 *
 * Dazzle NEVER blocks function: a device that can't do it gets `baseline`, not a broken scene.
 */
export type Tier = "baseline" | "enhanced" | "cinematic";

const TierContext = createContext<Tier>("enhanced");

/** Read the resolved tier. Components must gate dazzle on this — never on hardware directly. */
export function useTier(): Tier {
  return useContext(TierContext);
}

type NavConn = { saveData?: boolean; effectiveType?: string };
type NavExtras = Navigator & { connection?: NavConn; deviceMemory?: number };

export function resolveTier(): Tier {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return "enhanced";
  const mq = (q: string) => window.matchMedia(q).matches;
  const nav = navigator as NavExtras;
  const conn = nav.connection;

  const reducedMotion = mq("(prefers-reduced-motion: reduce)");
  const reducedTransparency = mq("(prefers-reduced-transparency: reduce)");
  const saveData = conn?.saveData === true;
  const slowNet = typeof conn?.effectiveType === "string" && /(^|\b)(slow-2g|2g)$/.test(conn.effectiveType);
  const lowMem = typeof nav.deviceMemory === "number" && nav.deviceMemory <= 2;

  // Any accessibility, bandwidth, or memory constraint → the always-shippable accessible baseline.
  if (reducedMotion || reducedTransparency || saveData || slowNet || lowMem) return "baseline";

  const highMem = (nav.deviceMemory ?? 4) >= 8;
  const fastNet = !conn?.effectiveType || conn.effectiveType === "4g";
  return highMem && fastNet ? "cinematic" : "enhanced";
}

/**
 * Client provider: resolves the tier and mirrors it onto <html> as `tier-*` (and `low-blur` for
 * baseline) so token/CSS fallbacks engage, and exposes it via context. SSR-safe (defaults to
 * `enhanced`, refines on mount).
 */
export function TierProvider({ children }: { children: React.ReactNode }) {
  const [tier, setTier] = useState<Tier>("enhanced");

  useEffect(() => {
    const apply = () => setTier(resolveTier());
    apply();
    const queries = ["(prefers-reduced-motion: reduce)", "(prefers-reduced-transparency: reduce)"];
    const mqls = queries.map((q) => window.matchMedia(q));
    mqls.forEach((m) => m.addEventListener?.("change", apply));
    return () => mqls.forEach((m) => m.removeEventListener?.("change", apply));
  }, []);

  useEffect(() => {
    const el = document.documentElement;
    el.classList.remove("tier-baseline", "tier-enhanced", "tier-cinematic", "low-blur");
    el.classList.add(`tier-${tier}`);
    if (tier === "baseline") el.classList.add("low-blur");
  }, [tier]);

  return <TierContext.Provider value={tier}>{children}</TierContext.Provider>;
}
