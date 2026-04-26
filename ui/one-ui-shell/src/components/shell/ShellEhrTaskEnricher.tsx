"use client";

import { useEffect, useMemo } from "react";
import { usePathname } from "next/navigation";
import { usePatient } from "@/hooks/queries/usePatients";
import { useShellStore } from "@/hooks/useShellStore";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { maskName } from "@/lib/pii-mask";

/**
 * When the user is in /ehr/[patientId]/*, replace generic task titles with a masked patient label.
 */
export function ShellEhrTaskEnricher() {
  const pathname = usePathname();
  const privacyLevel = usePrivacyDisplayStore((s) => s.level);
  const updateTaskTitleForRoute = useShellStore((s) => s.updateTaskTitleForRoute);

  const patientId = useMemo(() => {
    const m = pathname.match(/^\/ehr\/([^/]+)/);
    const id = m?.[1];
    if (!id || id === "undefined") return null;
    return id;
  }, [pathname]);

  const { data: patientData } = usePatient(patientId ?? "");

  useEffect(() => {
    if (!patientId) return;
    const patient = patientData?.data;
    const rawName = String(patient?.attributes?.displayName ?? "");
    if (!rawName.trim()) return;

    const segment = pathname.split("/").filter(Boolean)[2] ?? "chart";
    const label = maskName(rawName, privacyLevel) || "Patient";
    updateTaskTitleForRoute(pathname, `Patient: ${label} · ${segment}`);
  }, [pathname, patientId, patientData, privacyLevel, updateTaskTitleForRoute]);

  return null;
}
