'use client';

import { useMemo, useState } from 'react';
import {
  FileText, CreditCard, AlertCircle, CheckCircle2,
  Clock, Receipt, Send, BarChart3, Loader2,
} from 'lucide-react';
import { LiveDataSourceBadge } from '@/components/common/LiveDataSourceBadge';
import { NotLiveNotice } from '@/components/common/NotLiveNotice';
import { useCoverageClaimsList, useCoverageRemittances } from '@/hooks/queries/useCoverage';
import {
  useFinanceRevenueSummary,
  useFacilityUnbilledCharges,
  useFacilityInvoices,
  useFacilityPayments,
} from '@/hooks/queries/useFinanceBillingWorkspace';

const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function pickArray(node: unknown, key: string): Record<string, unknown>[] {
  const obj = node as Record<string, unknown> | undefined;
  const data = (obj?.data as Record<string, unknown> | undefined) ?? obj;
  const arr = data?.[key];
  return Array.isArray(arr) ? (arr as Record<string, unknown>[]) : [];
}

/** Extract the list from a {data:[...]} envelope (or a bare array). */
function dataArray(node: unknown): Record<string, unknown>[] {
  if (Array.isArray(node)) return node as Record<string, unknown>[];
  const obj = node as Record<string, unknown> | undefined;
  return Array.isArray(obj?.data) ? (obj!.data as Record<string, unknown>[]) : [];
}

function num(v: unknown): number {
  return Number(v ?? 0);
}

// ─── Types ───

type BillingTab = 'overview' | 'charges' | 'invoices' | 'claims' | 'payments';

// ─── Helpers ───

function getStatusBadge(status: string) {
  const map: Record<string, { label: string; classes: string }> = {
    unbilled: { label: 'Unbilled', classes: 'bg-neutral-100 text-muted-foreground' },
    partial: { label: 'Partial', classes: 'bg-amber-100 text-warning-foreground' },
    paid: { label: 'Paid', classes: 'bg-green-100 text-green-700' },
    sent: { label: 'Sent', classes: 'bg-primary-soft text-primary' },
    draft: { label: 'Draft', classes: 'bg-neutral-100 text-muted-foreground' },
    overdue: { label: 'Overdue', classes: 'bg-red-100 text-danger' },
    submitted: { label: 'Submitted', classes: 'bg-primary-soft text-primary' },
    partially_approved: { label: 'Part Approved', classes: 'bg-amber-100 text-warning-foreground' },
    rejected: { label: 'Rejected', classes: 'bg-red-100 text-danger' },
  };
  const cfg = map[status] || { label: status, classes: 'bg-neutral-100 text-muted-foreground' };
  return <span className={`px-2 py-0.5 rounded text-xs font-medium ${cfg.classes}`}>{cfg.label}</span>;
}

// ─── Component ───

interface BillingPanelProps {
  facilityId?: string | null;
}

