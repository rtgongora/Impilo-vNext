"use client";

import React, { useState } from "react";
import Link from "next/link";
import { apiClient } from "@/lib/apiClient";

interface FederationPod {
  id: string;
  podId: string;
  podName: string;
  podType: "NATIONAL" | "SOVEREIGN_MILITARY" | "SOVEREIGN_PRIVATE" | "EXPORT";
  status: "ACTIVE" | "SUSPENDED" | "REVOKED";
  registeredAt: string;
}

const POD_TYPE_BADGE: Record<string, string> = {
  NATIONAL: "bg-blue-50 text-blue-700",
  SOVEREIGN_MILITARY: "bg-red-50 text-red-700",
  SOVEREIGN_PRIVATE: "bg-purple-50 text-purple-700",
  EXPORT: "bg-teal-50 text-teal-700",
};

const STATUS_BADGE: Record<string, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700",
  SUSPENDED: "bg-amber-50 text-amber-700",
  REVOKED: "bg-red-50 text-red-700",
};

export default function TshepoDashboardPage() {
  const [pods, setPods] = useState<FederationPod[]>([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const loadPods = async () => {
    setLoading(true);
    try {
      const data = await apiClient.get<FederationPod[]>(
        "/internal/v1/federation/pods"
      );
      setPods(Array.isArray(data) ? data : []);
    } catch {
      setPods([]);
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          TSHEPO — Trust & Authorization Engine
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Policy Decision Point: RBAC/ABAC engine, federation control, consent management, audit chain
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <Link href="/tshepo/policy" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Authorization</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Policy Engine</p>
            <p className="text-xs text-neutral-500 mt-1">RBAC/ABAC rules and decision evidence</p>
          </div>
        </Link>

        <Link href="/tshepo/federation" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Governance</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Federation</p>
            <p className="text-xs text-neutral-500 mt-1">Pod registry, authority, obligations</p>
          </div>
        </Link>

        <Link href="/tshepo/consent" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Data Rights</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Consent</p>
            <p className="text-xs text-neutral-500 mt-1">Consent grants and revocations</p>
          </div>
        </Link>

        <Link href="/tshepo/audit" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Compliance</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Audit Chain</p>
            <p className="text-xs text-neutral-500 mt-1">Serialized audit events per decision</p>
          </div>
        </Link>
      </div>

      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-neutral-900">Federation Pod Registry</h2>
          <button
            onClick={loadPods}
            disabled={loading}
            className="px-4 py-1.5 text-sm font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90 disabled:opacity-50"
          >
            {loading ? "Loading…" : loaded ? "Refresh" : "Load Pods"}
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-100">
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Pod ID</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Name</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Type</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Status</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Registered</th>
              </tr>
            </thead>
            <tbody>
              {!loaded ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-neutral-400 text-sm">
                    Click &ldquo;Load Pods&rdquo; to fetch the federation pod registry
                  </td>
                </tr>
              ) : pods.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-neutral-400 text-sm">
                    No federation pods registered
                  </td>
                </tr>
              ) : (
                pods.map((pod) => (
                  <tr key={pod.id} className="border-b border-neutral-50 hover:bg-neutral-25">
                    <td className="py-3 px-2 font-mono text-xs text-neutral-600">{pod.podId}</td>
                    <td className="py-3 px-2 font-medium text-neutral-900">{pod.podName}</td>
                    <td className="py-3 px-2">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${POD_TYPE_BADGE[pod.podType] ?? "bg-neutral-100 text-neutral-600"}`}>
                        {pod.podType.replace(/_/g, " ")}
                      </span>
                    </td>
                    <td className="py-3 px-2">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_BADGE[pod.status] ?? "bg-neutral-100 text-neutral-600"}`}>
                        {pod.status}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-neutral-500 text-xs">
                      {new Date(pod.registeredAt).toLocaleDateString()}
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
