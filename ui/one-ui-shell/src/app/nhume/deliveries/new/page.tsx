"use client";

/**
 * New Delivery — citizen / provider / facility / programme / dispatcher entry.
 *
 * A pragmatic single-screen form that exposes the most common knobs of a
 * NhumeCreateDeliveryRequest without overwhelming the dispatcher. The full
 * shape (telehealth context, marketplace order ref, payment, MUSHEX, etc.)
 * is supported via the underlying API surface — additional fields can be
 * progressively disclosed as the workflows that need them are wired up.
 */

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { PackagePlus, ArrowLeft, Loader2 } from "lucide-react";
import Link from "next/link";
import { StickyActionBar } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCreateNhumeDelivery } from "@/hooks/useNhume";
import type { CreateDeliveryRequestPayload, NhumeDeliveryMode } from "@/lib/nhume";
import { CARGO_PROFILES, applyCargoProfile, getCargoProfile } from "@/lib/nhume/cargo-profiles";
import { ShellIcon } from "@/components/shell/ShellIcon";

const DELIVERY_TYPES = [
  "MEDICINE", "PRESCRIPTION_REFILL", "LAB_SAMPLE_PICKUP", "LAB_RESULT", "VACCINE",
  "COLD_CHAIN", "BLOOD_PRODUCT", "EQUIPMENT", "ASSISTIVE_DEVICE",
  "MARKETPLACE_ORDER", "TELEHEALTH_LINKED", "PUBLIC_HEALTH_CAMPAIGN",
  "EMERGENCY_SUPPLY", "FACILITY_TRANSFER", "HOME_CARE", "CHW_DELIVERY",
  "PROGRAMME_COMMODITY", "RETURN", "PICKUP_HUB", "DOCUMENT", "OTHER",
];

const PRIORITIES = ["ROUTINE", "STANDARD", "URGENT", "EMERGENCY"];

const SOURCES = [
  "CITIZEN_APP", "PROVIDER_APP", "WEB_PORTAL", "FACILITY_OPS", "PHARMACY",
  "LABORATORY", "MARKETPLACE", "TELEHEALTH", "PUBLIC_HEALTH_CAMPAIGN",
  "PROGRAMME_DISTRIBUTION", "PARTNER_SYSTEM", "API_INTEGRATION",
  "MANUAL_ADMIN", "BATCH_UPLOAD", "NOMPILO",
];

const MODES: NhumeDeliveryMode[] = [
  "WALKING_COURIER", "BICYCLE", "MOTORCYCLE", "CAR", "VAN", "TRUCK",
  "AMBULANCE", "BOAT", "DRONE", "AUTONOMOUS_VEHICLE", "ROBOT",
  "SMART_LOCKER", "PARTNER_COURIER", "THIRD_PARTY_FLEET",
  "PUBLIC_SECTOR_VEHICLE", "COMMUNITY_HEALTH_WORKER", "FACILITY_TRANSPORT", "OTHER",
];

