import type { PublicHealthAlert } from "@/hooks/queries/usePublicHealth";

export function countOpenSurveillanceAlerts(alerts: PublicHealthAlert[]): number {
  return alerts.filter((a) => a.status !== "acknowledged").length;
}

export function countHighOrCriticalSurveillanceAlerts(alerts: PublicHealthAlert[]): number {
  return alerts.filter((a) => {
    const s = a.severity.toLowerCase();
    return s === "high" || s === "critical";
  }).length;
}
