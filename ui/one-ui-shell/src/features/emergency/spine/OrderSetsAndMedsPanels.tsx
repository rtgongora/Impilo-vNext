"use client";

import { useState } from "react";
import { bffErrorMessage } from "@/lib/bff-errors";
import type {
  EmergencyMedicationRow,
  EmergencyOrderSetItemRow,
  EmergencyOrderSetRow,
} from "@/hooks/queries/useEmergencyEpisode";

const BUNDLE_PRESETS = [
  {
    code: "SEPSIS_SIX_BUNDLE",
    name: "Sepsis Six",
    items: [
      { orderCode: "LACTATE", orderLabel: "Serum lactate", orderType: "LAB" },
      { orderCode: "BLOOD_CULTURE", orderLabel: "Blood cultures", orderType: "LAB" },
      { orderCode: "IV_ABX", orderLabel: "IV antibiotics", orderType: "MEDICATION" },
      { orderCode: "IV_FLUIDS", orderLabel: "IV fluids", orderType: "MEDICATION" },
      { orderCode: "O2", orderLabel: "Oxygen", orderType: "PROCEDURE" },
      { orderCode: "URINE_OUTPUT", orderLabel: "Measure urine output", orderType: "PROCEDURE" },
    ],
  },
  {
    code: "TRAUMA_PRIMARY_SURVEY",
    name: "Trauma primary survey orders",
    items: [
      { orderCode: "CXR", orderLabel: "Chest X-ray", orderType: "IMAGING" },
      { orderCode: "EFAST", orderLabel: "eFAST ultrasound", orderType: "IMAGING" },
      { orderCode: "TYPE_CROSS", orderLabel: "Type and crossmatch", orderType: "LAB" },
    ],
  },
] as const;

export interface OrderSetsPanelProps {
  sets: EmergencyOrderSetRow[] | undefined;
  isLoading: boolean;
  isError: boolean;
  actor: string;
  onInvoke: (body: {
    orderSetCode: string;
    orderSetName?: string;
    invokedBy: string;
    items?: { orderCode: string; orderLabel?: string; orderType?: string }[];
  }) => Promise<unknown>;
  onOrderItem: (body: { itemId: string; orderedBy: string }) => Promise<unknown>;
  onDeclineItem: (body: { itemId: string; declinedBy: string; declineReason: string }) => Promise<unknown>;
  isMutating?: boolean;
}

