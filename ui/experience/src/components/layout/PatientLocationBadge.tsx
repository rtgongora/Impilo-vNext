"use client";

/**
 * PatientLocationBadge -- Compact badge showing the patient's current
 * ward / bed / care-point location. Color-coded by care type.
 *
 * Uses mock data rather than Supabase; production would query VITO or
 * the encounter context for live location.
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

// ---------------------------------------------------------------------------
// Mock encounter data (replace with real encounter context in production)
// ---------------------------------------------------------------------------
const MOCK_ENCOUNTER = {
  type: "inpatient" as "outpatient" | "inpatient" | "emergency" | "community" | "virtual",
  source: "queue" as "queue" | "referral" | "walk-in",
  patient: {
    ward: "Medical Ward 2",
    bed: "Bed 14A",
  },
};

const MOCK_QUEUE_NAME = "OPD Morning Queue";

// ---------------------------------------------------------------------------
// Variant styles
// ---------------------------------------------------------------------------
const VARIANT_STYLES = {
  default: "bg-blue-50 text-blue-700 border-blue-200",
  secondary: "bg-gray-100 text-gray-700 border-gray-200",
  destructive: "bg-red-50 text-red-700 border-red-200",
  outline: "bg-white text-gray-600 border-gray-300",
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

  if (!patientId) return null;

  const encounterType = MOCK_ENCOUNTER.type;
  const source = MOCK_ENCOUNTER.source;
  const patient = MOCK_ENCOUNTER.patient;
  const queueId = searchParams.get("queueId");

  const getLocationInfo = (): LocationInfo => {
    if (encounterType === "outpatient" && source === "referral") {
      return {
        icon: <Video className="h-3.5 w-3.5" />,
        label: "Remote Consult",
        detail: facility?.name ?? "Remote",
        variant: "secondary",
      };
    }

    if (encounterType === "inpatient" && patient.ward) {
      return {
        icon: <Bed className="h-3.5 w-3.5" />,
        label: patient.ward,
        detail: patient.bed ?? undefined,
        variant: "default",
      };
    }

    if (source === "queue" && (queueId || MOCK_QUEUE_NAME)) {
      return {
        icon: <Users className="h-3.5 w-3.5" />,
        label: MOCK_QUEUE_NAME,
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
      <div className="h-4 w-px bg-gray-200" />
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
      <div className="h-4 w-px bg-gray-200" />
    </div>
  );
}
