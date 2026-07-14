"use client";

/**
 * Facility profile completeness prompt — shown to the facility manager / PIC in
 * the facility-mode cockpit when governed fields (esp. geocodes) are missing.
 *
 * Display is gated on the user's management context (this banner only mounts on
 * manager surfaces such as the cockpit); the WRITE authz is enforced server-side
 * in TUSO fail-closed, so rendering the prompt never grants anything. Dismissible
 * per session (sessionStorage) — completeness is a nudge, not a gate.
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import { MapPin, X, ClipboardList } from "lucide-react";
import {
  useFacilityCompleteness,
  MISSING_FIELD_LABELS,
  type FacilityMissingField,
} from "@/hooks/queries/useFacilityCompleteness";

const DISMISS_KEY_PREFIX = "facility-completeness-dismissed:";

interface FacilityCompletenessBannerProps {
  facilityId: string | number;
  /**
   * Whether the viewer is operating in a facility-management context (cockpit /
   * facility mode). Display gate only — TUSO enforces the real authz on write.
   */
  managementContext?: boolean;
}

export function FacilityCompletenessBanner({
  facilityId,
  managementContext = true,
}: FacilityCompletenessBannerProps) {
  const { data, isLoading, isError } = useFacilityCompleteness(facilityId);
  const [dismissed, setDismissed] = useState(true); // start hidden until sessionStorage is read

  const dismissKey = `${DISMISS_KEY_PREFIX}${facilityId}`;

  useEffect(() => {
    try {
      setDismissed(window.sessionStorage.getItem(dismissKey) === "1");
    } catch {
      setDismissed(false);
    }
  }, [dismissKey]);

  if (!managementContext || isLoading || isError || !data || data.complete || dismissed) {
    return null;
  }

  const missing = (data.missingFields ?? []) as FacilityMissingField[];
  if (missing.length === 0) return null;
  const geocodeMissing = data.geocodeStatus === "MISSING";

  const dismiss = () => {
    setDismissed(true);
    try {
      window.sessionStorage.setItem(dismissKey, "1");
    } catch {
      // session-only nicety; ignore storage failures
    }
  };

  return (
    <div
      data-testid="facility-completeness-banner"
      className="mb-6 rounded-2xl border border-warning/35 bg-warning-soft/80 p-4"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className="rounded-xl bg-card/80 p-2 text-warning-foreground">
            {geocodeMissing ? <MapPin className="h-5 w-5" /> : <ClipboardList className="h-5 w-5" />}
          </div>
          <div>
            <p className="text-sm font-semibold text-warning-foreground">
              This facility&apos;s governed profile is incomplete
            </p>
            <p className="mt-1 text-sm text-warning-foreground">
              {geocodeMissing
                ? "Coordinates are missing — the facility cannot appear on map, routing, or nearest-facility surfaces until its geocode is captured."
                : "Registry consumers rely on these governed fields; please complete them."}
            </p>
            <div className="mt-3 flex flex-wrap gap-2 text-xs text-warning-foreground">
              {missing.map((field) => (
                <span
                  key={field}
                  data-testid={`missing-field-${field}`}
                  className="inline-flex items-center gap-1 rounded-full bg-card/70 px-2.5 py-1"
                >
                  {MISSING_FIELD_LABELS[field] ?? field}
                </span>
              ))}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Link
            href={`/facility/${facilityId}/complete-profile`}
            data-testid="facility-completeness-cta"
            className="inline-flex rounded-xl border border-amber-300 bg-card px-3 py-2 text-xs font-medium text-warning-foreground transition-colors hover:border-amber-400"
          >
            Complete profile
          </Link>
          <button
            type="button"
            aria-label="Dismiss completeness prompt for this session"
            data-testid="facility-completeness-dismiss"
            onClick={dismiss}
            className="rounded-xl p-2 text-warning-foreground transition-colors hover:bg-card/70"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
