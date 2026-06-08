import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SocialCommunityOrchestrationRail } from "./SocialCommunityOrchestrationRail";

vi.mock("@/hooks/queries/useSocial", () => ({
  useSocialFeed: () => ({
    data: { items: [{ id: "p1" }, { id: "p2" }] },
    isLoading: false,
  }),
}));

vi.mock("@/hooks/queries/useSurveillance", () => ({
  useAlerts: () => ({
    data: [{ id: "a1", title: "Cholera signal", severity: "HIGH" }],
    isLoading: false,
  }),
}));

describe("SocialCommunityOrchestrationRail", () => {
  it("binds feed and surveillance alerts", () => {
    render(<SocialCommunityOrchestrationRail />);
    expect(screen.getByTestId("social-community-orchestration-rail")).toBeInTheDocument();
    expect(screen.getByTestId("social-feed-stats")).toHaveTextContent("2 post(s)");
    expect(screen.getByTestId("social-feed-stats")).toHaveTextContent("1 public-health alert(s)");
    expect(screen.getByRole("link", { name: /Surveillance/i })).toHaveAttribute("href", "/public-health?tab=surveillance");
  });
});