export default function NhumeNewDeliveryPage() {
  const router = useRouter();
  const createMutation = useCreateNhumeDelivery();

  const searchParams = useSearchParams();
  const initialCargo = getCargoProfile(String(searchParams.get("cargo") ?? "").toUpperCase()) ? String(searchParams.get("cargo")).toUpperCase() : "MEDICINE";
  const [cargoProfileId, setCargoProfileId] = useState(initialCargo);
  const [cargoValues, setCargoValues] = useState<Record<string, string | number | boolean>>({});
  const cargoProfile = getCargoProfile(cargoProfileId);

  const [form, setForm] = useState({
    delivery_type: getCargoProfile(initialCargo)?.deliveryType ?? "MEDICINE",
    priority: "STANDARD",
    request_source: "WEB_PORTAL",
    origin_label: "",
    origin_address: "",
    destination_label: "",
    destination_address: "",
    recipient_name: "",
    recipient_phone: "",
    item_description: "",
    item_quantity: 1,
    item_unit: "unit",
    cold_chain: false,
    controlled: false,
    chain_of_custody: false,
    required_by: "",
    notes: "",
    allowed_modes: ["MOTORCYCLE", "CAR"] as NhumeDeliveryMode[],
    submit_immediately: false,
  });

  function toggleMode(m: NhumeDeliveryMode) {
    setForm((prev) => ({
      ...prev,
      allowed_modes: prev.allowed_modes.includes(m)
        ? prev.allowed_modes.filter((x) => x !== m)
        : [...prev.allowed_modes, m],
    }));
  }

  function selectCargoProfile(id: string) {
    const profile = getCargoProfile(id);
    if (!profile) return;
    setCargoProfileId(id);
    setCargoValues({});
    setForm((prev) => ({ ...prev, delivery_type: profile.deliveryType }));
  }

  function setCargoValue(key: string, value: string | number | boolean) {
    setCargoValues((prev) => ({ ...prev, [key]: value }));
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    // Fold the chosen cargo profile's type-specific fields onto the canonical
    // CreateDeliveryRequest: handling flags + the primary clinical link, with
    // everything else carried in metadata (cargo details + integration links).
    const cargo = applyCargoProfile(cargoProfileId, cargoValues);
    const payload: CreateDeliveryRequestPayload = {
      delivery_type: cargo.deliveryType || form.delivery_type,
      priority: form.priority,
      request_source: form.request_source,
      origin: {
        label: form.origin_label || "Origin",
        address_line: form.origin_address || undefined,
      },
      destination: {
        label: form.destination_label || "Destination",
        address_line: form.destination_address || undefined,
        privacy_class: "ZONE_ONLY",
      },
      recipient: {
        display_name: form.recipient_name || undefined,
        phone: form.recipient_phone || undefined,
        preferred_channel: form.recipient_phone ? "SMS" : "IN_APP",
      },
      items: [{
        description: form.item_description || cargoProfile?.label || "Item",
        quantity: Number(form.item_quantity) || 1,
        unit: form.item_unit || "unit",
        cold_chain: form.cold_chain || Boolean(cargo.flags.cold_chain_required),
        controlled: form.controlled || Boolean(cargo.flags.controlled_item),
      }],
      // Base flags from the generic toggles, then cargo-profile flags win.
      cold_chain_required: form.cold_chain,
      controlled_item: form.controlled,
      chain_of_custody_required: form.chain_of_custody,
      ...cargo.flags,
      clinical_context_ref: cargo.clinicalContextRef,
      metadata: cargo.metadata,
      required_by: form.required_by || undefined,
      notes: form.notes || undefined,
      allowed_modes: form.allowed_modes,
      submit_immediately: form.submit_immediately,
    };

    try {
      const created = await createMutation.mutateAsync(payload);
      const id = (created as { delivery_id?: string }).delivery_id;
      if (id) {
        router.push(`/nhume/deliveries/${id}`);
      } else {
        router.push("/nhume/deliveries");
      }
    } catch {
      // mutation surfaces error inline; keep form open so the user can correct.
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="New Delivery Request"
        subtitle="Capture origin, destination, items and policy. Submit to start the lifecycle."
        icon={<PackagePlus className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/nhume/deliveries" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" />
            Back to deliveries
          </Link>
        </div>

        <form onSubmit={onSubmit} className="space-y-6">
          <Section title="What are you moving?">
            <p className="text-xs text-muted-foreground mb-3">
              Nhume moves what the health system needs moved — people, samples, commodities, teams,
              blood, vaccines, equipment and documents. Pick the cargo so the right handling and
              details apply.
            </p>
            <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
              {CARGO_PROFILES.map((p) => {
                const active = p.id === cargoProfileId;
                return (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => selectCargoProfile(p.id)}
                    className={`flex flex-col items-start gap-1 rounded-xl border p-3 text-left transition ${
                      active ? "border-teal-500 bg-primary-soft" : "border-border hover:border-teal-300 hover:bg-background"
                    }`}
                    aria-pressed={active}
                  >
                    <ShellIcon name={p.icon} className="h-4 w-4 text-teal-600" />
                    <span className="text-sm font-medium text-foreground">{p.label}</span>
                    <span className="text-[11px] leading-tight text-muted-foreground">{p.description}</span>
                  </button>
                );
              })}
            </div>
          </Section>

          {cargoProfile && cargoProfile.fields.length > 0 ? (
            <Section title={`${cargoProfile.label} details`}>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                {cargoProfile.fields.map((field) => {
                  const value = cargoValues[field.key];
                  if (field.type === "checkbox") {
                    return (
                      <div key={field.key} className="flex items-end pb-1">
                        <Toggle label={field.label} checked={Boolean(value)} onChange={(v) => setCargoValue(field.key, v)} />
                      </div>
                    );
                  }
                  return (
                    <Field key={field.key} label={field.label}>
                      {field.type === "select" ? (
                        <select className="form-input" value={String(value ?? "")} onChange={(e) => setCargoValue(field.key, e.target.value)}>
                          <option value="">—</option>
                          {(field.options ?? []).map((o) => <option key={o} value={o}>{o.replace(/_/g, " ")}</option>)}
                        </select>
                      ) : (
                        <input
                          className="form-input"
                          type={field.type === "number" ? "number" : "text"}
                          value={String(value ?? "")}
                          placeholder={field.placeholder}
                          onChange={(e) => setCargoValue(field.key, field.type === "number" ? Number(e.target.value) : e.target.value)}
                        />
                      )}
                      {field.help ? <span className="mt-1 block text-[11px] text-muted-foreground">{field.help}</span> : null}
                    </Field>
                  );
                })}
              </div>
            </Section>
          ) : null}

          <Section title="Request basics">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <Field label="Delivery type">
                <select
                  className="form-input"
                  value={form.delivery_type}
                  onChange={(e) => setForm((p) => ({ ...p, delivery_type: e.target.value }))}
                >
                  {DELIVERY_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </Field>
              <Field label="Priority">
                <select
                  className="form-input"
                  value={form.priority}
                  onChange={(e) => setForm((p) => ({ ...p, priority: e.target.value }))}
                >
                  {PRIORITIES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </Field>
              <Field label="Request source">
                <select
                  className="form-input"
                  value={form.request_source}
                  onChange={(e) => setForm((p) => ({ ...p, request_source: e.target.value }))}
                >
                  {SOURCES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </Field>
            </div>
          </Section>

          <Section title="Origin → destination">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Field label="Origin label">
                <input className="form-input" value={form.origin_label}
                  onChange={(e) => setForm((p) => ({ ...p, origin_label: e.target.value }))}
                  placeholder="e.g. Parirenyatwa Pharmacy" />
              </Field>
              <Field label="Origin address (optional)">
                <input className="form-input" value={form.origin_address}
                  onChange={(e) => setForm((p) => ({ ...p, origin_address: e.target.value }))}
                  placeholder="Street address" />
              </Field>
              <Field label="Destination label">
                <input className="form-input" value={form.destination_label}
                  onChange={(e) => setForm((p) => ({ ...p, destination_label: e.target.value }))}
                  placeholder="e.g. Mbare Community Pickup" />
              </Field>
              <Field label="Destination address (optional)">
                <input className="form-input" value={form.destination_address}
                  onChange={(e) => setForm((p) => ({ ...p, destination_address: e.target.value }))}
                  placeholder="Street address (privacy-safe display only)" />
              </Field>
            </div>
          </Section>

          <Section title="Recipient">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Field label="Recipient display name">
                <input className="form-input" value={form.recipient_name}
                  onChange={(e) => setForm((p) => ({ ...p, recipient_name: e.target.value }))} />
              </Field>
              <Field label="Recipient phone (preferred channel SMS)">
                <input className="form-input" value={form.recipient_phone}
                  onChange={(e) => setForm((p) => ({ ...p, recipient_phone: e.target.value }))} placeholder="+263…" />
              </Field>
            </div>
          </Section>

          <Section title="What's moving?">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
              <Field label="Item description" className="md:col-span-2">
                <input className="form-input" value={form.item_description}
                  onChange={(e) => setForm((p) => ({ ...p, item_description: e.target.value }))}
                  placeholder="e.g. Amlodipine 5mg tabs" />
              </Field>
              <Field label="Quantity">
                <input type="number" min={1} className="form-input" value={form.item_quantity}
                  onChange={(e) => setForm((p) => ({ ...p, item_quantity: Number(e.target.value) }))} />
              </Field>
              <Field label="Unit">
                <input className="form-input" value={form.item_unit}
                  onChange={(e) => setForm((p) => ({ ...p, item_unit: e.target.value }))} />
              </Field>
            </div>
            <div className="flex flex-wrap gap-4 mt-3">
              <Toggle label="Cold chain required"
                checked={form.cold_chain}
                onChange={(v) => setForm((p) => ({ ...p, cold_chain: v }))} />
              <Toggle label="Controlled / restricted"
                checked={form.controlled}
                onChange={(v) => setForm((p) => ({ ...p, controlled: v }))} />
              <Toggle label="Chain of custody"
                checked={form.chain_of_custody}
                onChange={(v) => setForm((p) => ({ ...p, chain_of_custody: v }))} />
            </div>
          </Section>

          <Section title="Allowed modes">
            <p className="text-xs text-muted-foreground mb-2">
              The dispatcher may further restrict by policy at assignment time.
            </p>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              {MODES.map((m) => (
                <label key={m} className="inline-flex items-center gap-2 text-sm rounded-lg border border-border px-3 py-2 cursor-pointer hover:bg-background">
                  <input type="checkbox"
                    checked={form.allowed_modes.includes(m)}
                    onChange={() => toggleMode(m)} />
                  <span>{m.replace(/_/g, " ").toLowerCase()}</span>
                </label>
              ))}
            </div>
          </Section>

          <Section title="When & notes">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Field label="Required by (optional)">
                <input type="datetime-local" className="form-input" value={form.required_by}
                  onChange={(e) => setForm((p) => ({ ...p, required_by: e.target.value }))} />
              </Field>
              <Field label="Notes (visible to dispatcher)">
                <input className="form-input" value={form.notes}
                  onChange={(e) => setForm((p) => ({ ...p, notes: e.target.value }))} />
              </Field>
            </div>
            <div className="mt-3">
              <Toggle label="Submit immediately (skip Draft, go to Submitted)"
                checked={form.submit_immediately}
                onChange={(v) => setForm((p) => ({ ...p, submit_immediately: v }))} />
            </div>
          </Section>

          {createMutation.isError && (
            <div className="rounded-xl border border-danger/28 bg-danger-soft p-3 text-sm text-rose-800">
              {((createMutation.error as { error?: { message?: string } })?.error?.message) ?? "Could not create the delivery."}
            </div>
          )}

          <StickyActionBar status={cargoProfile ? `${cargoProfile.label} delivery` : "New delivery"}>
            <Link href="/nhume/deliveries" className="rounded-xl border border-border px-4 py-2 text-sm text-foreground hover:bg-background">
              Cancel
            </Link>
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
            >
              {createMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Create delivery
            </button>
          </StickyActionBar>
        </form>

        <style jsx>{`
          .form-input {
            display: block;
            width: 100%;
            border: 1px solid #d1d5db;
            border-radius: 0.75rem;
            padding: 0.5rem 0.75rem;
            font-size: 0.875rem;
            background: white;
          }
          .form-input:focus {
            outline: none;
            border-color: #14b8a6;
            box-shadow: 0 0 0 1px #14b8a6;
          }
        `}</style>
      </PageShell>
    </AppLayout>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-2xl border border-border bg-card p-5">
      <h2 className="text-sm font-semibold text-foreground mb-3">{title}</h2>
      {children}
    </section>
  );
}

function Field({ label, children, className }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <label className={`block ${className ?? ""}`}>
      <span className="text-xs font-medium text-muted-foreground mb-1 block">{label}</span>
      {children}
    </label>
  );
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="inline-flex items-center gap-2 text-sm cursor-pointer">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span className="text-foreground">{label}</span>
    </label>
  );
}
