import type { ReactNode } from "react";
import { render, screen, within, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import FinanceMushexPlatformPage from "./page";

const pushSpy = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushSpy }),
  useParams: () => ({}),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div data-testid="app-layout">{children}</div>,
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

vi.mock("@/components/workflow/WorkflowHeader", () => ({
  WorkflowHeader: ({
    badge,
    title,
    metrics,
  }: {
    badge?: string;
    title?: string;
    metrics?: { label: string; value: string; detail?: string }[];
  }) => (
    <div data-testid="workflow-header">
      {badge ? <span>{badge}</span> : null}
      {title ? <h2>{title}</h2> : null}
      {metrics?.map((m) => (
        <div key={m.label}>
          <div>{m.label}</div>
          <div data-testid={`metric-${m.label}`}>{m.value}</div>
        </div>
      ))}
    </div>
  ),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

interface QueryShape<T> {
  data: T;
  isLoading?: boolean;
  isFetching?: boolean;
  isError?: boolean;
  error?: unknown;
}

vi.mock("@/hooks/queries/useMushexPlatformAdmin", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useMushexPlatformAdmin")>(
    "@/hooks/queries/useMushexPlatformAdmin",
  );
  return {
    ...actual,
    useMushexPlatformWallets: (): QueryShape<unknown[]> => ({
      data: [{ id: "w-1" }, { id: "w-2" }, { id: "w-3" }],
      isLoading: false,
      isError: false,
    }),
    useMushexPlatformRemittanceTransfers: (): QueryShape<{ data: unknown[] }> => ({
      data: { data: [{ id: "r-1" }, { id: "r-2" }] },
      isLoading: false,
      isError: false,
    }),
    useMushexPlatformCardProfiles: (): QueryShape<{ items: unknown[] }> => ({
      data: { items: [{ id: "c-1" }] },
      isLoading: false,
      isError: false,
    }),
    useMushexPlatformReversals: (): QueryShape<unknown[]> => ({
      data: [],
      isLoading: false,
      isError: false,
    }),
    useMushexPlatformAdapterReadiness: (): QueryShape<
      import("@/hooks/queries/useMushexPlatformAdmin").AdapterReadinessRow[]
    > => ({
      data: [
        {
          adapterType: "MOBILE_MONEY",
          status: "DISABLED",
          liveCapable: false,
          sandboxCapable: false,
          detail: "Real-money rail MOBILE_MONEY is disabled by mushex.adapters.mobile-money.enabled=false.",
        },
        {
          adapterType: "BANK_TRANSFER",
          status: "CREDENTIALS_MISSING",
          liveCapable: false,
          sandboxCapable: false,
          detail: "Real-money rail BANK_TRANSFER is enabled but credentials are not declared as configured.",
        },
        {
          adapterType: "CARD_GATEWAY",
          status: "NOT_REGISTERED",
          liveCapable: false,
          sandboxCapable: false,
          detail: "No PaymentRailAdapter bean registered for CARD_GATEWAY",
        },
        {
          adapterType: "SANDBOX",
          status: "READY_SANDBOX",
          liveCapable: false,
          sandboxCapable: true,
          detail: "Sandbox adapter is enabled; suitable for non-production simulation only",
        },
      ],
      isLoading: false,
      isError: false,
    }),
  };
});

describe("FinanceMushexPlatformPage", () => {
  it("renders the MusheX platform admin hub header and canonical section titles", () => {
    render(<FinanceMushexPlatformPage />);

    expect(screen.getByText("MusheX Platform Administration")).toBeInTheDocument();
    expect(
      screen.getByText(/Read-only platform view for custodial wallets/),
    ).toBeInTheDocument();

    expect(screen.getByRole("heading", { name: "Custodial wallets" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Remittance transfers" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Card profiles" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Reversal records" })).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Platform routing & gateway readiness" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Related MusheX finance surfaces" }),
    ).toBeInTheDocument();
  });

  it("surfaces live counts from each MusheX platform admin endpoint without fabricating data", () => {
    render(<FinanceMushexPlatformPage />);

    // The count paragraph spans multiple text nodes (number in a <span>,
    // then " record", then conditional "s", then trailing text). Match by
    // combined textContent so the assertion describes the intent rather
    // than DOM accidents.
    const cardCountText = (cardHeading: string) => {
      const card = screen.getByRole("heading", { name: cardHeading }).closest("section");
      expect(card).not.toBeNull();
      const paragraphs = within(card as HTMLElement).getAllByText(
        (_, node) =>
          (node?.textContent ?? "").includes("returned from the MusheX platform admin API"),
      );
      // Exactly one paragraph per card carries the count phrase.
      expect(paragraphs.length).toBeGreaterThan(0);
      return paragraphs[0].textContent ?? "";
    };

    // Wallets: top-level array of 3 → "3 records returned".
    expect(cardCountText("Custodial wallets")).toContain("3 records returned");
    // Remittance: envelope { data: [...] } of 2 → "2 records returned".
    expect(cardCountText("Remittance transfers")).toContain("2 records returned");
    // Card profiles: envelope { items: [...] } of 1 → singular wording.
    expect(cardCountText("Card profiles")).toContain("1 record returned");
    // Reversals: empty array → "0 records returned" (still honest, not fabricated).
    expect(cardCountText("Reversal records")).toContain("0 records returned");
  });

  it("uses the standard AppLayout / PageShell / WorkflowHeader — does not introduce a new shell", () => {
    render(<FinanceMushexPlatformPage />);
    expect(screen.getByTestId("app-layout")).toBeInTheDocument();
    expect(screen.getByTestId("workflow-header")).toBeInTheDocument();
  });

  it("documents the BFF route family on every card (no hidden endpoints)", () => {
    render(<FinanceMushexPlatformPage />);

    expect(screen.getByText("/internal/v1/finance/mushex-platform/wallets")).toBeInTheDocument();
    expect(
      screen.getByText("/internal/v1/finance/mushex-platform/remittance-transfers"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("/internal/v1/finance/mushex-platform/card-profiles"),
    ).toBeInTheDocument();
    expect(screen.getByText("/internal/v1/finance/mushex-platform/reversals")).toBeInTheDocument();
  });

  it("links to neighbouring canonical finance/wallet surfaces — no sidecar links", () => {
    render(<FinanceMushexPlatformPage />);

    const expectedHrefs = new Set([
      "/finance/costa",
      "/finance/payer-ops",
      "/finance/payer-claims",
      "/finance/settlements",
      "/finance/reconciliation",
      "/finance/refunds",
      "/finance/ledger",
      "/wallet",
    ]);

    const hrefs = new Set(
      screen
        .getAllByRole("link")
        .map((link) => link.getAttribute("href"))
        .filter((href): href is string => Boolean(href)),
    );
    for (const href of expectedHrefs) {
      expect(hrefs.has(href)).toBe(true);
    }

    // Never link to legacy sidecar MusheX UIs.
    for (const link of screen.getAllByRole("link")) {
      const href = link.getAttribute("href") ?? "";
      expect(href).not.toMatch(/mushex-finance-console|mushex-ops-console|mushex-payer-portal/);
    }
  });

  it("renders no write-action buttons in this stage", () => {
    render(<FinanceMushexPlatformPage />);

    const buttons = screen.queryAllByRole("button");
    for (const button of buttons) {
      const name = (button.textContent || "").toLowerCase();
      // Defensive: this hub is read-only — no create/credit/debit/issue/reverse buttons.
      expect(name).not.toMatch(/create|credit|debit|issue|reverse|refund|approve|reject|cancel/);
    }
  });

  // Phase 2 — adapter readiness table (audit gap G-7 closed).
  it("renders the adapter readiness table with one row per AdapterType", () => {
    render(<FinanceMushexPlatformPage />);

    const section = screen
      .getByRole("heading", { name: "Platform routing & gateway readiness" })
      .closest("section");
    expect(section).not.toBeNull();
    const scope = within(section as HTMLElement);

    for (const rail of ["MOBILE_MONEY", "BANK_TRANSFER", "CARD_GATEWAY", "SANDBOX"]) {
      expect(scope.getByText(rail)).toBeInTheDocument();
    }
    expect(scope.getByText("Disabled")).toBeInTheDocument();
    expect(scope.getByText("Credentials missing")).toBeInTheDocument();
    expect(scope.getByText("Not registered")).toBeInTheDocument();
    expect(scope.getByText("Ready (sandbox)")).toBeInTheDocument();
  });

  it("documents the BFF route for the adapter readiness endpoint", () => {
    render(<FinanceMushexPlatformPage />);
    expect(
      screen.getByText("/internal/v1/finance/mushex-platform/adapter-readiness"),
    ).toBeInTheDocument();
  });

  // Phase 4 — read-only wallet detail drill-down.
  it("opens the custodial wallet detail page when the lookup form is submitted with a wallet ID", () => {
    pushSpy.mockClear();
    render(<FinanceMushexPlatformPage />);

    const form = screen.getByLabelText("Open custodial wallet detail by ID");
    const input = within(form as HTMLElement).getByLabelText("Wallet ID");
    fireEvent.change(input, { target: { value: "wallet-abc-123" } });
    fireEvent.submit(form);

    expect(pushSpy).toHaveBeenCalledTimes(1);
    expect(pushSpy).toHaveBeenCalledWith(
      "/finance/mushex-platform/wallets/wallet-abc-123",
    );
  });

  it("does not navigate when the wallet lookup form is submitted empty", () => {
    pushSpy.mockClear();
    render(<FinanceMushexPlatformPage />);

    const form = screen.getByLabelText("Open custodial wallet detail by ID");
    fireEvent.submit(form);

    expect(pushSpy).not.toHaveBeenCalled();
  });

  it("opens remittance/card/reversal detail pages when their lookup forms are submitted", () => {
    pushSpy.mockClear();
    render(<FinanceMushexPlatformPage />);

    const remittanceForm = screen.getByLabelText("Open remittance transfer detail by ID");
    fireEvent.change(within(remittanceForm as HTMLElement).getByLabelText("Remittance transfer ID"), {
      target: { value: "rem-001" },
    });
    fireEvent.submit(remittanceForm);

    const cardForm = screen.getByLabelText("Open card profile detail by ID");
    fireEvent.change(within(cardForm as HTMLElement).getByLabelText("Card profile ID"), {
      target: { value: "card-001" },
    });
    fireEvent.submit(cardForm);

    const reversalForm = screen.getByLabelText("Open reversal detail by ID");
    fireEvent.change(within(reversalForm as HTMLElement).getByLabelText("Reversal ID"), {
      target: { value: "rev-001" },
    });
    fireEvent.submit(reversalForm);

    expect(pushSpy).toHaveBeenNthCalledWith(1, "/finance/mushex-platform/remittance/rem-001");
    expect(pushSpy).toHaveBeenNthCalledWith(2, "/finance/mushex-platform/cards/card-001");
    expect(pushSpy).toHaveBeenNthCalledWith(3, "/finance/mushex-platform/reversals/rev-001");
  });
});

describe("FinanceMushexPlatformPage — graceful unavailable states", () => {
  it("does not crash and does not fabricate data when MusheX platform admin endpoints error", async () => {
    vi.resetModules();
    vi.doMock("@/hooks/queries/useMushexPlatformAdmin", async () => {
      const actual = await vi.importActual<
        typeof import("@/hooks/queries/useMushexPlatformAdmin")
      >("@/hooks/queries/useMushexPlatformAdmin");
      return {
        ...actual,
        useMushexPlatformWallets: () => ({
          data: undefined,
          isLoading: false,
          isError: true,
          error: new Error("503 Service Unavailable"),
        }),
        useMushexPlatformRemittanceTransfers: () => ({
          data: undefined,
          isLoading: false,
          isError: true,
          error: new Error("503 Service Unavailable"),
        }),
        useMushexPlatformCardProfiles: () => ({
          data: undefined,
          isLoading: false,
          isError: true,
          error: new Error("503 Service Unavailable"),
        }),
        useMushexPlatformReversals: () => ({
          data: undefined,
          isLoading: false,
          isError: true,
          error: new Error("503 Service Unavailable"),
        }),
        useMushexPlatformAdapterReadiness: () => ({
          data: undefined,
          isLoading: false,
          isError: true,
          error: new Error("503 Service Unavailable"),
        }),
      };
    });

    const { default: ReimportedMushexPlatformPage } = await import("./page");
    render(<ReimportedMushexPlatformPage />);

    expect(screen.getByText("MusheX Platform Administration")).toBeInTheDocument();
    expect(screen.getByText(/Could not load custodial wallets from MusheX/)).toBeInTheDocument();
    expect(screen.getByText(/Could not load remittance transfers from MusheX/)).toBeInTheDocument();
    expect(screen.getByText(/Could not load card profiles from MusheX/)).toBeInTheDocument();
    expect(screen.getByText(/Could not load reversal records from MusheX/)).toBeInTheDocument();
    expect(screen.getByText(/Could not load adapter readiness from MusheX/)).toBeInTheDocument();
  });
});
