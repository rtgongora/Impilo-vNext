"use client";

/**
 * PatientLocationBadge -- Compact badge showing the patient's current
 * ward / bed / care-point location. Color-coded by care type.
 *
 * For an admitted patient the ward + bed are read LIVE from the inpatient-service
 * system-of-record (active admission, resolved labels) via `usePatientLocation` (G053).
 * The URL search params remain a fallback for non-inpatient encounter contexts and for
 * patients with no active admission.
 */

import { useParams, useSearchParams } from "next/navigation";
import {
  MapPin,
  Bed,
  Users,
  Video,
  Globe,
  Building2,
} from "lucide-react";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { usePatientLocation } from "@/hooks/queries/useInpatient";

// ---------------------------------------------------------------------------
// Variant styles
// ---------------------------------------------------------------------------
const VARIANT_STYLES = {
  default: "bg-primary-soft text-primary border-primary/25",
  secondary: "bg-neutral-100 text-foreground border-border",
  destructive: "bg-danger-soft text-danger border-danger/28",
  outline: "bg-card text-muted-foreground border-border",
} as const;

type BadgeVariant = keyof typeof VARIANT_STYLES;

interface LocationInfo {
  icon: React.ReactNode;
  label: string;
  detail?: string;
  variant: BadgeVariant;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function PatientLocationBadge() {
  const params = useParams();
  const searchParams = useSearchParams();
  const patientId = params?.patientId as string | undefined;
  const { facility } = useFacilityStore();
  // Live inpatient location from the SoR (no-op until a patient context exists).
  const locationQuery = usePatientLocation(patientId ?? null, facility?.id ?? null);
  const liveLocation = locationQuery.data?.data ?? null;

  // Only show location when there is an active patient context
  if (!patientId) return null;

  const encounterType = searchParams.get("encounterType") as
    | "outpatient"
    | "inpatient"
    | "emergency"
    | "community"
    | "virtual"
    | null;
  const source = searchParams.get("source") as
    | "queue"
    | "referral"
    | "walk-in"
    | null;
  const ward = searchParams.get("ward");
  const bed = searchParams.get("bed");
  const queueId = searchParams.get("queueId");
  const queueName = searchParams.get("queueName");

  // If no encounter context is available, show nothing rather than fake data.
  // A live active admission is itself sufficient context.
  if (!encounterType && !queueId && !facility && !liveLocation?.wardName) return null;

  const getLocationInfo = (): LocationInfo => {
    // Live active admission is authoritative for where the patient physically is —
    // it takes precedence over URL-supplied ward/bed hints (G053).
    if (liveLocation?.wardName) {
      return {
        icon: <Bed className="h-3.5 w-3.5" />,
        label: liveLocation.wardName,
        detail: liveLocation.bedNumber ?? facility?.name,
        variant: "default",
      };
    }

    if (encounterType === "outpatient" && source === "referral") {
      return {
        icon: <Video className="h-3.5 w-3.5" />,
        label: "Remote Consult",
        detail: facility?.name ?? "Remote",
        variant: "secondary",
      };
    }

    if (encounterType === "inpatient" && ward) {
      return {
        icon: <Bed className="h-3.5 w-3.5" />,
        label: ward,
        detail: bed ?? undefined,
        variant: "default",
      };
    }

    if (source === "queue" && (queueId || queueName)) {
      return {
        icon: <Users className="h-3.5 w-3.5" />,
        label: queueName || "Queue",
        detail: facility?.name,
        variant: "default",
      };
    }

    if (encounterType === "emergency") {
      return {
        icon: <MapPin className="h-3.5 w-3.5" />,
        label: "Emergency Dept",
        detail: facility?.name,
        variant: "destructive",
      };
    }

    if (facility) {
      return {
        icon: <Building2 className="h-3.5 w-3.5" />,
        label: "Outpatient",
        detail: facility.name,
        variant: "default",
      };
    }

    return {
      icon: <Globe className="h-3.5 w-3.5" />,
      label: "Remote Consult",
      detail: "No facility assigned",
      variant: "outline",
    };
  };

  const location = getLocationInfo();

  return (
    <div className="flex items-center gap-2">
      <div className="h-4 w-px bg-neutral-100" />
      <span
        className={`inline-flex items-center gap-1.5 text-xs font-medium py-1 px-3 rounded-full border ${VARIANT_STYLES[location.variant]}`}
      >
        {location.icon}
        <span>{location.label}</span>
        {location.detail && (
          <>
            <span className="opacity-50">·</span>
            <span className="opacity-75 truncate max-w-[180px]">
              {location.detail}
            </span>
          </>
        )}
      </span>
      <div className="h-4 w-px bg-neutral-100" />
    </div>
  );
}
