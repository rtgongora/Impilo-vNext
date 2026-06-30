"use client";

/**
 * Wallet · Profile & demographic correction.
 *
 * Shows the person's current demographics (from the wallet overview, which reads VITO) and lets
 * them submit a demographic CORRECTION REQUEST. High-trust fields are never overwritten inline:
 * the request routes to VITO's correction workflow for steward review (see CitizenWalletController
 * → VitoServiceClient.recordClientCorrection). The submission is audited.
 */

import { useState } from "react";
import { useWalletOverview, useSubmitCorrection } from "@/hooks/queries/useWallet";

const CORRECTABLE_FIELDS = [
  { key: "phone", label: "Phone number", lowRisk: true },
  { key: "address", label: "Address", lowRisk: true },
  { key: "givenName", label: "Given name", lowRisk: false },
  { key: "familyName", label: "Family name", lowRisk: false },
  { key: "dateOfBirth", label: "Date of birth", lowRisk: false },
] as const;

export default function WalletProfilePage() {
  const { data, isLoading } = useWalletOverview();
  const profile = ((data?.data?.identity ?? {}) as Record<string, unknown>).profile as
    | Record<string, unknown>
    | undefined;

  const submit = useSubmitCorrection();
  const [field, setField] = useState<string>("phone");
  const [requestedValue, setRequestedValue] = useState("");
  const [reason, setReason] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");

  const selected = CORRECTABLE_FIELDS.find((f) => f.key === field);
  const currentValue = profile ? String(profile[field] ?? "") : "";

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setDone(false);
    try {
      await submit.mutateAsync({ field, currentValue, requestedValue, reason: reason || undefined });
      setDone(true);
      setRequestedValue("");
      setReason("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not submit correction");
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-2">
      <div>
        <h1 className="text-xl font-semibold text-foreground">Profile & corrections</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Low-risk details can be corrected directly; identity fields are verified, so changes are
          submitted as a request and reviewed before they take effect.
        </p>
      </div>

      <section className="rounded-lg border border-border bg-card p-4">
        <h2 className="mb-2 text-sm font-medium text-foreground">Current details</h2>
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : profile ? (
          <dl className="grid grid-cols-2 gap-2 text-sm">
            {CORRECTABLE_FIELDS.map((f) => (
              <div key={f.key}>
                <dt className="text-xs text-muted-foreground">{f.label}</dt>
                <dd className="text-foreground">{String(profile[f.key] ?? "—")}</dd>
              </div>
            ))}
          </dl>
        ) : (
          <p className="text-sm text-muted-foreground">
            No profile on file yet. Once you have a Health ID your details will appear here.
          </p>
        )}
      </section>

      <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border border-border bg-card p-4">
        <h2 className="text-sm font-medium text-foreground">Request a correction</h2>

        <label className="block text-sm font-medium text-foreground">
          Field
          <select
            className="mt-1 w-full rounded border border-border px-2 py-1 text-sm"
            value={field}
            onChange={(e) => setField(e.target.value)}
          >
            {CORRECTABLE_FIELDS.map((f) => (
              <option key={f.key} value={f.key}>
                {f.label}
                {f.lowRisk ? "" : " (verified — needs review)"}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm font-medium text-foreground">
          Correct value
          <input
            type="text"
            required
            value={requestedValue}
            onChange={(e) => setRequestedValue(e.target.value)}
            className="mt-1 w-full rounded border border-border px-2 py-1 text-sm"
          />
        </label>

        <label className="block text-sm font-medium text-foreground">
          Reason (optional)
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="mt-1 w-full rounded border border-border px-2 py-1 text-sm"
            rows={2}
          />
        </label>

        {selected && !selected.lowRisk ? (
          <p className="text-xs text-amber-700">
            This is a verified identity field. Your request will be reviewed by a registration steward
            before it changes.
          </p>
        ) : null}

        {error ? <p className="text-sm text-danger">{error}</p> : null}
        {done ? (
          <p className="rounded border border-success/25 bg-success-soft p-2 text-sm text-emerald-950">
            Correction request submitted. You can track its status in your support requests.
          </p>
        ) : null}

        <button
          type="submit"
          disabled={submit.isPending}
          className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800 disabled:opacity-50"
        >
          {submit.isPending ? "Submitting…" : "Submit correction request"}
        </button>
      </form>
    </div>
  );
}
