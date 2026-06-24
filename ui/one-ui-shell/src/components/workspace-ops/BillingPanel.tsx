'use client';

import { useMemo, useState } from 'react';
import {
  FileText, CreditCard, AlertCircle, CheckCircle2,
  Clock, Receipt, Send, BarChart3, Loader2,
} from 'lucide-react';
import { LiveDataSourceBadge } from '@/components/common/LiveDataSourceBadge';
import { NotLiveNotice } from '@/components/common/NotLiveNotice';
import { useCoverageClaimsList, useCoverageRemittances } from '@/hooks/queries/useCoverage';
import { useFinanceRevenueSummary } from '@/hooks/queries/useFinanceBillingWorkspace';

const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function pickArray(node: unknown, key: string): Record<string, unknown>[] {
  const obj = node as Record<string, unknown> | undefined;
  const data = (obj?.data as Record<string, unknown> | undefined) ?? obj;
  const arr = data?.[key];
  return Array.isArray(arr) ? (arr as Record<string, unknown>[]) : [];
}

// ─── Types ───

type BillingTab = 'overview' | 'charges' | 'invoices' | 'claims' | 'payments';

// ─── Mock Data ───

const UNBILLED_CHARGES = [
  { id: 'CH-001', patient: 'M. Ndlovu', mrn: 'MRN-10234', ward: 'Medical Ward', items: 6, amount: 4200, admitDate: '2026-04-03', status: 'unbilled' },
  { id: 'CH-002', patient: 'S. Khumalo', mrn: 'MRN-10456', ward: 'ICU', items: 14, amount: 28500, admitDate: '2026-04-01', status: 'unbilled' },
  { id: 'CH-003', patient: 'T. Dlamini', mrn: 'MRN-10789', ward: 'Surgical Ward', items: 9, amount: 12800, admitDate: '2026-04-04', status: 'partial' },
  { id: 'CH-004', patient: 'L. Phiri', mrn: 'MRN-11002', ward: 'Emergency', items: 3, amount: 1850, admitDate: '2026-04-06', status: 'unbilled' },
];

const INVOICES = [
  { id: 'INV-202604-000340', patient: 'J. Banda', amount: 5600, paidAmount: 5600, status: 'paid', date: '2026-04-05', payer: 'GEMS' },
  { id: 'INV-202604-000341', patient: 'R. Zuma', amount: 8900, paidAmount: 4450, status: 'partial', date: '2026-04-05', payer: 'Discovery' },
  { id: 'INV-202604-000342', patient: 'A. Moyo', amount: 4200, paidAmount: 0, status: 'sent', date: '2026-04-06', payer: 'Self-pay' },
  { id: 'INV-202604-000343', patient: 'P. Sithole', amount: 12400, paidAmount: 0, status: 'draft', date: '2026-04-06', payer: 'Bonitas' },
  { id: 'INV-202604-000344', patient: 'C. Ngwenya', amount: 3200, paidAmount: 0, status: 'overdue', date: '2026-03-20', payer: 'Self-pay' },
];

const CLAIMS = [
  { id: 'CLM-20260405-0001', patient: 'J. Banda', scheme: 'GEMS', amount: 5600, status: 'paid', submittedAt: '2026-04-05', approvedAmount: undefined, rejectReason: undefined },
  { id: 'CLM-20260405-0002', patient: 'R. Zuma', scheme: 'Discovery', amount: 8900, status: 'partially_approved', submittedAt: '2026-04-05', approvedAmount: 4450, rejectReason: 'Pre-auth required for MRI' },
  { id: 'CLM-20260406-0003', patient: 'P. Sithole', scheme: 'Bonitas', amount: 12400, status: 'submitted', submittedAt: '2026-04-06', approvedAmount: undefined, rejectReason: undefined },
  { id: 'CLM-20260404-0004', patient: 'K. Mthembu', scheme: 'Medihelp', amount: 6700, status: 'rejected', submittedAt: '2026-04-04', approvedAmount: undefined, rejectReason: 'Member not active' },
];

const RECENT_PAYMENTS = [
  { id: 'TXN-20260406-0001', patient: 'A. Moyo', amount: 2100, method: 'Card', time: '10:30', reference: 'REF-445566' },
  { id: 'TXN-20260406-0002', patient: 'M. Ndlovu', amount: 500, method: 'Cash', time: '11:15', reference: 'RCT-000234' },
  { id: 'TXN-20260405-0003', patient: 'J. Banda', amount: 5600, method: 'EFT', time: '14:00', reference: 'GEMS-REF-9988' },
];

