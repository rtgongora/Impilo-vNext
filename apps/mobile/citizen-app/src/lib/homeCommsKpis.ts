import type { CitizenTab } from "../types";

type CommsDashboardSnapshot = {
  active_threads?: number;
  sent_today?: number;
  failed_today?: number;
} | null;

export function buildCitizenCommsKpis(
  commsDashboard: CommsDashboardSnapshot,
  setActiveTab: (tab: CitizenTab) => void
) {
  if (!commsDashboard) {
    return [];
  }

  return [
    {
      id: "threads",
      label: "Threads",
      value: commsDashboard.active_threads,
      color: "#1E40AF",
      bg: "#DBEAFE",
      icon: "chatbubbles-outline",
      hint: "Tap to open messages",
      onPress: () => setActiveTab("messaging"),
    },
    {
      id: "sent",
      label: "Sent Today",
      value: commsDashboard.sent_today,
      color: "#059669",
      bg: "#D1FAE5",
      icon: "paper-plane-outline",
      hint: "Tap to open messages",
      onPress: () => setActiveTab("messaging"),
    },
    {
      id: "failed",
      label: "Failed",
      value: commsDashboard.failed_today,
      color: (commsDashboard.failed_today ?? 0) > 0 ? "#B91C1C" : "#6B7280",
      bg: "#FEE2E2",
      icon: "alert-circle-outline",
      hint: "Tap to open public health",
      onPress: () => setActiveTab("public_health"),
    },
  ];
}
