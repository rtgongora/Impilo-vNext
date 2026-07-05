import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/workspace/PlaneWorkspaceShell", () => ({
  PlaneWorkspaceShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/components/experience/TrustContextBanner", () => ({
  TrustContextBanner: () => <div data-testid="trust-banner" />,
}));

vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({
  NompiloContextualGuidance: () => <div data-testid="nompilo" />,
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

import InpatientDischargeBoardPage from "./page";

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <InpatientDischargeBoardPage />
    </QueryClientProvider>,
  );
}

describe("InpatientDischargeBoardPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("lists only ADMITTED inpatients from the real admissions endpoint", async () => {
    get.mockResolvedValue({
      data: [
        { id: "ADM-1", subject_cpid: "CPID-ZW-1", status: "ADMITTED", encounter_id: "ENC-1" },
        { id: "ADM-2", subject_cpid: "CPID-ZW-2", status: "DISCHARGED", encounter_id: "ENC-2" },
      ],
    });

    renderPage();

    await waitFor(() => expect(screen.getByText("Patient CPID-ZW-1")).toBeInTheDocument());
    // Discharged patient must not appear on the discharge planning board.
    expect(screen.queryByText("Patient CPID-ZW-2")).not.toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/inpatient/admissions");
  });

  it("opens the clearance-gated discharge summary panel and finalises via the BFF", async () => {
    get.mockImplementation((path: string) => {
      if (path.startsWith("/internal/v1/inpatient/admissions")) {
        return Promise.resolve({
          data: [{ id: "ADM-1", subject_cpid: "CPID-ZW-1", status: "ADMITTED", encounter_id: "ENC-1" }],
        });
      }
      // discharge-summary lookup
      return Promise.resolve({
        data: { id: "ds-1", encounter_id: "ENC-1", status: "DRAFT", discharge_diagnosis: "Pneumonia" },
      });
    });
    post.mockResolvedValue({ data: { status: "FINALISED" } });

    renderPage();
    await waitFor(() => expect(screen.getByText("Patient CPID-ZW-1")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Discharge summary" }));
    await waitFor(() => expect(screen.getByText("Pneumonia")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Finalise discharge summary" }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/inpatient/discharge-summary/ENC-1/finalise",
        {},
      ),
    );
  });

  it("shows an honest unavailable state when the service errors", async () => {
    get.mockRejectedValue(new Error("upstream down"));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText("Discharge board unavailable")).toBeInTheDocument(),
    );
  });

  it("blocks finalise while a required countersignature is missing and countersigns via the BFF", async () => {
    get.mockImplementation((path: string) => {
      if (path.startsWith("/internal/v1/inpatient/admissions")) {
        return Promise.resolve({
          data: [{ id: "ADM-1", subject_cpid: "CPID-ZW-1", status: "ADMITTED", encounter_id: "ENC-1" }],
        });
      }
      return Promise.resolve({
        data: {
          id: "ds-1",
          encounter_id: "ENC-1",
          status: "DRAFT",
          discharge_diagnosis: "Pneumonia",
          countersign_required: true,
          countersigned_by: null,
        },
      });
    });
    post.mockResolvedValue({ data: { status: "DRAFT", countersigned_by: "supervisor-1" } });

    renderPage();
    await waitFor(() => expect(screen.getByText("Patient CPID-ZW-1")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Discharge summary" }));

    // Finalise must be disabled and labelled honestly while countersign is missing.
    const blocked = await screen.findByRole("button", {
      name: "Countersignature required before finalise",
    });
    expect(blocked).toBeDisabled();
    expect(screen.getByText("required — not yet countersigned")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Countersign attestation"), {
      target: { value: "Reviewed and agreed" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Countersign summary" }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/inpatient/discharge-summary/ENC-1/countersign",
        { attestation: "Reviewed and agreed" },
      ),
    );
  });

  it("shows countersigned state and enables finalise once countersigned", async () => {
    get.mockImplementation((path: string) => {
      if (path.startsWith("/internal/v1/inpatient/admissions")) {
        return Promise.resolve({
          data: [{ id: "ADM-1", subject_cpid: "CPID-ZW-1", status: "ADMITTED", encounter_id: "ENC-1" }],
        });
      }
      return Promise.resolve({
        data: {
          id: "ds-1",
          encounter_id: "ENC-1",
          status: "DRAFT",
          countersign_required: true,
          countersigned_by: "supervisor-1",
        },
      });
    });

    renderPage();
    await waitFor(() => expect(screen.getByText("Patient CPID-ZW-1")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Discharge summary" }));

    await waitFor(() =>
      expect(screen.getByText("countersigned by supervisor-1")).toBeInTheDocument(),
    );
    expect(screen.getByRole("button", { name: "Finalise discharge summary" })).toBeEnabled();
  });
});
