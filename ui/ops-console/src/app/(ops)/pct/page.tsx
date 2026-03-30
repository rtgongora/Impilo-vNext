"use client";

import React, { useState } from "react";
import Link from "next/link";
import { apiClient } from "@/lib/apiClient";

interface Journey {
  id: string;
  patientCpid: string;
  state:
    | "REGISTERED"
    | "TRIAGED"
    | "WAITING"
    | "IN_CONSULTATION"
    | "REFERRED"
    | "LAB_ORDERED"
    | "PHARMACY"
    | "IN_PROCEDURE"
    | "OBSERVATION"
    | "DECISION"
    | "DISCHARGE_PENDING"
    | "DISCHARGED"
    | "CANCELLED";
  facilityId: string;
  chiefComplaint?: string;
  startedAt: string;
  updatedAt: string;
}

const STATE_BADGE: Record<string, string> = {
  REGISTERED: "bg-blue-50 text-blue-700",
  TRIAGED: "bg-purple-50 text-purple-700",
  WAITING: "bg-amber-50 text-amber-700",
  IN_CONSULTATION: "bg-green-50 text-green-700",
  REFERRED: "bg-cyan-50 text-cyan-700",
  LAB_ORDERED: "bg-indigo-50 text-indigo-700",
  PHARMACY: "bg-teal-50 text-teal-700",
  IN_PROCEDURE: "bg-orange-50 text-orange-700",
  OBSERVATION: "bg-yellow-50 text-yellow-700",
  DECISION: "bg-pink-50 text-pink-700",
  DISCHARGE_PENDING: "bg-lime-50 text-lime-700",
  DISCHARGED: "bg-neutral-100 text-neutral-500",
  CANCELLED: "bg-red-50 text-red-600",
};

const STATE_FILTERS = [
  { label: "All", value: "" },
  { label: "Registered", value: "REGISTERED" },
  { label: "Triaged", value: "TRIAGED" },
  { label: "Waiting", value: "WAITING" },
  { label: "In Consultation", value: "IN_CONSULTATION" },
  { label: "Discharge Pending", value: "DISCHARGE_PENDING" },
];

export default function PctDashboardPage() {
  const [stateFilter, setStateFilter] = useState("");
  const [journeys, setJourneys] = useState<Journey[]>([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const loadJourneys = async (state?: string) => {
    setLoading(true);
    try {
      const qs = state ? `?state=${state}&size=50` : "?size=50";
      const data = await apiClient.get<Journey[]>(`/internal/v1/journeys${qs}`);
      setJourneys(Array.isArray(data) ? data : []);
    } catch {
      setJourneys([]);
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  };

  const handleFilterChange = (value: string) => {
    setStateFilter(value);
    loadJourneys(value || undefined);
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          PCT — Patient Care Tracking
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Journey FSM: REGISTERED → TRIAGED → IN_CONSULTATION → DISCHARGED — 13-state patient tracking
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <Link href="/pct/queue" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Reception</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Queue</p>
            <p className="text-xs text-neutral-500 mt-1">Waiting patients and triage priority</p>
          </div>
        </Link>

        <Link href="/pct/triage" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Assessment</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Triage</p>
            <p className="text-xs text-neutral-500 mt-1">Triage assessments and acuity scores</p>
          </div>
        </Link>

        <Link href="/pct/encounters" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Clinical</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Encounters</p>
            <p className="text-xs text-neutral-500 mt-1">Active consultations and notes</p>
          </div>
        </Link>

        <Link href="/pct/discharge" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Exit</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Discharge</p>
            <p className="text-xs text-neutral-500 mt-1">Pending discharges and summaries</p>
          </div>
        </Link>
      </div>

      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-neutral-900">Active Journeys</h2>
          <button
            onClick={() => loadJourneys(stateFilter || undefined)}
            disabled={loading}
            className="px-4 py-1.5 text-sm font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90 disabled:opacity-50"
          >
            {loading ? "Loading…" : loaded ? "Refresh" : "Load Journeys"}
          </button>
        </div>

        <div className="flex gap-2 mb-4 flex-wrap">
          {STATE_FILTERS.map((f) => (
            <button
              key={f.value}
              onClick={() => handleFilterChange(f.value)}
              className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
                stateFilter === f.value
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
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Journey ID</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Patient CPID</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">State</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Chief Complaint</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Started</th>
                <th className="text-left py-3 px-2 text-neutral-500 font-medium">Last Update</th>
              </tr>
            </thead>
            <tbody>
              {!loaded ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-neutral-400 text-sm">
                    Click &ldquo;Load Journeys&rdquo; to fetch active journeys
                  </td>
                </tr>
              ) : journeys.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-neutral-400 text-sm">
                    No journeys found{stateFilter ? ` in state "${stateFilter}"` : ""}
                  </td>
                </tr>
              ) : (
                journeys.map((j) => (
                  <tr key={j.id} className="border-b border-neutral-50 hover:bg-neutral-25">
                    <td className="py-3 px-2 font-mono text-xs text-neutral-500">
                      {j.id.slice(0, 8)}…
                    </td>
                    <td className="py-3 px-2 font-mono text-xs text-neutral-700">
                      {j.patientCpid}
                    </td>
                    <td className="py-3 px-2">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                          STATE_BADGE[j.state] ?? "bg-neutral-100 text-neutral-600"
                        }`}
                      >
                        {j.state.replace(/_/g, " ")}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-neutral-700">{j.chiefComplaint ?? "—"}</td>
                    <td className="py-3 px-2 text-neutral-500 text-xs">
                      {new Date(j.startedAt).toLocaleString("en-ZW", {
                        day: "2-digit",
                        month: "short",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                    <td className="py-3 px-2 text-neutral-500 text-xs">
                      {new Date(j.updatedAt).toLocaleTimeString("en-ZW", {
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
