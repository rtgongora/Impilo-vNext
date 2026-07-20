import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { EnrolBiometricStep } from "./EnrolBiometricStep";

const post = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: { post: (...a: unknown[]) => post(...a) },
}));
vi.mock("next/link", () => ({
  default: ({ children, ...props }: { children: ReactNode; [k: string]: unknown }) => <a {...props}>{children}</a>,
}));

const CPID = "cpid-abc-123";

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

function file() {
  return new File(["x"], "print.png", { type: "image/png" });
}

describe("EnrolBiometricStep", () => {
  beforeEach(() => post.mockReset());

  it("captures → extracts → enrols under subjectRef=cpid with no duplicate", async () => {
    post
      .mockResolvedValueOnce({ ok: true, templateBase64: "TPL==", quality: 82 }) // /extract
      .mockResolvedValueOnce({ subjectRef: CPID, status: "ACTIVE", version: 1, duplicateReview: null }); // /enroll

    wrap(<EnrolBiometricStep subjectRef={CPID} subjectLabel="Jane Doe" />);
    fireEvent.change(screen.getByTestId("enrol-capture-file"), { target: { files: [file()] } });
    await waitFor(() => expect(screen.getByTestId("enrol-extracted-ok")).toBeInTheDocument());

    fireEvent.click(screen.getByTestId("enrol-btn"));
    await waitFor(() => expect(screen.getByTestId("enrol-clean")).toBeInTheDocument());

    // extract first, then enroll — enroll carries subjectRef = the patient's CPID.
    expect(post).toHaveBeenNthCalledWith(1, "/internal/v1/identity/biometric/abis/extract", expect.any(Object));
    expect(post).toHaveBeenNthCalledWith(
      2,
      "/internal/v1/identity/biometric/abis/enroll",
      expect.objectContaining({ subjectRef: CPID, modality: "FINGERPRINT", templateBase64: "TPL==" }),
    );
    expect(screen.queryByTestId("enrol-duplicate-review")).toBeNull();
  });

  it("surfaces the duplicateReview case (never auto-merges) with an adjudication link", async () => {
    post
      .mockResolvedValueOnce({ ok: true, templateBase64: "TPL==", quality: 80 })
      .mockResolvedValueOnce({
        subjectRef: CPID,
        status: "ACTIVE",
        version: 1,
        duplicateReview: { caseId: 77, candidateCount: 2, topScore: 0.94, status: "OPEN" },
      });

    wrap(<EnrolBiometricStep subjectRef={CPID} />);
    fireEvent.change(screen.getByTestId("enrol-capture-file"), { target: { files: [file()] } });
    await waitFor(() => expect(screen.getByTestId("enrol-extracted-ok")).toBeInTheDocument());

    fireEvent.click(screen.getByTestId("enrol-btn"));
    const panel = await screen.findByTestId("enrol-duplicate-review");
    expect(panel.textContent).toContain("Possible duplicate");
    expect(screen.getByTestId("enrol-review-link")).toHaveAttribute(
      "href",
      "/operations/vito/adjudication/77",
    );
    expect(screen.queryByTestId("enrol-clean")).toBeNull();
  });

  it("surfaces a sub-quality 422 honestly (no fake success)", async () => {
    post
      .mockResolvedValueOnce({ ok: true, templateBase64: "TPL==", quality: 30 })
      // Plain-object rejection (not `new Error`) so vitest's unhandled-rejection detector stays quiet.
      .mockRejectedValueOnce({ status: 422, error: { message: "capture quality below threshold" } });

    wrap(<EnrolBiometricStep subjectRef={CPID} />);
    fireEvent.change(screen.getByTestId("enrol-capture-file"), { target: { files: [file()] } });
    await waitFor(() => expect(screen.getByTestId("enrol-extracted-ok")).toBeInTheDocument());

    fireEvent.click(screen.getByTestId("enrol-btn"));
    const err = await screen.findByTestId("enrol-error");
    expect(err.textContent).toContain("capture quality below threshold");
    expect(screen.queryByTestId("enrol-clean")).toBeNull();
  });
});
