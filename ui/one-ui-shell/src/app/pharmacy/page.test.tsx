import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PharmacyHubPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) =>
      ({
        patientId: "patient-1",
        encounterId: "enc-1",
        source: "discharge",
      })[key] ?? null,
  }),
  usePathname: () => "/pharmacy",
}));

vi.mock("@/components/experience/TrustContextBanner", () => ({
  TrustContextBanner: () => null,
}));

vi.mock("@/components/intelligent/NompiloContextPanel", () => ({
  NompiloContextPanel: () => null,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

describe("PharmacyHubPage", () => {
  it("keeps pharmacy entry anchored to encounter-aware medication follow-through", () => {
    render(<PharmacyHubPage />);

    expect(screen.getByText("Medication handoff")).toBeInTheDocument();
    expect(screen.getByText("Medication loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");

    const prescriptionsLink = screen.getAllByRole("link").find((link) =>
      link.getAttribute("href")?.startsWith("/pharmacy/prescriptions?"),
    );
    expect(prescriptionsLink).toHaveAttribute(
      "href",
      "/pharmacy/prescriptions?patientId=patient-1&encounterId=enc-1&source=discharge",
    );
  });
});
