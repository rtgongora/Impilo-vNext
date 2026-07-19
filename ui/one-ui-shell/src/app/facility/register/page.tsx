"use client";

/**
 * Register a new facility (FJ2 / D-L4/D-L5). Route: /facility/register
 *
 * A person registers a facility that is not yet on the platform. The applicant
 * is the session Health ID (server-side) — never sent in the body. Registration
 * requires a proven personal identity (≥LOA2); the registry silently blocks on a
 * credible duplicate and routes to a steward. The facility is not listed or
 * usable until the registry confirms it — this screen never fakes acceptance.
 */

import { useState } from "react";
import Link from "next/link";
import { Building2, CheckCircle2, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  facilityLifecycleErrorMessage,
  useRegisterFacility,
  type FacilityRegistrationInput,
} from "@/hooks/queries/useFacilityLifecycle";

const inputClass =
  "w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground";

const FACILITY_TYPES = [
  "HOSPITAL",
  "CLINIC",
  "HEALTH_CENTRE",
  "PHARMACY",
  "LABORATORY",
  "DIAGNOSTIC_CENTRE",
  "OTHER",
];

export default function FacilityRegisterPage() {
  const register = useRegisterFacility();

  const [form, setForm] = useState<FacilityRegistrationInput>({
    name: "",
    facilityType: "CLINIC",
    province: "",
    district: "",
    ownershipCategory: "",
    evidenceRef: "",
    notes: "",
  });
  const [error, setError] = useState("");
  const [receipt, setReceipt] = useState<{ caseRef?: string; note?: string } | null>(null);

  function set<K extends keyof FacilityRegistrationInput>(key: K, value: FacilityRegistrationInput[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (!form.name.trim()) {
      setError("Enter the facility name.");
      return;
    }
    if (!form.evidenceRef?.trim()) {
      setError("Provide a supporting evidence reference (e.g. a registration or licence document id).");
      return;
    }
    register.mutate(
      { ...form, name: form.name.trim(), evidenceRef: form.evidenceRef?.trim() },
      {
        onSuccess: (res) => setReceipt({ caseRef: res.caseRef, note: res.note }),
        onError: (err) =>
          setError(
            facilityLifecycleErrorMessage(err, "Your facility registration could not be submitted."),
          ),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Register a facility"
        subtitle="Add a health facility that is not yet on the platform"
      >
        <div className="mx-auto max-w-2xl space-y-6">
          {receipt && (
            <div
              className="flex items-start gap-2 rounded-lg border border-success/35 bg-success-soft p-4 text-sm"
              data-testid="facility-registration-receipt"
            >
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
              <div>
                <p className="font-medium text-foreground">Registration received</p>
                <p className="mt-1 text-muted-foreground">{receipt.note}</p>
                {receipt.caseRef && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    Reference: <span className="font-mono">{receipt.caseRef}</span>
                  </p>
                )}
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4 rounded-2xl border border-border bg-card p-5">
            <div>
              <label htmlFor="name" className="mb-1 block text-sm font-medium text-foreground">
                Facility name
              </label>
              <input
                id="name"
                type="text"
                value={form.name}
                onChange={(e) => set("name", e.target.value)}
                className={inputClass}
                required
              />
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="type" className="mb-1 block text-sm font-medium text-foreground">
                  Facility type
                </label>
                <select
                  id="type"
                  value={form.facilityType}
                  onChange={(e) => set("facilityType", e.target.value)}
                  className={inputClass}
                >
                  {FACILITY_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t.replace(/_/g, " ")}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="ownership" className="mb-1 block text-sm font-medium text-foreground">
                  Ownership (optional)
                </label>
                <input
                  id="ownership"
                  type="text"
                  value={form.ownershipCategory}
                  onChange={(e) => set("ownershipCategory", e.target.value)}
                  placeholder="e.g. Government, Mission, Private"
                  className={inputClass}
                />
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="province" className="mb-1 block text-sm font-medium text-foreground">
                  Province (optional)
                </label>
                <input
                  id="province"
                  type="text"
                  value={form.province}
                  onChange={(e) => set("province", e.target.value)}
                  className={inputClass}
                />
              </div>
              <div>
                <label htmlFor="district" className="mb-1 block text-sm font-medium text-foreground">
                  District (optional)
                </label>
                <input
                  id="district"
                  type="text"
                  value={form.district}
                  onChange={(e) => set("district", e.target.value)}
                  className={inputClass}
                />
              </div>
            </div>

            <div>
              <label htmlFor="evidence" className="mb-1 block text-sm font-medium text-foreground">
                Supporting evidence reference
              </label>
              <input
                id="evidence"
                type="text"
                value={form.evidenceRef}
                onChange={(e) => set("evidenceRef", e.target.value)}
                placeholder="Document id for a registration/licence certificate"
                className={inputClass}
                required
              />
              <p className="mt-1 text-xs text-muted-foreground">
                Registering requires a proven personal identity and a document showing the facility is
                real. Knowing a facility’s details is never enough on its own.
              </p>
            </div>

            {error && (
              <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">
                <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={register.isPending}
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary-hover disabled:opacity-50"
            >
              <Building2 className="h-4 w-4" />
              {register.isPending ? "Submitting…" : "Register facility"}
            </button>
          </form>

          <p className="text-center text-xs text-muted-foreground">
            Facility already on the platform?{" "}
            <Link href="/facility/claim" className="text-primary hover:underline">
              Claim administration
            </Link>{" "}
            instead.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
