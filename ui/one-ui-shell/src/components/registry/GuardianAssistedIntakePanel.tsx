"use client";

import { useState } from "react";
import { Loader2, Users } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCreateClientRegistration } from "@/hooks/queries/useClientRegistry";

const inputClass =
  "w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sky-500";

export function GuardianAssistedIntakePanel() {
  const router = useRouter();
  const mutation = useCreateClientRegistration();
  const [form, setForm] = useState({
    guardianHealthId: "",
    guardianRelationship: "PARENT",
    firstName: "",
    lastName: "",
    dateOfBirth: "",
    sex: "UNKNOWN",
    phone: "",
    notes: "",
  });

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    mutation.mutate(
      {
        registrationType: "PROVIDER_ASSISTED",
        initiatedChannel: "PROVIDER_WORKFLOW",
        firstName: form.firstName || undefined,
        lastName: form.lastName || undefined,
        dateOfBirth: form.dateOfBirth || undefined,
        sex: form.sex || undefined,
        phone: form.phone || undefined,
        notes: [
          form.notes,
          form.guardianHealthId ? `Guardian Health ID: ${form.guardianHealthId}` : null,
          `Guardian relationship: ${form.guardianRelationship}`,
        ]
          .filter(Boolean)
          .join(" · "),
        issueProvisionalIdentifier: false,
        provenance: {
          intakeMode: "GUARDIAN_ASSISTED",
          guardianHealthId: form.guardianHealthId || undefined,
          guardianRelationship: form.guardianRelationship,
        },
      },
      {
        onSuccess: (response) => {
          router.push(`/registry/clients/${response.data.master.healthId}`);
        },
      },
    );
  }

  return (
    <section className="rounded-2xl border border-sky-200 bg-sky-50/50 p-5" data-testid="guardian-assisted-intake-panel">
      <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
        <Users className="h-4 w-4 text-sky-600" />
        Guardian-assisted intake
      </div>
      <p className="mt-1 text-xs text-gray-600">
        Provider-assisted registration with guardian linkage — posts to{" "}
        <code className="text-[10px]">POST /internal/v1/client-registry/registrations</code>.
      </p>

      <form onSubmit={handleSubmit} className="mt-4 grid gap-3 md:grid-cols-2">
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Guardian Health ID</label>
          <input
            value={form.guardianHealthId}
            onChange={(e) => setForm((c) => ({ ...c, guardianHealthId: e.target.value }))}
            className={inputClass}
            placeholder="ZW-…"
            required
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Relationship</label>
          <select
            value={form.guardianRelationship}
            onChange={(e) => setForm((c) => ({ ...c, guardianRelationship: e.target.value }))}
            className={inputClass}
          >
            <option value="PARENT">Parent</option>
            <option value="GUARDIAN">Legal guardian</option>
            <option value="SPOUSE">Spouse</option>
            <option value="SIBLING">Sibling</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Child first name</label>
          <input value={form.firstName} onChange={(e) => setForm((c) => ({ ...c, firstName: e.target.value }))} className={inputClass} required />
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Child last name</label>
          <input value={form.lastName} onChange={(e) => setForm((c) => ({ ...c, lastName: e.target.value }))} className={inputClass} required />
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Date of birth</label>
          <input type="date" value={form.dateOfBirth} onChange={(e) => setForm((c) => ({ ...c, dateOfBirth: e.target.value }))} className={inputClass} />
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Contact phone</label>
          <input value={form.phone} onChange={(e) => setForm((c) => ({ ...c, phone: e.target.value }))} className={inputClass} />
        </div>
        <div className="md:col-span-2">
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-gray-500">Notes</label>
          <textarea value={form.notes} onChange={(e) => setForm((c) => ({ ...c, notes: e.target.value }))} rows={2} className={inputClass} />
        </div>
        <div className="md:col-span-2 flex justify-end">
          <button
            type="submit"
            disabled={mutation.isPending}
            className="inline-flex items-center gap-2 rounded-xl bg-sky-700 px-4 py-2 text-sm font-medium text-white hover:bg-sky-800 disabled:opacity-70"
          >
            {mutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Submit guardian-assisted registration
          </button>
        </div>
      </form>
    </section>
  );
}
