import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CitizenQuickAccessRail } from "./CitizenQuickAccessRail";

const mockUsePathname = vi.fn();

vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
}));

describe("CitizenQuickAccessRail", () => {
  beforeEach(() => {
    mockUsePathname.mockReturnValue("/learning");
  });

  it("renders the home quick access destinations", () => {
    render(<CitizenQuickAccessRail />);

    expect(screen.getByRole("link", { name: /My Impilo ID/i })).toHaveAttribute("href", "/citizen/health-id/qr");
    expect(screen.getByRole("link", { name: /Medications/i })).toHaveAttribute("href", "/home/medications");
    expect(screen.getByRole("link", { name: /Care Team/i })).toHaveAttribute("href", "/home/care-team");
    expect(screen.getByRole("link", { name: /Coverage/i })).toHaveAttribute("href", "/coverage");
    // De-duplication: destinations that already live in the global NavRail (Marketplace,
    // Wellness, Blood donation) must NOT be repeated in the home rail — that overlap was the
    // "two side menus" redundancy.
    expect(screen.queryByRole("link", { name: /Marketplace/i })).toBeNull();
    expect(screen.queryByRole("link", { name: /Blood donation/i })).toBeNull();
    // RJ-2: self-service provider access is reachable from the citizen home rail.
    expect(screen.getByRole("link", { name: /Request Provider Access/i })).toHaveAttribute(
      "href",
      "/citizen/provider-claim",
    );
  });

  it("collapses and exposes icon-only quick access", async () => {
    render(<CitizenQuickAccessRail collapsible />);

    await userEvent.click(screen.getByRole("button", { name: "Close quick access sidebar" }));

    expect(screen.getByRole("button", { name: "Open quick access sidebar" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByTitle("Medications")).toHaveAttribute("href", "/home/medications");
  });
});
