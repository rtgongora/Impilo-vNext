"use client";

/**
 * Finance Dashboard — Hub with card grid linking to finance sub-pages.
 * Route: /finance | pageTitle: "Finance Dashboard"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  Receipt,
  FileText,
  CreditCard,
  BookOpen,
  ClipboardList,
  User,
  Layers,
  DatabaseZap,
  BarChart3,
  Wallet,
  Landmark,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { MusheXFinanceJourneysOrchestrationRail } from "@/components/finance/MusheXFinanceJourneysOrchestrationRail";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const FINANCE_SECTIONS = [
  {
    title: "Billing",
    description: "Manage invoices and billing records",
    href: "/finance/billing",
    icon: Receipt,
    color: "bg-primary-soft text-primary",
  },
  {
    title: "Claims",
    description: "Submit and track insurance claims",
    href: "/finance/claims",
    icon: FileText,
    color: "bg-green-100 text-green-600",
  },
  {
    title: "Payments",
    description: "View payment records and transactions",
    href: "/finance/payments",
    icon: CreditCard,
    color: "bg-purple-100 text-purple-600",
  },
  {
    title: "Remittances",
    description: "Read remittance rows from the canonical coverage feed",
    href: "/finance/remittances",
    icon: ClipboardList,
    color: "bg-cyan-100 text-cyan-700",
  },
  {
    title: "Tariffs",
    description: "Manage service tariff schedules",
    href: "/finance/tariffs",
    icon: BookOpen,
    color: "bg-amber-100 text-amber-600",
  },
  {
    title: "MSIKA Governance",
    description: "Govern catalogs, imports, mappings, and publication from Experience",
    href: "/finance/msika-governance",
    icon: DatabaseZap,
    color: "bg-indigo-100 text-primary-hover",
  },
  {
    title: "Financial reports",
    description: "Institutional revenue, receivables, budgets, and payer claims expenditure",
    href: "/finance/reports",
    icon: BarChart3,
    color: "bg-teal-100 text-teal-700",
  },
  {
    title: "Native ERP",
    description: "General ledger, HR & payroll, procurement, and fixed assets",
    href: "/erp",
    icon: Landmark,
    color: "bg-neutral-100 text-foreground",
  },
  {
    title: "My healthcare account",
    description: "Your balances, payment history, plans, and tax or invoice documents",
    href: "/finance/my-account",
    icon: Wallet,
    color: "bg-emerald-100 text-primary-hover",
  },
] as const;

export default function FinancePage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");
  const fromPlane = searchParams.get("from");
  const handoffParams = new URLSearchParams();

  if (patientId) handoffParams.set("patientId", patientId);
  if (encounterId) handoffParams.set("encounterId", encounterId);
  if (source) handoffParams.set("source", source);
  if (fromPlane) handoffParams.set("from", fromPlane);

  const withHandoff = (href: string) => {
    const query = handoffParams.toString();
    return query ? `${href}?${query}` : href;
  };

  const actions = [
    patientId && encounterId
      ? { href: `/ehr/${patientId}/encounter/${encounterId}`, label: "Encounter", icon: ClipboardList }
      : null,
    patientId
      ? { href: `/ehr/${patientId}`, label: "Chart", icon: User, tone: "secondary" as const }
      : null,
    { href: withHandoff("/finance/billing"), label: "Billing", icon: Receipt, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell
        title="Finance Dashboard"
        subtitle="Financial management and revenue cycle"
      >
        <OrganizationPlaneContextBar />
        <div className="space-y-6">
          <MusheXFinanceJourneysOrchestrationRail />
          <WorkflowHeader
            badge="Revenue follow-through"
            badgeIcon={Receipt}
            title="Keep finance work tied to the patient, encounter, and outcome handoff that created it instead of starting from an unscoped dashboard."
            description="The finance hub now surfaces facility context, incoming closure source, and the next revenue-cycle surface before staff branch into billing, claims, or payments."
            context={[
              { label: "Facility", value: facility?.name ?? "No facility selected" },
              { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Finance workspace" },
              {
                label: "Encounter context",
                value: encounterId && patientId ? "Linked to active clinical episode" : "General finance queue",
              },
            ]}
            actions={actions}
            metrics={[
              {
                label: "Finance surfaces",
                value: String(FINANCE_SECTIONS.length),
                detail: "Billing, claims, payments, and tariffs remain reachable from one handoff-aware hub.",
              },
              {
                label: "Next action",
                value: encounterId ? "Continue linked workflow" : "Choose finance lane",
                detail:
                  encounterId && patientId
                    ? "Use the linked surfaces below without losing the current encounter and chart context."
                    : "Choose the finance workspace that matches the next revenue-cycle step.",
              },
            ]}
          />

          <div className="rounded-3xl border border-border bg-background/70 p-4">
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
              Revenue loop status
            </p>
            <p className="mt-2 text-sm text-foreground">
              {source === "discharge"
                ? "This dashboard was reached from encounter outcome, so the loop here is keeping billing, claims, and payments connected to the same patient and encounter."
                : "This dashboard anchors finance work, but each downstream surface should still preserve the originating patient and encounter context when it exists."}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              Open the next finance surface below, or move back to the encounter or chart when the revenue step needs clinical clarification.
            </p>
          </div>

          <div className="rounded-xl border border-info/25 bg-info-soft/60 p-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-primary-hover">
                  <Layers className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-foreground">Commerce & payer stack</h3>
                  <p className="mt-1 text-xs text-muted-foreground max-w-xl">
                    MSIKA, MusheX, and facility marketplace: canonical Experience routes, what legacy sidecars superseded,
                    and exact BFF gaps — no separate UI required for acceptance.
                  </p>
                </div>
              </div>
              <Link
                href="/finance/commerce-integrations"
                className="inline-flex shrink-0 items-center justify-center rounded-lg border border-indigo-300 bg-card px-4 py-2 text-sm font-medium text-primary-hover hover:bg-info-soft"
              >
                Open integration map
              </Link>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {FINANCE_SECTIONS.map((section) => {
              const Icon = section.icon;
              return (
                <Link
                  key={section.href}
                  href={withHandoff(section.href)}
                  className="bg-card rounded-lg border border-border p-5 hover:border-primary/25 hover:shadow-md transition-all group"
                >
                  <div className="flex items-start gap-3">
                    <div
                      className={`w-10 h-10 rounded-lg ${section.color} flex items-center justify-center shrink-0`}
                    >
                      <Icon className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-medium text-foreground text-sm group-hover:text-primary transition-colors">
                        {section.title}
                      </h3>
                      <p className="text-xs text-muted-foreground mt-0.5">{section.description}</p>
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
