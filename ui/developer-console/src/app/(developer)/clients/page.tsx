"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { listClients } from "@/lib/developerApi";
import type { DeveloperClient } from "@/types/developer";
import { ClientStatusBadge } from "@/components/StatusBadge";

export default function ClientsPage() {
  const [clients, setClients] = useState<DeveloperClient[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const data = await listClients();
        setClients(data.items);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load clients");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Registered Clients</h1>
          <p className="text-sm text-neutral-500 mt-1">Manage partner applications and API consumers</p>
        </div>
        <Link
          href="/clients/register"
          className="inline-flex items-center gap-2 px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          Register Client
        </Link>
      </div>

      {loading && (
        <div className="flex items-center justify-center py-20">
          <div className="text-sm text-neutral-500">Loading clients...</div>
        </div>
      )}

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger">
          {error}
        </div>
      )}

      {!loading && !error && clients.length === 0 && (
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-12 text-center">
          <p className="text-neutral-500 mb-4">No clients registered yet</p>
          <Link
            href="/clients/register"
            className="text-brand-primary font-medium text-sm hover:underline"
          >
            Register your first client
          </Link>
        </div>
      )}

      {!loading && !error && clients.length > 0 && (
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-100">
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Client Name</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Status</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Contact</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Sandbox</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Deprecation</th>
                  <th className="text-left py-3 px-4 text-neutral-500 font-medium">Created</th>
                </tr>
              </thead>
              <tbody>
                {clients.map((c) => (
                  <tr key={c.client_id} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                    <td className="py-3 px-4">
                      <Link href={`/clients/${c.client_id}`} className="text-brand-primary font-medium hover:underline">
                        {c.client_name}
                      </Link>
                      {c.description && (
                        <p className="text-xs text-neutral-500 mt-0.5 truncate max-w-xs">{c.description}</p>
                      )}
                    </td>
                    <td className="py-3 px-4">
                      <ClientStatusBadge status={c.status} />
                    </td>
                    <td className="py-3 px-4 text-neutral-600">{c.contact_email}</td>
                    <td className="py-3 px-4">
                      <span className={`text-xs font-medium ${c.sandbox_enabled ? "text-info" : "text-neutral-400"}`}>
                        {c.sandbox_enabled ? "Enabled" : "Disabled"}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-neutral-600 text-xs">{c.deprecation_posture}</td>
                    <td className="py-3 px-4 text-neutral-500 text-xs">
                      {new Date(c.created_at).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
