"use client";

/**
 * /provider-workspace — intent-resolution shim (Phase F6).
 *
 * The real content that used to live on this page has been decomposed: the composed clinical
 * worklist is Work Home's own `clinical-worklist` BFF section (Phase E1/E4, duplicating it here
 * would have shown the same data twice), and the core-transaction journey + workflow/dispatch
 * operator telemetry moved to WorkOperationsPanel on /work (Phase F6), shown for the
 * facility-clinical family.
 *
 * This page now only resolves intent and redirects — it renders nothing of its own. No caller
 * in this codebase passes `?tab=...` today (verified before this change: `/provider-workspace`
 * was always linked bare), so the default (no `tab` param) goes straight to /work, matching
 * this route's historical behaviour. The `tab`/`section` handling exists for forward
 * compatibility with any future or external deep link built against the documented scheme.
 */

import { useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";

function resolveShimTarget(searchParams: URLSearchParams): string {
  const tab = searchParams.get("tab");
  const section = searchParams.get("section");

  const forwarded = new URLSearchParams(searchParams);
  forwarded.delete("tab");
  forwarded.delete("section");
  const forwardedQuery = forwarded.toString();

  if (tab === "professional") {
    const base = section ? `/professional/${section}` : "/professional";
    return forwardedQuery ? `${base}?${forwardedQuery}` : base;
  }

  // Default (including tab === "work" or no tab at all): /work, preserving any other intent
  // (e.g. facilityId, queue) as query params — /provider-workspace was always the "work" tab.
  return forwardedQuery ? `/work?${forwardedQuery}` : "/work";
}

export default function ProviderWorkspaceShimPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    router.replace(resolveShimTarget(searchParams));
  }, [router, searchParams]);

  return null;
}
