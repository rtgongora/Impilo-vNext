import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
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

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <TheatreCaseDetailPage />
    </QueryClientProvider>,
  );
}

describe("TheatreCaseDetailPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url.endsWith("/safety-events")) return Promise.resolve([]);
      if (url.endsWith("/note")) return Promise.resolve({ status: "NONE" });
      if (url.includes("/surgery/specialties/templates")) return Promise.resolve([]);
      if (/\/theatre\/cases\/c-1$/.test(url)) {
        return Promise.resolve({
          id: "c-1", patient_id: "CPID-1", procedure_name: "Appendectomy", status: "BOOKED", triage_priority: "URGENT", surgeon_id: "surgeon-1",
          checklist: [{ id: "i1", phase: "SIGN_IN", item_code: "CONSENT", item_label: "Consent verified", completed: false }],
          returns_to_theatre: [],
        });
      }
      return Promise.resolve({ data: [] });
    });
  });

  it("renders the real case with status, triage and the WHO checklist", async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    // §19: the persistent banner shows the human stage label for the status
    expect(screen.getByTestId("banner-stage")).toHaveTextContent("Booked");
    expect(screen.getByText("Consent verified")).toBeInTheDocument();
    expect(screen.getByText(/WHO Surgical Safety Checklist/)).toBeInTheDocument();
  });

  it("§19 shows the persistent context banner with an unconfirmed-allergy safety flag", async () => {
    renderPage();
    await waitFor(() => expect(screen.getByTestId("theatre-case-banner")).toBeInTheDocument());
    expect(screen.getByTestId("theatre-case-banner").className).toContain("sticky");
    expect(screen.getByTestId("banner-allergies")).toHaveTextContent(/unconfirmed/i);
    expect(screen.getByTestId("banner-site")).toHaveTextContent(/not marked/i);
  });

  it("§19 the operative note carries a DRAFT document-state badge until signed", async () => {
    renderPage();
    await waitFor(() => expect(screen.getByTestId("doc-state-badge")).toBeInTheDocument());
    expect(screen.getByTestId("doc-state-badge")).toHaveAttribute("data-state", "DRAFT");
  });

  it("§19 focus mode auto-hides surrounding sections when a note field is focused", async () => {
    // NB: jsdom does not load Tailwind CSS, so the `hidden` utility (display:none) is asserted
    // via the class rather than computed visibility.
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    const whoSection = () => screen.getByText(/WHO Surgical Safety Checklist/).closest("section")!;
    // the WHO checklist section is shown before entering data
    expect(whoSection().className).not.toContain("hidden");
    await userEvent.click(screen.getByText("Procedure performed").parentElement!.querySelector("input")!);
    // focusing a note field enables focus mode → surrounding sections collapse (hidden), banner stays
    await waitFor(() => expect(screen.getByRole("button", { name: /Show all sections/ })).toBeInTheDocument());
    expect(screen.getByTestId("theatre-case-banner")).toBeVisible();
    expect(whoSection().className).toContain("hidden");
    // the operative-note surface itself stays visible during entry
    expect(screen.getByTestId("operative-note-section").className).not.toContain("hidden");
  });

  it("evaluate readiness calls the BFF and shows owner blockers (no fake READY)", async () => {
    post.mockResolvedValueOnce({ bookable: false, checks: [{ domain: "ROOM", owner_service: "inpatient", status: "BLOCKED" }], blockers: [{ code: "NO_ROOM", message: "No theatre room assigned" }] });
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: /Evaluate readiness/ }));
    await waitFor(() => expect(screen.getByText(/Not bookable yet/)).toBeInTheDocument());
    expect(screen.getByText("No theatre room assigned")).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/readiness", {});
  });

  it("booking with an override sends the audited reason (no dead button)", async () => {
    post.mockResolvedValue({ status: "BOOKED" });
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    await userEvent.type(screen.getByText("Emergency override reason").parentElement!.querySelector("input")!, "Life-threatening");
    await userEvent.click(screen.getByRole("button", { name: /Book with override/ }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/book", { emergencyOverride: true, emergencyOverrideReason: "Life-threatening" }),
    );
  });

  it("cancellation sends a structured reason code (§15 cancelled case → rescheduling)", async () => {
    post.mockResolvedValue({ status: "CANCELLED", reason_code: "NO_BLOOD", reschedulable: true });
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByText("Reason code").parentElement!.querySelector("select")!, "NO_BLOOD");
    await userEvent.type(screen.getByText("Notes").parentElement!.querySelector("input")!, "cross-match not ready");
    await userEvent.click(screen.getByRole("button", { name: /Cancel .*release/ }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/cancel", { reasonCode: "NO_BLOOD", reason: "cross-match not ready" }),
    );
  });

  it("mounts the Lane 2/3 management panels bound to their real endpoints", async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    expect(screen.getByTestId("blood-panel")).toBeInTheDocument();
    expect(screen.getByTestId("specimen-panel")).toBeInTheDocument();
    expect(screen.getByTestId("count-panel")).toBeInTheDocument();
    expect(screen.getByTestId("anaesthesia-chart-panel")).toBeInTheDocument();
    expect(screen.getByTestId("commodities-panel")).toBeInTheDocument();
    // each panel drove its own GET against the real path
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/blood");
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/counts");
    expect(get).toHaveBeenCalledWith("/internal/v1/procedures/c-1/anaesthesia/chart");
  });

  it("gates Lane 1 surfaces: no obstetric/emergency-consent panel for an elective non-obstetric case", async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    expect(screen.queryByTestId("obstetric-section")).not.toBeInTheDocument();
    expect(screen.queryByTestId("emergency-consent-panel")).not.toBeInTheDocument();
  });

  it("shows the Lane 1 obstetric + emergency-consent surfaces for an EMERGENCY caesarean", async () => {
    get.mockImplementation((url: string) => {
      if (url.endsWith("/safety-events")) return Promise.resolve([]);
      if (url.endsWith("/note")) return Promise.resolve({ status: "NONE" });
      if (/\/theatre\/cases\/c-1$/.test(url)) {
        return Promise.resolve({ id: "c-1", patient_id: "CPID-1", procedure_name: "Emergency Caesarean Section", status: "IN_PROGRESS", triage_priority: "EMERGENCY", emergency_override: true, consent_status: "EMERGENCY_EXCEPTION", checklist: [], returns_to_theatre: [] });
      }
      return Promise.resolve({ data: [] });
    });
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    expect(screen.getByTestId("obstetric-section")).toBeInTheDocument();
    expect(screen.getByTestId("emergency-consent-panel")).toBeInTheDocument();
  });

  it("SB-5 draft note POST includes operative depth fields", async () => {
    post.mockResolvedValue({});
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    await userEvent.type(screen.getByTestId("note-performed-procedure"), "Appendicectomy");
    await userEvent.type(screen.getByTestId("note-patient-position"), "Supine");
    await userEvent.selectOptions(screen.getByTestId("note-wound-classification"), "CLEAN_CONTAMINATED");
    await userEvent.click(screen.getByTestId("note-save"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/theatre/cases/c-1/note",
        expect.objectContaining({
          performedProcedure: "Appendicectomy",
          patientPosition: "Supine",
          woundClassification: "CLEAN_CONTAMINATED",
        }),
      ),
    );
  });

  it("a failed note GET renders unavailable, not an empty draft pretending no note exists", async () => {
    get.mockImplementation((url: string) => {
      if (url.endsWith("/safety-events")) return Promise.resolve([]);
      if (url.endsWith("/note")) return Promise.reject({ status: 502 });
      if (/\/theatre\/cases\/c-1$/.test(url)) {
        return Promise.resolve({
          id: "c-1", patient_id: "CPID-1", procedure_name: "Appendectomy", status: "BOOKED", triage_priority: "URGENT", checklist: [],
        });
      }
      return Promise.resolve({ data: [] });
    });
    renderPage();
    await waitFor(() => expect(screen.getByTestId("operative-note-unavailable")).toBeInTheDocument());
    expect(screen.getByTestId("operative-note-unavailable")).toHaveTextContent(/Could not read the operative note/);
  });

  it("disables save when the operative template pair is half-set", async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText("CPID-1")).toBeInTheDocument());
    await userEvent.type(screen.getByTestId("note-performed-procedure"), "Appendicectomy");
    await userEvent.type(screen.getByTestId("note-template-ref"), "3f9a1c2e-4b5d-4a6f-9c7e-8d0f1a2b3c4d");
    expect(screen.getByTestId("note-save")).toBeDisabled();
    expect(screen.getByTestId("note-template-pair-required")).toBeInTheDocument();
    await userEvent.type(screen.getByTestId("note-template-code"), "APPENDECTOMY");
    expect(screen.getByTestId("note-save")).not.toBeDisabled();
  });

  it("shows the return-to-theatre panel when the case is in PACU", async () => {
    get.mockImplementation((url: string) => {
      if (url.endsWith("/safety-events")) return Promise.resolve([]);
      if (url.endsWith("/note")) return Promise.resolve({ status: "NONE" });
      if (/\/theatre\/cases\/c-1$/.test(url)) {
        return Promise.resolve({
          id: "c-1", patient_id: "CPID-1", procedure_name: "Appendectomy", status: "PACU", triage_priority: "URGENT", checklist: [],
          returns_to_theatre: [{ id: "r-1", seq: 1, complication_category: "SEPSIS", reason: "Washout", planned: false }],
        });
      }
      return Promise.resolve({ data: [] });
    });
    renderPage();
    await waitFor(() => expect(screen.getByTestId("return-to-theatre-panel")).toBeInTheDocument());
    expect(screen.getByTestId("return-to-theatre-list")).toHaveTextContent("SEPSIS");
  });
});
