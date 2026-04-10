'use client';

import { useState } from 'react';
import {
  FileText, CreditCard, AlertCircle, CheckCircle2,
  Clock, Receipt, Send, BarChart3,
} from 'lucide-react';

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
  { name: 'Medical Aid', pct: 62, barColor: 'bg-blue-500' },
  { name: 'Self-Pay', pct: 22, barColor: 'bg-amber-500' },
  { name: 'Government', pct: 12, barColor: 'bg-green-500' },
  { name: 'Corporate', pct: 4, barColor: 'bg-purple-500' },
];

// ─── Helpers ───

function getStatusBadge(status: string) {
  const map: Record<string, { label: string; classes: string }> = {
    unbilled: { label: 'Unbilled', classes: 'bg-gray-100 text-gray-600' },
    partial: { label: 'Partial', classes: 'bg-amber-100 text-amber-700' },
    paid: { label: 'Paid', classes: 'bg-green-100 text-green-700' },
    sent: { label: 'Sent', classes: 'bg-blue-100 text-blue-700' },
    draft: { label: 'Draft', classes: 'bg-gray-100 text-gray-600' },
    overdue: { label: 'Overdue', classes: 'bg-red-100 text-red-700' },
    submitted: { label: 'Submitted', classes: 'bg-blue-100 text-blue-700' },
    partially_approved: { label: 'Part Approved', classes: 'bg-amber-100 text-amber-700' },
    rejected: { label: 'Rejected', classes: 'bg-red-100 text-red-700' },
  };
  const cfg = map[status] || { label: status, classes: 'bg-gray-100 text-gray-600' };
  return <span className={`px-2 py-0.5 rounded text-xs font-medium ${cfg.classes}`}>{cfg.label}</span>;
}

// ─── Component ───

