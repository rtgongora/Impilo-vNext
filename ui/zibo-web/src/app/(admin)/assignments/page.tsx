"use client";

import { useState, useEffect, useCallback } from "react";
import {
  ziboApi,
  type Assignment,
  type AssignRequest,
  type PolicyMode,
} from "@/lib/ziboApi";

const POLICY_MODE_COLORS: Record<PolicyMode, string> = {
  ENFORCE: "bg-red-100 text-red-800",
  WARN: "bg-yellow-100 text-yellow-800",
  AUDIT: "bg-blue-100 text-blue-800",
};

const SCOPE_TYPES = ["FACILITY", "WORKSPACE", "SERVICE", "TENANT"] as const;
const POLICY_MODES: PolicyMode[] = ["ENFORCE", "WARN", "AUDIT"];

export default function AssignmentsPage() {
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Assign form
  const [showAssignForm, setShowAssignForm] = useState(false);
  const [assignData, setAssignData] = useState<AssignRequest>({
    packId: "",
    scope: "",
    scopeType: "FACILITY",
    policyMode: "WARN",
  });

  const loadAssignments = useCallback(async () => {
    try {
      setLoading(true);
      const data = await ziboApi.listAssignments();
      setAssignments(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load assignments");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAssignments();
  }, [loadAssignments]);

  const handleAssign = async () => {
    if (!assignData.packId.trim() || !assignData.scope.trim()) {
      setError("Pack ID and scope are required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await ziboApi.assignPack(assignData);
      setSuccessMessage("Pack assigned successfully.");
      setShowAssignForm(false);
      setAssignData({
        packId: "",
        scope: "",
        scopeType: "FACILITY",
        policyMode: "WARN",
      });
      await loadAssignments();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to assign pack");
    } finally {
      setActionLoading(false);
    }
  };

  const handleUnassign = async (id: string) => {
    setActionLoading(true);
    setError(null);

    try {
      await ziboApi.unassignPack(id);
      setSuccessMessage("Assignment removed.");
      await loadAssignments();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to remove assignment");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="card p-6 space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-10 bg-neutral-100 rounded" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Assignments</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Assign terminology packs to facilities, workspaces, or services with policy enforcement modes.
          </p>
        </div>
        <button
          onClick={() => setShowAssignForm(!showAssignForm)}
          className="btn-primary"
        >
          Assign Pack
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
          {successMessage}
        </div>
      )}

      {/* Assign form */}
      {showAssignForm && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Assign Pack</h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="assignPackId" className="block text-sm font-medium text-neutral-700 mb-1">
                Pack ID
              </label>
              <input
                id="assignPackId"
                type="text"
                value={assignData.packId}
                onChange={(e) => setAssignData({ ...assignData, packId: e.target.value })}
                placeholder="UUID of the terminology pack"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="assignScope" className="block text-sm font-medium text-neutral-700 mb-1">
                Scope
              </label>
              <input
                id="assignScope"
                type="text"
                value={assignData.scope}
                onChange={(e) => setAssignData({ ...assignData, scope: e.target.value })}
                placeholder="e.g., FAC-001 or TENANT-ZW"
                className="input-field"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="assignScopeType" className="block text-sm font-medium text-neutral-700 mb-1">
                Scope Type
              </label>
              <select
                id="assignScopeType"
                value={assignData.scopeType}
                onChange={(e) =>
                  setAssignData({
                    ...assignData,
                    scopeType: e.target.value as AssignRequest["scopeType"],
                  })
                }
                className="select-field"
              >
                {SCOPE_TYPES.map((st) => (
                  <option key={st} value={st}>{st}</option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="assignPolicyMode" className="block text-sm font-medium text-neutral-700 mb-1">
                Policy Mode
              </label>
              <select
                id="assignPolicyMode"
                value={assignData.policyMode}
                onChange={(e) =>
                  setAssignData({
                    ...assignData,
                    policyMode: e.target.value as PolicyMode,
                  })
                }
                className="select-field"
              >
                {POLICY_MODES.map((pm) => (
                  <option key={pm} value={pm}>{pm}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex gap-2">
            <button onClick={handleAssign} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Assigning..." : "Assign"}
            </button>
            <button onClick={() => setShowAssignForm(false)} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Assignments table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Scope</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Scope Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Pack</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Policy Mode</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Active</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Assigned At</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Assigned By</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {assignments.map((assignment) => (
              <tr key={assignment.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3 text-neutral-900 font-medium">
                  {assignment.scope}
                </td>
                <td className="px-4 py-3">
                  <span className="badge bg-neutral-100 text-neutral-700">
                    {assignment.scopeType}
                  </span>
                </td>
                <td className="px-4 py-3 text-neutral-900">
                  {assignment.packName}
                </td>
                <td className="px-4 py-3">
                  <span className={`badge ${POLICY_MODE_COLORS[assignment.policyMode]}`}>
                    {assignment.policyMode}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {assignment.active ? (
                    <span className="badge bg-emerald-100 text-emerald-800">Active</span>
                  ) : (
                    <span className="badge bg-neutral-100 text-neutral-500">Inactive</span>
                  )}
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {new Date(assignment.assignedAt).toLocaleString()}
                </td>
                <td className="px-4 py-3 text-neutral-600 text-xs">
                  {assignment.assignedBy}
                </td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => handleUnassign(assignment.id)}
                    disabled={actionLoading}
                    className="text-xs text-red-600 hover:text-red-800 font-medium"
                  >
                    Unassign
                  </button>
                </td>
              </tr>
            ))}
            {assignments.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                  No assignments found. Assign a terminology pack to get started.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {assignments.length} assignment{assignments.length !== 1 ? "s" : ""} total
      </div>
    </div>
  );
}
