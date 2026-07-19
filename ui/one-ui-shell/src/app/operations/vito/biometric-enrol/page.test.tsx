import type { ReactNode } from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BiometricEnrolWorkspace from "./page";

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

const extractMut = { mutateAsync: vi.fn(), isPending: false };
const enrollMut = { mutateAsync: vi.fn(), isPending: false };
const verifyMut = { mutateAsync: vi.fn(), isPending: false };

vi.mock("@/hooks/queries/useAbisBiometric", () => ({
  useExtractTemplate: () => extractMut,
  useEnrollTemplate: () => enrollMut,
  useVerifyTemplate: () => verifyMut,
  fileToBase64: vi.fn(async () => "SGVsbG8="),
}));

function file() {
  return new File(["x"], "print.png", { type: "image/png" });
}

describe("BiometricEnrolWorkspace", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    extractMut.mutateAsync.mockResolvedValue({ ok: true, templateBase64: "VEVNUExBVEU=", quality: 80 });
    enrollMut.mutateAsync.mockResolvedValue({ subjectRef: "subject-1", status: "ACTIVE" });
    verifyMut.mutateAsync.mockResolvedValue({ result: "MATCH", confidence: 0.97, summary: "score=420" });
  });

  it("captures → enrols → verifies and shows a real MATCH decision", async () => {
    render(<BiometricEnrolWorkspace />);

    fireEvent.change(screen.getByTestId("subject-ref"), { target: { value: "subject-1" } });

    // enrol capture → extract → enrol
    fireEvent.change(screen.getByTestId("enrol-file"), { target: { files: [file()] } });
    await waitFor(() => expect(screen.getByTestId("enrol-btn")).toBeTruthy());
    fireEvent.click(screen.getByTestId("enrol-btn"));
    await waitFor(() => expect(screen.getByTestId("enrolled-ok")).toBeTruthy());
    expect(enrollMut.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ subjectRef: "subject-1", modality: "FINGERPRINT", templateBase64: "VEVNUExBVEU=" }),
    );

    // verify capture → extract → verify → MATCH
    fireEvent.change(screen.getByTestId("verify-file"), { target: { files: [file()] } });
    await waitFor(() => expect(screen.getByTestId("verify-result").textContent).toContain("MATCH"));
    expect(verifyMut.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ subjectRef: "subject-1", probeBase64: "VEVNUExBVEU=" }),
    );
  });

  it("surfaces a NO_MATCH decision honestly", async () => {
    verifyMut.mutateAsync.mockResolvedValue({ result: "NO_MATCH", confidence: 0.1 });
    render(<BiometricEnrolWorkspace />);
    fireEvent.change(screen.getByTestId("subject-ref"), { target: { value: "subject-1" } });
    fireEvent.change(screen.getByTestId("enrol-file"), { target: { files: [file()] } });
    await waitFor(() => expect(screen.getByTestId("enrol-btn")).toBeTruthy());
    fireEvent.click(screen.getByTestId("enrol-btn"));
    await waitFor(() => expect(screen.getByTestId("enrolled-ok")).toBeTruthy());
    fireEvent.change(screen.getByTestId("verify-file"), { target: { files: [file()] } });
    await waitFor(() => expect(screen.getByTestId("verify-result").textContent).toContain("NO_MATCH"));
  });
});