export function BillingPanel({ facilityId }: BillingPanelProps) {
  const [activeTab, setActiveTab] = useState<BillingTab>('overview');
  const [preferLive, setPreferLive] = useState(true);
  const liveClaimsQ = useCoverageClaimsList({ facilityId });
  const liveRemittancesQ = useCoverageRemittances();
  const liveClaims = liveClaimsQ.data ?? [];
  const hasLiveClaims = liveClaims.length > 0;
  const dataSource = preferLive && hasLiveClaims ? 'live' : preferLive && liveClaimsQ.isLoading ? 'mixed' : 'demo';

  // Overview revenue trend + payer mix — real COSTA aggregation (revenue-summary).
  const now = new Date();
  const revenueQ = useFinanceRevenueSummary(facilityId, now.getFullYear(), now.getMonth() + 1);
  const livePayerMix = useMemo(() => {
    const rows = pickArray(revenueQ.data, 'byPayerType')
      .map(r => ({ name: String(r.bucket ?? 'UNSPECIFIED'), total: Number(r.total ?? 0) }))
      .filter(r => r.total > 0);
    const sum = rows.reduce((s, r) => s + r.total, 0);
    return sum > 0 ? rows.map(r => ({ name: r.name, pct: Math.round((r.total / sum) * 100) })) : [];
  }, [revenueQ.data]);
  const liveRevenueTrend = useMemo(
    () => pickArray(revenueQ.data, 'monthlyTrend')
      .map(r => ({ label: MONTH_LABELS[(Number(r.month ?? 0) - 1) % 12] ?? String(r.month), val: Number(r.total ?? 0) }))
      .filter(r => r.val > 0),
    [revenueQ.data],
  );
  const overviewLive = preferLive && (livePayerMix.length > 0 || liveRevenueTrend.length > 0);
  const trendMax = Math.max(1, ...liveRevenueTrend.map(d => d.val));
  const PAYER_BAR_COLORS = ['bg-primary', 'bg-amber-500', 'bg-green-500', 'bg-purple-500', 'bg-sky-500'];

  // Facility-scoped charges / invoices / payments — real COSTA reads.
  const chargesQ = useFacilityUnbilledCharges(facilityId);
  const invoicesQ = useFacilityInvoices(facilityId);
  const paymentsQ = useFacilityPayments(facilityId);
  const liveCharges = useMemo(() => dataArray(chargesQ.data).map(r => ({
    id: String(r.billId ?? ''), encounter: r.encounterId ? String(r.encounterId) : '—',
    status: String(r.status ?? ''), amount: num(r.totalCharge), payable: num(r.patientPayable),
    currency: String(r.currency ?? 'USD'),
  })), [chargesQ.data]);
  const liveInvoices = useMemo(() => dataArray(invoicesQ.data).map(r => ({
    id: String(r.invoiceId ?? ''), number: String(r.invoiceNumber ?? '—'), status: String(r.status ?? ''),
    total: num(r.total), outstanding: num(r.outstanding), currency: String(r.currency ?? 'USD'),
    payer: r.payerRef ? String(r.payerRef) : '—', issuedAt: r.issuedAt ? String(r.issuedAt).slice(0, 10) : '—',
  })), [invoicesQ.data]);
  const livePayments = useMemo(() => dataArray(paymentsQ.data).map(r => ({
    id: String(r.paymentId ?? ''), status: String(r.status ?? ''), type: String(r.paymentType ?? ''),
    amount: num(r.paidAmount) || num(r.amount), currency: String(r.currency ?? 'USD'),
    paidAt: r.paidAt ? String(r.paidAt).slice(0, 16).replace('T', ' ') : '—',
  })), [paymentsQ.data]);
  const chargesLive = preferLive && liveCharges.length > 0;
  const invoicesLive = preferLive && liveInvoices.length > 0;
  const paymentsLive = preferLive && livePayments.length > 0;

  // KPIs — derived from real COSTA reads only.
  const totalUnbilled = liveCharges.reduce((s, c) => s + c.amount, 0);
  const overdueInvoices = liveInvoices.filter(i => i.status.toLowerCase() === 'overdue').length;
  const totalOutstanding = liveInvoices.reduce((s, i) => s + i.outstanding, 0);
  const todayCollected = livePayments.reduce((s, p) => s + p.amount, 0);
  const displayClaims = useMemo(() => {
    return liveClaims.map((cl) => ({
      id: cl.claimNumber || cl.id,
      patient: cl.facilityId || 'Member',
      scheme: cl.claimType || 'Coverage',
      amount: cl.totalAmount,
      status: cl.status,
      submittedAt: cl.createdAt?.slice(0, 10) ?? '—',
      approvedAmount: undefined as number | undefined,
      rejectReason: undefined as string | undefined,
      live: true,
    }));
  }, [liveClaims]);

  const claimsPending = displayClaims.filter((c) => c.status.toLowerCase() === 'submitted').length;
  const claimsRejected = displayClaims.filter((c) => c.status.toLowerCase() === 'rejected').length;

  const tabConfig: { key: BillingTab; label: string; icon: React.ComponentType<{ className?: string }>; badge?: number }[] = [
    { key: 'overview', label: 'Overview', icon: BarChart3 },
    { key: 'charges', label: 'Charges', icon: Receipt, badge: liveCharges.length > 0 ? liveCharges.length : undefined },
    { key: 'invoices', label: 'Invoices', icon: FileText },
    { key: 'claims', label: 'Claims', icon: Send },
    { key: 'payments', label: 'Payments', icon: CreditCard },
  ];

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <LiveDataSourceBadge source={dataSource} />
        <label className="inline-flex items-center gap-2 text-xs text-muted-foreground">
          <input
            type="checkbox"
            checked={preferLive}
            onChange={(e) => setPreferLive(e.target.checked)}
            className="rounded border-border"
          />
          Prefer live coverage data
        </label>
        {preferLive && liveClaimsQ.isLoading ? (
          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
            <Loader2 className="h-3 w-3 animate-spin" /> Loading claims…
          </span>
        ) : null}
      </div>
      {preferLive && liveRemittancesQ.data && liveRemittancesQ.data.length > 0 ? (
        <p className="text-[11px] text-primary-hover bg-success-soft border border-emerald-100 rounded px-2 py-1">
          {liveRemittancesQ.data.length} live remittance(s) from coverage-service
        </p>
      ) : null}
      {/* KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><AlertCircle className="h-4 w-4 text-amber-500" /><span className="text-xs text-muted-foreground">Unbilled Charges</span></div>
          <p className="text-lg font-bold">{(totalUnbilled / 1000).toFixed(1)}k</p>
          <p className="text-[10px] text-muted-foreground">{liveCharges.length} accounts</p>
        </div>
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Clock className="h-4 w-4 text-red-500" /><span className="text-xs text-muted-foreground">Outstanding</span></div>
          <p className="text-lg font-bold">{(totalOutstanding / 1000).toFixed(1)}k</p>
          <p className="text-[10px] text-muted-foreground">{overdueInvoices} overdue</p>
        </div>
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><CreditCard className="h-4 w-4 text-green-500" /><span className="text-xs text-muted-foreground">Collected Today</span></div>
          <p className="text-lg font-bold">{(todayCollected / 1000).toFixed(1)}k</p>
          <p className="text-[10px] text-muted-foreground">{livePayments.length} transactions</p>
        </div>
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Send className="h-4 w-4 text-impilo-400" /><span className="text-xs text-muted-foreground">Claims Pending</span></div>
          <p className="text-lg font-bold">{claimsPending}</p>
          <p className="text-[10px] text-muted-foreground">{claimsRejected} rejected</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-border">
        {tabConfig.map(tab => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                activeTab === tab.key ? 'border-impilo-500 text-primary' : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {tab.label}
              {tab.badge && (
                <span className="ml-1 px-1.5 py-0.5 rounded-full bg-neutral-100 text-[10px] font-medium">{tab.badge}</span>
              )}
            </button>
          );
        })}
      </div>

      {/* Overview Tab — live COSTA revenue aggregates only */}
      {activeTab === 'overview' && (
        <div className="space-y-3">
        {!overviewLive && (
          <NotLiveNotice>
            <span className="font-semibold">No revenue data.</span> No COSTA revenue recorded
            for this facility/period.
          </NotLiveNotice>
        )}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Revenue trend */}
          <div className="bg-card border border-border rounded-lg">
            <div className="px-4 pt-4 pb-2"><h4 className="text-sm font-semibold">Revenue Trend (monthly)</h4></div>
            <div className="px-4 pb-4 space-y-3">
              {liveRevenueTrend.length === 0 ? (
                <p className="text-xs text-muted-foreground py-4 text-center">No revenue data for this period.</p>
              ) : (
                liveRevenueTrend.map(d => (
                  <div key={d.label} className="flex items-center gap-3">
                    <span className="text-xs w-8 text-muted-foreground">{d.label}</span>
                    <div className="flex-1 bg-neutral-100 rounded-full h-2">
                      <div className="h-2 rounded-full bg-primary" style={{ width: `${(d.val / trendMax) * 100}%` }} />
                    </div>
                    <span className="text-xs font-medium w-16 text-right">{Math.round(d.val).toLocaleString()}</span>
                  </div>
                ))
              )}
            </div>
          </div>
          {/* Payer Mix */}
          <div className="bg-card border border-border rounded-lg">
            <div className="px-4 pt-4 pb-2"><h4 className="text-sm font-semibold">Payer Mix</h4></div>
            <div className="px-4 pb-4 space-y-3">
              {livePayerMix.length === 0 ? (
                <p className="text-xs text-muted-foreground py-4 text-center">No payer-mix data for this period.</p>
              ) : (
                livePayerMix
                  .map((p, i) => ({ name: p.name, pct: p.pct, barColor: PAYER_BAR_COLORS[i % PAYER_BAR_COLORS.length] }))
                  .map(p => (
                    <div key={p.name} className="space-y-1">
                      <div className="flex justify-between text-xs">
                        <span>{p.name}</span><span className="text-muted-foreground">{p.pct}%</span>
                      </div>
                      <div className="w-full bg-neutral-100 rounded-full h-1.5">
                        <div className={`h-1.5 rounded-full ${p.barColor}`} style={{ width: `${p.pct}%` }} />
                      </div>
                    </div>
                  ))
              )}
            </div>
          </div>
        </div>
        </div>
      )}

      {/* Charges Tab */}
      {activeTab === 'charges' && chargesLive && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          {liveCharges.map(ch => (
            <div key={ch.id} className="bg-card border border-border border-l-4 border-l-amber-400 rounded-lg py-3 px-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">{ch.id}</p>
                  <p className="text-xs text-muted-foreground">Encounter {ch.encounter} &middot; patient payable {ch.currency} {ch.payable.toLocaleString()}</p>
                </div>
                <div className="flex items-center gap-3">
                  <p className="text-sm font-bold">{ch.currency} {ch.amount.toLocaleString()}</p>
                  {getStatusBadge(ch.status.toLowerCase())}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
      {activeTab === 'charges' && !chargesLive && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          <div className="text-center py-8 text-muted-foreground text-sm">
            No unbilled charges. No open bills for this facility.
          </div>
        </div>
      )}

      {/* Invoices Tab */}
      {activeTab === 'invoices' && invoicesLive && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          {liveInvoices.map(inv => (
            <div key={inv.id} className="bg-card border border-border rounded-lg py-3 px-4">
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-semibold">{inv.number}</p>
                    {getStatusBadge(inv.status.toLowerCase())}
                  </div>
                  <p className="text-xs text-muted-foreground">{inv.payer} &middot; {inv.issuedAt}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold">{inv.currency} {inv.total.toLocaleString()}</p>
                  {inv.outstanding > 0 && (
                    <p className="text-[10px] text-amber-600">{inv.currency} {inv.outstanding.toLocaleString()} outstanding</p>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
      {activeTab === 'invoices' && !invoicesLive && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          <div className="text-center py-8 text-muted-foreground text-sm">
            No invoices. None issued for this facility.
          </div>
        </div>
      )}

      {/* Claims Tab */}
      {activeTab === 'claims' && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          {displayClaims.length === 0 && (
            <div className="text-center py-8 text-muted-foreground text-sm">
              No claims. None submitted to coverage for this facility.
            </div>
          )}
          {displayClaims.map(cl => (
            <div key={cl.id} className={`bg-card border border-border rounded-lg py-3 px-4 ${cl.status === 'rejected' ? 'border-l-4 border-l-red-500' : ''}`}>
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-semibold">{cl.id}</p>
                    {getStatusBadge(cl.status)}
                  </div>
                  <p className="text-xs text-muted-foreground">{cl.patient} &middot; {cl.scheme} &middot; {cl.submittedAt}</p>
                  {cl.rejectReason && <p className="text-xs text-red-500 mt-0.5">{cl.rejectReason}</p>}
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold">R{cl.amount.toLocaleString()}</p>
                  {cl.approvedAmount && <p className="text-[10px] text-green-600">R{cl.approvedAmount.toLocaleString()} approved</p>}
                  {cl.status === 'rejected' && (
                    <button className="px-2 py-0.5 text-[10px] border border-border rounded mt-1 hover:bg-background">Resubmit</button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Payments Tab */}
      {activeTab === 'payments' && paymentsLive && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          {livePayments.map(pmt => (
            <div key={pmt.id} className="flex items-center justify-between p-3 rounded-lg border border-border">
              <div className="flex items-center gap-3">
                <div className="h-8 w-8 rounded-lg bg-green-100 flex items-center justify-center">
                  <CheckCircle2 className="h-4 w-4 text-green-600" />
                </div>
                <div>
                  <p className="text-sm font-medium">{pmt.type || 'Payment'}</p>
                  <p className="text-xs text-muted-foreground">{pmt.status} &middot; {pmt.paidAt}</p>
                </div>
              </div>
              <p className="text-sm font-bold text-green-600">+{pmt.currency} {pmt.amount.toLocaleString()}</p>
            </div>
          ))}
        </div>
      )}
      {activeTab === 'payments' && !paymentsLive && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          <div className="text-center py-8 text-muted-foreground text-sm">
            No payments. None recorded for this facility.
          </div>
        </div>
      )}
    </div>
  );
}
