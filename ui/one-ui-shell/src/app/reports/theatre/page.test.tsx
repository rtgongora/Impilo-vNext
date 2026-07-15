import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import TheatreUtilisationReportPage from "./page";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { post, get: vi.fn() } }));

describe("TheatreUtilisationReportPage", () => {
  it("runs the theatre-utilisation report and renders reconciled KPI tiles", async () => {
    post.mockResolvedValue({
      status: "COMPLETED",
      result: JSON.stringify([
        {
          total_cases: 5,
          completed_cases: 4,
          cancelled_cases: 1,
          cancellation_rate_pct: 20,
          anaesthesia_cases: 4,
          returns_to_theatre: 1,
          unplanned_icu_transfers: 1,
          complication_cases: 2,
          blood_units_used: 3,
          implants_used: 2,
          consumables_used: 9,
          avg_theatre_minutes: 72.5,
        },
      ]),
    });

    render(<TheatreUtilisationReportPage />);

    expect(screen.getByRole("heading", { level: 1, name: /Theatre Utilisation/i })).toBeInTheDocument();
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/reports/theatre-utilisation/run", {}),
    );
    // Metrics reconcile with the cases run.
    await waitFor(() => expect(screen.getByText("Total cases")).toBeInTheDocument());
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("20%")).toBeInTheDocument();
    expect(screen.getByText("72.5 min")).toBeInTheDocument();
  });

  it("shows an error when the report run fails", async () => {
    post.mockRejectedValue(new Error("reporting unavailable"));
    render(<TheatreUtilisationReportPage />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/reporting unavailable/i));
  });
});
