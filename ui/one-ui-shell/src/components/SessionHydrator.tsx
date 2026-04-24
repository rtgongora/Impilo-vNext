"use client";

import { useEffect } from "react";

import { useSession } from "@/hooks/useSession";
import { useWorkContext } from "@/hooks/useContext";
import { loadPersistedShellSession } from "@/lib/auth/sessionStorage";

/**
 * Restores OIDC-backed session + work context after refresh (sessionStorage).
 */
export function SessionHydrator() {
  useEffect(() => {
    const persisted = loadPersistedShellSession();
    if (!persisted) return;

    useSession.getState().setSession(persisted.session);
    useWorkContext.getState().setWorkContext(persisted.workContext);
    useWorkContext.getState().setPurposeOfUse(persisted.purposeOfUse);
  }, []);

  return null;
}
