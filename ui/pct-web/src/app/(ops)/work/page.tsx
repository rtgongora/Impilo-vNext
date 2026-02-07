"use client";

import { useState, useEffect, useCallback } from "react";
import { pctApi, type WorkSession, type WorkContext } from "@/lib/pctApi";
import { useSessionStore } from "@/stores/sessionStore";

const DUTY_MODES = ["CLINICAL", "ADMIN", "VIRTUAL"] as const;

export default function WorkPage() {
  const { activeSession, setActiveSession } = useSessionStore();

  const [facilityId, setFacilityId] = useState("");
  const [workspaceId, setWorkspaceId] = useState("");
  const [dutyMode, setDutyMode] = useState<"CLINICAL" | "ADMIN" | "VIRTUAL">("CLINICAL");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [contextLoading, setContextLoading] = useState(true);

  const loadWorkContext = useCallback(async () => {
    try {
      setContextLoading(true);
      const ctx: WorkContext = await pctApi.getWorkContext();
      if (ctx.activeShift && ctx.activeShift.status === "ACTIVE") {
        setActiveSession({
          shiftId: ctx.activeShift.shiftId,
          facilityId: ctx.activeShift.facilityId,
          facilityName: ctx.activeShift.facilityName,
          workspaceId: ctx.activeShift.workspaceId,
          workspaceName: ctx.activeShift.workspaceName,
          dutyMode: ctx.activeShift.dutyMode,
          startedAt: ctx.activeShift.startedAt,
          actorId: ctx.activeShift.actorId,
          actorDisplayName: ctx.activeShift.actorDisplayName,
        });
      }
    } catch {
      // No active session — that is fine
    } finally {
      setContextLoading(false);
    }
  }, [setActiveSession]);

  useEffect(() => {
    loadWorkContext();
  }, [loadWorkContext]);

  const handleStartShift = async () => {
    if (!facilityId.trim() || !workspaceId.trim()) {
      setError("Facility ID and Workspace ID are required.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const session: WorkSession = await pctApi.startShift(facilityId, workspaceId, dutyMode);
      setActiveSession({
        shiftId: session.shiftId,
        facilityId: session.facilityId,
        facilityName: session.facilityName,
        workspaceId: session.workspaceId,
        workspaceName: session.workspaceName,
        dutyMode: session.dutyMode,
        startedAt: session.startedAt,
        actorId: session.actorId,
        actorDisplayName: session.actorDisplayName,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start shift");
    } finally {
      setLoading(false);
    }
  };

  const handleEndShift = async () => {
    setLoading(true);
    setError(null);

    try {
      await pctApi.endShift();
      setActiveSession(null);
      setFacilityId("");
      setWorkspaceId("");
      setDutyMode("CLINICAL");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to end shift");
    } finally {
      setLoading(false);
    }
  };

  if (contextLoading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="card p-6 space-y-4">
            <div className="h-10 bg-neutral-100 rounded" />
            <div className="h-10 bg-neutral-100 rounded" />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8 max-w-2xl">
      <h1 className="text-2xl font-semibold text-neutral-900">Work Session</h1>
      <p className="text-sm text-neutral-500 mt-1 mb-6">
        Start or end your shift to begin tracking patient care activities.
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {activeSession ? (
        <div className="space-y-6">
          {/* Active session info */}
          <div className="card p-6">
            <div className="flex items-center gap-3 mb-4">
              <span className="w-3 h-3 rounded-full bg-emerald-500 animate-pulse" />
              <h2 className="text-lg font-semibold text-neutral-900">
                Active Shift
              </h2>
            </div>

            <dl className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <dt className="text-neutral-500 font-medium">Shift ID</dt>
                <dd className="text-neutral-900 font-mono text-xs mt-0.5">
                  {activeSession.shiftId}
                </dd>
              </div>
              <div>
                <dt className="text-neutral-500 font-medium">Clinician</dt>
                <dd className="text-neutral-900 mt-0.5">
                  {activeSession.actorDisplayName}
                </dd>
              </div>
              <div>
                <dt className="text-neutral-500 font-medium">Facility</dt>
                <dd className="text-neutral-900 mt-0.5">
                  {activeSession.facilityName}
                </dd>
              </div>
              <div>
                <dt className="text-neutral-500 font-medium">Workspace</dt>
                <dd className="text-neutral-900 mt-0.5">
                  {activeSession.workspaceName}
                </dd>
              </div>
              <div>
                <dt className="text-neutral-500 font-medium">Duty Mode</dt>
                <dd className="mt-0.5">
                  <span
                    className={`badge ${
                      activeSession.dutyMode === "CLINICAL"
                        ? "bg-emerald-100 text-emerald-800"
                        : activeSession.dutyMode === "ADMIN"
                          ? "bg-blue-100 text-blue-800"
                          : "bg-purple-100 text-purple-800"
                    }`}
                  >
                    {activeSession.dutyMode}
                  </span>
                </dd>
              </div>
              <div>
                <dt className="text-neutral-500 font-medium">Started At</dt>
                <dd className="text-neutral-900 mt-0.5">
                  {new Date(activeSession.startedAt).toLocaleString()}
                </dd>
              </div>
            </dl>
          </div>

          <button
            onClick={handleEndShift}
            disabled={loading}
            className="btn-danger w-full"
          >
            {loading ? "Ending shift..." : "End Shift"}
          </button>
        </div>
      ) : (
        <div className="card p-6 space-y-5">
          <h2 className="text-lg font-semibold text-neutral-900">
            Start a New Shift
          </h2>

          <div>
            <label
              htmlFor="facilityId"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Facility ID
            </label>
            <input
              id="facilityId"
              type="text"
              value={facilityId}
              onChange={(e) => setFacilityId(e.target.value)}
              placeholder="e.g., FAC-001"
              className="input-field"
            />
          </div>

          <div>
            <label
              htmlFor="workspaceId"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Workspace ID
            </label>
            <input
              id="workspaceId"
              type="text"
              value={workspaceId}
              onChange={(e) => setWorkspaceId(e.target.value)}
              placeholder="e.g., WS-OPD-01"
              className="input-field"
            />
          </div>

          <div>
            <label
              htmlFor="dutyMode"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Duty Mode
            </label>
            <select
              id="dutyMode"
              value={dutyMode}
              onChange={(e) =>
                setDutyMode(e.target.value as "CLINICAL" | "ADMIN" | "VIRTUAL")
              }
              className="select-field"
            >
              {DUTY_MODES.map((mode) => (
                <option key={mode} value={mode}>
                  {mode}
                </option>
              ))}
            </select>
          </div>

          <button
            onClick={handleStartShift}
            disabled={loading}
            className="btn-primary w-full"
          >
            {loading ? "Starting shift..." : "Start Shift"}
          </button>
        </div>
      )}
    </div>
  );
}
