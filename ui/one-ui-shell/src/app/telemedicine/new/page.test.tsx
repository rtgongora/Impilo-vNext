import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import NewTeleconsultPage from "./page";

const { push, createMutateAsync, post, put, get, uploadMutateAsync, searchParams } = vi.hoisted(() => ({
  push: vi.fn(),
  createMutateAsync: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  get: vi.fn(),
  uploadMutateAsync: vi.fn(),
  searchParams: { patientId: null as string | null, encounterId: null as string | null },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => ({
    get: (k: string) => (k === "patientId" ? searchParams.patientId : k === "encounterId" ? searchParams.encounterId : null),
  }),
}));

vi.mock("@/hooks/queries/useClinicalDocuments", () => ({
  useUploadDocumentFile: () => ({ mutateAsync: uploadMutateAsync, isPending: false }),
}));

vi.mock("@/hooks/queries/useSummary", () => ({
  usePatientSummary: () => ({ data: undefined, isLoading: false }),
}));

vi.mock("@/hooks/queries/useVitals", () => ({
  useEncounterVitals: () => ({ data: undefined, isLoading: false }),
}));

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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector?: (state: Record<string, unknown>) => unknown) => {
    const state = { user: { id: "user-1", displayName: "Dr. Moyo", email: "moyo@example.com" } };
    return selector ? selector(state) : state;
  },
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: Record<string, unknown>) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/lib/api-client", () => ({ apiClient: { get, post, put } }));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useCreateTelemedicineSession: () => ({ mutateAsync: createMutateAsync, isPending: false }),
}));

describe("NewTeleconsultPage", () => {
  beforeEach(() => {
    push.mockReset();
    createMutateAsync.mockReset();
    post.mockReset();
    put.mockReset();
    uploadMutateAsync.mockReset();
    searchParams.patientId = null;
    searchParams.encounterId = null;
    createMutateAsync.mockResolvedValue({ data: { id: "sess-9" } });
    post.mockImplementation((path: string) => {
      if (path.endsWith("/consent")) {
        return Promise.resolve({ data: { consentReference: "consent-1" } });
      }
      return Promise.resolve({ data: {} });
    });
    put.mockResolvedValue({ data: {} });
  });

  it("creates the session through the create mutation (with scheduled_at) and submits the referral", async () => {
    const user = userEvent.setup();

    render(<NewTeleconsultPage />);

    // ① Referral letter (required)
    await user.type(
      screen.getByPlaceholderText(/dear colleague/i),
      "Please advise on ongoing management.",
    );

    // ⑤ Routing (required)
    await user.click(screen.getByText("Routing"));
    await user.click(screen.getByText("General / Specialty Pool"));

    // ⑥ Consent (required) — recording consent creates the session via the mutation.
    await user.click(screen.getByText("Consent"));
    await user.click(screen.getByText("Digital (patient present)"));
    await user.click(screen.getByRole("button", { name: /record consent & issue token/i }));

    await screen.findByText("Consent recorded");

    expect(createMutateAsync).toHaveBeenCalledTimes(1);
    expect(createMutateAsync.mock.calls[0][0]).toEqual(
      expect.objectContaining({
        urgency: "ROUTINE",
        purpose_of_use: "TREATMENT",
        session_provider: "EXTERNAL_MANAGED",
        scheduled_at: expect.any(String),
      }),
    );
    // datetime-local default is passed through as an ISO instant.
    expect(() => new Date(String(createMutateAsync.mock.calls[0][0].scheduled_at)).toISOString()).not.toThrow();

    // Submit the referral package.
    await user.click(screen.getByRole("button", { name: /send referral/i }));

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith(
        "/internal/v1/teleconsult/sessions/sess-9/referral",
        expect.objectContaining({ routingType: "SPECIALTY_POOL", consentReference: "consent-1" }),
      ),
    );
    expect(post).toHaveBeenCalledWith("/internal/v1/teleconsult/sessions/sess-9/submit");
    expect(await screen.findByText("Referral Submitted Successfully")).toBeInTheDocument();
    // The session is created exactly once and reused for the whole flow.
    expect(createMutateAsync).toHaveBeenCalledTimes(1);
  }, 30000);

  it("uploads real attachments to the document service and submits their ids (G33)", async () => {
    const user = userEvent.setup();
    searchParams.patientId = "CPID-1";
    uploadMutateAsync.mockResolvedValue({ data: { id: "doc-uuid-1" } });

    render(<NewTeleconsultPage />);

    await user.type(screen.getByPlaceholderText(/dear colleague/i), "Letter");
    await user.click(screen.getByText("Attachments"));

    const file = new File(["x"], "xray.pdf", { type: "application/pdf" });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, file);

    await screen.findByText("xray.pdf");
    expect(uploadMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ patientId: "CPID-1", documentType: "REFERRAL_ATTACHMENT", title: "xray.pdf" }),
    );

    // Finish the required steps and submit — the referral carries the uploaded document id, not free text.
    await user.click(screen.getByText("Routing"));
    await user.click(screen.getByText("General / Specialty Pool"));
    await user.click(screen.getByText("Consent"));
    await user.click(screen.getByText("Digital (patient present)"));
    await user.click(screen.getByRole("button", { name: /record consent & issue token/i }));
    await screen.findByText("Consent recorded");
    await user.click(screen.getByRole("button", { name: /send referral/i }));

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith(
        "/internal/v1/teleconsult/sessions/sess-9/referral",
        expect.objectContaining({ attachments: ["doc-uuid-1"] }),
      ),
    );
  }, 30000);

  it("lets the referrer adjust the scheduled time before creating the session", async () => {
    const user = userEvent.setup();

    render(<NewTeleconsultPage />);

    const scheduledInput = screen.getByLabelText("Scheduled time");
    // fireEvent-style direct value change via userEvent clear+type is unreliable for
    // datetime-local; set the value through the change event instead.
    const { fireEvent } = await import("@testing-library/react");
    fireEvent.change(scheduledInput, { target: { value: "2026-08-01T09:30" } });

    await user.type(screen.getByPlaceholderText(/dear colleague/i), "Letter");
    await user.click(screen.getByText("Consent"));
    await user.click(screen.getByText("Verbal (documented)"));
    await user.click(screen.getByRole("button", { name: /record consent & issue token/i }));
    await screen.findByText("Consent recorded");

    expect(createMutateAsync).toHaveBeenCalledTimes(1);
    expect(String(createMutateAsync.mock.calls[0][0].scheduled_at)).toBe(
      new Date("2026-08-01T09:30").toISOString(),
    );
  }, 30000);
});
