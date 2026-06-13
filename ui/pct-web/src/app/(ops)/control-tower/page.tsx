"use client";

import { useState, useEffect, useCallback } from "react";
import {
  pctApi,
  type ControlTowerData,
  type Bottleneck,
} from "@/lib/pctApi";
import { useSessionStore } from "@/stores/sessionStore";

const JOURNEY_STATE_COLORS: Record<string, string> = {
  ARRIVED: "bg-blue-500",
  TRIAGED: "bg-yellow-500",
  QUEUED: "bg-orange-500",
  IN_SERVICE: "bg-emerald-500",
  ENCOUNTER_STARTED: "bg-teal-500",
  ADMITTED: "bg-purple-500",
  DISCHARGED: "bg-neutral-400",
  DEATH_RECORDED: "bg-neutral-700",
  COMPLETED: "bg-neutral-300",
  CANCELLED: "bg-red-300",
};

const BOTTLENECK_SEVERITY_COLORS: Record<string, string> = {
  LOW: "bg-blue-100 text-blue-800 border-info/25",
  MEDIUM: "bg-yellow-100 text-yellow-800 border-yellow-200",
  HIGH: "bg-orange-100 text-orange-800 border-orange-200",
  CRITICAL: "bg-red-100 text-red-800 border-danger/28",
};

const REFRESH_INTERVAL_MS = 30000;

