"use client";

import React, { useEffect, useState } from "react";
import {
  listClients,
  listKeys,
  configureSandbox,
  listCertifications,
} from "@/lib/developerApi";
import type { DeveloperClient, ApiKey, Certification } from "@/types/developer";
import { KeyStatusBadge, CertResultBadge } from "@/components/StatusBadge";

interface SandboxPayload {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: string;
}

const SAMPLE_PAYLOADS: SandboxPayload[] = [
  {
    method: "POST",
    path: "/internal/v1/developer/clients",
    headers: { "Content-Type": "application/json", "Idempotency-Key": "<generated>" },
    body: JSON.stringify({ clientName: "Test Partner", description: "Sandbox test", contactEmail: "test@example.org", sandboxEnabled: true }, null, 2),
  },
  {
    method: "POST",
    path: "/internal/v1/developer/clients/{client_id}/keys",
    headers: { "Content-Type": "application/json", "Idempotency-Key": "<generated>" },
    body: JSON.stringify({ label: "Test Key", scopes: ["read"], expiresInDays: 30 }, null, 2),
  },
  {
    method: "POST",
    path: "/internal/v1/schemas",
    headers: { "Content-Type": "application/json", "Idempotency-Key": "<generated>" },
    body: JSON.stringify({ subject: "impilo.test.entity.created.v1", schema: '{"type":"object","properties":{"id":{"type":"string"}}}', description: "Test schema" }, null, 2),
  },
];

export default function SandboxPage() {
  const [clients, setClients] = useState<DeveloperClient[]>([]);
  const [selectedClientId, setSelectedClientId] = useState<string>("");
  const [clientKeys, setClientKeys] = useState<ApiKey[]>([]);
  const [certHistory, setCertHistory] = useState<Certification[]>([]);
  const [selectedPayload, setSelectedPayload] = useState<SandboxPayload>(SAMPLE_PAYLOADS[0]);
  const [loading, setLoading] = useState(true);
  const [configuring, setConfiguring] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const data = await listClients();
        setClients(data.items);
        const sandboxClients = data.items.filter((c) => c.sandbox_enabled);
        if (sandboxClients.length > 0) {
          setSelectedClientId(sandboxClients[0].client_id);
        } else if (data.items.length > 0) {
          setSelectedClientId(data.items[0].client_id);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  useEffect(() => {
    if (!selectedClientId) return;
    async function loadClientData() {
      try {
        const [keysData, certsData] = await Promise.all([
          listKeys(selectedClientId),
          listCertifications(selectedClientId),
        ]);
        setClientKeys(keysData.items);
        setCertHistory(certsData.items);
      } catch {
        /* may not have data yet */
      }
    }
    loadClientData();
  }, [selectedClientId]);

  async function handleEnableSandbox() {
    setConfiguring(true);
    try {
      await configureSandbox(selectedClientId, {
        rate_limit_rpm: 60,
        allowed_endpoints: ["*"],
        mock_responses: {},
      });
      const data = await listClients();
      setClients(data.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Config failed");
    } finally {
      setConfiguring(false);
    }
  }

  const selectedClient = clients.find((c) => c.client_id === selectedClientId);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-sm text-neutral-500">Loading sandbox...</div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">Sandbox & Testing</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Test API integrations with sample payloads in a sandbox environment
        </p>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-6">
          {error}
        </div>
      )}

      {/* Client selector + sandbox status */}
      <div className="flex items-end gap-4 mb-8">
        <div className="flex-1 max-w-xs">
          <label className="block text-sm font-medium text-neutral-700 mb-1.5">Sandbox Client</label>
          <select
            value={selectedClientId}
            onChange={(e) => setSelectedClientId(e.target.value)}
            className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary"
          >
            {clients.map((c) => (
              <option key={c.client_id} value={c.client_id}>
                {c.client_name} {c.sandbox_enabled ? "(sandbox)" : ""}
              </option>
            ))}
          </select>
        </div>
        {selectedClient && !selectedClient.sandbox_enabled && (
          <button
            onClick={handleEnableSandbox}
            disabled={configuring}
            className="px-4 py-2 bg-info text-white rounded-lg text-sm font-medium hover:bg-info/90 transition-colors disabled:opacity-50"
          >
            {configuring ? "Enabling..." : "Enable Sandbox"}
          </button>
        )}
        {selectedClient?.sandbox_enabled && (
          <span className="inline-flex items-center gap-1.5 px-3 py-2 bg-info/10 text-info rounded-lg text-sm font-medium">
            <div className="w-2 h-2 rounded-full bg-info animate-pulse" />
            Sandbox Active
          </span>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Sample payloads */}
        <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
          <h2 className="text-lg font-semibold text-neutral-900 mb-4">Sample Payloads</h2>
          <div className="space-y-2 mb-4">
            {SAMPLE_PAYLOADS.map((p, i) => (
              <button
                key={i}
                onClick={() => setSelectedPayload(p)}
                className={`w-full text-left px-3 py-2.5 rounded-lg text-sm transition-colors ${
                  selectedPayload === p
                    ? "bg-brand-primary/10 text-brand-primary"
                    : "text-neutral-700 hover:bg-neutral-50"
                }`}
              >
                <span className="font-mono text-xs font-semibold">{p.method}</span>{" "}
                <code className="text-xs">{p.path}</code>
              </button>
            ))}
          </div>
          <div className="bg-neutral-900 rounded-lg p-4 overflow-x-auto">
            <pre className="text-xs text-green-400 font-mono whitespace-pre-wrap">
              {`${selectedPayload.method} ${selectedPayload.path}\n`}
              {Object.entries(selectedPayload.headers).map(([k, v]) => `${k}: ${v}\n`).join("")}
              {`\n${selectedPayload.body}`}
            </pre>
          </div>
        </div>

        {/* Client sandbox info */}
        <div className="space-y-6">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
            <h2 className="text-lg font-semibold text-neutral-900 mb-4">Client Keys</h2>
            {clientKeys.length === 0 ? (
              <p className="text-sm text-neutral-500 text-center py-4">No keys issued</p>
            ) : (
              <div className="space-y-2">
                {clientKeys.slice(0, 5).map((k) => (
                  <div key={k.key_id} className="flex items-center justify-between py-2 px-3 rounded-lg bg-neutral-50">
                    <div>
                      <code className="text-xs font-mono text-neutral-700">{k.key_prefix}...</code>
                      <span className="text-xs text-neutral-500 ml-2">{k.label}</span>
                    </div>
                    <KeyStatusBadge status={k.status} />
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
            <h2 className="text-lg font-semibold text-neutral-900 mb-4">Certification History</h2>
            {certHistory.length === 0 ? (
              <p className="text-sm text-neutral-500 text-center py-4">No certification runs</p>
            ) : (
              <div className="space-y-2">
                {certHistory.slice(0, 5).map((c) => (
                  <div key={c.certification_id} className="flex items-center justify-between py-2 px-3 rounded-lg bg-neutral-50">
                    <div className="flex items-center gap-2">
                      <CertResultBadge result={c.result} />
                      <span className="text-xs text-neutral-500">
                        {c.checks_passed}/{c.checks_total} passed
                      </span>
                    </div>
                    <span className="text-xs text-neutral-500">
                      {c.completed_at ? new Date(c.completed_at).toLocaleDateString() : "-"}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
