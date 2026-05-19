"use client";

import React, { Suspense, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { listClients, getFederationReadiness } from "@/lib/developerApi";
import type { DeveloperClient, FederationReadiness } from "@/types/developer";
import { ReadinessBadge } from "@/components/StatusBadge";

function FederationPageContent() {
  const searchParams = useSearchParams();
  const preselectedClientId = searchParams.get("clientId");

  const [clients, setClients] = useState<DeveloperClient[]>([]);
  const [selectedClientId, setSelectedClientId] = useState<string>(preselectedClientId ?? "");
  const [readiness, setReadiness] = useState<FederationReadiness | null>(null);
  const [loading, setLoading] = useState(true);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const data = await listClients();
        setClients(data.items);
        if (!selectedClientId && data.items.length > 0) {
          setSelectedClientId(data.items[0].client_id);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load");
      } finally {
        setLoading(false);
      }
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- initial client list
  }, []);

  const handleCheckReadiness = useCallback(async () => {
    if (!selectedClientId) return;
    setChecking(true);
    setError(null);
    setReadiness(null);
    try {
      const result = await getFederationReadiness(selectedClientId);
      setReadiness(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Readiness check failed");
    } finally {
      setChecking(false);
    }
  }, [selectedClientId]);

  useEffect(() => {
    if (selectedClientId && !loading) {
      void handleCheckReadiness();
    }
  }, [selectedClientId, loading, handleCheckReadiness]);

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
        <h1 className="text-2xl font-semibold text-neutral-900">Federation Readiness</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Check pod readiness, token validation, and federation prerequisites
        </p>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-6">
          {error}
        </div>
      )}

      {/* Client selector */}
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
          onClick={handleCheckReadiness}
          disabled={!selectedClientId || checking}
          className="px-6 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 transition-colors disabled:opacity-50"
        >
          {checking ? "Checking..." : "Check Readiness"}
        </button>
      </div>

      {/* Readiness result */}
      {readiness && (
        <div className="space-y-6">
          {/* Overall status */}
          <div className={`rounded-[12px] shadow-subtle border p-6 ${
            readiness.overall_ready
              ? "bg-success/5 border-success/20"
              : "bg-warning/5 border-warning/20"
          }`}>
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold text-neutral-900">
                  {readiness.client_name}
                </h2>
                <p className="text-sm text-neutral-500 mt-0.5">
                  Federation readiness assessment
                </p>
              </div>
              <div className="text-right">
                <ReadinessBadge ready={readiness.overall_ready} />
                <p className="text-xs text-neutral-500 mt-1">
                  Checked: {new Date(readiness.checked_at).toLocaleString()}
                </p>
              </div>
            </div>
          </div>

          {/* Checklist */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
            <h3 className="text-lg font-semibold text-neutral-900 mb-4">Readiness Checklist</h3>
            <div className="space-y-3">
              {readiness.checklist.map((item, i) => (
                <div key={i} className="flex items-center gap-4 py-3 px-4 rounded-lg bg-neutral-50/50">
                  <div className={`w-6 h-6 rounded-full flex items-center justify-center shrink-0 ${
                    item.ready ? "bg-success text-white" : "bg-warning text-white"
                  }`}>
                    {item.ready ? (
                      <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                      </svg>
                    ) : (
                      <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01" />
                      </svg>
                    )}
                  </div>
                  <div className="flex-1">
                    <p className="text-sm font-medium text-neutral-900">
                      {item.item.replace(/_/g, " ")}
                    </p>
                    <p className="text-xs text-neutral-500">{item.detail}</p>
                  </div>
                  <ReadinessBadge ready={item.ready} />
                </div>
              ))}
            </div>
          </div>

          {/* Pod registration info */}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
            <h3 className="text-lg font-semibold text-neutral-900 mb-4">Pod Registration Guide</h3>
            <div className="space-y-4 text-sm text-neutral-700">
              <div>
                <h4 className="font-medium text-neutral-900 mb-1">1. Prerequisites</h4>
                <ul className="list-disc list-inside text-xs text-neutral-600 space-y-1 ml-2">
                  <li>Active client with passing certification</li>
                  <li>At least one active API key with appropriate scopes</li>
                  <li>Sandbox testing completed</li>
                  <li>Deprecation posture configured (WARN or BLOCK recommended)</li>
                </ul>
              </div>
              <div>
                <h4 className="font-medium text-neutral-900 mb-1">2. Registration Flow</h4>
                <div className="bg-neutral-900 rounded-lg p-3 overflow-x-auto">
                  <pre className="text-xs text-green-400 font-mono">
{`POST /internal/v1/federation/pods/register
{
  "pod_id": "your-pod-id",
  "pod_name": "Your Pod Name",
  "authority_scope": "DISTRICT",
  "public_key_pem": "<your-public-key>",
  "callback_url": "https://your-pod.example.com/webhook"
}`}
                  </pre>
                </div>
              </div>
              <div>
                <h4 className="font-medium text-neutral-900 mb-1">3. Token Validation</h4>
                <p className="text-xs text-neutral-600">
                  After registration, validate your JWT tokens against the federation authority.
                  Tokens must include the standard Impilo trust headers and pod authority claims.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default function FederationPage() {
  return (
    <Suspense fallback={<div className="text-sm text-neutral-500 py-20 text-center">Loading federation readiness...</div>}>
      <FederationPageContent />
    </Suspense>
  );
}
