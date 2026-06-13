"use client";

import React, { useCallback, useEffect, useState } from "react";
import { vitoApi, type Client } from "@/lib/vitoApi";

/**
 * Offline Provisional Reconciliation — lists clients registered in
 * offline/provisional mode that require identity verification before
 * being promoted to VERIFIED status in the registry.
 */
export default function ProvisionalReconciliationPage() {
  const [clients, setClients] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);

  const loadProvisional = useCallback(async () => {
    setLoading(true);
    try {
      const data = await vitoApi.listClients(0, 50, "PROVISIONAL");
      setClients(data.items ?? []);
    } catch {
      setClients([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProvisional();
  }, [loadProvisional]);

  const handleVerify = async (healthId: string) => {
    try {
      await vitoApi.verifyClient(healthId);
      await loadProvisional();
    } catch {
      // Error handling via toast in production
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">
          Provisional Reconciliation
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Review and verify clients registered in offline or provisional mode
        </p>
      </div>

      <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-100 bg-neutral-50">
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Health ID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Name</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Date of Birth</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Sex</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Status</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Created At</th>
              <th className="px-4 py-3 text-right font-medium text-neutral-500">Action</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-neutral-500">Loading...</td>
              </tr>
            ) : clients.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-neutral-500">
                  No provisional clients pending reconciliation
                </td>
              </tr>
            ) : (
              clients.map((client) => (
                <tr key={client.healthId} className="border-b border-neutral-50 hover:bg-neutral-50">
                  <td className="px-4 py-3 font-mono text-xs">{client.healthId.substring(0, 8)}...</td>
                  <td className="px-4 py-3 text-neutral-900">
                    {client.givenName} {client.familyName}
                  </td>
                  <td className="px-4 py-3 text-neutral-500">
                    {new Date(client.dateOfBirth).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-neutral-500">{client.sex}</td>
                  <td className="px-4 py-3">
                    <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-warning/10 text-warning">
                      {client.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-neutral-500">
                    {new Date(client.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => handleVerify(client.healthId)}
                      className="px-3 py-1 text-xs font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90"
                    >
                      Verify
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
