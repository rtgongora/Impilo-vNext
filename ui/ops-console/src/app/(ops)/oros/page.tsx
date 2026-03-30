"use client";

import React, { useState } from "react";
import Link from "next/link";
import { apiClient } from "@/lib/apiClient";

interface ClinicalOrder {
  id: string;
  patientCpid: string;
  orderType: "LAB" | "IMAGING" | "PROCEDURE" | "REFERRAL" | "MEDICATION";
  status:
    | "PLACED"
    | "ACKNOWLEDGED"
    | "IN_PROGRESS"
    | "FULFILLED"
    | "CANCELLED"
    | "SLA_BREACHED";
  priority: "ROUTINE" | "URGENT" | "STAT";
  description: string;
  orderedBy: string;
  orderedAt: string;
  dueBy?: string;
}

const STATUS_BADGE: Record<string, string> = {
  PLACED: "bg-blue-50 text-blue-700",
  ACKNOWLEDGED: "bg-purple-50 text-purple-700",
  IN_PROGRESS: "bg-amber-50 text-amber-700",
  FULFILLED: "bg-emerald-50 text-emerald-700",
  CANCELLED: "bg-neutral-100 text-neutral-500",
  SLA_BREACHED: "bg-red-100 text-red-700",
};

const PRIORITY_BADGE: Record<string, string> = {
  STAT: "bg-red-100 text-red-700",
  URGENT: "bg-orange-50 text-orange-700",
  ROUTINE: "bg-neutral-50 text-neutral-500",
};

const STATUS_FILTERS = [
  { label: "All", value: "" },
  { label: "Placed", value: "PLACED" },
  { label: "In Progress", value: "IN_PROGRESS" },
  { label: "Fulfilled", value: "FULFILLED" },
  { label: "SLA Breached", value: "SLA_BREACHED" },
];

export default function OrosDashboardPage() {
  const [statusFilter, setStatusFilter] = useState("");
  const [orders, setOrders] = useState<ClinicalOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const loadOrders = async (status?: string) => {
    setLoading(true);
    try {
      const qs = status ? `?status=${status}&size=50` : "?size=50";
      const data = await apiClient.get<ClinicalOrder[]>(`/internal/v1/orders${qs}`);
      setOrders(Array.isArray(data) ? data : []);
    } catch {
      setOrders([]);
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  };

  const handleFilterChange = (value: string) => {
    setStatusFilter(value);
    loadOrders(value || undefined);
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          OROS — Orders & Results Service
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Clinical order lifecycle: lab, imaging, procedures, referrals — with SLA tracking and sign-off
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <Link href="/oros/worklist" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Active Work</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Worklist</p>
            <p className="text-xs text-neutral-500 mt-1">Orders in progress across the facility</p>
          </div>
        </Link>

        <Link href="/oros/results" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Reporting</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Results</p>
            <p className="text-xs text-neutral-500 mt-1">Incoming results awaiting sign-off</p>
          </div>
        </Link>

        <Link href="/oros/sla" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Performance</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">SLA Monitor</p>
            <p className="text-xs text-neutral-500 mt-1">TAT breach alerts and compliance metrics</p>
          </div>
        </Link>
      </div>

      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-neutral-900">Order Queue</h2>
          <button
            onClick={() => loadOrders(statusFilter || undefined)}
            disabled={loading}
            className="px-4 py-1.5 text-sm font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90 disabled:opacity-50"
          >
            {loading ? "Loading…" : loaded ? "Refresh" : "Load Orders"}
          </button>
        </div>

        <div className="flex gap-2 mb-4 flex-wrap">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.value}
              onClick={() => handleFilterChange(f.value)}
              className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
                statusFilter === f.value
                  ? "bg-brand-primary text-white"
                  : "bg-neutral-100 text-neutral-600 hover:bg-neutral-200"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-100">
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Order ID</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Patient CPID</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Type</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Description</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Priority</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Status</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Ordered</th>
              </tr>
            </thead>
            <tbody>
              {!loaded ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-neutral-400 text-sm">
                    Click &ldquo;Load Orders&rdquo; to fetch the order queue
                  </td>
                </tr>
              ) : orders.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-neutral-400 text-sm">
                    No orders found{statusFilter ? ` with status "${statusFilter}"` : ""}
                  </td>
                </tr>
              ) : (
                orders.map((order) => (
                  <tr
                    key={order.id}
                    className={`border-b border-neutral-50 hover:bg-neutral-25 ${
                      order.status === "SLA_BREACHED" ? "bg-red-50/40" : ""
                    }`}
                  >
                    <td className="py-3 px-2 font-mono text-xs text-neutral-500">
                      {order.id.slice(0, 8)}…
                    </td>
                    <td className="py-3 px-2 font-mono text-xs text-neutral-700">
                      {order.patientCpid}
                    </td>
                    <td className="py-3 px-2 text-neutral-600">{order.orderType}</td>
                    <td className="py-3 px-2 text-neutral-700 max-w-[180px] truncate">
                      {order.description}
                    </td>
                    <td className="py-3 px-2">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                          PRIORITY_BADGE[order.priority] ?? "bg-neutral-100 text-neutral-600"
                        }`}
                      >
                        {order.priority}
                      </span>
                    </td>
                    <td className="py-3 px-2">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                          STATUS_BADGE[order.status] ?? "bg-neutral-100 text-neutral-600"
                        }`}
                      >
                        {order.status.replace(/_/g, " ")}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-neutral-500 text-xs">
                      {new Date(order.orderedAt).toLocaleString("en-ZW", {
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
      </div>
    </div>
  );
}
