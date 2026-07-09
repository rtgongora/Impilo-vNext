"use client";

/**
 * Fleet asset create panel — wires the previously-dead "Add asset" button.
 *
 * Nhume moves everything the health system needs moved, so a fleet asset is
 * not only an ambulance: cold-chain vans, specimen couriers, blood-transport
 * vehicles, motorbikes, boats, drones and mobile clinics all live here. The
 * capability flags below are what let the dispatcher match a mission's cargo
 * to a suitable resource.
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Truck } from "lucide-react";
import { useCreateFleetAsset } from "@/hooks/useNhume";

const VEHICLE_TYPES = [
  "AMBULANCE",
  "RESPONSE_VEHICLE",
  "UTILITY_VEHICLE",
  "MOTORBIKE",
  "COLD_CHAIN_VEHICLE",
  "SPECIMEN_TRANSPORT_VEHICLE",
  "BLOOD_TRANSPORT_VEHICLE",
  "MOBILE_CLINIC",
  "BOAT",
  "DRONE",
  "COURIER_VEHICLE",
  "OTHER",
];

const MODES = [
  "AMBULANCE",
  "CAR",
  "VAN",
  "TRUCK",
  "MOTORCYCLE",
  "BICYCLE",
  "BOAT",
  "DRONE",
  "WALKING_COURIER",
  "OTHER",
];

const AVAILABILITY = ["AVAILABLE", "ASSIGNED", "MAINTENANCE", "OUT_OF_SERVICE", "OFFLINE"];

export function FleetAssetCreatePanel() {
  const router = useRouter();
  const create = useCreateFleetAsset();
  const [form, setForm] = useState({
    registration_number: "",
    display_name: "",
    vehicle_type: "AMBULANCE",
    mode_category: "AMBULANCE",
    ownership: "MOHCC",
    organisation_owner: "",
    home_facility_ref: "",
    availability_status: "AVAILABLE",
    cold_chain_capable: false,
    temperature_monitoring: false,
    specimen_capable: false,
    hazmat_capable: false,
    gps_capable: true,
    active: true,
  });
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof typeof form>(key: K, value: (typeof form)[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function close() {
    router.push("/nhume/fleet");
  }

  async function submit() {
    setError(null);
    if (!form.registration_number.trim()) {
      setError("Registration / identifier is required.");
      return;
    }
    try {
      await create.mutateAsync({
        ...form,
        asset_code: form.registration_number.trim(),
        display_name: form.display_name.trim() || form.registration_number.trim(),
        organisation_owner: form.organisation_owner.trim() || undefined,
        home_facility_ref: form.home_facility_ref.trim() || undefined,
      });
      close();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to register asset.");
    }
  }

  const cap = (label: string, key: "cold_chain_capable" | "temperature_monitoring" | "specimen_capable" | "hazmat_capable" | "gps_capable") => (
    <label className="flex items-center gap-2 text-sm text-foreground">
      <input type="checkbox" checked={form[key]} onChange={(e) => set(key, e.target.checked)} className="h-4 w-4" />
      {label}
    </label>
  );

  return (
    <div className="mb-4 rounded-2xl border border-border bg-card p-5">
      <div className="mb-4 flex items-center gap-2">
        <Truck className="h-5 w-5 text-teal-600" />
        <h3 className="font-semibold text-foreground">Register fleet asset</h3>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm text-foreground">
          Registration / identifier *
          <input value={form.registration_number} onChange={(e) => set("registration_number", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. AMB-HRE-014" />
        </label>
        <label className="block text-sm text-foreground">
          Display name
          <input value={form.display_name} onChange={(e) => set("display_name", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Harare Central Ambulance 2" />
        </label>
        <label className="block text-sm text-foreground">
          Asset type
          <select value={form.vehicle_type} onChange={(e) => set("vehicle_type", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm">
            {VEHICLE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
        <label className="block text-sm text-foreground">
          Transport mode
          <select value={form.mode_category} onChange={(e) => set("mode_category", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm">
            {MODES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
        <label className="block text-sm text-foreground">
          Owning organisation
          <input value={form.organisation_owner} onChange={(e) => set("organisation_owner", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. MoHCC / partner" />
        </label>
        <label className="block text-sm text-foreground">
          Home facility (ref)
          <input value={form.home_facility_ref} onChange={(e) => set("home_facility_ref", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="facility id / code" />
        </label>
        <label className="block text-sm text-foreground">
          Availability
          <select value={form.availability_status} onChange={(e) => set("availability_status", e.target.value)} className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm">
            {AVAILABILITY.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
      </div>

      <fieldset className="mt-4">
        <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Capabilities</legend>
        <div className="grid gap-2 sm:grid-cols-3">
          {cap("Cold-chain", "cold_chain_capable")}
          {cap("Temperature logging", "temperature_monitoring")}
          {cap("Specimen transport", "specimen_capable")}
          {cap("Hazmat / biohazard", "hazmat_capable")}
          {cap("GPS tracked", "gps_capable")}
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
          Register asset
        </button>
        <button type="button" onClick={close} className="rounded-xl border border-border px-4 py-2 text-sm text-foreground hover:bg-background">
          Cancel
        </button>
      </div>
    </div>
  );
}
