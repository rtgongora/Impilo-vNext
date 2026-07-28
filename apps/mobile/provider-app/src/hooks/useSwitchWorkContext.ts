/**
 * Phase G4 — mobile's context switch, the counterpart of web's
 * `useSwitchWorkContext`. Mints a fresh duty token for the target context and
 * repoints the app's context state at it.
 *
 * Web clears the whole react-query cache and hard-navigates
 * (`window.location.assign`) so no pre-switch data can survive. Mobile has no
 * page reload to lean on, so the equivalent guarantee is made explicitly here:
 * the previous token is passed as `previousJti` (the BFF revokes it), the
 * work-context token is cleared BEFORE the mint so a failure can never leave
 * the old facility's token in place, and facility/workspace state is
 * overwritten from the newly proven context rather than kept from the old one.
 *
 * Offline-cache partitioning is enforced via contextCachePartition: the switch
 * is refused while anything is unsynced, because the only available eviction
 * is all-or-nothing and would otherwise destroy the person's queued work.
 */
import { useCallback, useState } from "react";
import { useAuth } from "@impilo/mobile-auth";
import type { ResolvedWorkContextView } from "@impilo/mobile-trust";
import { appStore } from "../stores/appStore";
import { mintWorkContextSession } from "../services/workContextService";
import { canPartitionSafely, clearContextScopedCaches } from "./contextCachePartition";

export type SwitchState = { switching: boolean; error: string | null };

export function useSwitchWorkContext() {
  const auth = useAuth();
  const [state, setState] = useState<SwitchState>({ switching: false, error: null });

  const switchWorkContext = useCallback(
    async (context: ResolvedWorkContextView, workMode?: string) => {
      const targetMode = workMode ?? context.defaultMode ?? context.availableModes[0];
      if (!targetMode) {
        setState({ switching: false, error: "This workplace has no available work mode." });
        return false;
      }

      setState({ switching: true, error: null });

      // Checked BEFORE anything is torn down: refusing must leave the current
      // session exactly as it was, still able to sync the outstanding work.
      const partition = await canPartitionSafely();
      if (!partition.safe) {
        setState({ switching: false, error: partition.reason });
        return false;
      }

      const previousJti = auth.session?.workContextJti;

      // Cleared BEFORE the mint: if the mint fails, the app must not keep
      // sending the previous context's duty token on subsequent requests.
      auth.clearWorkContext();

      try {
        const minted = await mintWorkContextSession(context.contextId, targetMode, previousJti);
        if (!minted.token || !minted.jti) {
          setState({ switching: false, error: "Could not start a session for this workplace." });
          return false;
        }

        auth.setWorkContext({
          token: minted.token,
          jti: minted.jti,
          expiresAt: minted.expiresAt ? Date.parse(minted.expiresAt) : Date.now() + 15 * 60_000,
          contextId: minted.contextId ?? context.contextId,
          workMode: minted.workMode ?? targetMode,
        });

        // Nothing from the previous context may remain readable offline.
        await clearContextScopedCaches();

        // Context state follows the PROVEN context, never the pre-switch values.
        const store = appStore.getState();
        store.clearContext();
        if (context.facilityId) {
          store.setFacilityContext(context.facilityId, context.label);
          auth.setFacility?.(context.facilityId, context.label);
        }

        setState({ switching: false, error: null });
        return true;
      } catch {
        setState({ switching: false, error: "Could not switch workplace. Please try again." });
        return false;
      }
    },
    [auth]
  );

  return { ...state, switchWorkContext };
}
