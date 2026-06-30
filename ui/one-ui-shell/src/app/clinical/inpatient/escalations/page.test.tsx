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

import InpatientEscalationsPage from "./page";

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <InpatientEscalationsPage />
    </QueryClientProvider>,
  );
}

describe("InpatientEscalationsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("prompts for a ward before querying (no fake data, honest empty state)", () => {
    renderPage();
    expect(
      screen.getByText("Enter a ward to see open escalations"),
    ).toBeInTheDocument();
    // No ward → no escalation query fired (query disabled).
    expect(get).not.toHaveBeenCalled();
  });

  it("renders real escalations bound to the BFF endpoint and wires acknowledge", async () => {
    get.mockResolvedValue({
      data: [
        {
          escalation_id: "esc-1",
          subject_cpid: "CPID-ZW-9",
          total_score: 7,
          risk_level: "HIGH",
          status: "RAISED",
          response_due_at: "2026-07-01T10:00:00Z",
        },
      ],
    });
    post.mockResolvedValue({ data: { status: "ACKNOWLEDGED" } });

    renderPage();
    fireEvent.change(screen.getByLabelText("Ward"), { target: { value: "ward-3" } });

    await waitFor(() => expect(screen.getByText(/EWS 7/)).toBeInTheDocument());
    expect(get).toHaveBeenCalledWith(
      "/internal/v1/inpatient/escalations?wardId=ward-3",
    );
    expect(screen.getByText(/Patient CPID-ZW-9/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Acknowledge" }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/inpatient/escalations/esc-1/acknowledge",
        {},
      ),
    );
  });

  it("shows an honest unavailable state when the service errors", async () => {
    get.mockRejectedValue(new Error("upstream down"));
    renderPage();
    fireEvent.change(screen.getByLabelText("Ward"), { target: { value: "ward-3" } });
    await waitFor(() =>
      expect(screen.getByText("Escalations unavailable")).toBeInTheDocument(),
    );
  });
});
