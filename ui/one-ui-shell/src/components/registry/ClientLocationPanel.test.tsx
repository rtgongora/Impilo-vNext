import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ClientLocationPanel } from "./ClientLocationPanel";
import {
  useClientCanonicalLocation,
  type CanonicalLocation,
} from "@/hooks/queries/useClientCanonicalLocation";

// The map is a MapLibre component — stub it so the panel test stays in jsdom.
vi.mock("@/components/ndila/NdilaMap", () => ({
  NdilaMap: () => <div data-testid="ndila-map-mock" />,
}));
vi.mock("@/hooks/queries/useClientCanonicalLocation", () => ({
  useClientCanonicalLocation: vi.fn(),
}));

const mockHook = vi.mocked(useClientCanonicalLocation);

function withState(state: { data?: CanonicalLocation | null; isLoading?: boolean; isError?: boolean }) {
  mockHook.mockReturnValue({
    data: state.data ?? null,
    isLoading: state.isLoading ?? false,
    isError: state.isError ?? false,
  } as unknown as ReturnType<typeof useClientCanonicalLocation>);
}

describe("ClientLocationPanel", () => {
  it("shows coordinates + map when the location is materialized with coordinates", () => {
    withState({
      data: {
        id: "loc-1",
        name: "Client residence",
        district: "Harare Central",
        province: "Harare",
        latitude: -17.82912,
        longitude: 31.05337,
        verificationStatus: "UNVERIFIED",
      },
    });
    render(<ClientLocationPanel healthId="hid-1" />);
    expect(screen.getByText(/-17.82912, 31.05337/)).toBeInTheDocument();
    expect(screen.getByTestId("client-location-map")).toBeInTheDocument();
    expect(screen.getByText("Harare Central")).toBeInTheDocument();
  });

  it("shows 'awaiting geocode' when materialized without coordinates", () => {
    withState({ data: { id: "loc-2", province: "Manicaland" } });
    render(<ClientLocationPanel healthId="hid-2" />);
    expect(screen.getByText(/Awaiting geocode/i)).toBeInTheDocument();
    expect(screen.queryByTestId("client-location-map")).not.toBeInTheDocument();
  });

  it("shows the honest not-materialized empty state when no location exists", () => {
    withState({ data: null });
    render(<ClientLocationPanel healthId="hid-3" />);
    expect(screen.getByText(/No canonical location has been materialized/i)).toBeInTheDocument();
  });

  it("fails clean when the geography service errors", () => {
    withState({ isError: true });
    render(<ClientLocationPanel healthId="hid-4" />);
    expect(screen.getByText(/geography service is unavailable/i)).toBeInTheDocument();
  });
});
