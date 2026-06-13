"use client";

/**
 * Commerce & payer integration map — canonical Experience routes vs legacy sidecars.
 * Route: /finance/commerce-integrations
 *
 * No sovereign MSIKA/MusheX calls from this page: it documents BFF-backed paths and exact contract gaps.
 */

import Link from "next/link";
import { ArrowLeft, ExternalLink, Layers } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function CommerceIntegrationsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Commerce & payer stack"
        subtitle="Experience is the accepted UI. Sidecar apps are superseded here unless a flow is explicitly blocked pending a BFF contract."
      >
        <div className="mb-4">
          <Link
            href="/finance"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="w-4 h-4" /> Finance dashboard
          </Link>
        </div>

        <div className="space-y-8 max-w-4xl">
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="rounded-lg bg-indigo-100 p-2 text-primary-hover">
                <Layers className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-base font-semibold text-foreground">Acceptance rule</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Critical operator workflows for marketplace procurement, coverage, costing, and reporting must be reachable in the canonical{" "}
                  <code className="text-xs">ui/one-ui-shell</code> via{" "}
                  <code className="text-xs">NEXT_PUBLIC_BFF_URL</code> (Experience BFF{" "}
                  <code className="text-xs">/internal/v1/…</code>). Legacy Next apps under{" "}
                  <code className="text-xs">ui/msika-*</code> and <code className="text-xs">ui/mushex-*</code> are not an
                  acceptance path. Where no BFF proxy exists yet, this page lists the <strong>exact</strong> upstream paths
                  the sidecars use so Integration can add matching internal routes.
                </p>
              </div>
            </div>
          </section>

          <section>
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground mb-3">
              Live in Experience (BFF-backed)
            </h2>
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <thead className="bg-background text-left">
                  <tr>
                    <th className="px-3 py-2 font-medium text-foreground">Experience path</th>
                    <th className="px-3 py-2 font-medium text-foreground">BFF contract (representative)</th>
                    <th className="px-3 py-2 font-medium text-foreground">Supersedes sidecar</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/marketplace" className="text-primary hover:underline">
                        /marketplace
                      </Link>{" "}
                      (+ catalog, orders, vendors, bookings)
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      GET/POST /internal/v1/marketplace/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Facility marketplace workspace (Experience BFF marketplace endpoints).
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/marketplace/catalog" className="text-primary hover:underline">
                        /marketplace/catalog
                      </Link>{" "}
                      (MSIKA registry mode)
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      GET /internal/v1/product-registry/search, GET /internal/v1/product-registry/items/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Registry lookup now runs inside Experience (no sidecar required).
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/marketplace/orders/[id]" className="text-primary hover:underline">
                        /marketplace/orders/[id]
                      </Link>{" "}
                      (MSIKA Flow order detail)
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      GET/POST /internal/v1/commerce/orders/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Order detail + actions (validate/price/pay/cancel/tracking) now run inside Experience.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/marketplace/pickup" className="text-primary hover:underline">
                        /marketplace/pickup
                      </Link>
                      {" "}and{" "}
                      <Link href="/marketplace/substitutions" className="text-primary hover:underline">
                        /marketplace/substitutions
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      POST /internal/v1/commerce/orders/*/pickup/issue, POST /internal/v1/commerce/pickup/claim, GET/POST
                      /internal/v1/commerce/substitutions*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Pickup token handoff and substitution decisions now run in Experience instead of the MSIKA Flow
                      portal sidecar.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/marketplace/vendor" className="text-primary hover:underline">
                        /marketplace/vendor
                      </Link>
                      ,{" "}
                      <Link href="/marketplace/vendor/orders" className="text-primary hover:underline">
                        /marketplace/vendor/orders
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      GET/POST /internal/v1/commerce/vendor/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Trusted Experience-operator vendor queue and fulfilment (explicit vendor ID scope; not a vendor actor plane).
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/marketplace/ops" className="text-primary hover:underline">
                        /marketplace/ops
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      GET/POST /internal/v1/commerce/ops/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Reviews, stuck orders, audit, and vendor governance now have a real Experience workspace.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/finance/msika-governance" className="text-primary hover:underline">
                        /finance/msika-governance
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      GET/POST /internal/v1/msika/catalogs, GET/POST /internal/v1/msika/mappings/pending, POST /internal/v1/msika/import
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Catalog governance, mapping review, publishing, and CSV import now run in Experience instead of the MSIKA governance sidecar.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/finance/settlements" className="text-primary hover:underline">
                        /finance/settlements
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      POST/GET /internal/v1/finance/settlements/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Settlement run, detail, and payout release (MusheX upstream via BFF).
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/finance/reconciliation" className="text-primary hover:underline">
                        /finance/reconciliation
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      POST/GET /internal/v1/finance/reconciliation/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Statement import, unmatched list, match (MusheX recon). Restricted finance roles on BFF.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/finance/refunds" className="text-primary hover:underline">
                        /finance/refunds
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      GET/POST /internal/v1/finance/refunds/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Payment intent lookup and refund creation (MusheX). Uses general finance RBAC on BFF.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/finance/ledger" className="text-primary hover:underline">
                        /finance/ledger
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      GET /internal/v1/finance/ledger?intentId=...
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Ledger inspection is now handled in Experience instead of the MusheX finance sidecar.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/finance/payer-claims" className="text-primary hover:underline">
                        /finance/payer-claims
                      </Link>
                      ,{" "}
                      <Link href="/finance/payer-ops" className="text-primary hover:underline">
                        /finance/payer-ops
                      </Link>
                      {" "}and{" "}
                      <Link href="/finance/payer-claims/[claimId]" className="text-primary hover:underline">
                        /finance/payer-claims/[claimId]
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      GET/POST /internal/v1/finance/payer-ops/*, GET/POST /internal/v1/finance/payer-claims/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      Intents, remittance, adapters, fraud flags, ops reviews, payer-claim queue, and payer-claim detail actions (MusheX).
                      Tighter finance/admin roles apply on the BFF.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/coverage" className="text-primary hover:underline">
                        /coverage
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      /internal/v1/coverage/* (via CoverageController)
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">Payer/coverage ops in one surface.</td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/finance/billing" className="text-primary hover:underline">
                        /finance/billing
                      </Link>
                      ,{" "}
                      <Link href="/finance/payments" className="text-primary hover:underline">
                        /finance/payments
                      </Link>
                      ,{" "}
                      <Link href="/finance/claims" className="text-primary hover:underline">
                        /finance/claims
                      </Link>
                      ,{" "}
                      <Link href="/finance/tariffs" className="text-primary hover:underline">
                        /finance/tariffs
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      /internal/v1/finance/*
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      COSTA-backed billing/claims/payments — not MusheX settlement/recon APIs (see blocked).
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">
                      <Link href="/reports" className="text-primary hover:underline">
                        /reports
                      </Link>
                      ,{" "}
                      <Link href="/admin/data-export" className="text-primary hover:underline">
                        /admin/data-export
                      </Link>
                    </td>
                    <td className="px-3 py-2 font-mono text-xs">
                      POST/GET /internal/v1/reports/*, GET /internal/v1/admin/reports/jobs
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">Report job queue + detail — no fabricated file metrics.</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section>
            <h2 className="text-sm font-semibold uppercase tracking-wide text-warning-foreground mb-3">
              MSIKA — partially wired (registry + commerce)
            </h2>
            <p className="text-sm text-muted-foreground mb-3">
              Experience now proxies <strong>registry lookup</strong>, <strong>commerce flow</strong>, <strong>pickup</strong>,
              <strong> substitutions</strong>, and <strong>marketplace ops</strong> under{" "}
              <code className="text-xs">/internal/v1/product-registry/*</code> and <code className="text-xs">/internal/v1/commerce/*</code>.
              The remaining MSIKA gap is governance parity, not day-to-day operator rails.
            </p>
            <div className="mb-3 rounded-lg border border-success/25 bg-success-soft/50 p-4 text-sm text-emerald-950">
              <p className="font-medium">Governance is now live in Experience</p>
              <p className="mt-1">
                The canonical governance workspace is{" "}
                <Link href="/finance/msika-governance" className="underline underline-offset-2">
                  /finance/msika-governance
                </Link>
                , backed by <code className="text-[11px]">/internal/v1/msika/*</code>.
              </p>
            </div>
            <div className="overflow-x-auto rounded-lg border border-warning/35 bg-warning-soft/40">
              <table className="w-full text-sm">
                <thead className="bg-amber-100/60 text-left">
                  <tr>
                    <th className="px-3 py-2 font-medium text-foreground">Legacy UI</th>
                    <th className="px-3 py-2 font-medium text-foreground">Representative upstream paths (repo)</th>
                    <th className="px-3 py-2 font-medium text-foreground">Missing Experience contract</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-amber-100">
                  <tr>
                    <td className="px-3 py-2">ui/msika-web</td>
                    <td className="px-3 py-2 font-mono text-[11px]">
                      GET /v1/catalogs, POST /v1/catalogs, GET /v1/mappings/pending, GET /v1/search, GET
                      /v1/catalogs/&#123;id&#125;/items, …
                    </td>
                    <td className="px-3 py-2">
                      BFF pass-through or aggregate for catalog governance, mappings, publish, browse — e.g.{" "}
                      <code className="text-xs">/internal/v1/msika/catalogs</code> mirroring sovereign routes with v1.1 headers.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">ui/msika-flow-portal</td>
                    <td className="px-3 py-2 font-mono text-[11px]">
                      GET /v1/catalog/search, POST /v1/cart/validate, POST /v1/orders, POST /v1/orders/&#123;id&#125;/pay, GET
                      /v1/rx/substitutions, …
                    </td>
                    <td className="px-3 py-2">
                      Now covered in Experience via <code className="text-xs">/internal/v1/commerce/*</code> and{" "}
                      <code className="text-xs">/internal/v1/product-registry/*</code> across catalog, order detail, cart, pickup,
                      and substitutions.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">ui/msika-flow-vendor</td>
                    <td className="px-3 py-2 font-mono text-[11px]">Vendor queue/fulfillment paths under same /v1 host.</td>
                    <td className="px-3 py-2">
                      Vendor queue wired in Experience via{" "}
                      <code className="text-xs">/internal/v1/commerce/vendor/*</code> (see{" "}
                      <Link href="/marketplace/vendor/orders" className="text-primary hover:underline">
                        /marketplace/vendor/orders
                      </Link>
                      ). Dedicated vendor SSO / actor plane is still out of scope here.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">ui/msika-flow-ops</td>
                    <td className="px-3 py-2 font-mono text-[11px]">
                      GET /v1/ops/reviews/pending, GET /v1/ops/stuck-orders, …
                    </td>
                    <td className="px-3 py-2">
                      Now covered in Experience via <code className="text-xs">/internal/v1/commerce/ops/*</code> and{" "}
                      <Link href="/marketplace/ops" className="text-primary hover:underline">
                        /marketplace/ops
                      </Link>
                      .
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section>
            <h2 className="text-sm font-semibold uppercase tracking-wide text-warning-foreground mb-3">
              Blocked — MusheX (no <code className="text-xs">/internal/v1/mushex/…</code> proxy)
            </h2>
            <p className="text-sm text-muted-foreground mb-3">
              Sidecars call <code className="text-xs">/mushex/v1/…</code> on the same API host as MSIKA. Experience still
              exposes <strong>no</strong> generic <code className="text-xs">/internal/v1/mushex/…</code> pass-through.
              Instead, typed finance routes proxy MusheX: ledger (
              <Link href="/finance/ledger" className="text-primary hover:underline">
                /finance/ledger
              </Link>
              ), settlements (
              <Link href="/finance/settlements" className="text-primary hover:underline">
                /finance/settlements
              </Link>
              ), recon (
              <Link href="/finance/reconciliation" className="text-primary hover:underline">
                /finance/reconciliation
              </Link>
              ), refunds (
              <Link href="/finance/refunds" className="text-primary hover:underline">
                /finance/refunds
              </Link>
              ), payer/ops (
              <Link href="/finance/payer-ops" className="text-primary hover:underline">
                /finance/payer-ops
              </Link>
              ), payer claims queue (
              <Link href="/finance/payer-claims" className="text-primary hover:underline">
                /finance/payer-claims
              </Link>
              ), payer claim detail (
              <Link href="/finance/payer-claims/[claimId]" className="text-primary hover:underline">
                /finance/payer-claims/[claimId]
              </Link>
              ). Other MusheX paths outside these typed finance routes may still be absent.
            </p>
            <div className="overflow-x-auto rounded-lg border border-warning/35 bg-warning-soft/40">
              <table className="w-full text-sm">
                <thead className="bg-amber-100/60 text-left">
                  <tr>
                    <th className="px-3 py-2 font-medium text-foreground">Legacy UI</th>
                    <th className="px-3 py-2 font-medium text-foreground">Representative upstream paths (repo)</th>
                    <th className="px-3 py-2 font-medium text-foreground">Missing Experience contract</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-amber-100">
                  <tr>
                    <td className="px-3 py-2">ui/mushex-finance-console</td>
                    <td className="px-3 py-2 font-mono text-[11px]">
                      POST /mushex/v1/settlements/run, GET /mushex/v1/recon/unmatched, POST /mushex/v1/recon/match, POST
                      /mushex/v1/payment-intents/&#123;id&#125;/refund, …
                    </td>
                    <td className="px-3 py-2">
                      Core console flows map to <code className="text-xs">/internal/v1/finance/ledger</code>,{" "}
                      <code className="text-xs">/internal/v1/finance/settlements/*</code>,{" "}
                      <code className="text-xs">/internal/v1/finance/reconciliation/*</code>, and{" "}
                      <code className="text-xs">/internal/v1/finance/refunds/*</code>.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">ui/mushex-payer-portal</td>
                    <td className="px-3 py-2 font-mono text-[11px]">
                      GET /mushex/v1/payment-intents/&#123;id&#125;, POST …/issue-remittance-slip, POST /mushex/v1/remittance/claim
                    </td>
                    <td className="px-3 py-2">
                      Covered by <code className="text-xs">/internal/v1/finance/payer-ops/*</code> and{" "}
                      <code className="text-xs">/internal/v1/finance/refunds/*</code> where mirrored in experience-bff.
                    </td>
                  </tr>
                  <tr>
                    <td className="px-3 py-2">ui/mushex-ops-console</td>
                    <td className="px-3 py-2 font-mono text-[11px]">
                      GET /mushex/v1/adapters, GET /mushex/v1/fraud-flags, GET /mushex/v1/ops-reviews, POST …/claims/…
                    </td>
                    <td className="px-3 py-2">
                      Adapters, fraud flags, and ops reviews are proxied under{" "}
                      <code className="text-xs">/internal/v1/finance/payer-ops/*</code>. MusheX claim detail actions are
                      now paired with a real claims queue and detail surface under{" "}
                      <code className="text-xs">/internal/v1/finance/payer-claims*</code>.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section className="rounded-lg border border-border bg-background p-4 text-sm text-muted-foreground">
            <p className="font-medium text-foreground">Health intelligence (admin)</p>
            <p className="mt-1">
              Payer-side analytics that are not claims/remittance reporting remain scoped to{" "}
              <Link href="/coverage" className="text-primary hover:underline">
                Coverage → Intelligence
              </Link>{" "}
              tab, which only aggregates fields already returned by coverage remittance APIs — no synthetic fraud/MLR KPIs.
              Dedicated population-health admin dashboards are out of Agent 1 lane unless covered by a named BFF contract.
            </p>
          </section>

          <p className="text-xs text-muted-foreground flex items-center gap-1">
            <ExternalLink className="h-3.5 w-3.5" />
            Source references: sidecar <code className="text-[10px]">src/lib/*Api.ts</code> and page fetch paths in this
            repository.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
