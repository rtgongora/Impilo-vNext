import type { ReactNode } from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CoveragePage from "./page";

const { get, post, put } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));

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

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post, put },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CoveragePage />
    </QueryClientProvider>
  );
}

describe("CoveragePage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    put.mockReset();
    post.mockResolvedValue({ data: { ok: true } });
    put.mockResolvedValue({ data: { ok: true } });
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/plans")) {
        return Promise.resolve({
          data: [
            {
              id: "plan-1",
              type: "CoveragePlan",
              attributes: {
                plan_code: "P1",
                plan_name: "Test Plan",
                payer_id: "pay-x",
                plan_type: "STANDARD",
                status: "ACTIVE",
              },
            },
          ],
        });
      }
      if (url.includes("/internal/v1/coverage/remittances")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/contributions")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/preauths")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/utilization")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/appeals")) {
        return Promise.resolve({ data: [] });
      }
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });
  });

  it("dashboard shows real plan counts from coverage API (no demo millions)", async () => {
    renderPage();
    await waitFor(() => {
      const card = screen.getByText("Configured plans").closest(".bg-violet-50");
      expect(card).toBeTruthy();
      expect(within(card as HTMLElement).getByText("1")).toBeInTheDocument();
    });
    expect(get.mock.calls.some((c) => String(c[0]).includes("/internal/v1/coverage/plans"))).toBe(true);
    expect(screen.queryByText("4.7M")).not.toBeInTheDocument();
  });

  it("claims tab loads list via coverage claims query when user applies filter", async () => {
    const user = userEvent.setup();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/plans")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/remittances")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/contributions")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/preauths")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/utilization")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/appeals")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/claims")) {
        return Promise.resolve({
          data: [
            {
              id: "c1",
              type: "CoverageClaim",
              attributes: {
                claim_number: "CN-1",
                claim_type: "OUTPATIENT",
                total_amount: 120,
                status: "SUBMITTED",
                created_at: "2026-04-01T00:00:00Z",
                coverage_id: "cov-a",
                facility_id: "fac-1",
              },
            },
          ],
        });
      }
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });

    renderPage();
    await user.click(screen.getByRole("button", { name: /Claims/i }));

    await user.type(screen.getByLabelText(/Coverage ID \(list claims\)/i), "cov-a");
    await user.click(screen.getByRole("button", { name: /Load claims/i }));

    await waitFor(() => {
      expect(screen.getByText("CN-1")).toBeInTheDocument();
    });
    expect(get.mock.calls.some((c) => String(c[0]).includes("coverage/claims?coverageId=cov-a"))).toBe(true);
  });

  it("preauth tab renders rows from coverage preauth list endpoint", async () => {
    const user = userEvent.setup();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/plans")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/remittances")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/contributions")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/utilization")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/appeals")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/preauths")) {
        return Promise.resolve({
          data: [{ id: "pa-1", request_type: "SURGERY", status: "APPROVED", coverage_id: "cov-22" }],
        });
      }
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });

    renderPage();
    await user.click(screen.getByRole("button", { name: /^Pre-Auth$/i }));
    await waitFor(() => expect(screen.getByText("pa-1")).toBeInTheDocument());
    expect(get.mock.calls.some((c) => String(c[0]).includes("/internal/v1/coverage/preauths"))).toBe(true);
  });

  it("preauth reviewer approves a PENDING request via the decision endpoint (G15)", async () => {
    const user = userEvent.setup();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/plans")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/remittances")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/contributions")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/utilization")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/appeals")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/preauths")) {
        return Promise.resolve({ data: [{ id: "pa-9", request_type: "SURGERY", status: "PENDING" }] });
      }
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });

    renderPage();
    await user.click(screen.getByRole("button", { name: /^Pre-Auth$/i }));
    await waitFor(() => expect(screen.getByText("pa-9")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /^Approve$/ }));
    await waitFor(() =>
      expect(
        put.mock.calls.some((c) => String(c[0]) === "/internal/v1/coverage/preauth/pa-9/decision"),
      ).toBe(true),
    );
    const call = put.mock.calls.find((c) => String(c[0]).includes("/preauth/pa-9/decision"));
    expect((call?.[1] as { status?: string })?.status).toBe("APPROVED");
  });

  it("subsidies tab enrols a member into a programme value lane (G2)", async () => {
    const user = userEvent.setup();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/subsidies/enrolments")) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes("/internal/v1/coverage/subsidies")) {
        return Promise.resolve({ data: [{ id: "sp-1", program_code: "SUB-MOHCC-PRIMARY", program_name: "MOHCC Primary", subsidy_type: "GOVERNMENT", annual_cap: 1500, status: "ACTIVE" }] });
      }
      return Promise.resolve({ data: [] });
    });

    renderPage();
    await user.click(screen.getByRole("button", { name: /Subsidies/i }));
    await user.type(screen.getByPlaceholderText("CPID"), "CPID-77");
    await user.click(screen.getByRole("button", { name: /^Look up$/ }));
    await screen.findByText(/Enrol CPID-77 into a programme/);
    await user.selectOptions(screen.getByRole("combobox"), "SUB-MOHCC-PRIMARY");
    await user.click(screen.getByRole("button", { name: /^Enrol$/ }));
    await waitFor(() =>
      expect(
        post.mock.calls.some((c) => String(c[0]) === "/internal/v1/coverage/subsidies/enrolments"),
      ).toBe(true),
    );
    const call = post.mock.calls.find((c) => String(c[0]).includes("/subsidies/enrolments"));
    expect((call?.[1] as { member_cpid?: string })?.member_cpid).toBe("CPID-77");
  });

  it("contributions tab renders list rows from coverage contributions endpoint", async () => {
    const user = userEvent.setup();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/plans")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/remittances")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/preauths")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/utilization")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/appeals")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/contributions")) {
        return Promise.resolve({
          data: [{ id: "ctr-1", member_id: "mem-1", period_start: "2026-04-01", period_end: "2026-04-30", amount: 55, currency: "USD", status: "PAID" }],
        });
      }
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });

    renderPage();
    await user.click(screen.getByRole("button", { name: /Contributions/i }));
    await waitFor(() => expect(screen.getByText("ctr-1")).toBeInTheDocument());
    expect(get.mock.calls.some((c) => String(c[0]).includes("/internal/v1/coverage/contributions"))).toBe(true);
  });

  it("appeals tab renders list rows from coverage appeals endpoint", async () => {
    const user = userEvent.setup();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/plans")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/remittances")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/contributions")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/preauths")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/utilization")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/appeals")) {
        return Promise.resolve({
          data: [{ id: "appeal-1", claim_id: "claim-9", coverage_id: "cov-9", status: "UNDER_REVIEW" }],
        });
      }
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });

    renderPage();
    await user.click(screen.getByRole("button", { name: /Appeals/i }));
    await waitFor(() => expect(screen.getByText("appeal-1")).toBeInTheDocument());
    expect(get.mock.calls.some((c) => String(c[0]).includes("/internal/v1/coverage/appeals"))).toBe(true);
  });

  it("coverage command console posts guided eligibility payload through the command hook", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: /Run Eligibility check/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/coverage/eligibility/check",
        {
          memberCpid: "CPID-EXAMPLE",
          serviceCode: "CONSULTATION",
          coverageId: "COVERAGE-ID",
        },
        { extraHeaders: { "Idempotency-Key": "eligibility-COVERAGE-ID" } },
      );
    });
  });

  it("appeal form posts to the canonical appeals endpoint", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: /Appeals/i }));
    await user.click(screen.getByRole("button", { name: /File Appeal/i }));
    await user.type(screen.getByPlaceholderText("Claim ID"), "claim-42");
    await user.type(screen.getByPlaceholderText("Appellant ID"), "cpid-42");
    await user.type(screen.getByPlaceholderText("Appeal reason and supporting evidence..."), "Decision evidence mismatch");
    await user.click(screen.getByRole("button", { name: /Submit Appeal/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/appeals", {
        claimId: "claim-42",
        appellantId: "cpid-42",
        reason: "Decision evidence mismatch",
        coverageId: undefined,
        evidence: { summary: "" },
      });
    });
  });

  it("appeal operator actions call review and decide endpoints", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: /Appeals/i }));
    await user.type(screen.getByPlaceholderText("Appeal ID"), "appeal-99");
    await user.type(screen.getByPlaceholderText("Reviewer ID"), "reviewer-1");
    await user.click(screen.getByRole("button", { name: /Mark under review/i }));

    await waitFor(() => {
      expect(put).toHaveBeenCalledWith("/internal/v1/appeals/appeal-99/review", { reviewerId: "reviewer-1" });
    });

    await user.type(screen.getByPlaceholderText("Decided by"), "decider-1");
    await user.type(screen.getByPlaceholderText("Decision reason"), "All evidence confirms partial overturn.");
    await user.click(screen.getByRole("button", { name: /Record decision/i }));

    await waitFor(() => {
      expect(put).toHaveBeenCalledWith("/internal/v1/appeals/appeal-99/decide", {
        decision: "UPHELD",
        decisionReason: "All evidence confirms partial overturn.",
        decidedBy: "decider-1",
      });
    });
  });

  it("intelligence tab reads utilization list endpoint", async () => {
    const user = userEvent.setup();
    get.mockImplementation((url: string) => {
      if (url.includes("/internal/v1/coverage/plans")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/remittances")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/contributions")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/preauths")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/appeals")) return Promise.resolve({ data: [] });
      if (url.includes("/internal/v1/coverage/utilization")) {
        return Promise.resolve({
          data: [{ id: "util-1", metric_name: "utilization-rate", metric_value: "84", period_label: "2026-04" }],
        });
      }
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });

    renderPage();
    await user.click(screen.getByRole("button", { name: /Intelligence/i }));
    await waitFor(() => expect(screen.getByText("utilization-rate")).toBeInTheDocument());
    expect(get.mock.calls.some((c) => String(c[0]).includes("/internal/v1/coverage/utilization"))).toBe(true);
  });
});
