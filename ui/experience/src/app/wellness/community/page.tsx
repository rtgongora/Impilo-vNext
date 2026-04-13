"use client";

/**
 * Community management — community health units & outreach visits (clinical plane).
 * Route: /wellness/community
 */

import { Fragment, useMemo, useState } from "react";
import Link from "next/link";
import {
  Building2,
  CalendarClock,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Loader2,
  Play,
  Plus,
  Stethoscope,
  Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCommunityUnits,
  useOutreachVisits,
  useCreateCommunityUnit,
  useCreateOutreachVisit,
  useStartVisit,
  useCompleteVisit,
} from "@/hooks/queries/useCommunityService";

type Tab = "units" | "visits";

function unwrapList(res: unknown): Record<string, unknown>[] {
  if (!res) return [];
  if (Array.isArray(res)) return res as Record<string, unknown>[];
  const d = (res as { data?: unknown }).data;
  if (Array.isArray(d)) return d as Record<string, unknown>[];
  return [];
}

function defaultTenantId(): string {
  if (typeof window !== "undefined") {
    const t = sessionStorage.getItem("exp:tenant_id");
    if (t && /^[0-9a-f-]{36}$/i.test(t)) return t;
  }
  return "00000000-0000-0000-0000-000000000001";
}

const VISIT_STATUS_STYLES: Record<string, string> = {
  SCHEDULED: "bg-impilo-100 text-impilo-700",
  IN_PROGRESS: "bg-amber-100 text-amber-800",
  COMPLETED: "bg-green-100 text-green-800",
};

