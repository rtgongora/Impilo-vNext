"use client";

import { useEffect } from "react";
import Link from "next/link";
import { MessageCircleMore } from "lucide-react";
import { useFindCareJourneyStore } from "@/hooks/useFindCareJourneyStore";

/**
 * Khuluma continuity indicator for a real guest journey.
 *
 * It appears only when the find-care store contains an active public journey and
 * never implies that guest chat is enabled. The link returns to the persisted
 * care search, where the person can continue from the same need and results.
 */
export function PublicKhulumaIndicator() {
  const hydrate = useFindCareJourneyStore((state) => state.hydrate);
  const hydrated = useFindCareJourneyStore((state) => state.hydrated);
  const need = useFindCareJourneyStore((state) => state.need);
  const serviceLabel = useFindCareJourneyStore((state) => state.serviceLabel);
  const selectedFacilityId = useFindCareJourneyStore((state) => state.selectedFacilityId);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  const activeLabel = serviceLabel || need || (selectedFacilityId ? "selected facility" : "");
  if (!hydrated || !activeLabel) return null;

  return (
    <Link
      href="/welcome/find-care"
      data-testid="public-khuluma-indicator"
      className="inline-flex min-h-10 items-center gap-2 rounded-full border border-violet-200 bg-violet-50 px-3 py-1.5 text-xs font-semibold text-violet-800 hover:bg-violet-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-600"
      aria-label={`Khuluma journey update: continue ${activeLabel}`}
    >
      <MessageCircleMore className="h-4 w-4" aria-hidden />
      <span className="hidden xl:inline">Khuluma · continue</span>
      <span className="max-w-32 truncate">{activeLabel}</span>
    </Link>
  );
}
