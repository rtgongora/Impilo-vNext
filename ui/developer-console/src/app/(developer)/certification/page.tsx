"use client";

import React, { Suspense, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  listClients,
  runCertification,
  listCertifications,
  getCertification,
} from "@/lib/developerApi";
import type {
  DeveloperClient,
  Certification,
  CertificationRunResult,
  CertificationCheck,
} from "@/types/developer";
import { CertResultBadge } from "@/components/StatusBadge";
import { useDeveloperSession } from "@/stores/sessionStore";

function CertificationPageContent() {
  const searchParams = useSearchParams();
  const preselectedClientId = searchParams.get("clientId");

  const [clients, setClients] = useState<DeveloperClient[]>([]);
  const [selectedClientId, setSelectedClientId] = useState<string>(preselectedClientId ?? "");
  const [certifications, setCertifications] = useState<Certification[]>([]);
  const [currentRun, setCurrentRun] = useState<CertificationRunResult | null>(null);
  const [selectedReport, setSelectedReport] = useState<{ checks: CertificationCheck[] } | null>(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const session = useDeveloperSession((s) => s.session);

  useEffect(() => {
    async function load() {
      try {
        const data = await listClients();
        setClients(data.items);
        if (!selectedClientId && data.items.length > 0) {
          setSelectedClientId(data.items[0].client_id);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load clients");
      } finally {
        setLoading(false);
      }
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- initial client list only
  }, []);

  const loadCertifications = useCallback(async () => {
    if (!selectedClientId) return;
    try {
      const data = await listCertifications(selectedClientId);
      setCertifications(data.items);
    } catch {
      /* ignore if no certs yet */
    }
  }, [selectedClientId]);

  useEffect(() => {
    void loadCertifications();
  }, [loadCertifications]);

  async function handleRunCertification() {
    setRunning(true);
    setError(null);
    setCurrentRun(null);
    try {
      const result = await runCertification(
        selectedClientId,
        session?.actorId ?? "developer-console",
      );
      setCurrentRun(result);
      await loadCertifications();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Certification failed");
    } finally {
      setRunning(false);
    }
  }

  async function handleViewReport(certId: string) {
    try {
      const cert = await getCertification(certId);
      if (cert.report) {
        setSelectedReport(JSON.parse(cert.report));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load report");
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-sm text-neutral-500">Loading...</div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">Certification</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Run contract compliance checks and view certification history
        </p>
      </div>

      {/* Client selector + run button */}
      <div className="flex items-end gap-4 mb-8">
        <div className="flex-1 max-w-xs">
          <label className="block text-sm font-medium text-neutral-700 mb-1.5">Select Client</label>
          <select
            value={selectedClientId}
            onChange={(e) => setSelectedClientId(e.target.value)}
            className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary"
          >
            <option value="">Select a client...</option>
            {clients.map((c) => (
              <option key={c.client_id} value={c.client_id}>
                {c.client_name}
              </option>
            ))}
          </select>
        </div>
        <button
          onClick={handleRunCertification}
          disabled={!selectedClientId || running}
          className="px-6 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 transition-colors disabled:opacity-50"
        >
          {running ? "Running Checks..." : "Run Certification"}
        </button>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-6">
          {error}
        </div>
      )}

      {/* Current run results */}
      {currentRun && (
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6 mb-8">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-neutral-900">Latest Run Result</h2>
            <CertResultBadge result={currentRun.result} />
          </div>
          <div className="grid grid-cols-3 gap-4 mb-4">
            <div className="text-center p-3 bg-neutral-50 rounded-lg">
              <p className="text-2xl font-semibold text-neutral-900">{currentRun.checks_total}</p>
              <p className="text-xs text-neutral-500">Total Checks</p>
            </div>
            <div className="text-center p-3 bg-success/5 rounded-lg">
              <p className="text-2xl font-semibold text-success">{currentRun.checks_passed}</p>
              <p className="text-xs text-neutral-500">Passed</p>
            </div>
            <div className="text-center p-3 bg-danger/5 rounded-lg">
              <p className="text-2xl font-semibold text-danger">{currentRun.checks_failed}</p>
              <p className="text-xs text-neutral-500">Failed</p>
            </div>
          </div>
          <div className="space-y-2">
            {currentRun.checks.map((check, i) => (
              <div key={i} className="flex items-center gap-3 py-2 px-3 rounded-lg bg-neutral-50/50">
                <div className={`w-5 h-5 rounded-full flex items-center justify-center ${
                  check.passed ? "bg-success text-white" : "bg-danger text-white"
                }`}>
                  {check.passed ? (
                    <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  ) : (
                    <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  )}
                </div>
                <div className="flex-1">
                  <p className="text-sm font-medium text-neutral-900">{check.check.replace(/_/g, " ")}</p>
                  <p className="text-xs text-neutral-500">{check.detail}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Certification history */}
      <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <h2 className="text-lg font-semibold text-neutral-900 mb-4">Certification History</h2>
        {certifications.length === 0 ? (
          <p className="text-sm text-neutral-500 py-4 text-center">No certifications yet. Run your first check above.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-100">
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Result</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Checks</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Passed</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Failed</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Triggered By</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Date</th>
                  <th className="text-right py-2 px-2 text-neutral-500 font-medium text-xs">Report</th>
                </tr>
              </thead>
              <tbody>
                {certifications.map((c) => (
                  <tr key={c.certification_id} className="border-b border-neutral-50">
                    <td className="py-2.5 px-2"><CertResultBadge result={c.result} /></td>
                    <td className="py-2.5 px-2 text-neutral-700">{c.checks_total}</td>
                    <td className="py-2.5 px-2 text-success">{c.checks_passed}</td>
                    <td className="py-2.5 px-2 text-danger">{c.checks_failed}</td>
                    <td className="py-2.5 px-2 text-neutral-600 text-xs">{c.triggered_by ?? "-"}</td>
                    <td className="py-2.5 px-2 text-neutral-500 text-xs">
                      {c.completed_at ? new Date(c.completed_at).toLocaleDateString() : "-"}
                    </td>
                    <td className="py-2.5 px-2 text-right">
                      <button
                        onClick={() => handleViewReport(c.certification_id)}
                        className="text-xs text-brand-primary hover:underline"
                      >
                        View
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Report modal */}
      {selectedReport && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-card rounded-[16px] shadow-lg max-w-lg w-full max-h-[80vh] overflow-y-auto p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-neutral-900">Certification Report</h3>
              <button onClick={() => setSelectedReport(null)} className="text-neutral-400 hover:text-neutral-600">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="space-y-2">
              {selectedReport.checks.map((check, i) => (
                <div key={i} className="flex items-center gap-3 py-2 px-3 rounded-lg bg-neutral-50">
                  <span className={`text-xs font-medium ${check.passed ? "text-success" : "text-danger"}`}>
                    {check.passed ? "PASS" : "FAIL"}
                  </span>
                  <div className="flex-1">
                    <p className="text-sm text-neutral-900">{check.check.replace(/_/g, " ")}</p>
                    <p className="text-xs text-neutral-500">{check.detail}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default function CertificationPage() {
  return (
    <Suspense fallback={<div className="text-sm text-neutral-500 py-20 text-center">Loading certifications...</div>}>
      <CertificationPageContent />
    </Suspense>
  );
}
