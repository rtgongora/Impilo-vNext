"use client";

import { ShieldAlert } from "lucide-react";
import { FacilityLocationCapture } from "@/components/maps/FacilityLocationCapture";
import { useAuthStore } from "@/hooks/useAuthStore";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";

const LOCATION_ALLOWED_ROLES = new Set([
  "SYSTEM_ADMIN", "PROVIDER", "FACILITY_ADMIN", "OPERATIONS",
  "NATIONAL_ADMIN", "DISTRICT_ADMIN", "COMMUNITY_HEALTH_WORKER", "CHW",
]);
const LOCATION_REGISTRATION_TYPES = new Set([
  "OUTREACH_REGISTRATION",
  "COMMUNITY_REGISTRATION",
  "FACILITY_REGISTRATION",
]);

interface VitoRestrictedLocationCaptureProps {
  registrationType?: string;
  value?: NdilaCoordinate | null;
  onChange?: (next: NdilaCoordinate | null) => void;
}

/**
 * Vito registration location capture — gated by role and registration type.
 * Geocoding uses purpose IDENTITY_REGISTRATION (Tshepo RESTRICTED ops purpose).
 */
export function VitoRestrictedLocationCapture({
  registrationType,
  value,
  onChange,
}: VitoRestrictedLocationCaptureProps) {
  const roles = useAuthStore((s) => s.user?.roles ?? []);
  const hasRole = roles.some((role) => LOCATION_ALLOWED_ROLES.has(role));
  const typeAllowed = registrationType
    ? LOCATION_REGISTRATION_TYPES.has(registrationType)
    : false;

  if (!hasRole) {
    return (
      <section className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        <div className="flex items-center gap-2 font-medium">
          <ShieldAlert className="h-4 w-4" />
          Location capture restricted
        </div>
        <p className="mt-2 text-xs">
          Ndila coordinate capture for Vito registration requires registry operator or health-worker role with
          explicit Tshepo authorization.
        </p>
      </section>
    );
  }

  if (!typeAllowed) {
    return (
      <p className="text-xs text-slate-500">
        Location capture is available for outreach, community, and facility registration types only.
      </p>
    );
  }

  return (
    <FacilityLocationCapture
      value={value}
      onChange={onChange}
      label="Registration location (Vito / Ndila — Tshepo-gated)"
      purposeOfUse="IDENTITY_REGISTRATION"
    />
  );
}
