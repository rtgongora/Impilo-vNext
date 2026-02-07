"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";

interface FacilityStat {
  total: number;
  active: number;
  closed: number;
}

export default function TusoDashboardPage() {
  const [query, setQuery] = useState("");
  const [facilities, setFacilities] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  const handleSearch = async () => {
    if (!query.trim()) return;
    setLoading(true);
    try {
      const res = await fetch("/api/v1/internal/facilities/search", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query, page: 0, size: 20 }),
      });
      if (res.ok) {
        const data = await res.json();
        setFacilities(data.content || data || []);
      }
    } catch {
      // handle gracefully
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          TUSO — Facility Registry
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          National Facility Registry: facilities, workspaces, resources, calendars, configuration, control tower
        </p>
      </div>

      {/* Quick nav cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <Link href="/tuso/workspaces" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Operations</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Workspaces</p>
            <p className="text-xs text-neutral-500 mt-1">Manage workspaces, shifts, eligibility rules</p>
          </div>
        </Link>

        <Link href="/tuso/resources" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Capacity</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Resources</p>
            <p className="text-xs text-neutral-500 mt-1">Beds, rooms, equipment, calendars, bookings</p>
          </div>
        </Link>

        <Link href="/tuso/config" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Configuration</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Facility Config</p>
            <p className="text-xs text-neutral-500 mt-1">Feature flags, templates, workflow toggles</p>
          </div>
        </Link>

        <Link href="/tuso/control-tower" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Oversight</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Control Tower</p>
            <p className="text-xs text-neutral-500 mt-1">Telemetry, alerts, occupancy, throughput</p>
          </div>
        </Link>
      </div>

      {/* Facility Search */}
      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <h2 className="text-lg font-semibold text-neutral-900 mb-4">Facility Search</h2>
        <div className="flex gap-3 mb-4">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            placeholder="Search by name, facility code, district..."
            className="flex-1 px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
          />
          <button
            onClick={handleSearch}
            disabled={loading}
            className="px-6 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
          >
            {loading ? "Searching..." : "Search"}
          </button>
        </div>

        {facilities.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-100">
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Code</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Name</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Type</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">District</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Province</th>
                  <th className="text-left py-3 px-2 text-neutral-500 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {facilities.map((f: any) => (
                  <tr key={f.id} className="border-b border-neutral-50 hover:bg-neutral-25">
                    <td className="py-3 px-2 font-mono text-xs">{f.facilityCode}</td>
                    <td className="py-3 px-2 font-medium">{f.name}</td>
                    <td className="py-3 px-2">{f.facilityType || "-"}</td>
                    <td className="py-3 px-2">{f.district || "-"}</td>
                    <td className="py-3 px-2">{f.province || "-"}</td>
                    <td className="py-3 px-2">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                        f.status === "ACTIVE" ? "bg-emerald-50 text-emerald-700" :
                        f.status === "CLOSED" ? "bg-red-50 text-red-700" :
                        "bg-neutral-50 text-neutral-600"
                      }`}>
                        {f.status}
                      </span>
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
