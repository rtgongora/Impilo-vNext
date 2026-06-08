"use client";

import Link from "next/link";
import { AlertTriangle, MapPin, ShieldAlert } from "lucide-react";
import { useFacilityDataQuality } from "@/hooks/queries/useFacilityDataQuality";

export function FacilityDataQualityBanner() {
  const { data, isLoading, isError } = useFacilityDataQuality();
  const payload = data?.data;
  const summary = payload?.qualityReport?.summary;
  const pendingGeocode = payload?.geocodeReviewPending ?? 0;
  const missingCoords = summary?.records_missing_or_invalid_coordinates ?? 0;
  const missingCodes = summary?.records_missing_facility_code ?? 0;
  const duplicateCodes = summary?.duplicate_facility_codes_count ?? 0;

  if (isLoading || isError || !payload) {
    return null;
  }

  const hasWarnings = pendingGeocode > 0 || missingCoords > 0 || missingCodes > 0 || duplicateCodes > 0;
  if (!hasWarnings && payload.qualityReport?.status !== "NOT_GENERATED") {
    return null;
  }

  return (
    <div className="mb-6 rounded-2xl border border-amber-200 bg-amber-50/80 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className="rounded-xl bg-white/80 p-2 text-amber-700">
            <ShieldAlert className="h-5 w-5" />
          </div>
          <div>
            <p className="text-sm font-semibold text-amber-900">Master facility data quality</p>
            <p className="mt-1 text-sm text-amber-800">
              Tuso registry and Ndila geospatial index may contain records needing review before routing or
              nearest-facility search.
            </p>
            <div className="mt-3 flex flex-wrap gap-3 text-xs text-amber-900">
              {missingCoords > 0 ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-white/70 px-2.5 py-1">
                  <MapPin className="h-3.5 w-3.5" />
                  {missingCoords} missing/invalid coordinates
                </span>
              ) : null}
              {missingCodes > 0 ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-white/70 px-2.5 py-1">
                  <AlertTriangle className="h-3.5 w-3.5" />
                  {missingCodes} missing facility codes
                </span>
              ) : null}
              {duplicateCodes > 0 ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-white/70 px-2.5 py-1">
                  <AlertTriangle className="h-3.5 w-3.5" />
                  {duplicateCodes} duplicate codes in source pack
                </span>
              ) : null}
              {pendingGeocode > 0 ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-white/70 px-2.5 py-1">
                  <MapPin className="h-3.5 w-3.5" />
                  {pendingGeocode} Ndila geocode review queue
                </span>
              ) : null}
            </div>
          </div>
        </div>
        <Link
          href="/registry/intake"
          className="inline-flex rounded-xl border border-amber-300 bg-white px-3 py-2 text-xs font-medium text-amber-900 transition-colors hover:border-amber-400"
        >
          Governed import
        </Link>
      </div>
    </div>
  );
}
