import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TopBar } from "./TopBar";

const mockUseParams = vi.fn();
const mockUsePathname = vi.fn();

vi.mock("next/navigation", () => ({
  useParams: () => mockUseParams(),
  usePathname: () => mockUsePathname(),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { displayName: "Test", email: "t@x.com" } }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isFinance: true, isDispenser: true }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: () => ({ facility: { name: "Site A" } }),
}));

vi.mock("@/hooks/useShiftStore", () => ({
  useShiftStore: () => ({ shift: null }),
}));

vi.mock("@/hooks/usePrivacyDisplayStore", () => ({
  usePrivacyDisplayStore: () => ({ level: "FULL", cycleLevel: vi.fn() }),
}));

describe("TopBar", () => {
  it("renders registry-based breadcrumbs for EHR chart sections", () => {
    mockUseParams.mockReturnValue({ patientId: "p-42" });
    mockUsePathname.mockReturnValue("/ehr/p-42/orders");

    render(<TopBar />);

    const bc = screen.getByRole("navigation", { name: /breadcrumb/i });
    expect(bc).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Chart" })).toHaveAttribute("href", "/ehr/p-42");
    expect(within(bc).getByText("Orders")).toBeInTheDocument();
  });
});
