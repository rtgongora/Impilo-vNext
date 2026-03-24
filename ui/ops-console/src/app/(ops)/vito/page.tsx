"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { vitoApi, type Client } from "@/lib/vitoApi";

export default function VitoDashboardPage() {
  const [recentClients, setRecentClients] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalClients, setTotalClients] = useState(0);

  useEffect(() => {
    async function loadRecent() {
      try {
        const data = await vitoApi.listClients(0, 10);
        setRecentClients(data.content);
        setTotalClients(data.totalElements);
      } catch {
        // Dashboard is non-critical; silently fail
      } finally {
        setLoading(false);
      }
    }
    loadRecent();
  }, []);

  const STATUS_STYLES: Record<string, string> = {
    ACTIVE: "bg-emerald-100 text-emerald-700",
    VERIFIED: "bg-blue-100 text-blue-700",
    PROVISIONAL: "bg-amber-100 text-amber-700",
    INACTIVE: "bg-neutral-100 text-neutral-600",
    DECEASED: "bg-neutral-200 text-neutral-500",
    MERGED: "bg-purple-100 text-purple-700",
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          VITO — Client Identity Registry
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Sovereign Identity Node: WHO-compliant registry with SMART Card management
        </p>
      </div>

      {/* Quick nav cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <Link href="/vito/match-queue" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Identity Resolution</h3>
            <p className="text-2xl font-semibold text-neutral-900 mt-2">Match Queue</p>
            <p className="text-xs text-neutral-500 mt-1">
              Review and resolve pending identity matches
            </p>
          </div>
        </Link>

        <Link href="/vito/cards" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Card Management</h3>
            <p className="text-2xl font-semibold text-neutral-900 mt-2">SMART Cards</p>
            <p className="text-xs text-neutral-500 mt-1">
              Print, activate, and manage card lifecycle
            </p>
          </div>
        </Link>

        <Link href="/vito/config" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Configuration</h3>
            <p className="text-2xl font-semibold text-neutral-900 mt-2">Registry Mode</p>
            <p className="text-xs text-neutral-500 mt-1">
              Toggle OpenCR vs Standalone mode
            </p>
          </div>
        </Link>
      </div>

      {/* Recent registrations — real data from VITO API */}
      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-base font-semibold text-neutral-900">
            Recent Registrations
          </h2>
          {totalClients > 0 && (
            <span className="text-xs text-neutral-400">{totalClients} total clients</span>
          )}
        </div>

        {loading ? (
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="animate-pulse flex gap-4">
                <div className="h-4 bg-neutral-200 rounded w-24" />
                <div className="h-4 bg-neutral-100 rounded w-32" />
                <div className="h-4 bg-neutral-100 rounded w-20" />
              </div>
            ))}
          </div>
        ) : recentClients.length === 0 ? (
          <p className="text-sm text-neutral-500">No clients registered yet.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-100">
                <th className="text-left py-2 text-xs font-medium text-neutral-500">Health ID</th>
                <th className="text-left py-2 text-xs font-medium text-neutral-500">Name</th>
                <th className="text-left py-2 text-xs font-medium text-neutral-500">Sex</th>
                <th className="text-left py-2 text-xs font-medium text-neutral-500">DOB</th>
                <th className="text-left py-2 text-xs font-medium text-neutral-500">Status</th>
                <th className="text-left py-2 text-xs font-medium text-neutral-500">Registered</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-50">
              {recentClients.map((client) => (
                <tr key={client.id} className="hover:bg-neutral-50">
                  <td className="py-2 font-mono text-xs">{client.healthId}</td>
                  <td className="py-2 text-neutral-700">
                    {client.givenName} {client.familyName}
                  </td>
                  <td className="py-2 text-neutral-500">{client.sex}</td>
                  <td className="py-2 text-neutral-500 text-xs">{client.dateOfBirth}</td>
                  <td className="py-2">
                    <span className={`inline-block px-2 py-0.5 rounded-full text-[11px] font-medium ${STATUS_STYLES[client.status] ?? "bg-neutral-100"}`}>
                      {client.status}
                    </span>
                  </td>
                  <td className="py-2 text-neutral-400 text-xs">
                    {new Date(client.createdAt).toLocaleDateString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {recentClients.length > 0 && (
          <div className="mt-4 pt-3 border-t border-neutral-100 text-center">
            <Link
              href="/vito/clients"
              className="text-xs text-brand-primary hover:underline font-medium"
            >
              View all clients →
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
