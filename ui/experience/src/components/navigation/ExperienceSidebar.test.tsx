import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ExperienceSidebar } from "./ExperienceSidebar";

const mockUsePathname = vi.fn();
const mockUseAuthStore = vi.fn();
const mockUseExperienceEntry = vi.fn();

vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => mockUseAuthStore(),
}));

vi.mock("@/providers/ExperienceEntryProvider", () => ({
  useExperienceEntry: () => mockUseExperienceEntry(),
}));

describe("ExperienceSidebar", () => {
  beforeEach(() => {
    mockUsePathname.mockReturnValue("/clinical");
    mockUseAuthStore.mockReturnValue({
      user: { displayName: "Dr. Moyo", email: "dr.moyo@example.com" },
      hasRole: (role: string) => ["CLINICIAN", "SYSTEM_ADMIN"].includes(role),
    });
    mockUseExperienceEntry.mockReturnValue({
      currentRoute: { navZone: "work", pageTitle: "Clinical Care" },
      facility: { name: "Harare Central Hospital" },
      workspace: { name: "ED Frontline" },
      stage: "ready",
      shiftActive: true,
      workMode: "clinical",
    });

    sessionStorage.clear();
  });

  it("keeps ED / Casualty in the work navigation for queue-capable clinicians", () => {
    render(<ExperienceSidebar />);

    const links = screen.getAllByRole("link", { name: "ED / Casualty" });

    expect(links.some((link) => link.getAttribute("href") === "/clinical/emergency")).toBe(true);
  });

  it("keeps ED / Casualty in the clinical spotlight quick actions", () => {
    render(<ExperienceSidebar />);

    expect(screen.getByText("Clinical coordination")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "ED / Casualty" })).toHaveLength(2);
  });
});
