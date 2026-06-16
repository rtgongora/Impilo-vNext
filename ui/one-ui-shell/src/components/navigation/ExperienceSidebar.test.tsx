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

vi.mock("@/hooks/useSessionExperienceContract", () => ({
  useSessionExperienceContract: () => mockUseSessionExperienceContract(),
}));

const mockUseSessionExperienceContract = vi.fn();

const mockSetNavDrawerOpen = vi.fn();
vi.mock("@/hooks/useShellStore", () => ({
  useShellStore: (selector?: (s: { navDrawerOpen: boolean; setNavDrawerOpen: typeof mockSetNavDrawerOpen }) => unknown) => {
    const state = { navDrawerOpen: true, setNavDrawerOpen: mockSetNavDrawerOpen };
    return selector ? selector(state) : state;
  },
}));

describe("ExperienceSidebar", () => {
  beforeEach(() => {
    mockUseSessionExperienceContract.mockReturnValue({
      contract: {
        authenticated: true,
        tabs: {
          work: { visible: true },
          professional: { visible: true },
          personal: { visible: true },
        },
        visibleWorkspaces: ["facility_staff_management"],
        visibleManagementWorkspaces: ["national_organisation_registry"],
        friendlyResolutionState: null,
      },
      isLoading: false,
    });
    mockUsePathname.mockReturnValue("/clinical");
    mockUseAuthStore.mockReturnValue({
      user: {
        id: "provider-1",
        displayName: "Dr. Moyo",
        email: "dr.moyo@example.com",
        roles: ["CLINICIAN", "SYSTEM_ADMIN"],
        actorType: "PROVIDER",
        assuranceLevel: "VERIFIED",
        providerActivated: true,
        providerId: "PRV-TEST-001",
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

  it("hides Work and My Professional zones for citizen-only users", () => {
    mockUseSessionExperienceContract.mockReturnValue({
      contract: {
        authenticated: true,
        tabs: {
          work: { visible: false },
          professional: { visible: false },
          personal: { visible: true },
        },
        visibleWorkspaces: [],
        visibleManagementWorkspaces: [],
        friendlyResolutionState: null,
      },
      isLoading: false,
    });
    mockUseAuthStore.mockReturnValue({
      user: {
        id: "citizen-1",
        displayName: "Citizen",
        email: "citizen@example.com",
        roles: ["CITIZEN"],
        actorType: "CITIZEN",
        assuranceLevel: "VERIFIED",
        providerActivated: false,
      },
      hasRole: (role: string) => role === "CITIZEN",
    });

    render(<ExperienceSidebar />);

    expect(screen.queryByRole("link", { name: "Clinical Hub" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Professional Profile" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Home" })).toBeInTheDocument();
  });

  it("shows Administration & Governance in Work for citizen-only users when session contract grants governance", () => {
    mockUseAuthStore.mockReturnValue({
      user: {
        id: "b0000000-0000-4000-8000-000000000010",
        displayName: "Super Admin",
        email: "superadmin@impilo.gov.zw",
        roles: ["CITIZEN", "SYSTEM_ADMIN"],
        actorType: "CITIZEN",
        assuranceLevel: "VERIFIED",
        providerActivated: false,
      },
      hasRole: (role: string) => ["CITIZEN", "SYSTEM_ADMIN"].includes(role),
    });

    render(<ExperienceSidebar />);

    expect(screen.getByRole("link", { name: "Administration & Governance" })).toHaveAttribute(
      "href",
      "/work/administration-governance",
    );
    expect(screen.queryByRole("link", { name: "Clinical Hub" })).not.toBeInTheDocument();
  });
});
