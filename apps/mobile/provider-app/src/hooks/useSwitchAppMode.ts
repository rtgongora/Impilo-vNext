/**
 * Phase G3 (increment 2) — makes entering an app mode authority-bearing.
 *
 * Before this, ModeSwitcher flipped a local Zustand enum and nothing else: a
 * clinician tapping "Supervisor" kept their CLINICAL_CARE duty token, so at the
 * PDP they still held identified clinical access while the UI showed a
 * supervisory workspace. Mode was decorative.
 *
 * Now, entering a mode that maps to a governed WorkMode mints a token for that
 * mode first, and the local enum only moves if the mint succeeds. Combined with
 * the PDP's clinicalDataAccess gate (Phase B4), that is what makes "supervisor
 * grants no automatic patient access" structurally true rather than a label.
 *
 * `outreach`, `courier` and `offline` have no WorkMode analogue in the resolver,
 * so they remain local navigation choices — honestly, rather than being mapped
 * onto a governed mode that does not describe them.
 */
import { useCallback, useState } from "react";
import { useAuth } from "@impilo/mobile-auth";
import { appStore, useAppStore } from "../stores/appStore";
import { requiresWorkContextMint, selectContextForAppMode } from "../navigation/modeAvailability";
import { useSwitchWorkContext } from "./useSwitchWorkContext";
import type { AppMode } from "../types";

export function useSwitchAppMode() {
  const auth = useAuth();
  const { resolvedWorkContexts } = useAppStore();
  const { switching, error: switchError, switchWorkContext } = useSwitchWorkContext();
  const [modeError, setModeError] = useState<string | null>(null);

  const switchAppMode = useCallback(
    async (appMode: AppMode) => {
      setModeError(null);

      // No governed WorkMode describes this mode — local navigation only.
      if (!requiresWorkContextMint(appMode)) {
        appStore.getState().setMode(appMode);
        return true;
      }

      const selection = selectContextForAppMode(appMode, resolvedWorkContexts, auth.session?.workContextId);
      if (!selection) {
        setModeError("None of your current assignments grant this way of working.");
        return false;
      }

      // Mint FIRST; the visible mode only follows a granted token.
      const ok = await switchWorkContext(selection.context, selection.workMode);
      if (!ok) return false;

      appStore.getState().setMode(appMode);
      return true;
    },
    [auth.session?.workContextId, resolvedWorkContexts, switchWorkContext]
  );

  return { switching, error: modeError ?? switchError, switchAppMode };
}
