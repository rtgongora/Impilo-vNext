"use client";

/**
 * Sign-off capture for the two ends of a movement:
 *   • PICKUP   — collection sign-off (who released the cargo at origin)
 *   • DELIVERY — drop-off sign-off (who received it at destination)
 *
 * Records a proof on the mission (auditable) at the given stage. The drop-off
 * sign-off also marks the mission delivered. Wired to the real proof endpoint,
 * which now binds the client payload (proof_method/proof_stage aliases).
 */

import { useState } from "react";
import { Loader2, PenLine, PackageCheck } from "lucide-react";
import { useRecordProof } from "@/hooks/useNhume";

const METHODS = [
  { value: "RECIPIENT_SIGNATURE", label: "Signature" },
  { value: "OTP", label: "OTP code" },
  { value: "FACILITY_STAMP", label: "Facility stamp" },
  { value: "PHOTO", label: "Photo" },
  { value: "PROVIDER_CONFIRMATION", label: "Provider confirmation" },
];

export function SignOffPanel({
  deliveryId,
  stage,
  onDone,
}: {
  deliveryId: string;
  stage: "PICKUP" | "DELIVERY";
  onDone?: () => void;
}) {
  const proof = useRecordProof(deliveryId);
  const [method, setMethod] = useState("RECIPIENT_SIGNATURE");
  const [who, setWho] = useState("");
  const [reference, setReference] = useState("");
  const [error, setError] = useState<string | null>(null);

  const isPickup = stage === "PICKUP";
  const whoLabel = isPickup ? "Released by (name / role at origin)" : "Received by (name / role at destination)";

  async function confirm() {
    setError(null);
    if (!who.trim()) {
      setError(isPickup ? "Record who released the cargo." : "Record who received the cargo.");
      return;
    }
    try {
      await proof.mutateAsync({
        method,
        proof_stage: stage,
        mark_delivered: !isPickup, // drop-off closes the mission
        captured_by: who.trim(),
        recipient_name: who.trim(),
        ...(method === "OTP" ? { otp_code: reference.trim() || undefined } : { signature_uri: reference.trim() || undefined }),
        notes: isPickup ? "Collection sign-off" : "Drop-off sign-off",
      });
      setWho("");
      setReference("");
      onDone?.();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Sign-off was rejected — check status and role.");
    }
  }

  return (
    <div className="rounded-xl border border-border bg-background p-4">
      <div className="mb-3 flex items-center gap-2">
        {isPickup ? <PenLine className="h-4 w-4 text-teal-600" /> : <PackageCheck className="h-4 w-4 text-teal-600" />}
        <h4 className="text-sm font-semibold text-foreground">
          {isPickup ? "Collection sign-off" : "Drop-off sign-off"}
        </h4>
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="text-xs text-muted-foreground sm:col-span-1">
          Method
          <select value={method} onChange={(e) => setMethod(e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm">
            {METHODS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </label>
        <label className="text-xs text-muted-foreground sm:col-span-2">
          {whoLabel}
          <input value={who} onChange={(e) => setWho(e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="name / role" />
        </label>
        <label className="text-xs text-muted-foreground sm:col-span-3">
          {method === "OTP" ? "OTP code" : "Signature / evidence reference"}
          <input value={reference} onChange={(e) => setReference(e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder={method === "OTP" ? "e.g. 1234" : "e.g. signature:abc.png"} />
        </label>
      </div>
      {error ? <p className="mt-2 rounded-lg bg-danger-soft px-3 py-2 text-xs text-danger">{error}</p> : null}
      <button
        type="button"
        onClick={confirm}
        disabled={proof.isPending}
        className="mt-3 inline-flex items-center gap-2 rounded-xl bg-teal-600 px-3 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-60"
      >
        {proof.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        {isPickup ? "Confirm collection" : "Confirm drop-off & mark delivered"}
      </button>
    </div>
  );
}
