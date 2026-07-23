"use client";

/**
 * OF-B24/OF-B28 — honest calibration-posture badge for a device assignment.
 *
 * Posture comes ONLY from the assignment-gate resolve lane; asset-registry
 * problems arrive stamped (UNVERIFIED), never masked as OK. The badge says so
 * in plain words — no green-by-default.
 */

import { useResolvedDeviceAssignment } from "@/hooks/queries/useTelemonitoring";
import type { CalibrationPosture } from "@/lib/telemonitoring/types";

export const POSTURE_LABELS: Record<CalibrationPosture, string> = {
  OK: "Calibrated",
  DEGRADED: "Calibration lapsed — excluded from value alerts",
  UNVERIFIED: "Calibration could not be verified",
  UNTRACKED: "No calibration tracking (no asset record)",
};

const POSTURE_TONES: Record<CalibrationPosture, string> = {
  OK: "bg-emerald-100 text-emerald-800",
  DEGRADED: "bg-amber-100 text-amber-800",
  UNVERIFIED: "bg-orange-100 text-orange-800",
  UNTRACKED: "bg-slate-200 text-slate-700",
};

export function DevicePostureBadge({ deviceRef }: { deviceRef: string }) {
  const { data, isLoading, error } = useResolvedDeviceAssignment(deviceRef);

  if (isLoading) {
    return (
      <span className="inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
        Checking calibration…
      </span>
    );
  }
  if (error || !data) {
    return (
      <span
        className="inline-flex rounded-full bg-slate-200 px-2 py-0.5 text-xs text-slate-700"
        title="The assignment gate did not answer for this device."
      >
        Calibration state unavailable
      </span>
    );
  }
  const posture = data.calibrationPosture;
  return (
    <span
      className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${POSTURE_TONES[posture] ?? "bg-slate-200 text-slate-700"}`}
      title={data.calibrationPostureReason ?? undefined}
    >
      {POSTURE_LABELS[posture] ?? posture}
    </span>
  );
}
