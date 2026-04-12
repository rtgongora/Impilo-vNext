"use client";

/**
 * Data Access Governance — Policy management and access request workflow.
 * Route: /access/governance
 *
 * Health OS §6 (Privacy by Architecture): every data access must be governed
 * by policy, and exceptional access requires an auditable request/approval
 * workflow.
 *
 * Brief §12 (Intelligent Experience Shell): unified governance surface
 * within the experience shell.
 */

import { useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  Shield,
  AlertCircle,
  ShieldCheck,
  ShieldX,
  FileText,
  CheckCircle,
  XCircle,
  Clock,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useGovernancePolicies,
  useCreateGovernancePolicy,
  useAccessRequests,
  useApproveAccessRequest,
  useDenyAccessRequest,
  type GovernancePolicyResource,
  type AccessRequestResource,
} from "@/hooks/queries/useDataAccessGovernance";

type Tab = "policies" | "requests";

const EFFECT_STYLES: Record<string, string> = {
  ALLOW: "bg-green-100 text-green-700",
  DENY: "bg-red-100 text-red-700",
};

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  DRAFT: "bg-yellow-100 text-yellow-700",
  DISABLED: "bg-gray-100 text-gray-500",
};

const REQUEST_STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  APPROVED: "bg-green-100 text-green-700",
  DENIED: "bg-red-100 text-red-700",
  EXPIRED: "bg-gray-100 text-gray-500",
  REVOKED: "bg-orange-100 text-orange-700",
};

