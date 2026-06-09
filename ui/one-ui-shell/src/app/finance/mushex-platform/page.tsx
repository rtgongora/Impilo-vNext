"use client";

/**
 * MusheX Platform Administration — canonical read-only hub.
 * Route: /finance/mushex-platform | pageTitle: "MusheX Platform Administration"
 *
 * Closes audit gap G-2 in
 * {@code docs/audits/costa-mushex-experience-layer-wiring-audit.md} by giving
 * the existing BFF route family {@code /internal/v1/finance/mushex-platform/**}
 * a canonical web consumer inside {@code one-ui-shell}. The deprecated
 * sidecar {@code ui/mushex-ops-console} explicitly defers MusheX platform
 * admin to this page.
 *
 * Stage 3.3 scope was read-only; Wave 4 adds one controlled wallet credit
 * (top-up) via POST /wallets/{walletId}/credit. Other writes (create wallet,
 * debit, create remittance, create card, create reversal) remain backend-only
 * until step-up auth lands. No fabricated platform stats.
 *
 * Doctrine: {@link ../../../../../docs/doctrine/mushex-gateway-neutrality.md}.
 */

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { FINANCE_MUTATION_COLUMNS } from "@/lib/json-api/finance-table-columns";
import { useRouter } from "next/navigation";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  CreditCard,
  Globe2,
  Layers,
  Loader2,
  RotateCcw,
  Send,
  Shield,
  Wallet,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { MusheXProgrammeAttributionPanel } from "@/components/finance/MusheXProgrammeAttributionPanel";
import {
  extractCount,
  useMushexPlatformAdapterReadiness,
  useMushexPlatformCardProfiles,
  useMushexPlatformRemittanceTransfers,
  useMushexPlatformReversals,
  useMushexPlatformWalletCredit,
  useMushexPlatformWallets,
  type AdapterReadinessRow,
} from "@/hooks/queries/useMushexPlatformAdmin";

interface RelatedLink {
  href: string;
  label: string;
  description: string;
}

const RELATED_MUSHEX_LINKS: RelatedLink[] = [
  {
    href: "/finance/costa",
    label: "COSTA — Costing & Tariffs",
    description: "Costing, tariffs, and the COSTA → MusheX invoice handoff.",
  },
  {
    href: "/finance/payer-ops",
    label: "Payer operations",
    description: "Payment intents, remittance, adapters, fraud, and reviews on MusheX.",
  },
  {
    href: "/finance/payer-claims",
    label: "Payer claims",
    description: "Payer-side claim queue, scheme view, and remittance follow-up.",
  },
  {
    href: "/finance/settlements",
    label: "MusheX settlements",
    description: "Run settlement periods and inspect MusheX payment handoff.",
  },
  {
    href: "/finance/reconciliation",
    label: "Reconciliation",
    description: "Match MusheX intents to rail settlement files and COSTA invoices.",
  },
  {
    href: "/finance/refunds",
    label: "Refunds",
    description: "Refunds, reversals, and credit notes back through MusheX.",
  },
  {
    href: "/finance/ledger",
    label: "General ledger",
    description: "Posting, period close, and trial balance for the enterprise plane (finance domain).",
  },
  {
    href: "/wallet",
    label: "Mushe Wallet",
    description: "Patient-facing wallet surface — balances, transactions, funding sources.",
  },
];

interface ReadinessChrome {
  label: string;
  badgeClass: string;
}

function readinessChrome(status: AdapterReadinessRow["status"]): ReadinessChrome {
  switch (status) {
    case "READY_LIVE":
      return { label: "Ready (live)", badgeClass: "bg-emerald-100 text-emerald-800 border-emerald-200" };
    case "READY_SANDBOX":
      return { label: "Ready (sandbox)", badgeClass: "bg-sky-100 text-sky-800 border-sky-200" };
    case "CREDENTIALS_MISSING":
      return { label: "Credentials missing", badgeClass: "bg-amber-100 text-amber-800 border-amber-200" };
    case "DISABLED":
      return { label: "Disabled", badgeClass: "bg-slate-100 text-slate-700 border-slate-200" };
    case "NOT_REGISTERED":
      return { label: "Not registered", badgeClass: "bg-slate-100 text-slate-500 border-slate-200" };
    case "DEGRADED":
      return { label: "Degraded", badgeClass: "bg-orange-100 text-orange-800 border-orange-200" };
    default:
      return { label: status, badgeClass: "bg-slate-100 text-slate-700 border-slate-200" };
  }
}