export function OrderSetsPanel({
  sets,
  isLoading,
  isError,
  actor,
  onInvoke,
  onOrderItem,
  onDeclineItem,
  isMutating,
}: OrderSetsPanelProps) {
  const [preset, setPreset] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [declineReasons, setDeclineReasons] = useState<Record<string, string>>({});

  const run = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(bffErrorMessage(e, "order set action"));
    }
  };

  return (
    <div className="space-y-3 rounded border p-4" data-testid="order-sets-panel">
      <h3 className="text-sm font-semibold">Order sets</h3>
      <p className="text-xs text-muted-foreground">
        Invoke a bundle, then order or decline each item. Decline requires a reason.
      </p>

      <div className="flex flex-wrap gap-2">
        <select
          className="rounded border px-2 py-1 text-sm"
          value={preset}
          onChange={(e) => setPreset(Number(e.target.value))}
          data-testid="order-set-preset"
          aria-label="Order set preset"
        >
          {BUNDLE_PRESETS.map((p, i) => (
            <option key={p.code} value={i}>
              {p.name}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
          disabled={!actor || isMutating}
          data-testid="order-set-invoke"
          onClick={() => {
            const chosen = BUNDLE_PRESETS[preset];
            void run(() =>
              onInvoke({
                orderSetCode: chosen.code,
                orderSetName: chosen.name,
                invokedBy: actor,
                items: chosen.items.map((item) => ({ ...item })),
              }),
            );
          }}
        >
          Invoke bundle
        </button>
      </div>

      {isLoading ? <p className="text-sm text-muted-foreground">Loading order sets…</p> : null}
      {isError ? (
        <p className="text-sm text-danger" data-testid="order-sets-unreadable">
          Order sets could not be read.
        </p>
      ) : null}

      <ul className="space-y-3">
        {(sets ?? []).map((set) => (
          <li key={String(set.instance_id)} className="rounded border border-border p-3 text-sm">
            <p className="font-medium">
              {String(set.order_set_name ?? set.order_set_code)}{" "}
              <span className="text-muted-foreground">({String(set.status ?? "")})</span>
            </p>
            <ul className="mt-2 space-y-2">
              {((set.items as EmergencyOrderSetItemRow[] | undefined) ?? []).map((item) => {
                const itemId = String(item.item_id);
                const pending = String(item.status ?? "") === "PENDING" || !item.status;
                return (
                  <li key={itemId} className="flex flex-wrap items-center gap-2">
                    <span className="flex-1">
                      {String(item.order_label ?? item.order_code)} — {String(item.status ?? "PENDING")}
                    </span>
                    {pending ? (
                      <>
                        <button
                          type="button"
                          className="rounded border px-2 py-0.5 text-xs disabled:opacity-50"
                          disabled={isMutating}
                          onClick={() => run(() => onOrderItem({ itemId, orderedBy: actor }))}
                        >
                          Order
                        </button>
                        <input
                          className="w-40 rounded border px-1 py-0.5 text-xs"
                          placeholder="Decline reason"
                          value={declineReasons[itemId] ?? ""}
                          onChange={(e) =>
                            setDeclineReasons((prev) => ({ ...prev, [itemId]: e.target.value }))
                          }
                        />
                        <button
                          type="button"
                          className="rounded border px-2 py-0.5 text-xs disabled:opacity-50"
                          disabled={isMutating || !(declineReasons[itemId] ?? "").trim()}
                          onClick={() =>
                            run(() =>
                              onDeclineItem({
                                itemId,
                                declinedBy: actor,
                                declineReason: (declineReasons[itemId] ?? "").trim(),
                              }),
                            )
                          }
                        >
                          Decline
                        </button>
                      </>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          </li>
        ))}
      </ul>

      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export interface MedicationAdminPanelProps {
  medications: EmergencyMedicationRow[] | undefined;
  isLoading: boolean;
  isError: boolean;
  actor: string;
  onRecord: (body: {
    medicationCode: string;
    medicationName?: string;
    doseValue: number;
    doseUnit: string;
    route: string;
    administeredBy: string;
    witnessedBy?: string;
  }) => Promise<unknown>;
  isMutating?: boolean;
}

export function MedicationAdminPanel({
  medications,
  isLoading,
  isError,
  actor,
  onRecord,
  isMutating,
}: MedicationAdminPanelProps) {
  const [code, setCode] = useState("ADRENALINE");
  const [name, setName] = useState("Adrenaline");
  const [dose, setDose] = useState("1");
  const [unit, setUnit] = useState("mg");
  const [route, setRoute] = useState("IV");
  const [witness, setWitness] = useState("");
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="space-y-3 rounded border p-4" data-testid="medication-admin-panel">
      <h3 className="text-sm font-semibold">Medication administration</h3>
      <p className="text-xs text-muted-foreground">
        Dose is entered by the clinician — never computed here. Witness is optional unless pct policy requires it.
      </p>

      {isLoading ? <p className="text-sm text-muted-foreground">Loading medications…</p> : null}
      {isError ? (
        <p className="text-sm text-danger" data-testid="medications-unreadable">
          Medications could not be read.
        </p>
      ) : null}

      <ul className="space-y-1 text-sm">
        {(medications ?? []).map((med) => (
          <li key={String(med.administration_id)}>
            {String(med.medication_name ?? med.medication_code)} {String(med.dose_value)}
            {String(med.dose_unit)} {String(med.route)} — {String(med.administered_by)}
          </li>
        ))}
        {!isLoading && !isError && (medications ?? []).length === 0 ? (
          <li className="text-muted-foreground">No administrations recorded.</li>
        ) : null}
      </ul>

      <div className="grid gap-2 sm:grid-cols-3">
        <input
          className="rounded border px-2 py-1 text-sm"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="Code"
          data-testid="med-code"
        />
        <input
          className="rounded border px-2 py-1 text-sm"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Name"
          data-testid="med-name"
        />
        <input
          className="rounded border px-2 py-1 text-sm"
          value={dose}
          onChange={(e) => setDose(e.target.value)}
          placeholder="Dose"
          data-testid="med-dose"
        />
        <input
          className="rounded border px-2 py-1 text-sm"
          value={unit}
          onChange={(e) => setUnit(e.target.value)}
          placeholder="Unit"
          data-testid="med-unit"
        />
        <input
          className="rounded border px-2 py-1 text-sm"
          value={route}
          onChange={(e) => setRoute(e.target.value)}
          placeholder="Route"
          data-testid="med-route"
        />
        <input
          className="rounded border px-2 py-1 text-sm"
          value={witness}
          onChange={(e) => setWitness(e.target.value)}
          placeholder="Witness (optional)"
          data-testid="med-witness"
        />
      </div>
      <button
        type="button"
        className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
        disabled={!actor || !code.trim() || !dose.trim() || isMutating}
        data-testid="med-record"
        onClick={async () => {
          setError(null);
          try {
            await onRecord({
              medicationCode: code.trim(),
              medicationName: name.trim() || undefined,
              doseValue: Number(dose),
              doseUnit: unit.trim() || "mg",
              route: route.trim() || "IV",
              administeredBy: actor,
              witnessedBy: witness.trim() || undefined,
            });
          } catch (e) {
            setError(bffErrorMessage(e, "medication administration"));
          }
        }}
      >
        Record administration
      </button>
      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
