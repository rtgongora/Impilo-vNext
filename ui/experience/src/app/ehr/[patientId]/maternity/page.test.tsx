import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import MaternityMonitoringPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "u1", displayName: "Dr. Test" } }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isClinical: true }),
}));

vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS" } }],
    },
  }),
}));

vi.mock("@/features/maternity/partograph/VitalsPartographSection", () => ({
  VitalsPartographSection: () => <div data-testid="partograph-section">Partograph module</div>,
}));

vi.mock("@/features/maternity/ctg/VitalsCtgSection", () => ({
  VitalsCtgSection: () => <div data-testid="ctg-section">CTG module</div>,
}));

describe("MaternityMonitoringPage", () => {
  it("mounts the canonical maternity route with partograph and CTG sections", () => {
    render(<MaternityMonitoringPage />);

    expect(screen.getByRole("heading", { level: 1, name: /Maternity Monitoring/i })).toBeInTheDocument();
    expect(screen.getByText(/Active encounter: enc-1/i)).toBeInTheDocument();
    expect(screen.getByTestId("partograph-section")).toBeInTheDocument();
    expect(screen.getByTestId("ctg-section")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Back to vitals/i })).toHaveAttribute("href", "/ehr/patient-1/vitals");
  });
});
