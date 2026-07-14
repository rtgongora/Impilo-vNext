// @vitest-environment jsdom
import type { ReactNode } from "react";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

afterEach(() => cleanup());
beforeEach(() => window.sessionStorage.clear());

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string } & Record<string, unknown>) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const completenessState: {
  data?: unknown;
  isLoading: boolean;
  isError: boolean;
} = { data: undefined, isLoading: false, isError: false };

vi.mock("@/hooks/queries/useFacilityCompleteness", async () => {
  const actual = await vi.importActual<
    typeof import("@/hooks/queries/useFacilityCompleteness")
  >("@/hooks/queries/useFacilityCompleteness");
  return {
    ...actual,
    useFacilityCompleteness: () => completenessState,
  };
});

import { FacilityCompletenessBanner } from "./FacilityCompletenessBanner";

describe("FacilityCompletenessBanner", () => {
  it("renders the specific missing fields and the completion CTA when fields are missing", () => {
    completenessState.data = {
      facilityId: 42,
      complete: false,
      missingFields: ["missing_latitude", "missing_longitude", "missing_ownership"],
      geocodeStatus: "MISSING",
    };
    completenessState.isLoading = false;
    completenessState.isError = false;

    render(<FacilityCompletenessBanner facilityId="42" />);

    expect(screen.getByTestId("facility-completeness-banner")).toBeTruthy();
    expect(screen.getByTestId("missing-field-missing_latitude").textContent).toContain("Latitude");
    expect(screen.getByTestId("missing-field-missing_ownership").textContent).toContain("Ownership");
    const cta = screen.getByTestId("facility-completeness-cta");
    expect(cta.getAttribute("href")).toBe("/facility/42/complete-profile");
    // geocode-missing copy is explicit about map/routing consequences
    expect(screen.getByTestId("facility-completeness-banner").textContent).toContain("Coordinates are missing");
  });

  it("renders nothing when the profile is complete", () => {
    completenessState.data = {
      facilityId: 42,
      complete: true,
      missingFields: [],
      geocodeStatus: "PRESENT",
    };

    render(<FacilityCompletenessBanner facilityId="42" />);

    expect(screen.queryByTestId("facility-completeness-banner")).toBeNull();
  });

  it("renders nothing while loading or on error (no false prompts)", () => {
    completenessState.data = undefined;
    completenessState.isLoading = true;
    render(<FacilityCompletenessBanner facilityId="42" />);
    expect(screen.queryByTestId("facility-completeness-banner")).toBeNull();
    cleanup();

    completenessState.isLoading = false;
    completenessState.isError = true;
    render(<FacilityCompletenessBanner facilityId="42" />);
    expect(screen.queryByTestId("facility-completeness-banner")).toBeNull();
    completenessState.isError = false;
  });

  it("renders nothing outside a management context (display gate)", () => {
    completenessState.data = {
      facilityId: 42,
      complete: false,
      missingFields: ["missing_latitude"],
      geocodeStatus: "MISSING",
    };

    render(<FacilityCompletenessBanner facilityId="42" managementContext={false} />);

    expect(screen.queryByTestId("facility-completeness-banner")).toBeNull();
  });

  it("is dismissible for the session and stays dismissed on remount", () => {
    completenessState.data = {
      facilityId: 42,
      complete: false,
      missingFields: ["missing_latitude"],
      geocodeStatus: "MISSING",
    };

    render(<FacilityCompletenessBanner facilityId="42" />);
    fireEvent.click(screen.getByTestId("facility-completeness-dismiss"));
    expect(screen.queryByTestId("facility-completeness-banner")).toBeNull();
    expect(window.sessionStorage.getItem("facility-completeness-dismissed:42")).toBe("1");

    cleanup();
    render(<FacilityCompletenessBanner facilityId="42" />);
    expect(screen.queryByTestId("facility-completeness-banner")).toBeNull();
  });
});
