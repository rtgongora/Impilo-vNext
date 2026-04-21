"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Loader2, Save } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCreateFacilityApplication } from "@/hooks/queries/useFacilityRegulatory";

const inputClass =
  "w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500";

export default function NewFacilityPage() {
  const router = useRouter();
  const createApplication = useCreateFacilityApplication();
  const [form, setForm] = useState({
    applicationType: "NEW_REGISTRATION",
    pathway: "PRIVATE",
    applicantName: "",
    applicantEmail: "",
    applicantPhone: "",
    applicantOrganisation: "",
    facilityCode: "",
    facilityName: "",
    ownershipType: "PRIVATE",
    facilityClass: "PRIVATE_HEALTH_INSTITUTION",
    facilityCategory: "GENERAL",
    facilityType: "CLINIC",
    legalStatus: "PRIVATE_COMPANY",
    province: "",
    district: "",
    city: "",
    ward: "",
    addressLine1: "",
    addressLine2: "",
    workflowNotes: "",
    practitionerPublicId: "",
    practitionerStartDate: "",
    unitName: "",
    unitType: "OUTPATIENT",
  });

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    createApplication.mutate(
      {
        applicationType: form.applicationType,
        pathway: form.pathway,
        applicantName: form.applicantName.trim(),
        applicantEmail: form.applicantEmail.trim() || undefined,
        applicantPhone: form.applicantPhone.trim() || undefined,
        applicantOrganisation: form.applicantOrganisation.trim() || undefined,
        workflowNotes: form.workflowNotes.trim() || undefined,
        facilityCode: form.facilityCode.trim() || undefined,
        facilityName: form.facilityName.trim(),
        ownershipType: form.ownershipType,
        facilityClass: form.facilityClass,
        facilityCategory: form.facilityCategory,
        facilityType: form.facilityType,
        legalStatus: form.legalStatus,
        registrationPathway: form.pathway,
        province: form.province.trim(),
        district: form.district.trim(),
        city: form.city.trim() || undefined,
        ward: form.ward.trim() || undefined,
        addressLine1: form.addressLine1.trim() || undefined,
        addressLine2: form.addressLine2.trim() || undefined,
        requestedUnits: form.unitName.trim()
          ? [
              {
                name: form.unitName.trim(),
                unitType: form.unitType,
                registrationRequired: true,
              },
            ]
          : undefined,
        practitionerInCharge: form.practitionerPublicId.trim() && form.practitionerStartDate
          ? {
              providerPublicId: form.practitionerPublicId.trim(),
              role: "PRACTITIONER_IN_CHARGE",
              startDate: form.practitionerStartDate,
            }
          : undefined,
      },
      {
        onSuccess: (response) => {
          router.push(`/registry/facilities/${response.data.master.facilityId}`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="New Facility Application"
        subtitle="Open an HPA-governed registration or change workflow in Tuso"
      >
        <div className="mb-4">
          <Link
            href="/registry/facilities"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to facility operations
          </Link>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6 rounded-2xl border border-gray-200 bg-white p-6">
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">
                Application type
              </label>
              <select
                value={form.applicationType}
                onChange={(event) => setForm((current) => ({ ...current, applicationType: event.target.value }))}
                className={inputClass}
              >
                <option value="NEW_REGISTRATION">New registration</option>
                <option value="RENEWAL">Renewal</option>
                <option value="ADD_UNIT">Add unit / department</option>
                <option value="CHANGE_OF_PRACTITIONER_IN_CHARGE">Change practitioner in charge</option>
                <option value="CHANGE_OF_PREMISES">Change of premises</option>
                <option value="MATERIAL_CHANGE">Material change</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">
                Registration pathway
              </label>
              <select
                value={form.pathway}
                onChange={(event) => setForm((current) => ({ ...current, pathway: event.target.value }))}
                className={inputClass}
              >
                <option value="PRIVATE">Private</option>
                <option value="GOVERNMENT">Government</option>
                <option value="MISSION">Mission</option>
                <option value="LOCAL_AUTHORITY">Local authority</option>
              </select>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Applicant name</label>
              <input
                required
                value={form.applicantName}
                onChange={(event) => setForm((current) => ({ ...current, applicantName: event.target.value }))}
                className={inputClass}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Organisation</label>
              <input
                value={form.applicantOrganisation}
                onChange={(event) => setForm((current) => ({ ...current, applicantOrganisation: event.target.value }))}
                className={inputClass}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Applicant email</label>
              <input
                type="email"
                value={form.applicantEmail}
                onChange={(event) => setForm((current) => ({ ...current, applicantEmail: event.target.value }))}
                className={inputClass}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Applicant phone</label>
              <input
                value={form.applicantPhone}
                onChange={(event) => setForm((current) => ({ ...current, applicantPhone: event.target.value }))}
                className={inputClass}
              />
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Facility name</label>
              <input
                required
                value={form.facilityName}
                onChange={(event) => setForm((current) => ({ ...current, facilityName: event.target.value }))}
                className={inputClass}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Facility code</label>
              <input
                value={form.facilityCode}
                onChange={(event) => setForm((current) => ({ ...current, facilityCode: event.target.value }))}
                className={inputClass}
                placeholder="Optional if Tuso should assign one later"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Facility type</label>
              <select
                value={form.facilityType}
                onChange={(event) => setForm((current) => ({ ...current, facilityType: event.target.value }))}
                className={inputClass}
              >
                <option value="CLINIC">Clinic / consulting room</option>
                <option value="LABORATORY">Laboratory</option>
                <option value="MATERNITY">Maternity</option>
                <option value="HOSPITAL">Hospital</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Ownership</label>
              <select
                value={form.ownershipType}
                onChange={(event) => setForm((current) => ({ ...current, ownershipType: event.target.value }))}
                className={inputClass}
              >
                <option value="PRIVATE">Private</option>
                <option value="PUBLIC">Public</option>
                <option value="MISSION">Mission</option>
                <option value="LOCAL_AUTHORITY">Local authority</option>
              </select>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Province</label>
              <input
                required
                value={form.province}
                onChange={(event) => setForm((current) => ({ ...current, province: event.target.value }))}
                className={inputClass}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">District</label>
              <input
                required
                value={form.district}
                onChange={(event) => setForm((current) => ({ ...current, district: event.target.value }))}
                className={inputClass}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">City / locality</label>
              <input
                value={form.city}
                onChange={(event) => setForm((current) => ({ ...current, city: event.target.value }))}
                className={inputClass}
              />
            </div>
            <div className="md:col-span-2">
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Address line 1</label>
              <input
                value={form.addressLine1}
                onChange={(event) => setForm((current) => ({ ...current, addressLine1: event.target.value }))}
                className={inputClass}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Ward</label>
              <input
                value={form.ward}
                onChange={(event) => setForm((current) => ({ ...current, ward: event.target.value }))}
                className={inputClass}
              />
            </div>
            <div className="md:col-span-2">
              <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Address line 2</label>
              <input
                value={form.addressLine2}
                onChange={(event) => setForm((current) => ({ ...current, addressLine2: event.target.value }))}
                className={inputClass}
              />
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-2xl border border-gray-200 bg-gray-50 p-4">
              <h3 className="text-sm font-semibold text-gray-900">Practitioner in charge</h3>
              <div className="mt-3 space-y-3">
                <input
                  placeholder="Provider public ID"
                  value={form.practitionerPublicId}
                  onChange={(event) => setForm((current) => ({ ...current, practitionerPublicId: event.target.value }))}
                  className={inputClass}
                />
                <input
                  type="date"
                  value={form.practitionerStartDate}
                  onChange={(event) => setForm((current) => ({ ...current, practitionerStartDate: event.target.value }))}
                  className={inputClass}
                />
              </div>
            </div>
            <div className="rounded-2xl border border-gray-200 bg-gray-50 p-4">
              <h3 className="text-sm font-semibold text-gray-900">Initial unit / department</h3>
              <div className="mt-3 space-y-3">
                <input
                  placeholder="Unit name"
                  value={form.unitName}
                  onChange={(event) => setForm((current) => ({ ...current, unitName: event.target.value }))}
                  className={inputClass}
                />
                <select
                  value={form.unitType}
                  onChange={(event) => setForm((current) => ({ ...current, unitType: event.target.value }))}
                  className={inputClass}
                >
                  <option value="OUTPATIENT">Outpatient</option>
                  <option value="LABORATORY">Laboratory</option>
                  <option value="MATERNITY">Maternity</option>
                  <option value="IMAGING">Imaging</option>
                </select>
              </div>
            </div>
          </div>

          <div>
            <label className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-gray-500">Workflow notes</label>
            <textarea
              rows={4}
              value={form.workflowNotes}
              onChange={(event) => setForm((current) => ({ ...current, workflowNotes: event.target.value }))}
              className={inputClass}
              placeholder="Capture pathway notes, committee context, or known pre-inspection constraints."
            />
          </div>

          {createApplication.isError ? (
            <p className="text-sm text-red-600">
              The application could not be opened. Check the required fields and try again.
            </p>
          ) : null}

          <button
            type="submit"
            disabled={createApplication.isPending}
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-emerald-700 disabled:opacity-60"
          >
            {createApplication.isPending ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Opening workflow...
              </>
            ) : (
              <>
                <Save className="h-4 w-4" />
                Create application
              </>
            )}
          </button>
        </form>
      </PageShell>
    </AppLayout>
  );
}
