"use client";

/**
 * Lab Orders — View existing lab orders and create new ones.
 * Route: /ehr/[patientId]/orders | pageTitle: "Lab Orders"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  ClipboardList,
  Plus,
  Loader2,
  ArrowLeft,
  TestTube2,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useLabOrders,
  useCreateLabOrder,
} from "@/hooks/queries/useLabOrders";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useEncounters } from "@/hooks/queries/useEncounters";

const STATUS_BADGE: Record<string, string> = {
  ORDERED: "bg-blue-100 text-blue-700",
  COLLECTED: "bg-yellow-100 text-yellow-700",
  RESULTED: "bg-green-100 text-green-700",
  CANCELLED: "bg-gray-100 text-gray-600",
};

const PRIORITY_BADGE: Record<string, string> = {
  STAT: "bg-red-100 text-red-700",
  URGENT: "bg-orange-100 text-orange-700",
  ROUTINE: "bg-blue-100 text-blue-700",
};

const EMPTY_FORM = {
  test_name: "",
  test_code: "",
  category: "LABORATORY",
  priority: "ROUTINE",
  clinical_notes: "",
  ordered_by: "",
  ordered_by_name: "",
  facility_id: "",
};

export default function OrdersPage() {
  const params = useParams<{ patientId: string }>();
  const { patientId } = params;
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );

  const { data: ordersData, isLoading } = useLabOrders(patientId);
  const createOrder = useCreateLabOrder();

  const orders = ordersData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    ...EMPTY_FORM,
    ordered_by: user?.id ?? "",
    ordered_by_name: user?.displayName ?? user?.email ?? "",
    facility_id: facility?.id ?? "",
  });

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    createOrder.mutate(
      {
        patientId,
        encounterId: activeEncounter?.id ?? "",
        testName: form.test_name,
        testCode: form.test_code,
        category: form.category,
        priority: form.priority,
        clinicalNotes: form.clinical_notes || null,
        facilityId: facility?.id ?? form.facility_id,
        orderedBy: form.ordered_by || user?.id || "",
        orderedByName: form.ordered_by_name || user?.displayName || user?.email || "",
      },
      {
        onSuccess: () => {
          setForm({ ...EMPTY_FORM });
          setShowForm(false);
        },
      },
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Lab Orders">
        {/* Back link */}
        <div className="mb-4">
          <Link
            href={`/ehr/${patientId}`}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to patient chart
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading orders...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header with Add Order button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="w-5 h-5 text-indigo-500" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Orders ({orders.length})
                </h2>
              </div>
              <button
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Add Order
              </button>
            </div>

            {/* Add Order Form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center gap-2 mb-4">
                  <TestTube2 className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-medium text-gray-900">New Lab Order</h3>
                </div>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Test Name
                      </label>
                      <input
                        type="text"
                        value={form.test_name}
                        onChange={(e) => updateField("test_name", e.target.value)}
                        placeholder="e.g. Complete Blood Count"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Test Code
                      </label>
                      <input
                        type="text"
                        value={form.test_code}
                        onChange={(e) => updateField("test_code", e.target.value)}
                        placeholder="e.g. CBC"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Category
                      </label>
                      <select
                        value={form.category}
                        onChange={(e) => updateField("category", e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                      >
                        <option value="LABORATORY">Laboratory</option>
                        <option value="RADIOLOGY">Radiology</option>
                        <option value="PATHOLOGY">Pathology</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Priority
                      </label>
                      <select
                        value={form.priority}
                        onChange={(e) => updateField("priority", e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                      >
                        <option value="ROUTINE">Routine</option>
                        <option value="URGENT">Urgent</option>
                        <option value="STAT">STAT</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Ordered By (ID)
                      </label>
                      <input
                        type="text"
                        value={form.ordered_by}
                        onChange={(e) => updateField("ordered_by", e.target.value)}
                        placeholder="Practitioner ID"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Ordered By (Name)
                      </label>
                      <input
                        type="text"
                        value={form.ordered_by_name}
                        onChange={(e) => updateField("ordered_by_name", e.target.value)}
                        placeholder="Dr. Jane Smith"
                        required
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Facility ID
                    </label>
                    <input
                      type="text"
                      value={form.facility_id}
                      onChange={(e) => updateField("facility_id", e.target.value)}
                      placeholder="facility-uuid"
                      required
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">
                      Clinical Notes
                    </label>
                    <textarea
                      value={form.clinical_notes}
                      onChange={(e) => updateField("clinical_notes", e.target.value)}
                      placeholder="Any relevant clinical context..."
                      rows={3}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                    />
                  </div>
                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={() => {
                        setShowForm(false);
                        setForm({ ...EMPTY_FORM });
                      }}
                      className="flex-1 py-2.5 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={createOrder.isPending}
                      className="flex-1 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
                    >
                      {createOrder.isPending ? (
                        <>
                          <Loader2 className="w-4 h-4 animate-spin" />
                          Submitting...
                        </>
                      ) : (
                        <>
                          <Plus className="w-4 h-4" />
                          Submit Order
                        </>
                      )}
                    </button>
                  </div>
                  {createOrder.isError && (
                    <p className="text-sm text-red-600 text-center">
                      Failed to create order. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Orders Table */}
            {orders.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <TestTube2 className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No lab orders found</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Order #
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Test Name
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Category
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Priority
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Status
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Ordered By
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Date
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {orders.map((order) => (
                        <tr key={order.id} className="hover:bg-gray-50 transition-colors">
                          <td className="px-4 py-3 text-gray-900 font-medium whitespace-nowrap">
                            {order.attributes.orderNumber}
                          </td>
                          <td className="px-4 py-3 text-gray-900 whitespace-nowrap">
                            {order.attributes.testName}
                          </td>
                          <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                            {order.attributes.category}
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap">
                            <span
                              className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                PRIORITY_BADGE[order.attributes.priority] ??
                                "bg-gray-100 text-gray-600"
                              }`}
                            >
                              {order.attributes.priority}
                            </span>
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap">
                            <span
                              className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                STATUS_BADGE[order.attributes.status] ??
                                "bg-gray-100 text-gray-600"
                              }`}
                            >
                              {order.attributes.status}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                            {order.attributes.orderedByName}
                          </td>
                          <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                            {new Date(order.attributes.createdAt).toLocaleDateString()}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
