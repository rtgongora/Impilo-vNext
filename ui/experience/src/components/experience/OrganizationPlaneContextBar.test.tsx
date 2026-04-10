import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { OrganizationPlaneContextBar } from "./OrganizationPlaneContextBar";

const mockSearch = vi.fn((key: string): string | null => {
  void key;
  return null;
});

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: mockSearch }),
}));

vi.mock("@/hooks/useOperationalContextStore", () => ({
  useOperationalContextStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      operationalMode: "organization_admin",
      organizationAdminSurface: "facility_admin",
    }),
}));

describe("OrganizationPlaneContextBar", () => {
  beforeEach(() => {
    mockSearch.mockReset();
  });

  it("renders when URL indicates organization-admin entry", () => {
    mockSearch.mockImplementation((k) => (k === "from" ? "organization-admin" : null));
    render(<OrganizationPlaneContextBar />);
    expect(screen.getByText("Organization Admin", { exact: true })).toBeInTheDocument();
    expect(screen.getByText(/Facility & ward administration/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Org admin home/i })).toHaveAttribute(
      "href",
      "/organization-admin",
    );
  });

  it("renders from store when preferStore and mode is organization_admin", () => {
    mockSearch.mockImplementation(() => null);
    render(<OrganizationPlaneContextBar preferStore />);
    expect(screen.getByText("Organization Admin", { exact: true })).toBeInTheDocument();
  });
});
