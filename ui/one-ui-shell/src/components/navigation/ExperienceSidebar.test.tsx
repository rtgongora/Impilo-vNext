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
      user: {
        id: "provider-1",
        displayName: "Dr. Moyo",
        email: "dr.moyo@example.com",
        roles: ["CLINICIAN", "SYSTEM_ADMIN"],
        actorType: "PROVIDER",
      },
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

  it("exposes the sidecar retirement ledger in professional navigation for admin-capable users", () => {
    render(<ExperienceSidebar />);

    expect(screen.getByRole("link", { name: "Knowledge curation" })).toHaveAttribute(
      "href",
      "/admin/clinical-curation",
    );
    expect(screen.getByRole("link", { name: "Sidecar ledger" })).toHaveAttribute(
      "href",
      "/admin/sidecar-retirement",
    );
  });

  it("keeps citizen services in life navigation", () => {
    render(<ExperienceSidebar />);

    expect(screen.getByRole("link", { name: "Citizen services" })).toHaveAttribute(
      "href",
      "/citizen",
    );
    expect(screen.getByRole("link", { name: "Claim shared docs" })).toHaveAttribute(
      "href",
      "/share/claim",
    );
  });

  it("shows citizen self-service spotlight actions on citizen routes", () => {
    mockUsePathname.mockReturnValue("/citizen");

    render(<ExperienceSidebar />);

    expect(screen.getByText("Citizen self-service")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Health ID QR" })).toHaveAttribute(
      "href",
      "/citizen/health-id/qr",
    );
    expect(screen.getByRole("link", { name: "ID recovery" })).toHaveAttribute(
      "href",
      "/citizen/id-recovery",
    );
    expect(screen.getByRole("link", { name: "Claim docs" })).toHaveAttribute(
      "href",
      "/share/claim",
    );
  });

  it("shows credential verification in the citizen self-service spotlight on public verify routes", () => {
    mockUsePathname.mockReturnValue("/verify/credential");

    render(<ExperienceSidebar />);

    expect(screen.getByText("Citizen self-service")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Verify credential" })).toHaveAttribute(
      "href",
      "/verify/credential",
    );
    expect(screen.getByRole("link", { name: "Claim docs" })).toHaveAttribute(
      "href",
      "/share/claim",
    );
  });

  it("shows knowledge curation in the professional oversight spotlight on admin routes", () => {
    mockUsePathname.mockReturnValue("/admin/clinical-curation");

    render(<ExperienceSidebar />);

    expect(screen.getByText("Professional oversight")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Knowledge curation" }).some((link) => link.getAttribute("href") === "/admin/clinical-curation")).toBe(true);
  });

  it("exposes Enterprise resources in work navigation", () => {
    render(<ExperienceSidebar />);

    expect(screen.getByRole("link", { name: "Enterprise resources" })).toHaveAttribute("href", "/enterprise");
  });

  it("shows enterprise resource plane spotlight on /enterprise and /erp routes", () => {
    mockUsePathname.mockReturnValue("/enterprise");

    render(<ExperienceSidebar />);

    expect(screen.getByText("Enterprise resource plane")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Enterprise dashboard" })).toHaveAttribute("href", "/enterprise");
    expect(screen.getByRole("link", { name: "Procurement" })).toHaveAttribute("href", "/erp/procurement");
  });
});