export default function WellnessCommunityPage() {
  const [tab, setTab] = useState<Tab>("units");
  const tenantId = useMemo(() => defaultTenantId(), []);

  const { data: unitsPayload, isLoading: unitsLoading, error: unitsError } = useCommunityUnits();
  const { data: visitsPayload, isLoading: visitsLoading, error: visitsError } = useOutreachVisits(
    tenantId,
    null,
  );

  const createUnit = useCreateCommunityUnit();
  const createVisit = useCreateOutreachVisit();
  const startVisit = useStartVisit();
  const completeVisit = useCompleteVisit();

  const units = unwrapList(unitsPayload);
  const visits = unwrapList(visitsPayload);

  const [expandedUnitId, setExpandedUnitId] = useState<string | null>(null);
  const [showUnitForm, setShowUnitForm] = useState(false);
  const [showVisitForm, setShowVisitForm] = useState(false);

  const [unitForm, setUnitForm] = useState({
    name: "",
    unitType: "",
    facilityId: "",
    province: "",
    district: "",
    catchmentPopulation: "",
  });

  const [visitForm, setVisitForm] = useState({
    unitId: "",
    patientCpid: "",
    providerId: "",
    visitType: "",
    scheduledAt: "",
  });

  const handleCreateUnit = () => {
    if (!unitForm.name.trim() || !unitForm.unitType.trim()) return;
    const body: Record<string, unknown> = {
      tenantId,
      name: unitForm.name.trim(),
      unitType: unitForm.unitType.trim(),
      province: unitForm.province.trim() || undefined,
      district: unitForm.district.trim() || undefined,
    };
    if (unitForm.facilityId.trim()) body.facilityId = unitForm.facilityId.trim();
    if (unitForm.catchmentPopulation.trim()) {
      const n = Number.parseInt(unitForm.catchmentPopulation, 10);
      if (!Number.isNaN(n)) body.catchmentPopulation = n;
    }
    createUnit.mutate(body, {
      onSuccess: () => {
        setShowUnitForm(false);
        setUnitForm({
          name: "",
          unitType: "",
          facilityId: "",
          province: "",
          district: "",
          catchmentPopulation: "",
        });
      },
    });
  };

  const handleCreateVisit = () => {
    if (
      !visitForm.unitId.trim() ||
      !visitForm.patientCpid.trim() ||
      !visitForm.providerId.trim() ||
      !visitForm.visitType.trim()
    ) {
      return;
    }
    const body: Record<string, unknown> = {
      tenantId,
      unitId: visitForm.unitId.trim(),
      patientCpid: visitForm.patientCpid.trim(),
      providerId: visitForm.providerId.trim(),
      visitType: visitForm.visitType.trim(),
    };
    if (visitForm.scheduledAt) {
      body.scheduledAt = new Date(visitForm.scheduledAt).toISOString();
    }
    createVisit.mutate(body, {
      onSuccess: () => {
        setShowVisitForm(false);
        setVisitForm({
          unitId: "",
          patientCpid: "",
          providerId: "",
          visitType: "",
          scheduledAt: "",
        });
      },
    });
  };

  const inputClass =
    "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500";

  return (
    <AppLayout>
      <PageShell
        title="Community Management"
        subtitle="Community health units and outreach visits"
        icon={<Users className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/wellness"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            ← Back to Wellness Hub
          </Link>
        </div>

        <div className="flex gap-1 mb-6 border-b border-gray-200">
          <button
            type="button"
            onClick={() => setTab("units")}
            className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              tab === "units"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            <Building2 className="w-4 h-4" /> Community Units
          </button>
          <button
            type="button"
            onClick={() => setTab("visits")}
            className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              tab === "visits"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            <ClipboardList className="w-4 h-4" /> Outreach Visits
          </button>
        </div>

        {tab === "units" && (
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-3 flex-wrap">
              <h3 className="text-lg font-semibold text-gray-900">Community Units</h3>
              <button
                type="button"
                onClick={() => setShowUnitForm(!showUnitForm)}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                {showUnitForm ? "Cancel" : "New Unit"}
              </button>
            </div>

            {showUnitForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
                <h4 className="text-sm font-semibold text-gray-700">Create community unit</h4>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <input
                    type="text"
                    placeholder="Name *"
                    value={unitForm.name}
                    onChange={(e) => setUnitForm({ ...unitForm, name: e.target.value })}
                    className={inputClass}
                  />
                  <input
                    type="text"
                    placeholder="Type * (e.g. CHU, mobile_clinic)"
                    value={unitForm.unitType}
                    onChange={(e) => setUnitForm({ ...unitForm, unitType: e.target.value })}
                    className={inputClass}
                  />
                  <input
                    type="text"
                    placeholder="Facility UUID (optional)"
                    value={unitForm.facilityId}
                    onChange={(e) => setUnitForm({ ...unitForm, facilityId: e.target.value })}
                    className={inputClass}
                  />
                  <input
                    type="text"
                    placeholder="Province"
                    value={unitForm.province}
                    onChange={(e) => setUnitForm({ ...unitForm, province: e.target.value })}
                    className={inputClass}
                  />
                  <input
                    type="text"
                    placeholder="District"
                    value={unitForm.district}
                    onChange={(e) => setUnitForm({ ...unitForm, district: e.target.value })}
                    className={inputClass}
                  />
                  <input
                    type="number"
                    placeholder="Catchment population"
                    value={unitForm.catchmentPopulation}
                    onChange={(e) =>
                      setUnitForm({ ...unitForm, catchmentPopulation: e.target.value })
                    }
                    className={inputClass}
                  />
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={handleCreateUnit}
                    disabled={createUnit.isPending || !unitForm.name.trim() || !unitForm.unitType.trim()}
                    className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-teal-600 text-white rounded-lg disabled:opacity-50 hover:bg-teal-700"
                  >
                    {createUnit.isPending ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <Plus className="w-4 h-4" />
                    )}
                    Save unit
                  </button>
                  {createUnit.isError && (
                    <span className="text-sm text-red-600">Could not create unit.</span>
                  )}
                </div>
              </div>
            )}

            {unitsLoading ? (
              <div className="flex items-center gap-2 text-gray-600 py-12">
                <Loader2 className="w-6 h-6 animate-spin" /> Loading units…
              </div>
            ) : unitsError ? (
              <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg p-4">
                Failed to load community units.
              </p>
            ) : units.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center text-gray-500 text-sm">
                No community units yet. Create one to get started.
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="w-8 px-2 py-3" />
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Facility</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">District</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Catchment</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {units.map((u) => {
                      const uid = String(u.unitId ?? u.unit_id ?? "");
                      const open = expandedUnitId === uid;
                      return (
                        <Fragment key={uid}>
                          <tr className="hover:bg-gray-50">
                            <td className="px-2 py-3">
                              <button
                                type="button"
                                aria-label={open ? "Collapse" : "Expand"}
                                onClick={() => setExpandedUnitId(open ? null : uid)}
                                className="p-1 rounded text-gray-500 hover:bg-gray-100"
                              >
                                {open ? (
                                  <ChevronDown className="w-4 h-4" />
                                ) : (
                                  <ChevronRight className="w-4 h-4" />
                                )}
                              </button>
                            </td>
                            <td className="px-4 py-3 font-medium text-gray-900">{String(u.name ?? "")}</td>
                            <td className="px-4 py-3 text-gray-600">{String(u.unitType ?? u.unit_type ?? "")}</td>
                            <td className="px-4 py-3 font-mono text-xs text-gray-500">
                              {(u.facilityId ?? u.facility_id)
                                ? String(u.facilityId ?? u.facility_id).slice(0, 8) + "…"
                                : "—"}
                            </td>
                            <td className="px-4 py-3 text-gray-600">{String(u.district ?? "—")}</td>
                            <td className="px-4 py-3 text-right text-gray-700">
                              {u.catchmentPopulation != null || u.catchment_population != null
                                ? String(u.catchmentPopulation ?? u.catchment_population)
                                : "—"}
                            </td>
                            <td className="px-4 py-3">
                              <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-green-100 text-green-800 font-medium">
                                {String(u.status ?? "ACTIVE")}
                              </span>
                            </td>
                          </tr>
                          {open && (
                            <tr className="bg-gray-50">
                              <td colSpan={7} className="px-6 py-3 text-xs text-gray-600 space-y-1">
                                <div>
                                  <span className="font-semibold text-gray-700">Unit ID:</span>{" "}
                                  <span className="font-mono">{uid}</span>
                                </div>
                                <div>
                                  <span className="font-semibold text-gray-700">Province:</span>{" "}
                                  {String(u.province ?? "—")}
                                </div>
                                <div>
                                  <span className="font-semibold text-gray-700">Ward:</span>{" "}
                                  {String(u.ward ?? "—")}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {tab === "visits" && (
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-3 flex-wrap">
              <h3 className="text-lg font-semibold text-gray-900">Outreach Visits</h3>
              <button
                type="button"
                onClick={() => setShowVisitForm(!showVisitForm)}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                {showVisitForm ? "Cancel" : "New Visit"}
              </button>
            </div>

            {showVisitForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
                <h4 className="text-sm font-semibold text-gray-700">Schedule outreach visit</h4>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <select
                    value={visitForm.unitId}
                    onChange={(e) => setVisitForm({ ...visitForm, unitId: e.target.value })}
                    className={inputClass}
                  >
                    <option value="">Select unit *</option>
                    {units.map((u) => {
                      const uid = String(u.unitId ?? u.unit_id ?? "");
                      return (
                        <option key={uid} value={uid}>
                          {String(u.name ?? uid).slice(0, 48)} ({uid.slice(0, 8)}…)
                        </option>
                      );
                    })}
                  </select>
                  <input
                    type="text"
                    placeholder="Patient CPID *"
                    value={visitForm.patientCpid}
                    onChange={(e) => setVisitForm({ ...visitForm, patientCpid: e.target.value })}
                    className={inputClass}
                  />
                  <input
                    type="text"
                    placeholder="Provider ID *"
                    value={visitForm.providerId}
                    onChange={(e) => setVisitForm({ ...visitForm, providerId: e.target.value })}
                    className={inputClass}
                  />
                  <input
                    type="text"
                    placeholder="Visit type * (e.g. home_visit)"
                    value={visitForm.visitType}
                    onChange={(e) => setVisitForm({ ...visitForm, visitType: e.target.value })}
                    className={inputClass}
                  />
                  <input
                    type="datetime-local"
                    value={visitForm.scheduledAt}
                    onChange={(e) => setVisitForm({ ...visitForm, scheduledAt: e.target.value })}
                    className={inputClass}
                  />
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={handleCreateVisit}
                    disabled={createVisit.isPending}
                    className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-teal-600 text-white rounded-lg disabled:opacity-50 hover:bg-teal-700"
                  >
                    {createVisit.isPending ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <CalendarClock className="w-4 h-4" />
                    )}
                    Schedule visit
                  </button>
                  {createVisit.isError && (
                    <span className="text-sm text-red-600">Could not create visit.</span>
                  )}
                </div>
                {units.length === 0 && (
                  <p className="text-xs text-amber-700">
                    Create a community unit first to enable the unit selector.
                  </p>
                )}
              </div>
            )}

            {visitsLoading ? (
              <div className="flex items-center gap-2 text-gray-600 py-12">
                <Loader2 className="w-6 h-6 animate-spin" /> Loading visits…
              </div>
            ) : visitsError ? (
              <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg p-4">
                Failed to load outreach visits.
              </p>
            ) : visits.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center text-gray-500 text-sm">
                No outreach visits scheduled.
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Patient CPID</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Provider</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Visit type</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Scheduled</th>
                      <th className="text-right px-4 py-3 font-medium text-gray-600">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {visits.map((v) => {
                      const vid = String(v.visitId ?? v.visit_id ?? "");
                      const status = String(v.status ?? "SCHEDULED").toUpperCase();
                      const badge = VISIT_STATUS_STYLES[status] ?? "bg-gray-100 text-gray-700";
                      const sched = v.scheduledAt ?? v.scheduled_at;
                      return (
                        <tr key={vid} className="hover:bg-gray-50">
                          <td className="px-4 py-3 font-mono text-xs text-gray-900">
                            {String(v.patientCpid ?? v.patient_cpid ?? "—")}
                          </td>
                          <td className="px-4 py-3 text-gray-700">
                            {String(v.providerId ?? v.provider_id ?? "—")}
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            {String(v.visitType ?? v.visit_type ?? "—")}
                          </td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${badge}`}
                            >
                              {status}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-gray-600 whitespace-nowrap">
                            {sched
                              ? new Date(String(sched)).toLocaleString(undefined, {
                                  dateStyle: "medium",
                                  timeStyle: "short",
                                })
                              : "—"}
                          </td>
                          <td className="px-4 py-3 text-right space-x-2">
                            {status === "SCHEDULED" && (
                              <button
                                type="button"
                                disabled={startVisit.isPending}
                                onClick={() => startVisit.mutate({ id: vid })}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-lg bg-amber-50 text-amber-800 border border-amber-200 hover:bg-amber-100 disabled:opacity-50"
                              >
                                <Play className="w-3 h-3" /> Start
                              </button>
                            )}
                            {status === "IN_PROGRESS" && (
                              <button
                                type="button"
                                disabled={completeVisit.isPending}
                                onClick={() => completeVisit.mutate({ id: vid, body: {} })}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-lg bg-green-50 text-green-800 border border-green-200 hover:bg-green-100 disabled:opacity-50"
                              >
                                <Stethoscope className="w-3 h-3" /> Complete
                              </button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