export default function ControlTowerPage() {
  const { facilityId } = useSessionStore();

  const [data, setData] = useState<ControlTowerData | null>(null);
  const [bottlenecks, setBottlenecks] = useState<Bottleneck[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastRefresh, setLastRefresh] = useState<Date | null>(null);
  const [facilityInput, setFacilityInput] = useState(facilityId || "");

  const loadData = useCallback(async (fId: string) => {
    if (!fId) return;
    try {
      setLoading(true);
      setError(null);
      const [towerData, bottleneckData] = await Promise.all([
        pctApi.getControlTower(fId),
        pctApi.getBottlenecks(fId),
      ]);
      setData(towerData);
      setBottlenecks(bottleneckData);
      setLastRefresh(new Date());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load control tower data");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (facilityId) {
      setFacilityInput(facilityId);
      loadData(facilityId);
    }
  }, [facilityId, loadData]);

  // Auto-refresh every 30s
  useEffect(() => {
    if (!facilityInput) return;
    const interval = setInterval(() => {
      loadData(facilityInput);
    }, REFRESH_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [facilityInput, loadData]);

  const handleLoadFacility = () => {
    if (facilityInput.trim()) {
      loadData(facilityInput.trim());
    }
  };

  const totalJourneys = data
    ? Object.values(data.journeysByState).reduce((sum, n) => sum + n, 0)
    : 0;

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">
            Control Tower
          </h1>
          <p className="text-sm text-neutral-500 mt-1">
            Real-time facility operations overview. Auto-refreshes every 30 seconds.
          </p>
        </div>
        {lastRefresh && (
          <div className="text-xs text-neutral-400">
            Last updated: {lastRefresh.toLocaleTimeString()}
          </div>
        )}
      </div>

      {/* Facility selector (when no active session) */}
      {!facilityId && (
        <div className="card p-4 mb-6 flex items-end gap-3">
          <div className="flex-1">
            <label
              htmlFor="facilityInput"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Facility ID
            </label>
            <input
              id="facilityInput"
              type="text"
              value={facilityInput}
              onChange={(e) => setFacilityInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleLoadFacility()}
              placeholder="e.g., FAC-001"
              className="input-field"
            />
          </div>
          <button onClick={handleLoadFacility} className="btn-primary">
            Load
          </button>
        </div>
      )}

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {loading && !data && (
        <div className="animate-pulse space-y-6">
          <div className="grid grid-cols-4 gap-4">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="card p-5 space-y-2">
                <div className="h-4 bg-neutral-100 rounded w-2/3" />
                <div className="h-8 bg-neutral-100 rounded w-1/2" />
              </div>
            ))}
          </div>
        </div>
      )}

      {data && (
        <div className="space-y-6">
          {/* Summary cards */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="card p-5">
              <p className="text-xs font-medium text-neutral-500 uppercase tracking-wider">
                Active Journeys
              </p>
              <p className="text-3xl font-bold text-brand-primary mt-1">
                {data.activeJourneyCount}
              </p>
            </div>
            <div className="card p-5">
              <p className="text-xs font-medium text-neutral-500 uppercase tracking-wider">
                Avg Total Wait
              </p>
              <p className="text-3xl font-bold text-neutral-900 mt-1">
                {data.averageTotalWaitMinutes}
                <span className="text-sm font-normal text-neutral-500 ml-1">min</span>
              </p>
            </div>
            <div className="card p-5">
              <p className="text-xs font-medium text-neutral-500 uppercase tracking-wider">
                Bed Occupancy
              </p>
              <p className="text-3xl font-bold text-neutral-900 mt-1">
                {data.admissionSummary.occupancyPercent}
                <span className="text-sm font-normal text-neutral-500 ml-0.5">%</span>
              </p>
              <p className="text-xs text-neutral-400 mt-0.5">
                {data.admissionSummary.occupiedBeds} / {data.admissionSummary.totalBeds} beds
              </p>
            </div>
            <div className="card p-5">
              <p className="text-xs font-medium text-neutral-500 uppercase tracking-wider">
                Active Bottlenecks
              </p>
              <p
                className={`text-3xl font-bold mt-1 ${
                  bottlenecks.filter((b) => !b.resolved).length > 0
                    ? "text-danger"
                    : "text-primary"
                }`}
              >
                {bottlenecks.filter((b) => !b.resolved).length}
              </p>
            </div>
          </div>

          {/* Queue stats */}
          <div>
            <h2 className="text-lg font-semibold text-neutral-900 mb-3">
              Queue Statistics
            </h2>
            <div className="card overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-neutral-200 bg-neutral-50">
                    <th className="text-left px-4 py-3 font-medium text-neutral-600">Queue</th>
                    <th className="text-left px-4 py-3 font-medium text-neutral-600">Workspace</th>
                    <th className="text-right px-4 py-3 font-medium text-neutral-600">Waiting</th>
                    <th className="text-right px-4 py-3 font-medium text-neutral-600">In Service</th>
                    <th className="text-right px-4 py-3 font-medium text-neutral-600">Completed</th>
                    <th className="text-right px-4 py-3 font-medium text-neutral-600">Avg Wait</th>
                    <th className="text-right px-4 py-3 font-medium text-neutral-600">Longest Wait</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-100">
                  {data.queueStats.map((qs) => (
                    <tr key={qs.queueId} className="hover:bg-neutral-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-neutral-900">
                        {qs.queueName}
                      </td>
                      <td className="px-4 py-3 text-neutral-500">
                        {qs.workspaceName}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <span
                          className={`font-semibold ${
                            qs.waitingCount > 10
                              ? "text-danger"
                              : qs.waitingCount > 5
                                ? "text-warning"
                                : "text-neutral-900"
                          }`}
                        >
                          {qs.waitingCount}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right text-primary font-medium">
                        {qs.inServiceCount}
                      </td>
                      <td className="px-4 py-3 text-right text-neutral-500">
                        {qs.completedToday}
                      </td>
                      <td className="px-4 py-3 text-right text-neutral-700">
                        {qs.averageWaitMinutes} min
                      </td>
                      <td className="px-4 py-3 text-right">
                        <span
                          className={`font-medium ${
                            qs.longestWaitMinutes > 60
                              ? "text-danger"
                              : qs.longestWaitMinutes > 30
                                ? "text-warning"
                                : "text-neutral-700"
                          }`}
                        >
                          {qs.longestWaitMinutes} min
                        </span>
                      </td>
                    </tr>
                  ))}
                  {data.queueStats.length === 0 && (
                    <tr>
                      <td
                        colSpan={7}
                        className="px-4 py-12 text-center text-neutral-500"
                      >
                        No queue data available.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Admission occupancy */}
          <div>
            <h2 className="text-lg font-semibold text-neutral-900 mb-3">
              Admission Occupancy
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {data.admissionSummary.wardBreakdown.map((ward) => (
                <div key={ward.wardId} className="card p-4">
                  <h3 className="text-sm font-semibold text-neutral-900">
                    {ward.wardName}
                  </h3>
                  <div className="mt-2">
                    <div className="flex items-center justify-between text-xs text-neutral-500 mb-1">
                      <span>
                        {ward.occupiedBeds} / {ward.totalBeds} beds
                      </span>
                      <span>{ward.occupancyPercent}%</span>
                    </div>
                    <div className="w-full h-2 bg-neutral-100 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${
                          ward.occupancyPercent > 90
                            ? "bg-danger"
                            : ward.occupancyPercent > 75
                              ? "bg-warning"
                              : "bg-emerald-500"
                        }`}
                        style={{ width: `${Math.min(ward.occupancyPercent, 100)}%` }}
                      />
                    </div>
                  </div>
                </div>
              ))}
              {data.admissionSummary.wardBreakdown.length === 0 && (
                <div className="col-span-full text-sm text-neutral-500">
                  No ward data available.
                </div>
              )}
            </div>
          </div>

          {/* Journeys by state */}
          <div>
            <h2 className="text-lg font-semibold text-neutral-900 mb-3">
              Active Journeys by State
            </h2>
            <div className="card p-5">
              <div className="flex flex-wrap gap-3">
                {Object.entries(data.journeysByState).map(([state, count]) => (
                  <div
                    key={state}
                    className="flex items-center gap-2 px-3 py-2 rounded-lg bg-neutral-50 border border-neutral-200"
                  >
                    <span
                      className={`w-3 h-3 rounded-full ${
                        JOURNEY_STATE_COLORS[state] || "bg-neutral-300"
                      }`}
                    />
                    <span className="text-sm font-medium text-neutral-700">
                      {state}
                    </span>
                    <span className="text-sm font-bold text-neutral-900">
                      {count}
                    </span>
                    {totalJourneys > 0 && (
                      <span className="text-xs text-neutral-400">
                        ({Math.round((count / totalJourneys) * 100)}%)
                      </span>
                    )}
                  </div>
                ))}
              </div>
              {Object.keys(data.journeysByState).length === 0 && (
                <p className="text-sm text-neutral-500">No active journeys.</p>
              )}
            </div>
          </div>

          {/* Bottleneck alerts */}
          <div>
            <h2 className="text-lg font-semibold text-neutral-900 mb-3">
              Bottleneck Alerts
            </h2>
            <div className="space-y-3">
              {bottlenecks
                .filter((b) => !b.resolved)
                .map((b) => (
                  <div
                    key={b.id}
                    className={`p-4 rounded-lg border ${
                      BOTTLENECK_SEVERITY_COLORS[b.severity] ||
                      "bg-neutral-100 text-neutral-800 border-neutral-200"
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-semibold uppercase">
                          {b.severity}
                        </span>
                        <span className="badge bg-card/50 text-inherit">
                          {b.type.replace(/_/g, " ")}
                        </span>
                      </div>
                      <span className="text-xs opacity-75">
                        {new Date(b.detectedAt).toLocaleTimeString()}
                      </span>
                    </div>
                    <p className="text-sm mt-1">{b.description}</p>
                  </div>
                ))}
              {bottlenecks.filter((b) => !b.resolved).length === 0 && (
                <div className="card p-6 text-center">
                  <p className="text-sm text-primary font-medium">
                    No active bottlenecks detected.
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
