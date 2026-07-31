import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatreSurgicalDischargePanel } from "./TheatreSurgicalDischargePanel";

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { get: (u: string) => get(u), post: (u: string, b: unknown) => post(u, b) } }));

describe("TheatreSurgicalDischargePanel (draft → complete, pending histopath)", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("shows an honest empty state when no discharge exists ({status:NONE})", async () => {
    get.mockResolvedValue({ data: { status: "NONE" } });
    render(<TheatreSurgicalDischargePanel caseId="c-1" />);
    expect(await screen.findByText(/No discharge summary started/i)).toBeInTheDocument();
  });

  it("distinguishes GET failure from genuine {status:NONE} and preserves last good record", async () => {
    get.mockResolvedValueOnce({
      data: { status: "DRAFT", final_diagnosis: "Kept diagnosis" },
    });
    render(<TheatreSurgicalDischargePanel caseId="c-keep" />);
    expect(await screen.findByDisplayValue("Kept diagnosis")).toBeInTheDocument();

    get.mockRejectedValueOnce({ status: 502, error: { message: "upstream down" } });
    fireEvent.click(screen.getByText("Refresh"));
    expect(await screen.findByTestId("discharge-load-unavailable")).toHaveTextContent(/do not treat this as no summary started/i);
    expect(screen.getByDisplayValue("Kept diagnosis")).toBeInTheDocument();
    expect(screen.queryByText(/No discharge summary started/i)).not.toBeInTheDocument();
  });

  it("saves a draft carrying the structured summary against the real endpoint", async () => {
    get.mockResolvedValue({ data: { status: "NONE" } });
    post.mockResolvedValue({ data: { status: "DRAFT" } });
    render(<TheatreSurgicalDischargePanel caseId="c-7" />);
    await screen.findByText(/No discharge summary started/i);
    fireEvent.change(screen.getByTestId("discharge-final-diagnosis"), { target: { value: "Acute appendicitis" } });
    fireEvent.click(screen.getByTestId("discharge-save-draft"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-7/discharge", expect.objectContaining({ finalDiagnosis: "Acute appendicitis" })),
    );
  });

  it("surfaces COMPLETED status, the Butano ref, and the pending-pathology snapshot", async () => {
    get.mockResolvedValue({
      data: {
        status: "COMPLETED",
        final_diagnosis: "Acute appendicitis",
        butano_document_ref: "shr-doc-123",
        pending_pathology: [{ specimen_id: "sp-1" }],
      },
    });
    render(<TheatreSurgicalDischargePanel caseId="c-9" />);
    expect(await screen.findByTestId("discharge-completed")).toHaveTextContent(/shr-doc-123/);
    expect(screen.getByTestId("discharge-pending-pathology")).toHaveTextContent(/1 specimen/i);
  });
});
