"use client";

import React, { useState } from "react";
import Link from "next/link";
import { apiClient } from "@/lib/apiClient";

interface Provider {
  id: string;
  providerCode: string;
  givenName: string;
  familyName: string;
  specialization: string;
  licenseNumber: string;
  licenseStatus: "ACTIVE" | "SUSPENDED" | "REVOKED" | "EXPIRED";
  facilityId?: string;
  registeredAt: string;
}

const LICENSE_STATUS_BADGE: Record<string, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700",
  SUSPENDED: "bg-amber-50 text-amber-700",
  REVOKED: "bg-red-50 text-red-700",
  EXPIRED: "bg-neutral-100 text-neutral-500",
};

export default function VarapiDashboardPage() {
  const [query, setQuery] = useState("");
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const handleSearch = async () => {
    if (!query.trim()) return;
    setLoading(true);
    setSearched(true);
    try {
      const data = await apiClient.get<Provider[]>(
        `/internal/v1/providers?q=${encodeURIComponent(query.trim())}&size=20`
      );
      setProviders(Array.isArray(data) ? data : []);
    } catch {
      setProviders([]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          VARAPI — Provider Registry
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Healthcare provider registry: credentials, licenses, privileges, and DEA scheduling
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <Link href="/varapi/providers" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Registry</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Providers</p>
            <p className="text-xs text-neutral-500 mt-1">Search and manage registered providers</p>
          </div>
        </Link>

        <Link href="/varapi/licenses" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Credentialing</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Licenses</p>
            <p className="text-xs text-neutral-500 mt-1">License lifecycle and revocations</p>
          </div>
        </Link>

        <Link href="/varapi/privileges" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Access Control</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Privileges</p>
            <p className="text-xs text-neutral-500 mt-1">Grant and revoke clinical privileges</p>
          </div>
        </Link>
      </div>

      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <h2 className="text-lg font-semibold text-neutral-900 mb-4">Provider Search</h2>
        <div className="flex gap-3 mb-4">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            placeholder="Search by name, provider code, or license number..."
            className="flex-1 px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
          />
          <button
            onClick={handleSearch}
            disabled={loading || !query.trim()}
            className="px-6 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
          >
            {loading ? "Searching…" : "Search"}
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-100">
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Code</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Name</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Specialization</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">License No.</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">License Status</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Registered</th>
              </tr>
            </thead>
            <tbody>
              {!searched ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-neutral-400 text-sm">
                    Enter a search query to find providers
                  </td>
                </tr>
              ) : loading ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-neutral-400 text-sm">
                    Searching…
                  </td>
                </tr>
              ) : providers.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-neutral-400 text-sm">
                    No providers found matching your query
                  </td>
                </tr>
              ) : (
                providers.map((p) => (
                  <tr key={p.id} className="border-b border-neutral-50 hover:bg-neutral-25">
                    <td className="py-3 px-2 font-mono text-xs text-neutral-600">
                      {p.providerCode}
                    </td>
                    <td className="py-3 px-2 font-medium text-neutral-900">
                      {p.familyName}, {p.givenName}
                    </td>
                    <td className="py-3 px-2 text-neutral-600">{p.specialization || "—"}</td>
                    <td className="py-3 px-2 font-mono text-xs text-neutral-600">
                      {p.licenseNumber}
                    </td>
                    <td className="py-3 px-2">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                          LICENSE_STATUS_BADGE[p.licenseStatus] ?? "bg-neutral-100 text-neutral-600"
                        }`}
                      >
                        {p.licenseStatus}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-neutral-500 text-xs">
                      {new Date(p.registeredAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
