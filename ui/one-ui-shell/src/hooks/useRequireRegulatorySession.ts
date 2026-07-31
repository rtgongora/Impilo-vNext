"use client";

/**
 * Redirects to /work/regulatory when there is no minted regulatory duty session
 * for the requested organisation.
 */

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useWorkSessionStore } from "@/hooks/useWorkSessionStore";

export function useRequireRegulatorySession(orgId: string): boolean {
  const router = useRouter();
  const session = useWorkSessionStore((s) => s.session);
  const ok =
    !!session?.token &&
    session.workMode === "REGULATORY_OPERATIONS" &&
    (!!session.organisationId ? session.organisationId === orgId : true);

  useEffect(() => {
    if (!orgId) return;
    if (!session?.token || session.workMode !== "REGULATORY_OPERATIONS") {
      router.replace("/work/regulatory");
      return;
    }
    if (session.organisationId && session.organisationId !== orgId) {
      router.replace("/work/regulatory");
    }
  }, [orgId, router, session]);

  return ok;
}
