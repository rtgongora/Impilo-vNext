"use client";

import React, { useEffect, useState, useCallback } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  getClient,
  listKeys,
  issueKey,
  rotateKey,
  revokeKey,
  configureSandbox,
  updateDeprecationPosture,
} from "@/lib/developerApi";
import type { DeveloperClient, ApiKey, ApiKeyIssuance, DeprecationPosture } from "@/types/developer";
import { ClientStatusBadge, KeyStatusBadge } from "@/components/StatusBadge";

export default function ClientDetailPage() {
  const params = useParams();
  const clientId = params.clientId as string;

  const [client, setClient] = useState<DeveloperClient | null>(null);
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Key issuance state
  const [showIssueForm, setShowIssueForm] = useState(false);
  const [keyLabel, setKeyLabel] = useState("");
  const [keyExpiry, setKeyExpiry] = useState(90);
  const [issuedKey, setIssuedKey] = useState<ApiKeyIssuance | null>(null);
  const [issuing, setIssuing] = useState(false);

  // Rotation state
  const [rotatingKeyId, setRotatingKeyId] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      const [clientData, keysData] = await Promise.all([
        getClient(clientId),
        listKeys(clientId),
      ]);
      setClient(clientData);
      setKeys(keysData.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load client");
    } finally {
      setLoading(false);
    }
  }, [clientId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  async function handleIssueKey(e: React.FormEvent) {
    e.preventDefault();
    setIssuing(true);
    try {
      const result = await issueKey(clientId, {
        label: keyLabel,
        expiresInDays: keyExpiry,
      });
      setIssuedKey(result);
      setShowIssueForm(false);
      setKeyLabel("");
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Key issuance failed");
    } finally {
      setIssuing(false);
    }
  }

  async function handleRotateKey(keyId: string) {
    setRotatingKeyId(keyId);
    try {
      const result = await rotateKey(keyId, { expiresInDays: 90 });
      setIssuedKey({
        key_id: result.key_id,
        api_key: result.api_key,
        key_prefix: result.key_prefix,
        label: result.label,
        status: result.status,
        created_at: "",
      });
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Key rotation failed");
    } finally {
      setRotatingKeyId(null);
    }
  }

  async function handleRevokeKey(keyId: string) {
    try {
      await revokeKey(keyId);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Key revocation failed");
    }
  }

  async function handlePostureChange(posture: DeprecationPosture) {
    try {
      await updateDeprecationPosture(clientId, posture);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Posture update failed");
    }
  }

  async function handleSandboxToggle() {
    try {
      await configureSandbox(clientId, { rate_limit_rpm: 60, allowed_endpoints: ["*"] });
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sandbox config failed");
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-sm text-neutral-500">Loading client details...</div>
      </div>
    );
  }

  if (error && !client) {
    return (
      <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger">
        {error}
      </div>
    );
  }

  return (
    <div>
      {/* Header */}
      <div className="flex items-center gap-3 mb-2">
        <Link href="/clients" className="text-neutral-500 hover:text-neutral-700 text-sm">&larr; Clients</Link>
      </div>

      <div className="flex items-center justify-between mb-8">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-neutral-900">{client?.client_name}</h1>
            {client && <ClientStatusBadge status={client.status} />}
          </div>
          <p className="text-sm text-neutral-500 mt-1">{client?.description}</p>
        </div>
        <div className="flex gap-2">
          <Link
            href={`/certification?clientId=${clientId}`}
            className="px-4 py-2 border border-neutral-200 rounded-lg text-sm font-medium text-neutral-600 hover:bg-neutral-50 transition-colors"
          >
            Run Certification
          </Link>
          <Link
            href={`/federation?clientId=${clientId}`}
            className="px-4 py-2 border border-neutral-200 rounded-lg text-sm font-medium text-neutral-600 hover:bg-neutral-50 transition-colors"
          >
            Federation Readiness
          </Link>
        </div>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-3 text-sm text-danger mb-6">
          {error}
          <button onClick={() => setError(null)} className="ml-2 underline">dismiss</button>
        </div>
      )}

      {/* Client info grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-5">
          <p className="text-xs font-medium text-neutral-500 uppercase tracking-wide mb-1">Contact</p>
          <p className="text-sm text-neutral-900">{client?.contact_email}</p>
        </div>
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-5">
          <p className="text-xs font-medium text-neutral-500 uppercase tracking-wide mb-1">Sandbox</p>
          <div className="flex items-center justify-between">
            <span className={`text-sm font-medium ${client?.sandbox_enabled ? "text-info" : "text-neutral-400"}`}>
              {client?.sandbox_enabled ? "Enabled" : "Disabled"}
            </span>
            {!client?.sandbox_enabled && (
              <button
                onClick={handleSandboxToggle}
                className="text-xs text-brand-primary hover:underline"
              >
                Enable
              </button>
            )}
          </div>
        </div>
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-5">
          <p className="text-xs font-medium text-neutral-500 uppercase tracking-wide mb-1">Deprecation Posture</p>
          <div className="flex gap-1 mt-1">
            {(["NONE", "WARN", "BLOCK"] as DeprecationPosture[]).map((p) => (
              <button
                key={p}
                onClick={() => handlePostureChange(p)}
                className={`px-2.5 py-1 rounded text-xs font-medium transition-colors ${
                  client?.deprecation_posture === p
                    ? p === "BLOCK" ? "bg-danger/10 text-danger" : p === "WARN" ? "bg-warning/10 text-warning" : "bg-neutral-100 text-neutral-600"
                    : "text-neutral-400 hover:bg-neutral-50"
                }`}
              >
                {p}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Issued key alert */}
      {issuedKey && (
        <div className="bg-success/5 border border-success/20 rounded-lg p-4 mb-6">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm font-medium text-success">API Key Issued Successfully</p>
              <p className="text-xs text-neutral-600 mt-1">Copy this key now. It will not be shown again.</p>
              <code className="block mt-2 px-3 py-2 bg-neutral-900 text-success rounded text-sm font-mono select-all">
                {issuedKey.api_key}
              </code>
              <p className="text-xs text-neutral-500 mt-1">Prefix: {issuedKey.key_prefix}</p>
            </div>
            <button onClick={() => setIssuedKey(null)} className="text-neutral-400 hover:text-neutral-600">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      )}

      {/* API Keys section */}
      <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-neutral-900">API Keys</h2>
          <button
            onClick={() => setShowIssueForm(!showIssueForm)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-brand-primary text-white rounded-lg text-xs font-medium hover:bg-brand-primary/90 transition-colors"
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
            </svg>
            Issue Key
          </button>
        </div>

        {/* Issue key form */}
        {showIssueForm && (
          <form onSubmit={handleIssueKey} className="border border-neutral-100 rounded-lg p-4 mb-4 bg-neutral-50/50">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-neutral-600 mb-1">Label</label>
                <input
                  type="text"
                  required
                  value={keyLabel}
                  onChange={(e) => setKeyLabel(e.target.value)}
                  className="w-full px-3 py-1.5 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary"
                  placeholder="e.g. Production Key"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-neutral-600 mb-1">Expires In (days)</label>
                <input
                  type="number"
                  min={1}
                  max={365}
                  value={keyExpiry}
                  onChange={(e) => setKeyExpiry(parseInt(e.target.value, 10))}
                  className="w-full px-3 py-1.5 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary"
                />
              </div>
            </div>
            <div className="flex gap-2 mt-3">
              <button
                type="submit"
                disabled={issuing}
                className="px-4 py-1.5 bg-brand-primary text-white rounded-lg text-xs font-medium hover:bg-brand-primary/90 transition-colors disabled:opacity-50"
              >
                {issuing ? "Issuing..." : "Issue Key"}
              </button>
              <button
                type="button"
                onClick={() => setShowIssueForm(false)}
                className="px-4 py-1.5 border border-neutral-200 rounded-lg text-xs font-medium text-neutral-600 hover:bg-neutral-50 transition-colors"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {keys.length === 0 ? (
          <p className="text-sm text-neutral-500 py-4 text-center">No API keys issued yet</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-100">
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Prefix</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Label</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Status</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium text-xs">Created</th>
                  <th className="text-right py-2 px-2 text-neutral-500 font-medium text-xs">Actions</th>
                </tr>
              </thead>
              <tbody>
                {keys.map((k) => (
                  <tr key={k.key_id} className="border-b border-neutral-50">
                    <td className="py-2.5 px-2 font-mono text-xs text-neutral-700">{k.key_prefix}...</td>
                    <td className="py-2.5 px-2 text-neutral-700">{k.label}</td>
                    <td className="py-2.5 px-2">
                      <KeyStatusBadge status={k.status} />
                    </td>
                    <td className="py-2.5 px-2 text-neutral-500 text-xs">
                      {new Date(k.created_at).toLocaleDateString()}
                    </td>
                    <td className="py-2.5 px-2 text-right">
                      {k.status === "ACTIVE" && (
                        <div className="flex gap-1 justify-end">
                          <button
                            onClick={() => handleRotateKey(k.key_id)}
                            disabled={rotatingKeyId === k.key_id}
                            className="px-2 py-1 text-xs text-info hover:bg-info/10 rounded transition-colors disabled:opacity-50"
                          >
                            {rotatingKeyId === k.key_id ? "Rotating..." : "Rotate"}
                          </button>
                          <button
                            onClick={() => handleRevokeKey(k.key_id)}
                            className="px-2 py-1 text-xs text-danger hover:bg-danger/10 rounded transition-colors"
                          >
                            Revoke
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
