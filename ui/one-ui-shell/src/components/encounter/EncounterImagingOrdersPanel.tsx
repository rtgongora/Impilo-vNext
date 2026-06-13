"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Loader2, Plus, ScanLine } from "lucide-react";
import { useCreateLabOrder, useLabOrders } from "@/hooks/queries/useLabOrders";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";

interface EncounterImagingOrdersPanelProps {
  patientId: string;
  encounterId: string;
  patientCpid?: string;
  disabled?: boolean;
}

const PRIORITIES = ["ROUTINE", "URGENT", "STAT"] as const;
const MODALITIES = ["XRAY", "CT", "MRI", "ULTRASOUND", "MAMMOGRAPHY"] as const;

export function EncounterImagingOrdersPanel({
  patientId,
  encounterId,
  patientCpid,
  disabled = false,
}: EncounterImagingOrdersPanelProps) {
  const { user } = useAuthStore();
  const facility = useFacilityStore((state) => state.facility);
  const { data, isLoading } = useLabOrders(patientId);
  const createOrder = useCreateLabOrder();

  const [studyName, setStudyName] = useState("");
  const [modality, setModality] = useState<(typeof MODALITIES)[number]>("XRAY");
  const [priority, setPriority] = useState<(typeof PRIORITIES)[number]>("ROUTINE");
  const [clinicalNotes, setClinicalNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [lastOrderId, setLastOrderId] = useState<string | null>(null);

  const encounterOrders = useMemo(() => {
    return (data?.data ?? []).filter((order) => {
      const attrs = order.attributes as Record<string, unknown>;
      const linked =
        attrs.encounterId ?? attrs.encounter_id ?? (order as { encounter_id?: string }).encounter_id;
      const category = String(attrs.category ?? "").toUpperCase();
      const isImaging =
        category.includes("IMAG") || category.includes("RADIO") || String(attrs.test_name ?? "").includes("IMAG");
      return String(linked ?? "") === encounterId && isImaging;
    });
  }, [data?.data, encounterId]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (disabled || !facility?.id) return;

    setError(null);
    setSuccess(null);

    const orderedBy = user?.id ?? "";
    if (!orderedBy) {
      setError("Provider identity is required to place an imaging order.");
      return;
    }
    if (!studyName.trim()) {
      setError("Enter a study name before submitting.");
      return;
    }

    try {
      const response = await createOrder.mutateAsync({
        patientId,
        encounterId,
        testName: studyName.trim(),
        testCode: `${modality}-${studyName.trim().replace(/\s+/g, "_").toUpperCase()}`,
        category: "IMAGING",
        priority,
        clinicalNotes: clinicalNotes.trim() || undefined,
        facilityId: facility.id,
        orderedBy,
        orderedByName: user?.displayName ?? user?.email ?? orderedBy,
        patientCpid,
        pctEncounterRef: encounterId,
      });
      const createdId = response.data?.id ?? null;
      setLastOrderId(createdId);
      setStudyName("");
      setModality("XRAY");
      setPriority("ROUTINE");
      setClinicalNotes("");
      setSuccess("Imaging order submitted — correlate to PACS study when available.");
    } catch {
      setError("Failed to place imaging order. Verify BFF and OROS availability.");
    }
  }

  return (
    <section
      className="rounded-xl border border-sky-200 bg-card p-4 shadow-sm"
      data-testid="encounter-imaging-orders-panel"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <ScanLine className="h-4 w-4 text-sky-600" />
          <h3 className="text-sm font-semibold text-foreground">Encounter imaging orders</h3>
        </div>
        <Link
          href={`/ehr/${patientId}/orders?encounterId=${encodeURIComponent(encounterId)}&lane=IMAGING`}
          className="text-xs font-medium text-primary hover:underline"
        >
          Open imaging lane
        </Link>
      </div>

      <p className="mt-1 text-xs text-muted-foreground">
        Typed BFF write via <code className="rounded bg-neutral-100 px-1">POST /internal/v1/lab-orders</code> with
        category IMAGING → OROS IMAGING order type.
      </p>

      {isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading orders…
        </div>
      ) : (
        <ul className="mt-3 space-y-1 text-sm text-foreground">
          {encounterOrders.length === 0 ? (
            <li className="text-muted-foreground">No imaging orders linked to this encounter yet.</li>
          ) : (
            encounterOrders.map((order) => {
              const attrs = order.attributes as Record<string, unknown>;
              const name = String(attrs.testName ?? attrs.test_name ?? "Imaging study");
              const status = String(attrs.status ?? "ORDERED");
              return (
                <li key={order.id} className="flex justify-between gap-2 rounded-lg bg-background px-3 py-2">
                  <span>{name}</span>
                  <span className="flex items-center gap-2">
                    <Link
                      href={`/ehr/${patientId}/imaging/viewer?orderId=${encodeURIComponent(order.id)}`}
                      className="text-xs font-medium text-primary hover:underline"
                    >
                      View study
                    </Link>
                    <span className="text-xs font-medium uppercase text-muted-foreground">{status}</span>
                  </span>
                </li>
              );
            })
          )}
        </ul>
      )}

      {!disabled ? (
        <form onSubmit={(e) => void handleSubmit(e)} className="mt-4 space-y-3 border-t border-border pt-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="sm:col-span-2">
              <label className="mb-1 block text-xs font-medium text-muted-foreground" htmlFor="encounter-imaging-study">
                Study / procedure
              </label>
              <input
                id="encounter-imaging-study"
                value={studyName}
                onChange={(e) => setStudyName(e.target.value)}
                placeholder="e.g. Chest X-ray PA view"
                className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground" htmlFor="encounter-imaging-modality">
                Modality
              </label>
              <select
                id="encounter-imaging-modality"
                value={modality}
                onChange={(e) => setModality(e.target.value as (typeof MODALITIES)[number])}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              >
                {MODALITIES.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground" htmlFor="encounter-imaging-priority">
                Priority
              </label>
              <select
                id="encounter-imaging-priority"
                value={priority}
                onChange={(e) => setPriority(e.target.value as (typeof PRIORITIES)[number])}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              >
                {PRIORITIES.map((level) => (
                  <option key={level} value={level}>
                    {level}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground" htmlFor="encounter-imaging-notes">
                Clinical indication
              </label>
              <input
                id="encounter-imaging-notes"
                value={clinicalNotes}
                onChange={(e) => setClinicalNotes(e.target.value)}
                placeholder="Brief indication"
                className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              />
            </div>
          </div>

          {error ? <p className="text-xs text-red-600">{error}</p> : null}
          {success ? <p className="text-xs text-primary-hover">{success}</p> : null}
          {lastOrderId ? (
            <Link
              href={`/ehr/${patientId}/imaging?orderId=${encodeURIComponent(lastOrderId)}`}
              className="inline-flex text-xs font-medium text-primary hover:underline"
            >
              Correlate order to PACS study →
            </Link>
          ) : null}

          <button
            type="submit"
            disabled={createOrder.isPending}
            className="inline-flex items-center gap-1.5 rounded-lg bg-sky-600 px-3 py-2 text-xs font-medium text-white hover:bg-sky-700 disabled:opacity-50"
          >
            {createOrder.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
            Place imaging order
          </button>
        </form>
      ) : null}
    </section>
  );
}
