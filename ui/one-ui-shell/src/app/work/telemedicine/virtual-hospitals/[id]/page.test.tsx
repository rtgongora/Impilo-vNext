import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import VirtualHospitalDetailPage from "./page";

const { paramsRef } = vi.hoisted(() => ({
  paramsRef: { current: { id: "vh-national-telemedicine" } as { id: string } },
}));

vi.mock("next/navigation", () => ({
  useParams: () => paramsRef.current,
}));

describe("/work/telemedicine/virtual-hospitals/[id] detail", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("answers the identity-model questions for a routable institution", () => {
    paramsRef.current = { id: "vh-national-telemedicine" };
    render(<VirtualHospitalDetailPage />);

    expect(screen.getByText("National Telemedicine Hospital")).toBeInTheDocument();
    expect(screen.getByText("vh-national-telemedicine")).toBeInTheDocument();
    expect(
      screen.getByText(/virtual service-delivery entity \(never a physical facility record\)/i),
    ).toBeInTheDocument();
    // Regulatory posture is configurable, never presumed
    expect(screen.getByText(/pending regulatory determination/i)).toBeInTheDocument();
    // Real entry path shown for routable institutions
    expect(screen.getByRole("link", { name: /create request/i })).toHaveAttribute(
      "href",
      "/telemedicine/new",
    );
    // Staffing doctrine: no implicit assignment
    expect(screen.getByText(/no provider is auto-assigned virtual duties/i)).toBeInTheDocument();
  });

  it("offers no pretend entry path for a not-yet-routable institution", () => {
    paramsRef.current = { id: "vh-mental-health" };
    render(<VirtualHospitalDetailPage />);

    expect(screen.getByText("Virtual Mental Health Unit")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /create request/i })).not.toBeInTheDocument();
    expect(screen.getByText(/not routable yet/i)).toBeInTheDocument();
    // Every queue is honestly awaiting backend
    expect(screen.getAllByText("awaiting backend").length).toBeGreaterThan(0);
  });

  it("handles unknown ids without pretending data exists", () => {
    paramsRef.current = { id: "vh-does-not-exist" };
    render(<VirtualHospitalDetailPage />);
    expect(screen.getByText(/no virtual hospital with id/i)).toBeInTheDocument();
  });
});