function renderCount(
  label: string,
  isLoading: boolean,
  isError: boolean,
  count: number | null,
  unavailableMessage: string,
) {
  if (isLoading) {
    return (
      <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
        <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading {label}…
      </p>
    );
  }
  if (isError) {
    return (
      <p className="mt-3 flex items-center gap-2 text-xs text-red-700">
        <AlertCircle className="h-3.5 w-3.5" /> {unavailableMessage}
      </p>
    );
  }
  if (count === null) {
    return (
      <p className="mt-3 text-xs text-slate-500">
        Upstream returned a payload but the row count could not be derived from a recognised
        envelope. Open the MusheX service log to inspect the response shape.
      </p>
    );
  }
  return (
    <p className="mt-3 text-xs text-slate-700">
      <span className="font-medium">{count}</span> record{count === 1 ? "" : "s"} returned from the
      MusheX platform admin API.
    </p>
  );
}

export default function FinanceMushexPlatformPage() {
  const facility = useFacilityStore((state) => state.facility);
  const router = useRouter();
  const [walletLookupId, setWalletLookupId] = useState("");
  const [remittanceLookupId, setRemittanceLookupId] = useState("");
  const [cardLookupId, setCardLookupId] = useState("");
  const [reversalLookupId, setReversalLookupId] = useState("");
  const [creditWalletId, setCreditWalletId] = useState("");
  const [creditAmount, setCreditAmount] = useState("");
  const [creditReason, setCreditReason] = useState("");
  const [creditConfirmed, setCreditConfirmed] = useState(false);
  const [creditErr, setCreditErr] = useState<string | null>(null);

  const walletsQ = useMushexPlatformWallets();
  const walletCreditM = useMushexPlatformWalletCredit(creditWalletId.trim() || null);
  const remittanceQ = useMushexPlatformRemittanceTransfers();
  const cardsQ = useMushexPlatformCardProfiles();
  const reversalsQ = useMushexPlatformReversals();
  const readinessQ = useMushexPlatformAdapterReadiness();

  function onOpenWallet(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = walletLookupId.trim();
    if (!trimmed) return;
    router.push(`/finance/mushex-platform/wallets/${encodeURIComponent(trimmed)}`);
  }

  function onOpenRemittance(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = remittanceLookupId.trim();
    if (!trimmed) return;
    router.push(`/finance/mushex-platform/remittance/${encodeURIComponent(trimmed)}`);
  }

  function onOpenCard(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = cardLookupId.trim();
    if (!trimmed) return;
    router.push(`/finance/mushex-platform/cards/${encodeURIComponent(trimmed)}`);
  }

  function onOpenReversal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = reversalLookupId.trim();
    if (!trimmed) return;
    router.push(`/finance/mushex-platform/reversals/${encodeURIComponent(trimmed)}`);
  }

  const walletsCount = extractCount(walletsQ.data);
  const remittanceCount = extractCount(remittanceQ.data);
  const cardsCount = extractCount(cardsQ.data);
  const reversalsCount = extractCount(reversalsQ.data);

  const formatMetric = (q: { isLoading: boolean; isError: boolean }, value: number | null) => {
    if (q.isLoading) return "…";
    if (q.isError) return "—";
    if (value === null) return "—";
    return String(value);
  };

  return (
    <AppLayout>
      <PageShell
        title="MusheX Platform Administration"
        subtitle="Platform view for custodial wallets, remittance transfers, card profiles, reversals, and gateway readiness — with one controlled wallet credit action."
      >
        <div className="mb-4">
          <Link
            href="/finance"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to finance
          </Link>
        </div>

        <div className="space-y-6">
          <WorkflowHeader
            badge="MusheX enterprise plane (finance domain)"
            badgeIcon={Shield}
            title="MusheX platform administration — read-first with one controlled write."
            description="This hub surfaces custodial wallets, remittance transfers, card profiles, and reversal records from the MusheX platform admin BFF. Wave 4 adds a single governed wallet credit (top-up) action; other writes remain backend-only until step-up auth lands."
            context={[
              { label: "Facility", value: facility?.name ?? "No facility selected" },
              { label: "Mode", value: "Mode A — orchestration gateway" },
              { label: "BFF route family", value: "/internal/v1/finance/mushex-platform/**" },
            ]}
            actions={[
              { href: "/finance/costa", label: "COSTA", icon: Layers, tone: "secondary" },
              { href: "/finance/payer-ops", label: "Payer ops", icon: Send, tone: "secondary" },
              { href: "/finance/settlements", label: "Settlements", icon: Wallet, tone: "secondary" },
              { href: "/wallet", label: "Wallet", icon: Wallet, tone: "secondary" },
            ]}
            metrics={[
              {
                label: "Custodial wallets",
                value: formatMetric(walletsQ, walletsCount),
                detail: "Records returned from /internal/v1/finance/mushex-platform/wallets.",
              },
              {
                label: "Remittance transfers",
                value: formatMetric(remittanceQ, remittanceCount),
                detail:
                  "Records returned from /internal/v1/finance/mushex-platform/remittance-transfers.",
              },
              {
                label: "Card profiles",
                value: formatMetric(cardsQ, cardsCount),
                detail: "Records returned from /internal/v1/finance/mushex-platform/card-profiles.",
              },
            ]}
          />

          <MusheXProgrammeAttributionPanel />

          <div className="grid gap-4 md:grid-cols-2">
            {/* ── Custodial wallets ─────────────────────────────────── */}
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-indigo-700">
                  <Wallet className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h2 className="text-sm font-semibold text-slate-900">Custodial wallets</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    MusheX-managed custodial wallets backing tenant, facility, payer, sponsor, and
                    programme balances. Patient-facing wallets live under the wallet plane below.
                  </p>
                  {renderCount(
                    "wallets",
                    walletsQ.isLoading,
                    walletsQ.isError,
                    walletsCount,
                    "Could not load custodial wallets from MusheX.",
                  )}
                  <p className="mt-2 text-[11px] text-slate-400">
                    BFF route:{" "}
                    <code className="text-[10px]">/internal/v1/finance/mushex-platform/wallets</code>
                  </p>

                  <form
                    onSubmit={onOpenWallet}
                    className="mt-3 flex items-center gap-2"
                    aria-label="Open custodial wallet detail by ID"
                  >
                    <input
                      type="text"
                      inputMode="text"
                      placeholder="Wallet ID"
                      value={walletLookupId}
                      onChange={(e) => setWalletLookupId(e.target.value)}
                      className="flex-1 rounded-md border border-slate-200 px-2 py-1 text-xs font-mono placeholder:font-sans placeholder:text-slate-400 focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-200"
                      aria-label="Wallet ID"
                    />
                    <button
                      type="submit"
                      disabled={walletLookupId.trim().length === 0}
                      className="inline-flex items-center gap-1 rounded-md border border-indigo-200 bg-indigo-50 px-2 py-1 text-xs font-medium text-indigo-700 hover:bg-indigo-100 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Open <ArrowRight className="h-3 w-3" />
                    </button>
                  </form>
                  <p className="mt-1 text-[10px] text-slate-400">
                    Read-only detail: transactions + linked cards. No write actions surfaced.
                  </p>

                  <Link
                    href="/wallet"
                    className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    Open patient wallet surface →
                  </Link>
                </div>
              </div>
            </section>

            {/* ── Remittance transfers ──────────────────────────────── */}
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-100 text-emerald-700">
                  <Send className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h2 className="text-sm font-semibold text-slate-900">Remittance transfers</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Payer-to-facility and facility-to-provider remittance flows orchestrated by
                    MusheX. Reconciliation files are matched in the reconciliation surface.
                  </p>
                  {renderCount(
                    "remittance transfers",
                    remittanceQ.isLoading,
                    remittanceQ.isError,
                    remittanceCount,
                    "Could not load remittance transfers from MusheX.",
                  )}
                  <p className="mt-2 text-[11px] text-slate-400">
                    BFF route:{" "}
                    <code className="text-[10px]">
                      /internal/v1/finance/mushex-platform/remittance-transfers
                    </code>
                  </p>
                  <form
                    onSubmit={onOpenRemittance}
                    className="mt-3 flex items-center gap-2"
                    aria-label="Open remittance transfer detail by ID"
                  >
                    <input
                      type="text"
                      inputMode="text"
                      placeholder="Transfer ID"
                      value={remittanceLookupId}
                      onChange={(e) => setRemittanceLookupId(e.target.value)}
                      className="flex-1 rounded-md border border-slate-200 px-2 py-1 text-xs font-mono placeholder:font-sans placeholder:text-slate-400 focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-200"
                      aria-label="Remittance transfer ID"
                    />
                    <button
                      type="submit"
                      disabled={remittanceLookupId.trim().length === 0}
                      className="inline-flex items-center gap-1 rounded-md border border-indigo-200 bg-indigo-50 px-2 py-1 text-xs font-medium text-indigo-700 hover:bg-indigo-100 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Open <ArrowRight className="h-3 w-3" />
                    </button>
                  </form>
                  <Link
                    href="/finance/reconciliation"
                    className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    Open reconciliation →
                  </Link>
                </div>
              </div>
            </section>

            {/* ── Card profiles ─────────────────────────────────────── */}
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
                  <CreditCard className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h2 className="text-sm font-semibold text-slate-900">Card profiles</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Tokenised card profiles registered with the accredited card acquirer adapter.
                    Card lifecycle changes (issue, suspend, retire) are backend-only in this stage.
                  </p>
                  {renderCount(
                    "card profiles",
                    cardsQ.isLoading,
                    cardsQ.isError,
                    cardsCount,
                    "Could not load card profiles from MusheX.",
                  )}
                  <p className="mt-2 text-[11px] text-slate-400">
                    BFF route:{" "}
                    <code className="text-[10px]">
                      /internal/v1/finance/mushex-platform/card-profiles
                    </code>
                  </p>
                  <form
                    onSubmit={onOpenCard}
                    className="mt-3 flex items-center gap-2"
                    aria-label="Open card profile detail by ID"
                  >
                    <input
                      type="text"
                      inputMode="text"
                      placeholder="Card profile ID"
                      value={cardLookupId}
                      onChange={(e) => setCardLookupId(e.target.value)}
                      className="flex-1 rounded-md border border-slate-200 px-2 py-1 text-xs font-mono placeholder:font-sans placeholder:text-slate-400 focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-200"
                      aria-label="Card profile ID"
                    />
                    <button
                      type="submit"
                      disabled={cardLookupId.trim().length === 0}
                      className="inline-flex items-center gap-1 rounded-md border border-indigo-200 bg-indigo-50 px-2 py-1 text-xs font-medium text-indigo-700 hover:bg-indigo-100 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Open <ArrowRight className="h-3 w-3" />
                    </button>
                  </form>
                  <Link
                    href="/finance/payer-ops"
                    className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    Open payer ops adapters →
                  </Link>
                </div>
              </div>
            </section>

            {/* ── Reversal records ──────────────────────────────────── */}
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-purple-100 text-purple-700">
                  <RotateCcw className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h2 className="text-sm font-semibold text-slate-900">Reversal records</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Adapter-level reversals raised against MusheX `PaymentIntent`s. Refund execution
                    and credit-note issuance happen in the refunds and ledger surfaces.
                  </p>
                  {renderCount(
                    "reversal records",
                    reversalsQ.isLoading,
                    reversalsQ.isError,
                    reversalsCount,
                    "Could not load reversal records from MusheX.",
                  )}
                  <p className="mt-2 text-[11px] text-slate-400">
                    BFF route:{" "}
                    <code className="text-[10px]">/internal/v1/finance/mushex-platform/reversals</code>
                  </p>
                  <form
                    onSubmit={onOpenReversal}
                    className="mt-3 flex items-center gap-2"
                    aria-label="Open reversal detail by ID"
                  >
                    <input
                      type="text"
                      inputMode="text"
                      placeholder="Reversal ID"
                      value={reversalLookupId}
                      onChange={(e) => setReversalLookupId(e.target.value)}
                      className="flex-1 rounded-md border border-slate-200 px-2 py-1 text-xs font-mono placeholder:font-sans placeholder:text-slate-400 focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-200"
                      aria-label="Reversal ID"
                    />
                    <button
                      type="submit"
                      disabled={reversalLookupId.trim().length === 0}
                      className="inline-flex items-center gap-1 rounded-md border border-indigo-200 bg-indigo-50 px-2 py-1 text-xs font-medium text-indigo-700 hover:bg-indigo-100 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Open <ArrowRight className="h-3 w-3" />
                    </button>
                  </form>
                  <Link
                    href="/finance/refunds"
                    className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    Open refunds workspace →
                  </Link>
                </div>
              </div>
            </section>
          </div>

          {/* ── Platform routing / gateway readiness (Phase 2 — read-only) ── */}
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-sky-100 text-sky-700">
                <Globe2 className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h2 className="text-sm font-semibold text-slate-900">
                  Platform routing & gateway readiness
                </h2>
                <p className="mt-1 text-xs text-slate-500">
                  MusheX is gateway-neutral by design and gateway-capable by default. The readiness
                  snapshot below is derived from MusheX configuration and adapter registration only
                  — it never reads payment-provider credentials and never causes money movement.
                  Operators must still verify credentials in their secret-management system.
                </p>

                <div className="mt-4">
                  {readinessQ.isLoading && (
                    <p className="flex items-center gap-2 text-xs text-slate-500">
                      <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading adapter readiness…
                    </p>
                  )}
                  {readinessQ.isError && (
                    <p className="flex items-center gap-2 text-xs text-red-700">
                      <AlertCircle className="h-3.5 w-3.5" /> Could not load adapter readiness from
                      MusheX.
                    </p>
                  )}
                  {!readinessQ.isLoading && !readinessQ.isError && readinessQ.data?.length === 0 && (
                    <p className="text-xs text-slate-500">
                      MusheX returned an empty readiness snapshot. Check that the
                      <code className="ml-1 text-[10px]">/mushex/v1/platform/adapter-readiness</code>
                      {" "}endpoint is reachable.
                    </p>
                  )}
                  {!readinessQ.isLoading && !readinessQ.isError && (readinessQ.data?.length ?? 0) > 0 && (
                    <div className="overflow-hidden rounded-lg border border-slate-200">
                      <table className="w-full text-xs">
                        <thead className="bg-slate-50 text-slate-600">
                          <tr>
                            <th className="px-3 py-2 text-left font-medium">Rail</th>
                            <th className="px-3 py-2 text-left font-medium">Readiness</th>
                            <th className="px-3 py-2 text-left font-medium">Capability</th>
                            <th className="px-3 py-2 text-left font-medium">Detail</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {readinessQ.data?.map((row) => {
                            const chrome = readinessChrome(row.status);
                            const capability = row.liveCapable
                              ? "Live capable"
                              : row.sandboxCapable
                                ? "Sandbox only"
                                : "Not available";
                            return (
                              <tr key={row.adapterType} className="bg-white">
                                <td className="px-3 py-2 font-mono text-[11px] text-slate-900">
                                  {row.adapterType}
                                </td>
                                <td className="px-3 py-2">
                                  <span
                                    className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium ${chrome.badgeClass}`}
                                  >
                                    {chrome.label}
                                  </span>
                                </td>
                                <td className="px-3 py-2 text-slate-700">{capability}</td>
                                <td className="px-3 py-2 text-slate-600">{row.detail}</td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                  <p className="mt-2 text-[11px] text-slate-400">
                    BFF route:{" "}
                    <code className="text-[10px]">
                      /internal/v1/finance/mushex-platform/adapter-readiness
                    </code>
                  </p>
                </div>

                <ul className="mt-4 list-disc space-y-1 pl-5 text-xs text-slate-600">
                  <li>
                    <span className="font-medium text-slate-800">Mode A</span> — orchestration
                    gateway: routes to a configured rail adapter (mobile money, bank transfer, card
                    acquirer) while MusheX owns the intent lifecycle. Rails marked
                    <span className="font-mono"> Ready (live)</span> are configuration-eligible.
                  </li>
                  <li>
                    <span className="font-medium text-slate-800">Mode B</span> — direct/default
                    gateway: MusheX settles internally (wallet, sandbox, cash) when no preferred
                    rail is configured or available, so care is never blocked.
                  </li>
                  <li>
                    Per-intent rail selection ({" "}
                    <span className="font-mono">RailSelectionPolicy</span>, audit gap{" "}
                    <span className="font-mono">G-4</span>) is closed — see{" "}
                    <code className="text-[10px]">
                      contracts/openapi/mushex.openapi.yaml
                    </code>
                    {" "}for the additive request fields and{" "}
                    <code className="text-[10px]">
                      payment_intents.metadata.rail_selection
                    </code>
                    {" "}for the persisted decision.
                  </li>
                </ul>
              </div>
            </div>
          </section>

          {/* ── Controlled write: wallet credit (top-up) ─────────────── */}
          <section className="rounded-xl border border-amber-200 bg-amber-50/40 p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Controlled action — wallet credit (top-up)</h2>
            <p className="mt-1 text-xs text-slate-600">
              POST{" "}
              <code className="text-[10px]">
                /internal/v1/finance/mushex-platform/wallets/&#123;walletId&#125;/credit
              </code>
              . Requires explicit confirmation and audit reason. Finance role + MusheX platform gate enforced
              by TSHEPO on the BFF.
            </p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <label className="text-xs text-slate-600">
                Wallet ID
                <input
                  type="text"
                  value={creditWalletId}
                  onChange={(e) => setCreditWalletId(e.target.value)}
                  placeholder="custodial-wallet-id"
                  className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                  aria-label="Wallet ID for credit"
                />
              </label>
              <label className="text-xs text-slate-600">
                Amount
                <input
                  type="text"
                  inputMode="decimal"
                  value={creditAmount}
                  onChange={(e) => setCreditAmount(e.target.value)}
                  placeholder="0.00"
                  className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                  aria-label="Credit amount"
                />
              </label>
            </div>
            <input
              type="text"
              placeholder="Reason for this write (required)"
              className="mt-3 w-full rounded-lg border border-slate-200 px-3 py-2 text-xs"
              value={creditReason}
              onChange={(e) => setCreditReason(e.target.value)}
              aria-label="Audit reason for wallet credit"
            />
            <label className="mt-2 flex items-center gap-2 text-xs text-slate-700">
              <input
                type="checkbox"
                checked={creditConfirmed}
                onChange={(e) => setCreditConfirmed(e.target.checked)}
              />
              I confirm this is an intentional operational wallet credit.
            </label>
            {creditErr ? <p className="mt-2 text-xs text-red-700">{creditErr}</p> : null}
            <button
              type="button"
              disabled={
                walletCreditM.isPending ||
                !creditConfirmed ||
                !creditReason.trim() ||
                !creditWalletId.trim() ||
                !creditAmount.trim()
              }
              className="mt-3 rounded-lg bg-amber-700 px-3 py-2 text-xs font-medium text-white hover:bg-amber-800 disabled:opacity-50"
              onClick={() => {
                const amount = Number(creditAmount);
                if (!Number.isFinite(amount) || amount <= 0) {
                  setCreditErr("Amount must be a positive number.");
                  return;
                }
                setCreditErr(null);
                walletCreditM.mutate({
                  amount,
                  currency: "USD",
                  audit_reason: creditReason.trim(),
                  reference: `web-topup-${Date.now()}`,
                });
              }}
            >
              {walletCreditM.isPending ? "Posting credit…" : "Submit wallet credit"}
            </button>
            {walletCreditM.data || walletCreditM.isError ? (
              <div className="mt-3">
                <JsonApiDataTable
                  data={walletCreditM.data}
                  columns={[
                    ...FINANCE_MUTATION_COLUMNS,
                    { key: "walletId", header: "Wallet ID", fields: ["walletId", "id"] },
                  ]}
                  isLoading={walletCreditM.isPending}
                  error={walletCreditM.error as Error | null}
                  emptyTitle="Credit posted"
                />
              </div>
            ) : null}
          </section>

          {/* ── Related MusheX finance surfaces ─────────────────────── */}
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Related MusheX finance surfaces</h2>
            <p className="mt-1 text-xs text-slate-500">
              Other canonical enterprise-plane finance-domain routes that consume or feed MusheX state.
            </p>
            <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {RELATED_MUSHEX_LINKS.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-2 text-xs hover:border-indigo-300 hover:bg-white transition-colors"
                >
                  <div className="font-medium text-slate-900">{item.label}</div>
                  <div className="text-slate-500 mt-0.5">{item.description}</div>
                </Link>
              ))}
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
