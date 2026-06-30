import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TheatreCaseDetailPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("next/navigation", () => ({ useParams: () => ({ id: "c-1" }) }));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (<div><h1>{title}</h1>{children}</div>),
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({ NompiloContextualGuidance: () => <div data-testid="nompilo" /> }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("TheatreCaseDetailPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url.endsWith("/safety-events")) return Promise.resolve([]);
      return Promise.resolve({
        id: "c-1", patient_id: "CPID-1", procedure_name: "Appendectomy", status: "BOOKED", triage_priority: "URGENT", surgeon_id: "surgeon-1",
        checklist: [{ id: "i1", phase: "SIGN_IN", item_code: "CONSENT", item_label: "Consent verified", completed: false }],
      });
    });
  });

  it("renders the real case with status, triage and the WHO checklist", async () => {
    render(<TheatreCaseDetailPage />);
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    expect(screen.getByText("BOOKED")).toBeInTheDocument();
    expect(screen.getByText("Consent verified")).toBeInTheDocument();
    expect(screen.getByText(/WHO Surgical Safety Checklist/)).toBeInTheDocument();
  });

  it("evaluate readiness calls the BFF and shows owner blockers (no fake READY)", async () => {
    post.mockResolvedValueOnce({ bookable: false, checks: [{ domain: "ROOM", owner_service: "inpatient", status: "BLOCKED" }], blockers: [{ code: "NO_ROOM", message: "No theatre room assigned" }] });
    render(<TheatreCaseDetailPage />);
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: /Evaluate readiness/ }));
    await waitFor(() => expect(screen.getByText(/Not bookable yet/)).toBeInTheDocument());
    expect(screen.getByText("No theatre room assigned")).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/readiness", {});
  });

  it("booking with an override sends the audited reason (no dead button)", async () => {
    post.mockResolvedValue({ status: "BOOKED" });
    render(<TheatreCaseDetailPage />);
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    await userEvent.type(screen.getByText("Emergency override reason").parentElement!.querySelector("input")!, "Life-threatening");
    await userEvent.click(screen.getByRole("button", { name: /Book with override/ }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/book", { emergencyOverride: true, emergencyOverrideReason: "Life-threatening" }),
    );
  });
});
