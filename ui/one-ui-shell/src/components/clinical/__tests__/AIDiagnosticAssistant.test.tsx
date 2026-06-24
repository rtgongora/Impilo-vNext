import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AIDiagnosticAssistant } from "../AIDiagnosticAssistant";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { post },
}));

function renderAssistant() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AIDiagnosticAssistant />
    </QueryClientProvider>,
  );
}

async function ask(text: string) {
  renderAssistant();
  const user = userEvent.setup();
  await user.type(screen.getByTestId("cds-complaint-input"), text);
  await user.click(screen.getByTestId("cds-submit"));
  return user;
}

describe("AIDiagnosticAssistant", () => {
  beforeEach(() => {
    post.mockReset();
  });

  it("renders the real backend answer with citations, support mode and an auditable trace", async () => {
    post.mockResolvedValue({
      data: {
        answer_summary: "Manage community-acquired pneumonia with amoxicillin per EDLIZ.",
        support_mode: "SOURCE_GROUNDED",
        source_citations: [{ section_id: "s1", page: 142, section_title: "Respiratory infections", excerpt: "CAP first-line…" }],
        warnings: [],
        recommendation: { medicine: { name: "Amoxicillin", dose: "500", unit: "mg", route: "PO" } },
        disclaimer: "Guideline support assists clinical judgment.",
        trace_id: "trace-123",
      },
    });

    await ask("adult productive cough and fever");

    await waitFor(() => expect(screen.getByTestId("cds-result")).toBeInTheDocument());
    expect(screen.getByTestId("cds-answer")).toHaveTextContent("community-acquired pneumonia");
    expect(screen.getByTestId("cds-support-mode")).toHaveTextContent(/Grounded/i);
    expect(screen.getByTestId("cds-citations")).toHaveTextContent("Respiratory infections");
    expect(screen.getByTestId("cds-citations")).toHaveTextContent("p.142");
    expect(screen.getByTestId("cds-recommendation")).toHaveTextContent("Amoxicillin");
    expect(screen.getByTestId("cds-trace")).toHaveTextContent("trace-123");

    // It forwarded the real backend contract (question + provider role), not a fabricated payload.
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/clinical/assistant/ask",
      expect.objectContaining({ question: "adult productive cough and fever", role: "PROVIDER" }),
    );
  });

  it("renders INSUFFICIENT_EVIDENCE honestly and never fabricates an answer", async () => {
    post.mockResolvedValue({
      data: {
        answer_summary: "No validated national guidance excerpt is indexed for that question yet.",
        support_mode: "INSUFFICIENT_EVIDENCE",
        source_citations: [],
        warnings: [],
        disclaimer: "Guideline support assists clinical judgment.",
      },
    });

    await ask("obscure unindexed question");

    await waitFor(() => expect(screen.getByTestId("cds-support-mode")).toBeInTheDocument());
    expect(screen.getByTestId("cds-support-mode")).toHaveTextContent(/No indexed evidence/i);
    expect(screen.getByTestId("cds-answer")).toHaveTextContent("No validated national guidance");
    // No invented citations or recommendation when evidence is insufficient.
    expect(screen.queryByTestId("cds-citations")).not.toBeInTheDocument();
    expect(screen.queryByTestId("cds-recommendation")).not.toBeInTheDocument();
  });

  it("fails honest on backend error — surfaces unavailability, no local fabrication", async () => {
    post.mockRejectedValue(new Error("service down"));

    await ask("any question");

    await waitFor(() => expect(screen.getByTestId("cds-error")).toBeInTheDocument());
    expect(screen.getByTestId("cds-error")).toHaveTextContent(/unavailable/i);
    expect(screen.queryByTestId("cds-result")).not.toBeInTheDocument();
  });
});
