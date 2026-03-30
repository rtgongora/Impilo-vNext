"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ClinicalOrder } from "@/lib/apiClient";

const STATUS_OPTIONS: Array<{ label: string; value: string }> = [
  { label: "All", value: "" },
  { label: "Placed", value: "PLACED" },
  { label: "Acknowledged", value: "ACKNOWLEDGED" },
  { label: "In Progress", value: "IN_PROGRESS" },
  { label: "Fulfilled", value: "FULFILLED" },
  { label: "SLA Breached", value: "SLA_BREACHED" },
];

const PRIORITY_OPTIONS: Array<{ label: string; value: string }> = [
  { label: "All Priorities", value: "" },
  { label: "STAT", value: "STAT" },
  { label: "Urgent", value: "URGENT" },
  { label: "Routine", value: "ROUTINE" },
];

const STATUS_BADGE: Record<string, string> = {
  PLACED: "bg-blue-50 text-blue-700",
  ACKNOWLEDGED: "bg-purple-50 text-purple-700",
  IN_PROGRESS: "bg-amber-50 text-amber-700",
  FULFILLED: "bg-green-50 text-green-700",
  CANCELLED: "bg-gray-100 text-gray-500",
  SLA_BREACHED: "bg-red-100 text-red-700 font-semibold",
};

const PRIORITY_BADGE: Record<string, string> = {
  STAT: "bg-red-100 text-red-700 font-bold",
  URGENT: "bg-orange-50 text-orange-700",
  ROUTINE: "bg-gray-50 text-gray-600",
};

export default function OrdersPage() {
  const [statusFilter, setStatusFilter] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("");

  const { data, isLoading, isError } = useQuery<ClinicalOrder[]>({
    queryKey: ["orders-worklist", statusFilter, priorityFilter],
    queryFn: () =>
      apiClient.listOrders(statusFilter || undefined, priorityFilter || undefined),
    staleTime: 15_000,
  });

  return (
    <div className="min-h-screen bg-impilo-surface">
      <header className="bg-impilo-primary text-white px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <h1 className="text-lg font-semibold">Impilo EHR</h1>
          <nav className="flex gap-4 text-sm">
            <Link href="/dashboard" className="opacity-80 hover:opacity-100">
              Dashboard
            </Link>
            <Link href="/encounters" className="opacity-80 hover:opacity-100">
              Encounters
            </Link>
            <Link href="/orders" className="font-medium underline underline-offset-4">
              Orders
            </Link>
            <Link href="/results" className="opacity-80 hover:opacity-100">
              Results
            </Link>
          </nav>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-6 py-8">
        <div className="mb-6">
          <h2 className="text-xl font-semibold text-gray-900">Orders Worklist</h2>
          <p className="text-sm text-gray-500 mt-1">
            Lab, imaging, procedure, referral, and medication orders — filter by status or priority
          </p>
        </div>

        <div className="mb-4 flex flex-wrap gap-3 items-center">
          <div className="flex gap-2 flex-wrap">
            {STATUS_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => setStatusFilter(opt.value)}
                className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                  statusFilter === opt.value
                    ? "bg-impilo-primary text-white"
                    : "bg-white border border-gray-200 text-gray-700 hover:border-impilo-primary/40"
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
          <div className="h-4 border-l border-gray-300" />
          <div className="flex gap-2">
            {PRIORITY_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => setPriorityFilter(opt.value)}
                className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                  priorityFilter === opt.value
                    ? "bg-impilo-warning text-white"
                    : "bg-white border border-gray-200 text-gray-700 hover:border-impilo-warning/40"
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100">
                <th className="text-left py-3 px-4 font-medium text-gray-500">Order ID</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Patient CPID</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Type</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Description</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Priority</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Status</th>
                <th className="text-left py-3 px-4 font-medium text-gray-500">Ordered</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={7} className="py-10 text-center text-gray-400">
                    Loading orders…
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={7} className="py-10 text-center text-impilo-danger">
                    Failed to load orders. Clinical API may be unavailable.
                  </td>
                </tr>
              ) : data && data.length > 0 ? (
                data.map((order) => (
                  <tr
                    key={order.id}
                    className={`border-b border-gray-50 hover:bg-gray-50 transition-colors ${
                      order.status === "SLA_BREACHED" ? "bg-red-50/50" : ""
                    }`}
                  >
                    <td className="py-3 px-4 font-mono text-xs text-gray-500">
                      {order.id.slice(0, 8)}…
                    </td>
                    <td className="py-3 px-4">
                      <Link
                        href={`/patients/${order.patientCpid}`}
                        className="font-mono text-xs text-impilo-primary hover:underline"
                      >
                        {order.patientCpid}
                      </Link>
                    </td>
                    <td className="py-3 px-4 text-gray-600">{order.orderType}</td>
                    <td className="py-3 px-4 text-gray-700 max-w-xs truncate">
                      {order.description}
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs ${
                          PRIORITY_BADGE[order.priority] ?? "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {order.priority}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                          STATUS_BADGE[order.status] ?? "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {order.status.replace(/_/g, " ")}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-gray-500 text-xs">
                      {new Date(order.orderedAt).toLocaleString("en-ZW", {
                        day: "2-digit",
                        month: "short",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-10 text-center text-gray-400">
                    No orders found
                    {statusFilter ? ` with status "${statusFilter}"` : ""}
                    {priorityFilter ? ` and priority "${priorityFilter}"` : ""}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </main>
    </div>
  );
}
