import type { ReactNode } from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import WalkInPage from "./page";

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <WalkInPage />
    </QueryClientProvider>,
  );
}

const { push, post } = vi.hoisted(() => ({
  push: vi.fn(),
  post: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams("patientId=patient-1"),
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

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatients: () => ({ data: { data: [] }, isLoading: false }),
  usePatient: () => ({
    data: {
      data: {
        id: "patient-1",
        attributes: {
          displayName: "Tariro Moyo",
          cpid: "CP-001",
          dateOfBirth: "1991-06-11",
          gender: "female",
        },
      },
    },
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post,
  },
}));

describe("WalkInPage", () => {
  beforeEach(() => {
    push.mockReset();
    post.mockReset();
    post.mockResolvedValue({
      data: { id: "entry-1" },
      meta: { journey_id: "journey-42", core_transaction_id: "journey-42" },
    });
  });

  it("can receive a patient from search and returns an explicit queue receipt", async () => {
    const user = userEvent.setup();

    renderPage();

    expect((await screen.findAllByText("Selected patient")).length).toBeGreaterThan(0);
    expect(screen.getByText("Tariro Moyo")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Add to Queue" }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/queue/entries",
        expect.objectContaining({
          patient_id: "patient-1",
          facility_id: "facility-1",
          queue_type: "WALK_IN",
        }),
      ),
    );
    expect(await screen.findByText("The patient is ready for the next step")).toBeInTheDocument();
    expect(screen.getByText("entry-1")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: /open patient encounter/i }));
    expect(push).toHaveBeenCalledWith(
      "/ehr/patient-1/encounters?journey_id=journey-42&transaction_id=journey-42",
    );
  });
});

describe("WalkInPage biometric check-in", () => {
  beforeEach(() => {
    push.mockReset();
    post.mockReset();
  });

  async function captureProbe() {
    // Patient is preselected via useSearchParams(patientId=patient-1) + usePatient.
    const file = new File(["x"], "finger.png", { type: "image/png" });
    fireEvent.change(await screen.findByTestId("biometric-probe-file"), { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId("biometric-captured")).toBeInTheDocument());
  }

  it("sends the captured probe with the queue-entry check-in", async () => {
    post.mockImplementation((url: string) => {
      if (url === "/internal/v1/identity/biometric/abis/extract") {
        return Promise.resolve({ ok: true, templateBase64: "PPROBE==" });
      }
      return Promise.resolve({ data: { id: "entry-1" }, meta: {} });
    });

    renderPage();
    await captureProbe();

    fireEvent.click(screen.getByRole("button", { name: "Add to Queue" }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/queue/entries",
        expect.objectContaining({
          patient_id: "patient-1",
          patient_cpid: "CP-001",
          biometric_subject_ref: "CP-001",
          biometric_modality: "FINGERPRINT",
          biometric_probe_base64: "PPROBE==",
        }),
      ),
    );
  });

  it("surfaces a 4xx NO_MATCH from the service as a blocking error", async () => {
    post.mockImplementation((url: string) => {
      if (url === "/internal/v1/identity/biometric/abis/extract") {
        return Promise.resolve({ ok: true, templateBase64: "PPROBE==" });
      }
      return Promise.reject({ status: 422, error: { message: "Biometric did not match the patient on file." } });
    });

    renderPage();
    await captureProbe();

    fireEvent.click(screen.getByRole("button", { name: "Add to Queue" }));

    await waitFor(() =>
      expect(screen.getByText(/did not match the patient on file/i)).toBeInTheDocument(),
    );
    expect(push).not.toHaveBeenCalled();
  });
});
