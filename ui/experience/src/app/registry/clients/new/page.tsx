"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Loader2, Save } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCreateClientRegistration } from "@/hooks/queries/useClientRegistry";

const inputClass =
  "w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-500";

export default function NewClientRegistrationPage() {
  const router = useRouter();
  const mutation = useCreateClientRegistration();
  const [form, setForm] = useState({
    registrationType: "FACILITY_REGISTRATION",
    initiatedChannel: "FACILITY_DESK",
    firstName: "",
    middleName: "",
    lastName: "",
    dateOfBirth: "",
    sex: "FEMALE",
    phone: "",
    email: "",
    nationalIdReference: "",
    passportReference: "",
    linkedFacilityId: "",
    linkedProviderId: "",
    linkedServiceContextId: "",
    addressLine1: "",
    city: "",
    district: "",
    province: "",
    notes: "",
    issueProvisionalIdentifier: true,
  });

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    mutation.mutate(
      {
        registrationType: form.registrationType,
        initiatedChannel: form.initiatedChannel,
        firstName: form.firstName || undefined,
        middleName: form.middleName || undefined,
        lastName: form.lastName || undefined,
        dateOfBirth: form.dateOfBirth || undefined,
        sex: form.sex || undefined,
        phone: form.phone || undefined,
        email: form.email || undefined,
        nationalIdReference: form.nationalIdReference || undefined,
        passportReference: form.passportReference || undefined,
        linkedFacilityId: form.linkedFacilityId ? Number(form.linkedFacilityId) : undefined,
        linkedProviderId: form.linkedProviderId || undefined,
        linkedServiceContextId: form.linkedServiceContextId || undefined,
        addressLine1: form.addressLine1 || undefined,
        city: form.city || undefined,
        district: form.district || undefined,
        province: form.province || undefined,
        notes: form.notes || undefined,
        issueProvisionalIdentifier: form.issueProvisionalIdentifier,
      },
      {
        onSuccess: (response) => {
          router.push(`/registry/clients/${response.data.master.healthId}`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="New Client Registration"
        subtitle="Capture a self, facility, community, outreach, or provider-assisted registration into VITO"
      >
        <div className="mb-4">
          <Link
            href="/registry/clients"
            className="inline-flex items-center gap-1 text-sm text-gray-500 transition-colors hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to client registry
          </Link>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6 rounded-2xl border border-gray-200 bg-white p-6">
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">
                Registration type
              </label>
              <select
                value={form.registrationType}
                onChange={(event) => setForm((current) => ({ ...current, registrationType: event.target.value }))}
                className={inputClass}
              >
                <option value="SELF_INITIATED">Self initiated</option>
                <option value="PROVIDER_ASSISTED">Provider assisted</option>
                <option value="FACILITY_REGISTRATION">Facility registration</option>
                <option value="COMMUNITY_REGISTRATION">Community registration</option>
                <option value="OUTREACH_REGISTRATION">Outreach registration</option>
                <option value="VIRTUAL_REGISTRATION">Virtual registration</option>
                <option value="INTEROPERABILITY_IMPORT">Interoperability import</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">
                Initiated channel
              </label>
              <select
                value={form.initiatedChannel}
                onChange={(event) => setForm((current) => ({ ...current, initiatedChannel: event.target.value }))}
                className={inputClass}
              >
                <option value="FACILITY_DESK">Facility desk</option>
                <option value="SELF_SERVICE">Self service</option>
                <option value="PROVIDER_WORKFLOW">Provider workflow</option>
                <option value="COMMUNITY_MOBILE">Community mobile</option>
                <option value="OUTREACH_MOBILE">Outreach mobile</option>
                <option value="VIRTUAL_FRONT_DOOR">Virtual front door</option>
              </select>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">First name</label>
              <input value={form.firstName} onChange={(event) => setForm((current) => ({ ...current, firstName: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Middle name</label>
              <input value={form.middleName} onChange={(event) => setForm((current) => ({ ...current, middleName: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Last name</label>
              <input value={form.lastName} onChange={(event) => setForm((current) => ({ ...current, lastName: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Date of birth</label>
              <input type="date" value={form.dateOfBirth} onChange={(event) => setForm((current) => ({ ...current, dateOfBirth: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Sex / gender</label>
              <select value={form.sex} onChange={(event) => setForm((current) => ({ ...current, sex: event.target.value }))} className={inputClass}>
                <option value="FEMALE">Female</option>
                <option value="MALE">Male</option>
                <option value="UNKNOWN">Unknown</option>
              </select>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Phone</label>
              <input value={form.phone} onChange={(event) => setForm((current) => ({ ...current, phone: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Email</label>
              <input type="email" value={form.email} onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">National ID reference</label>
              <input value={form.nationalIdReference} onChange={(event) => setForm((current) => ({ ...current, nationalIdReference: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Passport reference</label>
              <input value={form.passportReference} onChange={(event) => setForm((current) => ({ ...current, passportReference: event.target.value }))} className={inputClass} />
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Facility ID</label>
              <input value={form.linkedFacilityId} onChange={(event) => setForm((current) => ({ ...current, linkedFacilityId: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Provider ID</label>
              <input value={form.linkedProviderId} onChange={(event) => setForm((current) => ({ ...current, linkedProviderId: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Service context</label>
              <input value={form.linkedServiceContextId} onChange={(event) => setForm((current) => ({ ...current, linkedServiceContextId: event.target.value }))} className={inputClass} />
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <div className="md:col-span-2">
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Address line</label>
              <input value={form.addressLine1} onChange={(event) => setForm((current) => ({ ...current, addressLine1: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">City</label>
              <input value={form.city} onChange={(event) => setForm((current) => ({ ...current, city: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">District</label>
              <input value={form.district} onChange={(event) => setForm((current) => ({ ...current, district: event.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Province</label>
              <input value={form.province} onChange={(event) => setForm((current) => ({ ...current, province: event.target.value }))} className={inputClass} />
            </div>
            <div className="md:col-span-3">
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Notes</label>
              <textarea value={form.notes} onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))} rows={3} className={inputClass} />
            </div>
          </div>

          <label className="flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={form.issueProvisionalIdentifier}
              onChange={(event) => setForm((current) => ({ ...current, issueProvisionalIdentifier: event.target.checked }))}
              className="h-4 w-4 rounded border-gray-300 text-slate-900 focus:ring-slate-500"
            />
            Issue a provisional identifier immediately so the client can move through care workflows while identity stewardship continues.
          </label>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={mutation.isPending}
              className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-slate-700 disabled:opacity-70"
            >
              {mutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Create registration
            </button>
          </div>
        </form>
      </PageShell>
    </AppLayout>
  );
}
