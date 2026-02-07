"use client";

import { useState, useEffect, useCallback } from "react";
import {
  orosApi,
  type Capability,
  type AdapterMode,
  type OrderType,
  type UpsertCapabilityRequest,
} from "@/lib/orosApi";

const ORDER_TYPES: OrderType[] = ["LAB", "IMAGING", "PHARMACY", "REFERRAL", "PROCEDURE", "SUPPLY"];

const ADAPTER_MODE_LABEL: Record<AdapterMode, { label: string; badge: string; desc: string }> = {
  INTERNAL: {
    label: "Internal",
    badge: "badge bg-emerald-100 text-emerald-800",
    desc: "All orders fulfilled within Impilo",
  },
  ADAPTER: {
    label: "Adapter",
    badge: "badge bg-blue-100 text-blue-800",
    desc: "All orders routed to external LIS/RIS/pharmacy",
  },
  HYBRID: {
    label: "Hybrid",
    badge: "badge bg-purple-100 text-purple-800",
    desc: "Mix of internal and external fulfillment",
  },
};

export default function CapabilitiesPage() {
  const [capabilities, setCapabilities] = useState<Capability[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Upsert form
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formData, setFormData] = useState<UpsertCapabilityRequest>({
    facilityId: "",
    adapterMode: "INTERNAL",
    supportedOrderTypes: ["LAB"],
    autoAcceptOrders: false,
    criticalResultNotification: true,
  });

  const loadCapabilities = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await orosApi.listCapabilities();
      setCapabilities(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load capabilities");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCapabilities();
  }, [loadCapabilities]);

  const handleUpsert = async () => {
    if (!formData.facilityId.trim()) {
      setError("Facility ID is required.");
      return;
    }
    if (formData.supportedOrderTypes.length === 0) {
      setError("At least one supported order type is required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await orosApi.upsertCapability(formData);
      setSuccessMessage(editingId ? "Capability updated." : "Capability created.");
      setShowForm(false);
      setEditingId(null);
      setFormData({
        facilityId: "",
        adapterMode: "INTERNAL",
        supportedOrderTypes: ["LAB"],
        autoAcceptOrders: false,
        criticalResultNotification: true,
      });
      await loadCapabilities();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save capability");
    } finally {
      setActionLoading(false);
    }
  };

  const handleEdit = (cap: Capability) => {
    setEditingId(cap.id);
    setFormData({
      facilityId: cap.facilityId,
      adapterMode: cap.adapterMode,
      supportedOrderTypes: [...cap.supportedOrderTypes],
      routingRules: cap.routingRules.length > 0 ? [...cap.routingRules] : undefined,
      defaultDepartmentId: cap.defaultDepartmentId || undefined,
      autoAcceptOrders: cap.autoAcceptOrders,
      criticalResultNotification: cap.criticalResultNotification,
    });
    setShowForm(true);
  };

  const toggleOrderType = (type: OrderType) => {
    const current = formData.supportedOrderTypes;
    if (current.includes(type)) {
      setFormData({
        ...formData,
        supportedOrderTypes: current.filter((t) => t !== type),
      });
    } else {
      setFormData({
        ...formData,
        supportedOrderTypes: [...current, type],
      });
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
          <h1 className="text-2xl font-semibold text-neutral-900">Capabilities</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Configure OROS capabilities per tenant and facility: adapter modes, supported order types, and routing.
          </p>
        </div>
        <button
          onClick={() => {
            setEditingId(null);
            setFormData({
              facilityId: "",
              adapterMode: "INTERNAL",
              supportedOrderTypes: ["LAB"],
              autoAcceptOrders: false,
              criticalResultNotification: true,
            });
            setShowForm(!showForm);
          }}
          className="btn-primary"
        >
          Add Capability
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
          <button
            onClick={() => setSuccessMessage(null)}
            className="ml-2 text-emerald-600 hover:text-emerald-800 font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Upsert form */}
      {showForm && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">
            {editingId ? "Edit Capability" : "Add Capability"}
          </h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="facilityId" className="block text-sm font-medium text-neutral-700 mb-1">
                Facility ID
              </label>
              <input
                id="facilityId"
                type="text"
                value={formData.facilityId}
                onChange={(e) => setFormData({ ...formData, facilityId: e.target.value })}
                placeholder="UUID of the facility"
                className="input-field"
                disabled={!!editingId}
              />
            </div>
            <div>
              <label htmlFor="adapterMode" className="block text-sm font-medium text-neutral-700 mb-1">
                Adapter Mode
              </label>
              <select
                id="adapterMode"
                value={formData.adapterMode}
                onChange={(e) => setFormData({ ...formData, adapterMode: e.target.value as AdapterMode })}
                className="select-field"
              >
                {(Object.keys(ADAPTER_MODE_LABEL) as AdapterMode[]).map((mode) => (
                  <option key={mode} value={mode}>
                    {ADAPTER_MODE_LABEL[mode].label} -- {ADAPTER_MODE_LABEL[mode].desc}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-2">
              Supported Order Types
            </label>
            <div className="flex flex-wrap gap-2">
              {ORDER_TYPES.map((type) => {
                const selected = formData.supportedOrderTypes.includes(type);
                return (
                  <button
                    key={type}
                    onClick={() => toggleOrderType(type)}
                    className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors border ${
                      selected
                        ? "bg-indigo-50 text-indigo-700 border-indigo-300"
                        : "bg-white text-neutral-600 border-neutral-300 hover:bg-neutral-50"
                    }`}
                  >
                    {type.charAt(0) + type.slice(1).toLowerCase()}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="defaultDept" className="block text-sm font-medium text-neutral-700 mb-1">
                Default Department ID (optional)
              </label>
              <input
                id="defaultDept"
                type="text"
                value={formData.defaultDepartmentId || ""}
                onChange={(e) =>
                  setFormData({ ...formData, defaultDepartmentId: e.target.value || undefined })
                }
                placeholder="UUID (optional)"
                className="input-field"
              />
            </div>
            <div className="flex flex-col gap-3 pt-6">
              <label className="flex items-center gap-2 text-sm text-neutral-700">
                <input
                  type="checkbox"
                  checked={formData.autoAcceptOrders || false}
                  onChange={(e) => setFormData({ ...formData, autoAcceptOrders: e.target.checked })}
                  className="rounded border-neutral-300"
                />
                Auto-accept orders (skip worklist)
              </label>
              <label className="flex items-center gap-2 text-sm text-neutral-700">
                <input
                  type="checkbox"
                  checked={formData.criticalResultNotification ?? true}
                  onChange={(e) =>
                    setFormData({ ...formData, criticalResultNotification: e.target.checked })
                  }
                  className="rounded border-neutral-300"
                />
                Critical result notifications
              </label>
            </div>
          </div>

          <div className="flex gap-2">
            <button onClick={handleUpsert} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Saving..." : editingId ? "Update" : "Create"}
            </button>
            <button
              onClick={() => {
                setShowForm(false);
                setEditingId(null);
              }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Capabilities list */}
      <div className="space-y-4">
        {capabilities.map((cap) => (
          <div key={cap.id} className="card p-5">
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-3">
                  <h3 className="text-sm font-semibold text-neutral-900">
                    {cap.facilityName || cap.facilityId}
                  </h3>
                  <span className={ADAPTER_MODE_LABEL[cap.adapterMode].badge}>
                    {ADAPTER_MODE_LABEL[cap.adapterMode].label}
                  </span>
                  {cap.active ? (
                    <span className="badge bg-emerald-100 text-emerald-800">Active</span>
                  ) : (
                    <span className="badge bg-neutral-100 text-neutral-500">Inactive</span>
                  )}
                </div>

                <div className="flex flex-wrap gap-1.5 mt-3">
                  {cap.supportedOrderTypes.map((type) => (
                    <span key={type} className="badge bg-indigo-100 text-indigo-800">
                      {type}
                    </span>
                  ))}
                </div>

                <div className="flex gap-4 mt-3 text-xs text-neutral-500">
                  <span>
                    Auto-accept: <span className="font-medium">{cap.autoAcceptOrders ? "Yes" : "No"}</span>
                  </span>
                  <span>
                    Critical notifications:{" "}
                    <span className="font-medium">{cap.criticalResultNotification ? "Yes" : "No"}</span>
                  </span>
                  {cap.routingRules.length > 0 && (
                    <span>
                      Routing rules: <span className="font-medium">{cap.routingRules.length}</span>
                    </span>
                  )}
                </div>

                <div className="text-xs text-neutral-400 mt-2">
                  Updated: {new Date(cap.updatedAt).toLocaleString()}
                </div>
              </div>

              <button
                onClick={() => handleEdit(cap)}
                className="text-xs text-indigo-600 hover:text-indigo-800 font-medium ml-4"
              >
                Edit
              </button>
            </div>

            {/* Routing rules */}
            {cap.routingRules.length > 0 && (
              <div className="mt-4 border-t border-neutral-200 pt-3">
                <h4 className="text-xs font-medium text-neutral-600 mb-2">Routing Rules</h4>
                <div className="border border-neutral-200 rounded-lg overflow-hidden">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b border-neutral-200 bg-neutral-50">
                        <th className="text-left px-3 py-1.5 font-medium text-neutral-600">Order Type</th>
                        <th className="text-left px-3 py-1.5 font-medium text-neutral-600">Target</th>
                        <th className="text-left px-3 py-1.5 font-medium text-neutral-600">Identifier</th>
                        <th className="text-left px-3 py-1.5 font-medium text-neutral-600">Priority</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-neutral-100">
                      {cap.routingRules.map((rule, index) => (
                        <tr key={index} className="hover:bg-neutral-50">
                          <td className="px-3 py-1.5">{rule.orderType}</td>
                          <td className="px-3 py-1.5">{rule.targetType}</td>
                          <td className="px-3 py-1.5 font-mono">{rule.targetIdentifier}</td>
                          <td className="px-3 py-1.5">{rule.priority}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        ))}

        {capabilities.length === 0 && (
          <div className="card p-12 text-center text-neutral-500">
            No capabilities configured yet. Add a capability to define OROS behavior for a facility.
          </div>
        )}
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {capabilities.length} capabilit{capabilities.length !== 1 ? "ies" : "y"} configured
      </div>
    </div>
  );
}
