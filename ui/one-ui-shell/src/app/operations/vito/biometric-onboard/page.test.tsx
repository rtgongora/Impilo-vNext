import type { ReactNode } from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BiometricOnboardWorkspace from "./page";

vi.mock("next/link", () => ({
  default: ({ children, ...props }: { children: ReactNode; [k: string]: unknown }) => (
    <a {...props}>{children}</a>
  ),
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

const extractMut = { mutateAsync: vi.fn(), isPending: false };
const enrollMut = { mutateAsync: vi.fn(), isPending: false };

vi.mock("@/hooks/queries/useAbisBiometric", () => ({
  useExtractTemplate: () => extractMut,
  useEnrollTemplate: () => enrollMut,
  fileToBase64: vi.fn(async () => "SGVsbG8="),
}));

function file() {
  return new File(["x"], "print.png", { type: "image/png" });
}

describe("BiometricOnboardWorkspace", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    extractMut.mutateAsync.mockResolvedValue({ ok: true, templateBase64: "VEVNUExBVEU=", quality: 80 });
  });

  it("capture → extract → dedup → enrol with no duplicate", async () => {
    enrollMut.mutateAsync.mockResolvedValue({ subjectRef: "subject-1", status: "ACTIVE", version: 1, duplicateReview: null });
    render(<BiometricOnboardWorkspace />);

    fireEvent.change(screen.getByTestId("subject-ref"), { target: { value: "subject-1" } });
    fireEvent.change(screen.getByTestId("capture-file"), { target: { files: [file()] } });
    await waitFor(() => expect(screen.getByTestId("extracted-ok")).toBeTruthy());

    fireEvent.click(screen.getByTestId("enrol-btn"));
    await waitFor(() => expect(screen.getByTestId("enrolled-clean")).toBeTruthy());
    expect(screen.queryByTestId("duplicate-review")).toBeNull();
  });

  it("opens a duplicate-investigation case when dedup trips (never auto-merge)", async () => {
    enrollMut.mutateAsync.mockResolvedValue({
      subjectRef: "subject-1",
      status: "ACTIVE",
      version: 1,
      duplicateReview: { caseId: 42, candidateCount: 2, topScore: 0.93, status: "OPEN" },
    });
    render(<BiometricOnboardWorkspace />);

    fireEvent.change(screen.getByTestId("subject-ref"), { target: { value: "subject-1" } });
    fireEvent.change(screen.getByTestId("capture-file"), { target: { files: [file()] } });
    await waitFor(() => expect(screen.getByTestId("extracted-ok")).toBeTruthy());

    fireEvent.click(screen.getByTestId("enrol-btn"));
    await waitFor(() => expect(screen.getByTestId("duplicate-review")).toBeTruthy());
    expect(screen.getByTestId("duplicate-review").textContent).toContain("Possible duplicate");
    expect(screen.getByTestId("review-link")).toBeTruthy();
    expect(screen.queryByTestId("enrolled-clean")).toBeNull();
  });
});
