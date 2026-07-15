import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import InsurancePlansPage from "./page";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn(), put: vi.fn() } }));
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;
const put = apiClient.put as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <InsurancePlansPage />
    </QueryClientProvider>,
  );
}

const PENDING_PLAN = {
  id: 1,
  insurerName: "PSMAS",
  planCode: "PSMAS-STD",
  planName: "Standard",
  coveragePct: 80,
  copayPct: 20,
  copayFixed: null,
  deductible: 0,
  annualCap: null,
  status: "PENDING_TERMS",
  effectiveFrom: "2026-01-01",
};

describe("InsurancePlansPage", () => {
  beforeEach(() => {
    get.mockReset();
    put.mockReset();
  });

  it("highlights pending plans and configures terms to activate", async () => {
    get.mockResolvedValue({ data: [PENDING_PLAN] });
    put.mockResolvedValue({ data: { ...PENDING_PLAN, status: "ACTIVE" } });

    renderPage();

    // Pending highlighted in the banner and the status badge.
    expect(await screen.findByText(/awaiting terms/i)).toBeInTheDocument();
    expect(screen.getByText("PENDING TERMS")).toBeInTheDocument();

    // Open the configure form and enter governed terms.
    await userEvent.click(screen.getByRole("button", { name: /configure & activate/i }));
    const inputs = screen.getAllByRole("spinbutton");
    await userEvent.type(inputs[0], "70"); // coverage %
    await userEvent.type(inputs[1], "30"); // co-pay %

    await userEvent.click(screen.getByRole("button", { name: /save terms & activate/i }));

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith(
        "/internal/v1/finance/insurance-plans/PSMAS-STD",
        expect.objectContaining({ coveragePct: 70, copayPct: 30 }),
      ),
    );
  });

  it("blocks submit when required terms are missing", async () => {
    get.mockResolvedValue({ data: [PENDING_PLAN] });
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /configure & activate/i }));
    // Submit with empty coverage/co-pay.
    await userEvent.click(screen.getByRole("button", { name: /save terms & activate/i }));

    expect(screen.getByText(/Coverage % and co-pay % are required/i)).toBeInTheDocument();
    expect(put).not.toHaveBeenCalled();
  });
});
