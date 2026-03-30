"use client";

import React, { useState } from "react";
import Link from "next/link";
import { apiClient } from "@/lib/apiClient";

interface Claim {
  id: string;
  patientCpid: string;
  facilityId: string;
  payerId: string;
  status: "SUBMITTED" | "ACKNOWLEDGED" | "ADJUDICATED" | "PAID" | "REJECTED" | "APPEALED";
  totalAmount: number;
  currency: string;
  submittedAt: string;
  adjudicatedAt?: string;
}

interface LedgerEntry {
  id: string;
  entryType: "DEBIT" | "CREDIT";
  amount: number;
  currency: string;
  description: string;
  reference: string;
  postedAt: string;
}

const CLAIM_STATUS_BADGE: Record<string, string> = {
  SUBMITTED: "bg-blue-50 text-blue-700",
  ACKNOWLEDGED: "bg-purple-50 text-purple-700",
  ADJUDICATED: "bg-amber-50 text-amber-700",
  PAID: "bg-emerald-50 text-emerald-700",
  REJECTED: "bg-red-50 text-red-700",
  APPEALED: "bg-orange-50 text-orange-700",
};

export default function MushexDashboardPage() {
  const [claims, setClaims] = useState<Claim[]>([]);
  const [ledger, setLedger] = useState<LedgerEntry[]>([]);
  const [claimsLoading, setClaimsLoading] = useState(false);
  const [ledgerLoading, setLedgerLoading] = useState(false);
  const [claimsLoaded, setClaimsLoaded] = useState(false);
  const [ledgerLoaded, setLedgerLoaded] = useState(false);
  const [activeTab, setActiveTab] = useState<"claims" | "ledger">("claims");

  const loadClaims = async () => {
    setClaimsLoading(true);
    try {
      const data = await apiClient.get<Claim[]>("/internal/v1/claims/snapshot?limit=50");
      setClaims(Array.isArray(data) ? data : []);
    } catch {
      setClaims([]);
    } finally {
      setClaimsLoading(false);
      setClaimsLoaded(true);
    }
  };

  const loadLedger = async () => {
    setLedgerLoading(true);
    try {
      const data = await apiClient.get<LedgerEntry[]>("/internal/v1/ledger?limit=50");
      setLedger(Array.isArray(data) ? data : []);
    } catch {
      setLedger([]);
    } finally {
      setLedgerLoading(false);
      setLedgerLoaded(true);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          MUSHEX — Finance & Claims Engine
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Claims switching, adjudication, double-entry ledger, payment orchestration, and fraud detection
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <Link href="/mushex/claims" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Billing</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Claims</p>
            <p className="text-xs text-neutral-500 mt-1">Submit and adjudicate insurance claims</p>
          </div>
        </Link>

        <Link href="/mushex/payments" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Settlement</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Payments</p>
            <p className="text-xs text-neutral-500 mt-1">Payment runs and disbursement tracking</p>
          </div>
        </Link>

        <Link href="/mushex/ledger" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Accounting</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Ledger</p>
            <p className="text-xs text-neutral-500 mt-1">Double-entry journal and audit trail</p>
          </div>
        </Link>

        <Link href="/mushex/fraud" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Integrity</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Fraud Flags</p>
            <p className="text-xs text-neutral-500 mt-1">Suspicious patterns and anomaly alerts</p>
          </div>
        </Link>
      </div>

      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 overflow-hidden">
        <div className="flex border-b border-neutral-100">
          <button
            onClick={() => setActiveTab("claims")}
            className={`px-6 py-3 text-sm font-medium transition-colors ${
              activeTab === "claims"
                ? "text-brand-primary border-b-2 border-brand-primary"
                : "text-neutral-500 hover:text-neutral-900"
            }`}
          >
            Claims
          </button>
          <button
            onClick={() => setActiveTab("ledger")}
            className={`px-6 py-3 text-sm font-medium transition-colors ${
              activeTab === "ledger"
                ? "text-brand-primary border-b-2 border-brand-primary"
                : "text-neutral-500 hover:text-neutral-900"
            }`}
          >
            Ledger
          </button>
        </div>

        <div className="p-6">
          {activeTab === "claims" && (
            <>
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-base font-semibold text-neutral-900">Recent Claims</h2>
                <button
                  onClick={loadClaims}
                  disabled={claimsLoading}
                  className="px-4 py-1.5 text-sm font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90 disabled:opacity-50"
                >
                  {claimsLoading ? "Loading…" : claimsLoaded ? "Refresh" : "Load Claims"}
                </button>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-neutral-100">
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Claim ID</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Patient CPID</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Payer</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Amount</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Status</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Submitted</th>
                    </tr>
                  </thead>
                  <tbody>
                    {!claimsLoaded ? (
                      <tr>
                        <td colSpan={6} className="py-8 text-center text-neutral-400 text-sm">
                          Click &ldquo;Load Claims&rdquo; to fetch recent claims
                        </td>
                      </tr>
                    ) : claims.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="py-8 text-center text-neutral-400 text-sm">
                          No claims found
                        </td>
                      </tr>
                    ) : (
                      claims.map((claim) => (
                        <tr key={claim.id} className="border-b border-neutral-50 hover:bg-neutral-25">
                          <td className="py-3 px-2 font-mono text-xs text-neutral-500">
                            {claim.id.slice(0, 8)}…
                          </td>
                          <td className="py-3 px-2 font-mono text-xs text-neutral-700">
                            {claim.patientCpid}
                          </td>
                          <td className="py-3 px-2 text-neutral-600 text-xs">{claim.payerId}</td>
                          <td className="py-3 px-2 font-medium text-neutral-900">
                            {claim.currency} {claim.totalAmount.toLocaleString("en-ZW", { minimumFractionDigits: 2 })}
                          </td>
                          <td className="py-3 px-2">
                            <span
                              className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                                CLAIM_STATUS_BADGE[claim.status] ?? "bg-neutral-100 text-neutral-600"
                              }`}
                            >
                              {claim.status}
                            </span>
                          </td>
                          <td className="py-3 px-2 text-neutral-500 text-xs">
                            {new Date(claim.submittedAt).toLocaleDateString()}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {activeTab === "ledger" && (
            <>
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-base font-semibold text-neutral-900">Ledger Entries</h2>
                <button
                  onClick={loadLedger}
                  disabled={ledgerLoading}
                  className="px-4 py-1.5 text-sm font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90 disabled:opacity-50"
                >
                  {ledgerLoading ? "Loading…" : ledgerLoaded ? "Refresh" : "Load Ledger"}
                </button>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-neutral-100">
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Entry ID</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Type</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Amount</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Description</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Reference</th>
                      <th className="text-left py-3 px-2 text-neutral-500 font-medium">Posted</th>
                    </tr>
                  </thead>
                  <tbody>
                    {!ledgerLoaded ? (
                      <tr>
                        <td colSpan={6} className="py-8 text-center text-neutral-400 text-sm">
                          Click &ldquo;Load Ledger&rdquo; to fetch entries
                        </td>
                      </tr>
                    ) : ledger.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="py-8 text-center text-neutral-400 text-sm">
                          No ledger entries found
                        </td>
                      </tr>
                    ) : (
                      ledger.map((entry) => (
                        <tr key={entry.id} className="border-b border-neutral-50 hover:bg-neutral-25">
                          <td className="py-3 px-2 font-mono text-xs text-neutral-500">
                            {entry.id.slice(0, 8)}…
                          </td>
                          <td className="py-3 px-2">
                            <span
                              className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                                entry.entryType === "CREDIT"
                                  ? "bg-emerald-50 text-emerald-700"
                                  : "bg-red-50 text-red-700"
                              }`}
                            >
                              {entry.entryType}
                            </span>
                          </td>
                          <td className="py-3 px-2 font-medium text-neutral-900">
                            {entry.currency} {entry.amount.toLocaleString("en-ZW", { minimumFractionDigits: 2 })}
                          </td>
                          <td className="py-3 px-2 text-neutral-600 max-w-[180px] truncate">
                            {entry.description}
                          </td>
                          <td className="py-3 px-2 font-mono text-xs text-neutral-500">
                            {entry.reference}
                          </td>
                          <td className="py-3 px-2 text-neutral-500 text-xs">
                            {new Date(entry.postedAt).toLocaleString("en-ZW", {
                              day: "2-digit",
                              month: "short",
                              hour: "2-digit",
                              minute: "2-digit",
                            })}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