export default function DataAccessGovernancePage() {
  const [tab, setTab] = useState<Tab>("policies");

  const tabs: { id: Tab; label: string; icon: typeof Shield }[] = [
    { id: "policies", label: "Governance Policies", icon: Shield },
    { id: "requests", label: "Access Requests", icon: FileText },
  ];

  return (
    <AppLayout>
      <PageShell
        title="Data Access Governance"
        subtitle="Manage data governance policies and access request workflows"
        icon={<ShieldCheck className="w-5 h-5" />}
      >
        <div className="mb-4">
          <Link
            href="/access"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to access channels
          </Link>
        </div>

        <div className="flex gap-1 mb-6 border-b border-gray-200">
          {tabs.map((t) => {
            const Icon = t.icon;
            return (
              <button
                key={t.id}
                onClick={() => setTab(t.id)}
                className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                  tab === t.id
                    ? "border-blue-600 text-blue-600"
                    : "border-transparent text-gray-500 hover:text-gray-700"
                }`}
              >
                <Icon className="w-4 h-4" /> {t.label}
              </button>
            );
          })}
        </div>

        {tab === "policies" && <PoliciesTab />}
        {tab === "requests" && <AccessRequestsTab />}
      </PageShell>
    </AppLayout>
  );
}

// ── Policies tab ───────────────────────────────────────────────────

function PoliciesTab() {
  const { data, isLoading, error } = useGovernancePolicies();
  const createMutation = useCreateGovernancePolicy();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    name: "",
    description: "",
    resourceType: "",
    effect: "ALLOW" as "ALLOW" | "DENY",
    dataClassification: "CONFIDENTIAL",
  });

  const policies: GovernancePolicyResource[] = data?.data ?? [];

  function handleCreate() {
    if (!form.name || !form.resourceType) return;
    createMutation.mutate(form, {
      onSuccess: () => {
        setShowForm(false);
        setForm({
          name: "",
          description: "",
          resourceType: "",
          effect: "ALLOW",
          dataClassification: "CONFIDENTIAL",
        });
      },
    });
  }

  if (error) {
    return (
      <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
        <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
        <p className="text-red-600 text-sm">Failed to load governance policies</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
        <span className="ml-2 text-sm text-gray-500">Loading governance policies...</span>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-900">
          Governance Policies ({policies.length})
        </h3>
        <button
          onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          {showForm ? "Cancel" : "New Policy"}
        </button>
      </div>

      {showForm && (
        <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
          <h4 className="text-sm font-semibold text-gray-700">Create Governance Policy</h4>
          <div className="grid grid-cols-2 gap-3">
            <input
              type="text"
              placeholder="Policy name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
            <select
              value={form.resourceType}
              onChange={(e) => setForm({ ...form, resourceType: e.target.value })}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="">Select resource type</option>
              <option value="CLINICAL_RECORD">Clinical Record</option>
              <option value="PATIENT_DEMOGRAPHICS">Patient Demographics</option>
              <option value="LAB_RESULT">Lab Result</option>
              <option value="PRESCRIPTION">Prescription</option>
              <option value="IMAGING">Imaging</option>
              <option value="FINANCIAL">Financial</option>
              <option value="AUDIT_LOG">Audit Log</option>
            </select>
          </div>
          <textarea
            placeholder="Policy description"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            rows={2}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
          <div className="grid grid-cols-2 gap-3">
            <select
              value={form.effect}
              onChange={(e) =>
                setForm({ ...form, effect: e.target.value as "ALLOW" | "DENY" })
              }
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="ALLOW">ALLOW</option>
              <option value="DENY">DENY</option>
            </select>
            <select
              value={form.dataClassification}
              onChange={(e) =>
                setForm({ ...form, dataClassification: e.target.value })
              }
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="PUBLIC">Public</option>
              <option value="INTERNAL">Internal</option>
              <option value="CONFIDENTIAL">Confidential</option>
              <option value="RESTRICTED">Restricted</option>
            </select>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleCreate}
              disabled={!form.name || !form.resourceType || createMutation.isPending}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg disabled:opacity-50 hover:bg-blue-700 transition-colors"
            >
              {createMutation.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" /> Creating...
                </>
              ) : (
                "Create Policy"
              )}
            </button>
            {createMutation.isError && (
              <span className="text-sm text-red-600">Failed to create policy</span>
            )}
          </div>
        </div>
      )}

      {policies.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <Shield className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-400 text-sm">No governance policies configured</p>
        </div>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-gray-50">
                <th className="text-left px-4 py-3 font-medium text-gray-600">Policy Name</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Resource Type</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Classification</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Effect</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Updated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {policies.map((policy) => {
                const effectStyle =
                  EFFECT_STYLES[policy.attributes.effect] ?? "bg-gray-100 text-gray-600";
                const statusStyle =
                  STATUS_STYLES[policy.attributes.status] ?? "bg-gray-100 text-gray-600";
                return (
                  <tr key={policy.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3">
                      <div className="font-medium text-gray-900">{policy.attributes.name}</div>
                      {policy.attributes.description && (
                        <div className="text-xs text-gray-400 mt-0.5 max-w-xs truncate">
                          {policy.attributes.description}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-purple-100 text-purple-700">
                        {policy.attributes.resourceType}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-blue-100 text-blue-700">
                        {policy.attributes.dataClassification}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-0.5 text-xs rounded-full ${effectStyle}`}
                      >
                        {policy.attributes.effect}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                      >
                        {policy.attributes.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500 whitespace-nowrap text-xs">
                      {new Date(policy.attributes.updatedAt).toLocaleDateString()}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ── Access requests tab ────────────────────────────────────────────

function AccessRequestsTab() {
  const [statusFilter, setStatusFilter] = useState<string>("");
  const { data, isLoading, error } = useAccessRequests(
    statusFilter || undefined,
  );
  const approveMutation = useApproveAccessRequest();
  const denyMutation = useDenyAccessRequest();

  const requests: AccessRequestResource[] = data?.data ?? [];

  function handleApprove(id: string) {
    approveMutation.mutate({ id });
  }

  function handleDeny(id: string) {
    denyMutation.mutate({ id });
  }

  if (error) {
    return (
      <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
        <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
        <p className="text-red-600 text-sm">Failed to load access requests</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-900">Access Requests</h3>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
        >
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="APPROVED">Approved</option>
          <option value="DENIED">Denied</option>
          <option value="EXPIRED">Expired</option>
          <option value="REVOKED">Revoked</option>
        </select>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          <span className="ml-2 text-sm text-gray-500">Loading access requests...</span>
        </div>
      ) : requests.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-400 text-sm">
            {statusFilter
              ? `No ${statusFilter.toLowerCase()} access requests`
              : "No access requests"}
          </p>
        </div>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-gray-50">
                <th className="text-left px-4 py-3 font-medium text-gray-600">Requester</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Resource</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Purpose</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Requested</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {requests.map((request) => {
                const statusStyle =
                  REQUEST_STATUS_STYLES[request.attributes.status] ??
                  "bg-gray-100 text-gray-600";
                const isPending = request.attributes.status === "PENDING";
                const isActing =
                  (approveMutation.isPending &&
                    approveMutation.variables?.id === request.id) ||
                  (denyMutation.isPending &&
                    denyMutation.variables?.id === request.id);

                return (
                  <tr key={request.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3">
                      <div className="font-medium text-gray-900">
                        {request.attributes.requesterName}
                      </div>
                      <div className="text-xs text-gray-400 mt-0.5">
                        {request.attributes.requesterId}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-purple-100 text-purple-700">
                        {request.attributes.resourceType}
                      </span>
                      <div className="text-xs text-gray-400 mt-0.5 font-mono">
                        {request.attributes.resourceId}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-600 max-w-xs">
                      <div className="truncate">{request.attributes.purpose}</div>
                      {request.attributes.justification && (
                        <div className="text-xs text-gray-400 mt-0.5 truncate">
                          {request.attributes.justification}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                      >
                        {request.attributes.status === "PENDING" && (
                          <Clock className="w-3 h-3" />
                        )}
                        {request.attributes.status === "APPROVED" && (
                          <CheckCircle className="w-3 h-3" />
                        )}
                        {request.attributes.status === "DENIED" && (
                          <XCircle className="w-3 h-3" />
                        )}
                        {request.attributes.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500 whitespace-nowrap text-xs">
                      {new Date(request.attributes.requestedAt).toLocaleDateString()}
                    </td>
                    <td className="px-4 py-3">
                      {isPending ? (
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => handleApprove(request.id)}
                            disabled={isActing}
                            className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors"
                          >
                            {isActing &&
                            approveMutation.variables?.id === request.id ? (
                              <Loader2 className="w-3 h-3 animate-spin" />
                            ) : (
                              <CheckCircle className="w-3 h-3" />
                            )}
                            Approve
                          </button>
                          <button
                            onClick={() => handleDeny(request.id)}
                            disabled={isActing}
                            className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors"
                          >
                            {isActing &&
                            denyMutation.variables?.id === request.id ? (
                              <Loader2 className="w-3 h-3 animate-spin" />
                            ) : (
                              <ShieldX className="w-3 h-3" />
                            )}
                            Deny
                          </button>
                        </div>
                      ) : (
                        <span className="text-xs text-gray-400">
                          {request.attributes.decidedBy
                            ? `by ${request.attributes.decidedBy}`
                            : "--"}
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {(approveMutation.isError || denyMutation.isError) && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-600">
          Failed to process access request. Please try again.
        </div>
      )}
    </div>
  );
}