const REVENUE_WEEK = [
  { day: 'Mon', val: 32 },
  { day: 'Tue', val: 45 },
  { day: 'Wed', val: 28 },
  { day: 'Thu', val: 52 },
  { day: 'Fri', val: 41 },
  { day: 'Sat', val: 18 },
];

const PAYER_MIX = [
  { name: 'Medical Aid', pct: 62, barColor: 'bg-primary' },
  { name: 'Self-Pay', pct: 22, barColor: 'bg-amber-500' },
  { name: 'Government', pct: 12, barColor: 'bg-green-500' },
  { name: 'Corporate', pct: 4, barColor: 'bg-purple-500' },
];

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

  const totalUnbilled = UNBILLED_CHARGES.reduce((s, c) => s + c.amount, 0);
  const totalOutstanding = INVOICES.filter(i => ['sent', 'overdue', 'partial'].includes(i.status)).reduce((s, i) => s + i.amount - i.paidAmount, 0);
  const todayCollected = RECENT_PAYMENTS.reduce((s, p) => s + p.amount, 0);
  const displayClaims = useMemo(() => {
    if (preferLive && liveClaims.length > 0) {
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
    }
    return CLAIMS.map((cl) => ({ ...cl, live: false }));
  }, [preferLive, liveClaims]);

  const claimsPending = displayClaims.filter((c) => c.status === 'submitted').length;

  const tabConfig: { key: BillingTab; label: string; icon: React.ComponentType<{ className?: string }>; badge?: number }[] = [
    { key: 'overview', label: 'Overview', icon: BarChart3 },
    { key: 'charges', label: 'Charges', icon: Receipt, badge: UNBILLED_CHARGES.length > 0 ? UNBILLED_CHARGES.length : undefined },
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
          <p className="text-lg font-bold">R{(totalUnbilled / 1000).toFixed(1)}k</p>
          <p className="text-[10px] text-muted-foreground">{UNBILLED_CHARGES.length} accounts</p>
        </div>
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Clock className="h-4 w-4 text-red-500" /><span className="text-xs text-muted-foreground">Outstanding</span></div>
          <p className="text-lg font-bold">R{(totalOutstanding / 1000).toFixed(1)}k</p>
          <p className="text-[10px] text-muted-foreground">{INVOICES.filter(i => i.status === 'overdue').length} overdue</p>
        </div>
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><CreditCard className="h-4 w-4 text-green-500" /><span className="text-xs text-muted-foreground">Collected Today</span></div>
          <p className="text-lg font-bold">R{(todayCollected / 1000).toFixed(1)}k</p>
          <p className="text-[10px] text-muted-foreground">{RECENT_PAYMENTS.length} transactions</p>
        </div>
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Send className="h-4 w-4 text-impilo-400" /><span className="text-xs text-muted-foreground">Claims Pending</span></div>
          <p className="text-lg font-bold">{claimsPending}</p>
          <p className="text-[10px] text-muted-foreground">{CLAIMS.filter(c => c.status === 'rejected').length} rejected</p>
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

      {/* Overview Tab */}
      {activeTab === 'overview' && (
        <div className="space-y-3">
        {!overviewLive && (
          <NotLiveNotice>
            <span className="font-semibold">Not live yet.</span> No COSTA revenue recorded for
            this facility/period — showing illustrative figures.
          </NotLiveNotice>
        )}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Revenue trend */}
          <div className="bg-card border border-border rounded-lg">
            <div className="px-4 pt-4 pb-2"><h4 className="text-sm font-semibold">{overviewLive ? 'Revenue Trend (monthly)' : 'Revenue This Week'}</h4></div>
            <div className="px-4 pb-4 space-y-3">
              {overviewLive
                ? liveRevenueTrend.map(d => (
                    <div key={d.label} className="flex items-center gap-3">
                      <span className="text-xs w-8 text-muted-foreground">{d.label}</span>
                      <div className="flex-1 bg-neutral-100 rounded-full h-2">
                        <div className="h-2 rounded-full bg-primary" style={{ width: `${(d.val / trendMax) * 100}%` }} />
                      </div>
                      <span className="text-xs font-medium w-16 text-right">R{Math.round(d.val).toLocaleString()}</span>
                    </div>
                  ))
                : REVENUE_WEEK.map(d => (
                    <div key={d.day} className="flex items-center gap-3">
                      <span className="text-xs w-8 text-muted-foreground">{d.day}</span>
                      <div className="flex-1 bg-neutral-100 rounded-full h-2">
                        <div className="h-2 rounded-full bg-primary" style={{ width: `${(d.val / 60) * 100}%` }} />
                      </div>
                      <span className="text-xs font-medium w-12 text-right">R{d.val}k</span>
                    </div>
                  ))}
            </div>
          </div>
          {/* Payer Mix */}
          <div className="bg-card border border-border rounded-lg">
            <div className="px-4 pt-4 pb-2"><h4 className="text-sm font-semibold">Payer Mix</h4></div>
            <div className="px-4 pb-4 space-y-3">
              {(overviewLive && livePayerMix.length > 0
                ? livePayerMix.map((p, i) => ({ name: p.name, pct: p.pct, barColor: PAYER_BAR_COLORS[i % PAYER_BAR_COLORS.length] }))
                : PAYER_MIX
              ).map(p => (
                <div key={p.name} className="space-y-1">
                  <div className="flex justify-between text-xs">
                    <span>{p.name}</span><span className="text-muted-foreground">{p.pct}%</span>
                  </div>
                  <div className="w-full bg-neutral-100 rounded-full h-1.5">
                    <div className={`h-1.5 rounded-full ${p.barColor}`} style={{ width: `${p.pct}%` }} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
        </div>
      )}

      {/* Charges Tab */}
      {activeTab === 'charges' && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          <NotLiveNotice>
            <span className="font-semibold">Not live yet.</span> Unbilled charges are
            demo data — no facility-scoped charges endpoint is wired.
          </NotLiveNotice>
          {UNBILLED_CHARGES.map(ch => (
            <div key={ch.id} className="bg-card border border-border border-l-4 border-l-amber-400 rounded-lg py-3 px-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">{ch.patient} <span className="text-xs text-muted-foreground">({ch.mrn})</span></p>
                  <p className="text-xs text-muted-foreground">{ch.ward} &middot; {ch.items} charge items &middot; Admitted {ch.admitDate}</p>
                </div>
                <div className="flex items-center gap-3">
                  <p className="text-sm font-bold">R{ch.amount.toLocaleString()}</p>
                  {getStatusBadge(ch.status)}
                  <button className="px-2 py-1 text-xs bg-primary text-white rounded hover:bg-primary-hover">Bill</button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Invoices Tab */}
      {activeTab === 'invoices' && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          <NotLiveNotice>
            <span className="font-semibold">Not live yet.</span> Invoices are demo data —
            no facility-scoped invoices endpoint is wired.
          </NotLiveNotice>
          {INVOICES.map(inv => (
            <div key={inv.id} className={`bg-card border border-border rounded-lg py-3 px-4 ${inv.status === 'overdue' ? 'border-l-4 border-l-red-500' : ''}`}>
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-semibold">{inv.id}</p>
                    {getStatusBadge(inv.status)}
                  </div>
                  <p className="text-xs text-muted-foreground">{inv.patient} &middot; {inv.payer} &middot; {inv.date}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold">R{inv.amount.toLocaleString()}</p>
                  {inv.paidAmount > 0 && inv.paidAmount < inv.amount && (
                    <p className="text-[10px] text-green-600">R{inv.paidAmount.toLocaleString()} paid</p>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Claims Tab */}
      {activeTab === 'claims' && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
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
      {activeTab === 'payments' && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          <NotLiveNotice>
            <span className="font-semibold">Not live yet.</span> Recent payments are demo
            data — no facility-scoped payments endpoint is wired.
          </NotLiveNotice>
          {RECENT_PAYMENTS.map(pmt => (
            <div key={pmt.id} className="flex items-center justify-between p-3 rounded-lg border border-border">
              <div className="flex items-center gap-3">
                <div className="h-8 w-8 rounded-lg bg-green-100 flex items-center justify-center">
                  <CheckCircle2 className="h-4 w-4 text-green-600" />
                </div>
                <div>
                  <p className="text-sm font-medium">{pmt.patient}</p>
                  <p className="text-xs text-muted-foreground">{pmt.method} &middot; {pmt.reference} &middot; {pmt.time}</p>
                </div>
              </div>
              <p className="text-sm font-bold text-green-600">+R{pmt.amount.toLocaleString()}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
