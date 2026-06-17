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

    expect(screen.getByRole("link", { name: /My Health ID/i })).toHaveAttribute("href", "/citizen/health-id/qr");
    expect(screen.getByRole("link", { name: /Medications/i })).toHaveAttribute("href", "/home/medications");
    expect(screen.getByRole("link", { name: /Care Team/i })).toHaveAttribute("href", "/home/care-team");
    expect(screen.getByRole("link", { name: /Marketplace/i })).toHaveAttribute("href", "/marketplace");
  });

  it("collapses and exposes icon-only quick access", async () => {
    render(<CitizenQuickAccessRail collapsible />);

    await userEvent.click(screen.getByRole("button", { name: "Close quick access sidebar" }));

    expect(screen.getByRole("button", { name: "Open quick access sidebar" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByTitle("Medications")).toHaveAttribute("href", "/home/medications");
  });
});
