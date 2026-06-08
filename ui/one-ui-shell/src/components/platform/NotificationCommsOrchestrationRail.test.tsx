import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { NotificationCommsOrchestrationRail } from "./NotificationCommsOrchestrationRail";

vi.mock("@/hooks/queries/useOmnichannel", () => ({
  useOmnichannelDashboard: () => ({
    data: {
      data: {
        active_campaigns: 2,
        completed_campaigns: 5,
        configured_channels: 3,
      },
    },
    isLoading: false,
  }),
  useOmnichannelCampaigns: () => ({
    data: [{ id: "c1", name: "Flu reminder" }],
    isLoading: false,
  }),
}));

vi.mock("@/hooks/queries/useNotifications", () => ({
  useNotifications: () => ({ data: { data: [{ id: "n1", read: false }] }, isLoading: false }),
}));

vi.mock("@/hooks/queries/useNotificationTemplates", () => ({
  useNotificationTemplates: () => ({ data: [{ id: "t1", name: "Welcome SMS" }], isLoading: false }),
}));

describe("NotificationCommsOrchestrationRail", () => {
  it("renders compose → templates → delivery pipeline and campaign depth", () => {
    render(<NotificationCommsOrchestrationRail />);
    expect(screen.getByTestId("notification-comms-orchestration-rail")).toBeInTheDocument();
    expect(screen.getByTestId("notification-comms-pipeline")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Compose campaign/i })).toHaveAttribute(
      "href",
      "/omnichannel?tab=campaigns",
    );
    const templateLinks = screen.getAllByRole("link", { name: /Templates/i });
    expect(templateLinks.some((link) => link.getAttribute("href") === "/admin/notifications/templates")).toBe(
      true,
    );
    expect(screen.getByTestId("notification-campaign-preview")).toHaveTextContent(/Flu reminder/i);
  });
});
