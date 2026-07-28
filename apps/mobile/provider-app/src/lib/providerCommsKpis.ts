import { appStore } from "../stores/appStore";
import type { ProviderTabKey } from "../types";

type CommsDashboardSnapshot = {
  active_threads?: number;
  sent_today?: number;
  failed_today?: number;
} | null;

export function buildProviderCommsKpis(
  commsDashboard: CommsDashboardSnapshot,
  setProviderTab: (tab: ProviderTabKey) => void,
  /**
   * Opens supervisor escalations. Takes an opaque callback rather than the
   * store's mode setter because supervisor is a governed mode: the caller must
   * route this through `useSwitchAppMode`, which mints a supervisory duty token
   * before the workspace changes. Passing a raw setter here is what previously
   * let this KPI drop a clinician into supervisor UI still holding their
   * CLINICAL_CARE token.
   */
  openSupervisorEscalations: () => void
) {
  if (!commsDashboard) {
    return [];
  }

  return [
    {
      label: "Active Threads",
      value: commsDashboard.active_threads,
      color: "#1E40AF",
      icon: "chatbubbles",
      hint: "Tap to open inbox",
      onPress: () => setProviderTab("messaging"),
    },
    {
      label: "Sent Today",
      value: commsDashboard.sent_today,
      color: "#059669",
      icon: "send",
      hint: "Tap to open inbox",
      onPress: () => setProviderTab("messaging"),
    },
    {
      label: "Failed Today",
      value: commsDashboard.failed_today,
      color: (commsDashboard.failed_today ?? 0) > 0 ? "#DC2626" : "#111827",
      icon: "alert-circle",
      hint: "Tap to open escalations",
      onPress: () => {
        appStore.getState().setSupervisorEntryTab("escalations");
        openSupervisorEscalations();
      },
    },
  ];
}
