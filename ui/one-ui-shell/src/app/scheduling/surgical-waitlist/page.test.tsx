import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SurgicalWaitlistPage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (<div><h1>{title}</h1>{children}</div>),
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({ NompiloContextualGuidance: () => <div data-testid="nompilo" /> }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));

describe("SurgicalWaitlistPage", () => {
  beforeEach(() => { get.mockReset(); });

  it("renders real waitlist entries with escalation and wait days", async () => {
    get.mockResolvedValueOnce([
      { id: "w-1", patientId: "CPID-1", zibo: "ZIBO-CHOLE", clinicalPriority: "P2", waitDays: 40, targetDate: "2026-05-01", escalationState: "BREACHED", status: "WAITING" },
    ]);
    render(<SurgicalWaitlistPage />);
    await waitFor(() => expect(screen.getByText("ZIBO-CHOLE")).toBeInTheDocument());
    expect(screen.getByText("BREACHED")).toBeInTheDocument();
    expect(screen.getByText("40")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/scheduling/surgical-waitlist?status=WAITING&sort=wait_days");
  });

  it("shows an honest empty state", async () => {
    get.mockResolvedValueOnce([]);
    render(<SurgicalWaitlistPage />);
    await waitFor(() => expect(screen.getByText(/No patients are currently waiting/)).toBeInTheDocument());
  });

  it("surfaces a real error", async () => {
    get.mockRejectedValueOnce({ error: { message: "Scheduling service unavailable" } });
    render(<SurgicalWaitlistPage />);
    await waitFor(() => expect(screen.getByText(/Scheduling service unavailable/)).toBeInTheDocument());
  });
});
