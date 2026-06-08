import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { NdilaWorkspaceMapPanel } from "./NdilaWorkspaceMapPanel";

vi.mock("@/hooks/queries/useNdila", () => ({
  useNdilaTileConfig: () => ({
    data: { provider: "ndila-mock", tileUrlTemplate: "mock://tiles/{z}/{x}/{y}" },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("./NdilaAddressSearchBox", () => ({
  NdilaAddressSearchBox: ({ onSelect }: { onSelect?: (r: { latitude: number; longitude: number }) => void }) => (
    <button type="button" onClick={() => onSelect?.({ latitude: -17.8, longitude: 31.0 })}>
      mock geocode
    </button>
  ),
}));

vi.mock("./NdilaMap", () => ({
  NdilaMap: () => <div data-testid="ndila-map">map</div>,
}));

vi.mock("./NdilaNearbyServicesMap", () => ({
  NdilaNearbyServicesMap: () => <div data-testid="ndila-nearby-map">nearby</div>,
}));

vi.mock("./NdilaRoutePreview", () => ({
  NdilaRoutePreview: () => <div data-testid="ndila-route-preview">route</div>,
}));

vi.mock("./NdilaOfflineNotice", () => ({
  NdilaOfflineNotice: () => null,
}));

vi.mock("./NdilaProviderStatusBadge", () => ({
  NdilaProviderStatusBadge: () => <span>provider-badge</span>,
}));

describe("NdilaWorkspaceMapPanel", () => {
  it("renders full map workspace with search and nearby surfaces", () => {
    render(<NdilaWorkspaceMapPanel />);
    expect(screen.getByTestId("ndila-workspace-map-panel")).toBeInTheDocument();
    expect(screen.getByTestId("ndila-map")).toBeInTheDocument();
    expect(screen.getByTestId("ndila-nearby-map")).toBeInTheDocument();
    expect(screen.getByText(/Route destination \(optional\)/i)).toBeInTheDocument();
  });
});