export function BillingPanel() {
  const [activeTab, setActiveTab] = useState<BillingTab>('overview');

  const totalUnbilled = UNBILLED_CHARGES.reduce((s, c) => s + c.amount, 0);
  const totalOutstanding = INVOICES.filter(i => ['sent', 'overdue', 'partial'].includes(i.status)).reduce((s, i) => s + i.amount - i.paidAmount, 0);
  const todayCollected = RECENT_PAYMENTS.reduce((s, p) => s + p.amount, 0);
  const claimsPending = CLAIMS.filter(c => c.status === 'submitted').length;

  const tabConfig: { key: BillingTab; label: string; icon: React.ComponentType<{ className?: string }>; badge?: number }[] = [
    { key: 'overview', label: 'Overview', icon: BarChart3 },
    { key: 'charges', label: 'Charges', icon: Receipt, badge: UNBILLED_CHARGES.length > 0 ? UNBILLED_CHARGES.length : undefined },
    { key: 'invoices', label: 'Invoices', icon: FileText },
    { key: 'claims', label: 'Claims', icon: Send },
    { key: 'payments', label: 'Payments', icon: CreditCard },
  ];

  return (
    <div className="space-y-3">
      {/* KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><AlertCircle className="h-4 w-4 text-amber-500" /><span className="text-xs text-gray-500">Unbilled Charges</span></div>
          <p className="text-lg font-bold">R{(totalUnbilled / 1000).toFixed(1)}k</p>
          <p className="text-[10px] text-gray-400">{UNBILLED_CHARGES.length} accounts</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Clock className="h-4 w-4 text-red-500" /><span className="text-xs text-gray-500">Outstanding</span></div>
          <p className="text-lg font-bold">R{(totalOutstanding / 1000).toFixed(1)}k</p>
          <p className="text-[10px] text-gray-400">{INVOICES.filter(i => i.status === 'overdue').length} overdue</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><CreditCard className="h-4 w-4 text-green-500" /><span className="text-xs text-gray-500">Collected Today</span></div>
          <p className="text-lg font-bold">R{(todayCollected / 1000).toFixed(1)}k</p>
          <p className="text-[10px] text-gray-400">{RECENT_PAYMENTS.length} transactions</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Send className="h-4 w-4 text-blue-500" /><span className="text-xs text-gray-500">Claims Pending</span></div>
          <p className="text-lg font-bold">{claimsPending}</p>
          <p className="text-[10px] text-gray-400">{CLAIMS.filter(c => c.status === 'rejected').length} rejected</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {tabConfig.map(tab => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                activeTab === tab.key ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {tab.label}
              {tab.badge && (
                <span className="ml-1 px-1.5 py-0.5 rounded-full bg-gray-100 text-[10px] font-medium">{tab.badge}</span>
              )}
            </button>
          );
        })}
      </div>

      {/* Overview Tab */}
      {activeTab === 'overview' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Revenue This Week */}
          <div className="bg-white border border-gray-200 rounded-lg">
            <div className="px-4 pt-4 pb-2"><h4 className="text-sm font-semibold">Revenue This Week</h4></div>
            <div className="px-4 pb-4 space-y-3">
              {REVENUE_WEEK.map(d => (
                <div key={d.day} className="flex items-center gap-3">
                  <span className="text-xs w-8 text-gray-500">{d.day}</span>
                  <div className="flex-1 bg-gray-100 rounded-full h-2">
                    <div className="h-2 rounded-full bg-blue-500" style={{ width: `${(d.val / 60) * 100}%` }} />
                  </div>
                  <span className="text-xs font-medium w-12 text-right">R{d.val}k</span>
                </div>
              ))}
            </div>
          </div>
          {/* Payer Mix */}
          <div className="bg-white border border-gray-200 rounded-lg">
            <div className="px-4 pt-4 pb-2"><h4 className="text-sm font-semibold">Payer Mix</h4></div>
            <div className="px-4 pb-4 space-y-3">
              {PAYER_MIX.map(p => (
                <div key={p.name} className="space-y-1">
                  <div className="flex justify-between text-xs">
                    <span>{p.name}</span><span className="text-gray-500">{p.pct}%</span>
                  </div>
                  <div className="w-full bg-gray-100 rounded-full h-1.5">
                    <div className={`h-1.5 rounded-full ${p.barColor}`} style={{ width: `${p.pct}%` }} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Charges Tab */}
      {activeTab === 'charges' && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          {UNBILLED_CHARGES.map(ch => (
            <div key={ch.id} className="bg-white border border-gray-200 border-l-4 border-l-amber-400 rounded-lg py-3 px-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">{ch.patient} <span className="text-xs text-gray-500">({ch.mrn})</span></p>
                  <p className="text-xs text-gray-500">{ch.ward} &middot; {ch.items} charge items &middot; Admitted {ch.admitDate}</p>
                </div>
                <div className="flex items-center gap-3">
                  <p className="text-sm font-bold">R{ch.amount.toLocaleString()}</p>
                  {getStatusBadge(ch.status)}
                  <button className="px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700">Bill</button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Invoices Tab */}
      {activeTab === 'invoices' && (
        <div className="space-y-2 max-h-[400px] overflow-auto">
          {INVOICES.map(inv => (
            <div key={inv.id} className={`bg-white border border-gray-200 rounded-lg py-3 px-4 ${inv.status === 'overdue' ? 'border-l-4 border-l-red-500' : ''}`}>
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-semibold">{inv.id}</p>
                    {getStatusBadge(inv.status)}
                  </div>
                  <p className="text-xs text-gray-500">{inv.patient} &middot; {inv.payer} &middot; {inv.date}</p>
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
          {CLAIMS.map(cl => (
            <div key={cl.id} className={`bg-white border border-gray-200 rounded-lg py-3 px-4 ${cl.status === 'rejected' ? 'border-l-4 border-l-red-500' : ''}`}>
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-semibold">{cl.id}</p>
                    {getStatusBadge(cl.status)}
                  </div>
                  <p className="text-xs text-gray-500">{cl.patient} &middot; {cl.scheme} &middot; {cl.submittedAt}</p>
                  {cl.rejectReason && <p className="text-xs text-red-500 mt-0.5">{cl.rejectReason}</p>}
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold">R{cl.amount.toLocaleString()}</p>
                  {cl.approvedAmount && <p className="text-[10px] text-green-600">R{cl.approvedAmount.toLocaleString()} approved</p>}
                  {cl.status === 'rejected' && (
                    <button className="px-2 py-0.5 text-[10px] border border-gray-200 rounded mt-1 hover:bg-gray-50">Resubmit</button>
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
          {RECENT_PAYMENTS.map(pmt => (
            <div key={pmt.id} className="flex items-center justify-between p-3 rounded-lg border border-gray-200">
              <div className="flex items-center gap-3">
                <div className="h-8 w-8 rounded-lg bg-green-100 flex items-center justify-center">
                  <CheckCircle2 className="h-4 w-4 text-green-600" />
                </div>
                <div>
                  <p className="text-sm font-medium">{pmt.patient}</p>
                  <p className="text-xs text-gray-500">{pmt.method} &middot; {pmt.reference} &middot; {pmt.time}</p>
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
