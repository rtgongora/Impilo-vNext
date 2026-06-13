"use client";

import { useState } from "react";
import { costaApi, type AuditTrail, type BillStatus } from "@/lib/costaApi";

const STATUS_BADGE: Record<BillStatus, string> = {
  DRAFT: "badge-draft",
  ACCUMULATING: "badge-accumulating",
  PENDING_APPROVAL: "badge-pending-approval",
  APPROVED: "badge-approved",
  FINALIZED: "badge-finalized",
  INVOICED: "badge-invoiced",
  PAID: "badge-paid",
  PARTIALLY_PAID: "badge-partially-paid",
  REFUNDED: "badge-refunded",
  VOIDED: "badge-voided",
};

export default function AuditPage() {
  const [billId, setBillId] = useState("");
  const [audit, setAudit] = useState<AuditTrail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadAudit = async () => {
    if (!billId.trim()) {
      setError("Enter a bill ID to audit.");
      return;
    }

    setLoading(true);
    setError(null);
    setAudit(null);

    try {
      const data = await costaApi.auditBill(billId.trim());
      setAudit(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load audit trail");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Audit Trail</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Complete bill audit trail with lines, parties, approvals, payments, refunds, and claims.
        </p>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {/* Search */}
      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={billId}
          onChange={(e) => setBillId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && loadAudit()}
          placeholder="Enter Bill ID (ULID)..."
          className="input-field flex-1"
        />
        <button onClick={loadAudit} disabled={loading} className="btn-primary">
          {loading ? "Loading..." : "Audit"}
        </button>
      </div>

      {audit && (
        <div className="space-y-4">
          {/* Bill summary */}
          <div className="card p-6">
            <div className="flex items-center gap-3 mb-4">
              <h2 className="text-lg font-semibold text-neutral-900">Bill {audit.bill.id}</h2>
              <span className={STATUS_BADGE[audit.bill.status]}>{audit.bill.status.replace(/_/g, " ")}</span>
            </div>
            <div className="grid grid-cols-5 gap-4 text-sm">
              <div>
                <span className="text-neutral-500">Type</span>
                <p className="font-medium">{audit.bill.billType}</p>
              </div>
              <div>
                <span className="text-neutral-500">Total Net</span>
                <p className="font-medium font-mono">{Number(audit.bill.totalNet).toFixed(2)} {audit.bill.currency}</p>
              </div>
              <div>
                <span className="text-neutral-500">Lines</span>
                <p className="font-medium">{audit.lines_count} ({audit.voided_lines} voided)</p>
              </div>
              <div>
                <span className="text-neutral-500">Created</span>
                <p className="font-medium text-xs">{new Date(audit.bill.createdAt).toLocaleString()}</p>
              </div>
              <div>
                <span className="text-neutral-500">Updated</span>
                <p className="font-medium text-xs">{new Date(audit.bill.updatedAt).toLocaleString()}</p>
              </div>
            </div>
          </div>

          {/* Approvals */}
          {audit.approvals.length > 0 && (
            <div className="card p-4">
              <h3 className="text-sm font-semibold text-neutral-700 mb-3">Approvals ({audit.approvals.length})</h3>
              <div className="space-y-2">
                {audit.approvals.map((a) => (
                  <div key={a.id} className="flex items-center justify-between bg-neutral-50 rounded-lg p-3 text-xs">
                    <div className="flex items-center gap-3">
                      <span className={`badge ${a.status === "APPROVED" ? "bg-emerald-100 text-primary-hover" : "bg-red-100 text-red-800"}`}>
                        {a.status}
                      </span>
                      <span className="text-neutral-600">Step: {a.step}</span>
                      <span className="text-neutral-500">by {a.actorId}</span>
                    </div>
                    <span className="text-neutral-400">{new Date(a.createdAt).toLocaleString()}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Payments */}
          {audit.payments.length > 0 && (
            <div className="card p-4">
              <h3 className="text-sm font-semibold text-neutral-700 mb-3">Payments ({audit.payments.length})</h3>
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b border-neutral-200">
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Type</th>
                    <th className="text-right px-3 py-2 font-medium text-neutral-600">Amount</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Status</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">External Ref</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Date</th>
                  </tr>
                </thead>
                <tbody>
                  {audit.payments.map((p) => (
                    <tr key={p.id} className="border-b border-neutral-100">
                      <td className="px-3 py-2">{p.paymentType}</td>
                      <td className="px-3 py-2 text-right font-mono">{Number(p.amount).toFixed(2)}</td>
                      <td className="px-3 py-2">
                        <span className="badge bg-emerald-100 text-primary-hover">{p.status}</span>
                      </td>
                      <td className="px-3 py-2 font-mono text-neutral-500">{p.externalRef || "—"}</td>
                      <td className="px-3 py-2 text-neutral-500">{new Date(p.createdAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Refunds */}
          {audit.refunds.length > 0 && (
            <div className="card p-4">
              <h3 className="text-sm font-semibold text-neutral-700 mb-3">Refunds ({audit.refunds.length})</h3>
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b border-neutral-200">
                    <th className="text-right px-3 py-2 font-medium text-neutral-600">Amount</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Reason</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Code</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Status</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Date</th>
                  </tr>
                </thead>
                <tbody>
                  {audit.refunds.map((r) => (
                    <tr key={r.id} className="border-b border-neutral-100">
                      <td className="px-3 py-2 text-right font-mono text-red-600">{Number(r.amount).toFixed(2)}</td>
                      <td className="px-3 py-2">{r.reason}</td>
                      <td className="px-3 py-2 font-mono text-neutral-500">{r.reasonCode}</td>
                      <td className="px-3 py-2">
                        <span className="badge bg-red-100 text-red-800">{r.status}</span>
                      </td>
                      <td className="px-3 py-2 text-neutral-500">{new Date(r.createdAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Invoice */}
          {audit.invoice && (
            <div className="card p-4">
              <h3 className="text-sm font-semibold text-neutral-700 mb-3">Invoice</h3>
              <div className="grid grid-cols-4 gap-4 text-sm">
                <div>
                  <span className="text-neutral-500">Invoice #</span>
                  <p className="font-medium font-mono">{audit.invoice.invoiceNumber}</p>
                </div>
                <div>
                  <span className="text-neutral-500">Total Due</span>
                  <p className="font-medium font-mono">{Number(audit.invoice.totalDue).toFixed(2)} {audit.invoice.currency}</p>
                </div>
                <div>
                  <span className="text-neutral-500">Issued</span>
                  <p className="font-medium text-xs">{new Date(audit.invoice.issuedAt).toLocaleString()}</p>
                </div>
              </div>
            </div>
          )}

          {/* Parties */}
          {audit.parties.length > 0 && (
            <div className="card p-4">
              <h3 className="text-sm font-semibold text-neutral-700 mb-3">Party Allocations</h3>
              <div className="grid grid-cols-4 gap-3">
                {audit.parties.map((p) => (
                  <div key={p.id} className="bg-neutral-50 rounded-lg p-3 border border-neutral-200">
                    <span className="text-xs text-neutral-500 uppercase tracking-wider">{p.partyType}</span>
                    <p className="text-lg font-semibold text-neutral-900 mt-1">
                      {Number(p.allocatedAmount).toFixed(2)}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Full JSON */}
          <details className="card p-4">
            <summary className="text-sm font-semibold text-neutral-700 cursor-pointer">
              Raw Audit Data (JSON)
            </summary>
            <pre className="mt-3 text-xs text-neutral-800 font-mono whitespace-pre-wrap bg-neutral-50 rounded-lg p-4 border border-neutral-200 max-h-96 overflow-y-auto">
              {JSON.stringify(audit, null, 2)}
            </pre>
          </details>
        </div>
      )}

      {!audit && !loading && !error && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          Enter a bill ID above to view the complete audit trail.
        </div>
      )}
    </div>
  );
}
