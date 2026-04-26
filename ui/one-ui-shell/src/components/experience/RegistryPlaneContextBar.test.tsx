import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { RegistryPlaneContextBar } from "./RegistryPlaneContextBar";

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
      operationalMode: "registry_admin",
      registryAdminSubtype: "client_registry",
    }),
}));

describe("RegistryPlaneContextBar", () => {
  beforeEach(() => {
    mockSearch.mockReset();
  });

  it("renders when URL indicates registry-admin entry", () => {
    mockSearch.mockImplementation((k) => (k === "from" ? "registry-admin" : null));
    render(<RegistryPlaneContextBar />);
    expect(screen.getByText("Registry Admin", { exact: true })).toBeInTheDocument();
    expect(screen.getByText(/Client registry \(MPI\)/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Registry admin home/i })).toHaveAttribute(
      "href",
      "/registry-admin",
    );
  });

  it("renders from store when preferStore and mode is registry_admin", () => {
    mockSearch.mockImplementation(() => null);
    render(<RegistryPlaneContextBar preferStore />);
    expect(screen.getByText("Registry Admin", { exact: true })).toBeInTheDocument();
  });
});
