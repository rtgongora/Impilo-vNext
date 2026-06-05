"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Loader2, Microscope, Plus } from "lucide-react";
import { useCreateLabOrder, useLabOrders } from "@/hooks/queries/useLabOrders";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";

interface EncounterLabOrdersPanelProps {
  patientId: string;
  encounterId: string;
  patientCpid?: string;
  disabled?: boolean;
}

const PRIORITIES = ["ROUTINE", "URGENT", "STAT"] as const;

export function EncounterLabOrdersPanel({
  patientId,
  encounterId,
  patientCpid,
  disabled = false,
}: EncounterLabOrdersPanelProps) {
  const { user } = useAuthStore();
  const facility = useFacilityStore((state) => state.facility);
  const { data, isLoading } = useLabOrders(patientId);
  const createOrder = useCreateLabOrder();

  const [testName, setTestName] = useState("");
  const [testCode, setTestCode] = useState("");
  const [priority, setPriority] = useState<(typeof PRIORITIES)[number]>("ROUTINE");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const encounterOrders = useMemo(() => {
    return (data?.data ?? []).filter((order) => {
      const attrs = order.attributes as Record<string, unknown>;
      const linked =
        attrs.encounterId ?? attrs.encounter_id ?? (order as { encounter_id?: string }).encounter_id;
      return String(linked ?? "") === encounterId;
    });
  }, [data?.data, encounterId]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (disabled || !facility?.id) return;

    setError(null);
    setSuccess(null);

    const orderedBy = user?.id ?? "";
    if (!orderedBy) {
      setError("Provider identity is required to place a lab order.");
      return;
    }
    if (!testName.trim()) {
      setError("Enter a test name before submitting.");
      return;
    }

    try {
      await createOrder.mutateAsync({
        patientId,
        encounterId,
        testName: testName.trim(),
        testCode: testCode.trim() || testName.trim(),
        category: "LABORATORY",
        priority,
        facilityId: facility.id,
        orderedBy,
        orderedByName: user?.displayName ?? user?.email ?? orderedBy,
        patientCpid,
        pctEncounterRef: encounterId,
      });
      setTestName("");
      setTestCode("");
      setPriority("ROUTINE");
      setSuccess("Lab order submitted for this encounter.");
    } catch {
      setError("Failed to place lab order. Verify BFF and OROS availability.");
    }
  }

  return (
    <section
      className="rounded-xl border border-purple-200 bg-white p-4 shadow-sm"
      data-testid="encounter-lab-orders-panel"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Microscope className="h-4 w-4 text-purple-600" />
          <h3 className="text-sm font-semibold text-slate-900">Encounter lab orders</h3>
        </div>
        <Link
          href={`/ehr/${patientId}/orders?encounterId=${encodeURIComponent(encounterId)}`}
          className="text-xs font-medium text-impilo-600 hover:underline"
        >
          Open full orders workspace
        </Link>
      </div>

      <p className="mt-1 text-xs text-slate-600">
        Orders are episode-scoped to encounter <span className="font-mono">{encounterId}</span> via typed BFF
        <code className="mx-1 rounded bg-slate-100 px-1">POST /internal/v1/lab-orders</code>.
      </p>

      {isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading orders…
        </div>
      ) : (
        <ul className="mt-3 space-y-1 text-sm text-slate-700">
          {encounterOrders.length === 0 ? (
            <li className="text-slate-500">No lab orders linked to this encounter yet.</li>
          ) : (
            encounterOrders.map((order) => {
              const attrs = order.attributes as Record<string, unknown>;
              const name = String(attrs.testName ?? attrs.test_name ?? "Lab test");
              const status = String(attrs.status ?? "ORDERED");
              return (
                <li key={order.id} className="flex justify-between gap-2 rounded-lg bg-slate-50 px-3 py-2">
                  <span>{name}</span>
                  <span className="text-xs font-medium uppercase text-slate-500">{status}</span>
                </li>
              );
            })
          )}
        </ul>
      )}

      {!disabled ? (
        <form onSubmit={(e) => void handleSubmit(e)} className="mt-4 space-y-3 border-t border-slate-100 pt-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="sm:col-span-2">
              <label className="mb-1 block text-xs font-medium text-slate-600" htmlFor="encounter-lab-test-name">
                Test name
              </label>
              <input
                id="encounter-lab-test-name"
                value={testName}
                onChange={(e) => setTestName(e.target.value)}
                placeholder="e.g. Full Blood Count"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600" htmlFor="encounter-lab-priority">
                Priority
              </label>
              <select
                id="encounter-lab-priority"
                value={priority}
                onChange={(e) => setPriority(e.target.value as (typeof PRIORITIES)[number])}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
              >
                {PRIORITIES.map((level) => (
                  <option key={level} value={level}>
                    {level}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600" htmlFor="encounter-lab-test-code">
              Test code (optional)
            </label>
            <input
              id="encounter-lab-test-code"
              value={testCode}
              onChange={(e) => setTestCode(e.target.value)}
              placeholder="e.g. FBC"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
            />
          </div>

          {error ? <p className="text-xs text-red-600">{error}</p> : null}
          {success ? <p className="text-xs text-emerald-700">{success}</p> : null}

          <button
            type="submit"
            disabled={createOrder.isPending}
            className="inline-flex items-center gap-1.5 rounded-lg bg-purple-600 px-3 py-2 text-xs font-medium text-white hover:bg-purple-700 disabled:opacity-50"
          >
            {createOrder.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
            Place lab order
          </button>
        </form>
      ) : null}
    </section>
  );
}
