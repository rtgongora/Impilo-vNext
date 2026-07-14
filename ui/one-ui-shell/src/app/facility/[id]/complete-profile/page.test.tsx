// @vitest-environment jsdom
import type { ReactNode } from "react";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => cleanup());

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "42" }),
}));

vi.mock("next/link", () => ({
  default: ({ children }: { children: ReactNode }) => <span>{children}</span>,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

// Reused governed geocode capture — stubbed with a button that emits a
// DEVICE_GPS capture, so the page's provenance wiring is what's under test.
vi.mock("@/components/maps/FacilityLocationCapture", () => ({
  FacilityLocationCapture: ({
    onChange,
  }: {
    onChange?: (
      next: { latitude: number; longitude: number } | null,
      meta?: { source: string; accuracyMeters?: number },
    ) => void;
  }) => (
    <button
      type="button"
      data-testid="stub-capture-gps"
      onClick={() =>
        onChange?.({ latitude: -17.8292, longitude: 31.0522 }, { source: "DEVICE_GPS", accuracyMeters: 8 })
      }
    >
      capture
    </button>
  ),
}));

// Facility resource pre-fill read (the page's direct useQuery). useMutation /
// useQueryClient are stubbed because the actual hooks module imports them.
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: {
      id: "42",
      type: "facility",
      attributes: {
        name: "Bangure Clinic",
        facilityType: "CLINIC",
        // ownership + coordinates missing on purpose
      },
    },
    isLoading: false,
    error: null,
  }),
  useMutation: () => ({ mutate: vi.fn(), isPending: false, isSuccess: false, isError: false }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));

const mutateMock = vi.fn();
const completenessState = {
  data: {
    facilityId: 42,
    complete: false,
    missingFields: ["missing_latitude", "missing_longitude", "missing_ownership"],
    geocodeStatus: "MISSING",
  },
  isLoading: false,
  isError: false,
};

vi.mock("@/hooks/queries/useFacilityCompleteness", async () => {
  const actual = await vi.importActual<
    typeof import("@/hooks/queries/useFacilityCompleteness")
  >("@/hooks/queries/useFacilityCompleteness");
  return {
    ...actual,
    useFacilityCompleteness: () => completenessState,
    useCompleteFacilityProfile: () => ({
      mutate: mutateMock,
      isPending: false,
      isSuccess: false,
      isError: false,
      data: undefined,
      error: null,
    }),
  };
});

import FacilityCompleteProfilePage from "./page";

describe("Facility complete-profile page", () => {
  it("lists the missing governed fields and pre-fills known values", () => {
    render(<FacilityCompleteProfilePage />);

    expect(screen.getByTestId("missing-summary").textContent).toContain("Ownership");
    expect(screen.getByTestId("missing-summary").textContent).toContain("Latitude");
    // pre-filled from the registry read
    expect((screen.getByTestId("field-facility-type") as HTMLSelectElement).value).toBe("CLINIC");
    expect((screen.getByTestId("field-ownership") as HTMLSelectElement).value).toBe("");
  });

  it("highlights missing fields only", () => {
    render(<FacilityCompleteProfilePage />);

    expect(screen.getByTestId("field-ownership").className).toContain("border-warning");
    expect(screen.getByTestId("field-facility-type").className).not.toContain("border-warning");
  });

  it("submits governed fields with geocode provenance from the capture component", () => {
    mutateMock.mockClear();
    render(<FacilityCompleteProfilePage />);

    fireEvent.change(screen.getByTestId("field-ownership"), { target: { value: "GOVERNMENT" } });
    fireEvent.click(screen.getByTestId("stub-capture-gps"));
    fireEvent.submit(screen.getByTestId("complete-profile-form"));

    expect(mutateMock).toHaveBeenCalledTimes(1);
    const payload = mutateMock.mock.calls[0][0];
    expect(payload.ownership).toBe("GOVERNMENT");
    expect(payload.facilityType).toBe("CLINIC");
    expect(payload.latitude).toBeCloseTo(-17.8292);
    expect(payload.longitude).toBeCloseTo(31.0522);
    expect(payload.geocodeProvenance).toEqual({ source: "DEVICE_GPS", accuracyMeters: 8 });
  });

  it("does not send coordinates without a capture (no provenance, no geocode)", () => {
    mutateMock.mockClear();
    render(<FacilityCompleteProfilePage />);

    fireEvent.submit(screen.getByTestId("complete-profile-form"));

    const payload = mutateMock.mock.calls[0][0];
    expect(payload.latitude).toBeUndefined();
    expect(payload.geocodeProvenance).toBeUndefined();
  });
});
