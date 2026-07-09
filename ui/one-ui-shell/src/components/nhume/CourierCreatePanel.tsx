"use client";

/**
 * Courier / responder create panel — wires the previously-dead "Add courier"
 * button. A Nhume courier is any responsible mover: ambulance crew, specimen
 * courier, cold-chain officer, outreach team lead, motorbike runner or partner
 * driver. courier_kind + capabilities drive mission matching.
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, UserPlus } from "lucide-react";
import { useCreateCourier } from "@/hooks/useNhume";

const COURIER_KINDS = [
  "AMBULANCE_CREW",
  "SPECIMEN_COURIER",
  "COLD_CHAIN_OFFICER",
  "BLOOD_COURIER",
  "OUTREACH_TEAM_LEAD",
  "COMMUNITY_HEALTH_WORKER",
  "MOTORBIKE_RUNNER",
  "PARTNER_DRIVER",
  "FACILITY_LOGISTICS_OFFICER",
  "OTHER",
];

const STATUSES = ["AVAILABLE", "ON_DUTY", "ASSIGNED", "OFF_DUTY", "OFFLINE"];

export function CourierCreatePanel() {
  const router = useRouter();
  const create = useCreateCourier();
  const [form, setForm] = useState({
    display_name: "",
    courier_kind: "AMBULANCE_CREW",
    phone: "",
    email: "",
    varapi_provider_ref: "",
    tuso_facility_ref: "",
    organisation_ref: "",
    current_status: "AVAILABLE",
    training_status: "",
    trust_verified: false,
    council_verified: false,
    active: true,
  });
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof typeof form>(key: K, value: (typeof form)[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function close() {
    router.push("/nhume/couriers");
  }

  async function submit() {
    setError(null);
    if (!form.display_name.trim()) {
      setError("Courier name is required.");
      return;
    }
    try {
      await create.mutateAsync({
        ...form,
        courier_code: form.display_name.trim().toUpperCase().replace(/\s+/g, "-").slice(0, 24),
        phone: form.phone.trim() || undefined,
        email: form.email.trim() || undefined,
        varapi_provider_ref: form.varapi_provider_ref.trim() || undefined,
        tuso_facility_ref: form.tuso_facility_ref.trim() || undefined,
        organisation_ref: form.organisation_ref.trim() || undefined,
        training_status: form.training_status.trim() || undefined,
      });
      close();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to register courier.");
    }
  }

  return (
    <div className="mb-4 rounded-2xl border border-border bg-card p-5">
      <div className="mb-4 flex items-center gap-2">
        <UserPlus className="h-5 w-5 text-teal-600" />
        <h3 className="font-semibold text-foreground">Register courier / responder</h3>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm text-foreground">
          Name *
          <input value={form.display_name} onChange={(e) => set("display_name", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Tafadzwa Moyo" />
        </label>
        <label className="block text-sm text-foreground">
          Kind
          <select value={form.courier_kind} onChange={(e) => set("courier_kind", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm">
            {COURIER_KINDS.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
        <label className="block text-sm text-foreground">
          Phone
          <input value={form.phone} onChange={(e) => set("phone", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="+263…" />
        </label>
        <label className="block text-sm text-foreground">
          Email
          <input value={form.email} onChange={(e) => set("email", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" />
        </label>
        <label className="block text-sm text-foreground">
          Provider ID (Varapi ref)
          <input value={form.varapi_provider_ref} onChange={(e) => set("varapi_provider_ref", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="PROV-ZW-…" />
        </label>
        <label className="block text-sm text-foreground">
          Base facility (Tuso ref)
          <input value={form.tuso_facility_ref} onChange={(e) => set("tuso_facility_ref", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="facility id / code" />
        </label>
        <label className="block text-sm text-foreground">
          Organisation
          <input value={form.organisation_ref} onChange={(e) => set("organisation_ref", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. MoHCC / partner" />
        </label>
        <label className="block text-sm text-foreground">
          Status
          <select value={form.current_status} onChange={(e) => set("current_status", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm">
            {STATUSES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
      </div>

      <fieldset className="mt-4">
        <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Verification</legend>
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="flex items-center gap-2 text-sm text-foreground">
            <input type="checkbox" checked={form.trust_verified} onChange={(e) => set("trust_verified", e.target.checked)} className="h-4 w-4" />
            Trust verified
          </label>
          <label className="flex items-center gap-2 text-sm text-foreground">
            <input type="checkbox" checked={form.council_verified} onChange={(e) => set("council_verified", e.target.checked)} className="h-4 w-4" />
            Council / licence verified
          </label>
        </div>
      </fieldset>

      {error ? <p className="mt-3 rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">{error}</p> : null}

      <div className="mt-4 flex items-center gap-2">
        <button
          type="button"
          onClick={submit}
          disabled={create.isPending}
          className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-60"
        >
          {create.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          Register courier
        </button>
        <button type="button" onClick={close} className="rounded-xl border border-border px-4 py-2 text-sm text-foreground hover:bg-background">
          Cancel
        </button>
      </div>
    </div>
  );
}
