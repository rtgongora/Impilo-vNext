"use client";

import { useState } from "react";
import {
  costaApi,
  type BillHeader,
  type BillLine,
  type BillParty,
  type BillStatus,
} from "@/lib/costaApi";

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

export default function BillsPage() {
  const [billId, setBillId] = useState("");
  const [bill, setBill] = useState<BillHeader | null>(null);
  const [lines, setLines] = useState<BillLine[]>([]);
  const [parties, setParties] = useState<BillParty[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const loadBill = async () => {
    if (!billId.trim()) {
      setError("Enter a bill ID to search.");
      return;
    }

    setLoading(true);
    setError(null);
    setBill(null);
    setLines([]);
    setParties([]);

    try {
      const data = await costaApi.getBill(billId.trim());
      setBill(data.bill);
      setLines(data.lines);
      setParties(data.parties);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load bill");
    } finally {
      setLoading(false);
    }
  };

  const performAction = async (action: string) => {
    if (!bill) return;
    setActionLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      let updated: BillHeader;
      switch (action) {
        case "recompute":
          updated = await costaApi.recompute(bill.id);
          break;
        case "submit-approval":
          updated = await costaApi.submitApproval(bill.id);
          break;
        case "approve":
          updated = await costaApi.approve(bill.id, "Approved via COSTA Console");
          break;
        case "finalize":
          updated = await costaApi.finalize(bill.id);
          break;
        default:
          return;
      }
      setBill(updated);
      setSuccessMessage(`Action "${action}" completed successfully.`);
      // Reload lines and parties
      const data = await costaApi.getBill(bill.id);
      setLines(data.lines);
      setParties(data.parties);
    } catch (err) {
      setError(err instanceof Error ? err.message : `Action "${action}" failed`);
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Bill Review</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Search and review bills. Perform lifecycle actions: recompute, approve, finalize.
        </p>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-success-soft border border-success/25 text-sm text-primary-hover">
          {successMessage}
          <button
            onClick={() => setSuccessMessage(null)}
            className="ml-2 text-primary hover:text-primary-hover font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Search */}
      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={billId}
          onChange={(e) => setBillId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && loadBill()}
          placeholder="Enter Bill ID (ULID)..."
          className="input-field flex-1"
        />
        <button onClick={loadBill} disabled={loading} className="btn-primary">
          {loading ? "Loading..." : "Search"}
        </button>
      </div>

      {/* Bill detail */}
      {bill && (
        <>
          <div className="card p-6 mb-4">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <h2 className="text-lg font-semibold text-neutral-900">
                  Bill {bill.id}
                </h2>
                <span className={STATUS_BADGE[bill.status]}>{bill.status.replace(/_/g, " ")}</span>
              </div>
              <div className="flex gap-2">
                {["DRAFT", "ACCUMULATING"].includes(bill.status) && (
                  <button onClick={() => performAction("recompute")} disabled={actionLoading} className="btn-secondary text-xs">
                    Recompute
                  </button>
                )}
                {bill.status === "ACCUMULATING" && (
                  <button onClick={() => performAction("submit-approval")} disabled={actionLoading} className="btn-secondary text-xs">
                    Submit for Approval
                  </button>
                )}
                {bill.status === "PENDING_APPROVAL" && (
                  <button onClick={() => performAction("approve")} disabled={actionLoading} className="btn-primary text-xs">
                    Approve
                  </button>
                )}
                {bill.status === "APPROVED" && (
                  <button onClick={() => performAction("finalize")} disabled={actionLoading} className="btn-primary text-xs">
                    Finalize
                  </button>
                )}
              </div>
            </div>

            <div className="grid grid-cols-4 gap-4 text-sm">
              <div>
                <span className="text-neutral-500">Type</span>
                <p className="font-medium">{bill.billType}</p>
              </div>
              <div>
                <span className="text-neutral-500">Currency</span>
                <p className="font-medium">{bill.currency}</p>
              </div>
              <div>
                <span className="text-neutral-500">Encounter</span>
                <p className="font-mono text-xs">{bill.encounterId || "—"}</p>
              </div>
              <div>
                <span className="text-neutral-500">MSIKA Order</span>
                <p className="font-mono text-xs">{bill.msikaOrderId || "—"}</p>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4 mt-4 pt-4 border-t border-neutral-200">
              <div>
                <span className="text-neutral-500 text-sm">Total Charge</span>
                <p className="text-xl font-semibold text-neutral-900">
                  {Number(bill.totalCharge).toFixed(2)}
                </p>
              </div>
              <div>
                <span className="text-neutral-500 text-sm">Total Discount</span>
                <p className="text-xl font-semibold text-red-600">
                  -{Number(bill.totalDiscount).toFixed(2)}
                </p>
              </div>
              <div>
                <span className="text-neutral-500 text-sm">Total Net</span>
                <p className="text-xl font-semibold text-primary-hover">
                  {Number(bill.totalNet).toFixed(2)}
                </p>
              </div>
            </div>
          </div>

          {/* Bill lines */}
          <div className="card overflow-hidden mb-4">
            <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
              <h3 className="text-sm font-semibold text-neutral-700">
                Lines ({lines.length})
              </h3>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200">
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">#</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Code</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Description</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Kind</th>
                  <th className="text-right px-4 py-2 font-medium text-neutral-600 text-xs">Qty</th>
                  <th className="text-right px-4 py-2 font-medium text-neutral-600 text-xs">Unit Cost</th>
                  <th className="text-right px-4 py-2 font-medium text-neutral-600 text-xs">Charge</th>
                  <th className="text-right px-4 py-2 font-medium text-neutral-600 text-xs">Net</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Method</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {lines.map((line) => (
                  <tr
                    key={line.id}
                    className={`hover:bg-neutral-50 transition-colors ${
                      line.voided ? "opacity-40 line-through" : ""
                    }`}
                  >
                    <td className="px-4 py-2 text-neutral-500 text-xs">{line.lineSeq}</td>
                    <td className="px-4 py-2 font-mono text-xs">{line.msikaCode}</td>
                    <td className="px-4 py-2 text-xs">{line.description}</td>
                    <td className="px-4 py-2">
                      <span className={`badge-kind-${line.kind.toLowerCase()}`}>{line.kind}</span>
                    </td>
                    <td className="px-4 py-2 text-right font-mono text-xs">{line.qty}</td>
                    <td className="px-4 py-2 text-right font-mono text-xs">{Number(line.unitCost).toFixed(2)}</td>
                    <td className="px-4 py-2 text-right font-mono text-xs">{Number(line.chargeAmount).toFixed(2)}</td>
                    <td className="px-4 py-2 text-right font-mono text-xs font-medium">{Number(line.netAmount).toFixed(2)}</td>
                    <td className="px-4 py-2 text-neutral-500 text-xs">{line.costMethod}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Bill parties */}
          {parties.length > 0 && (
            <div className="card p-4">
              <h3 className="text-sm font-semibold text-neutral-700 mb-3">Party Allocations</h3>
              <div className="grid grid-cols-4 gap-3">
                {parties.map((p) => (
                  <div key={p.id} className="bg-neutral-50 rounded-lg p-3 border border-neutral-200">
                    <span className="text-xs text-neutral-500 uppercase tracking-wider">{p.partyType}</span>
                    <p className="text-lg font-semibold text-neutral-900 mt-1">
                      {Number(p.allocatedAmount).toFixed(2)}
                    </p>
                    {p.partyRef && (
                      <span className="text-xs text-neutral-400 font-mono">{p.partyRef}</span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}

      {!bill && !loading && !error && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          Enter a bill ID above to view bill details and perform lifecycle actions.
        </div>
      )}
    </div>
  );
}
