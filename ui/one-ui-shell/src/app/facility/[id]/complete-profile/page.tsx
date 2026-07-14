"use client";

/**
 * Governed facility profile completion — /facility/[id]/complete-profile
 *
 * Destination of the FacilityCompletenessBanner CTA. Pre-fills the governed
 * fields from the registry read, highlights the specific missing ones, and
 * reuses FacilityLocationCapture (address search / device GPS / manual coords
 * via Ndila) so geocode provenance rides the submission. TUSO enforces the
 * facility-administrator / PIC authz FAIL-CLOSED server-side — a 403 here is
 * surfaced honestly, never masked.
 */

import { useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle2, Loader2, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  FacilityLocationCapture,
  type FacilityLocationCaptureMeta,
} from "@/components/maps/FacilityLocationCapture";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";
import { apiClient } from "@/lib/api-client";
import {
  useFacilityCompleteness,
  useCompleteFacilityProfile,
  MISSING_FIELD_LABELS,
  type CompleteFacilityProfileInput,
  type FacilityMissingField,
  type GeocodeProvenance,
} from "@/hooks/queries/useFacilityCompleteness";

interface FacilityResource {
  id: string;
  attributes?: {
    name?: string;
    facilityType?: string;
    ownership?: string;
    operationalStatus?: string;
    latitude?: number;
    longitude?: number;
  };
}

const FACILITY_TYPES = [
  "CENTRAL_HOSPITAL", "PROVINCIAL_HOSPITAL", "DISTRICT_HOSPITAL", "MISSION_HOSPITAL",
  "RURAL_HOSPITAL", "PRIVATE_HOSPITAL", "POLYCLINIC", "CLINIC", "RURAL_HEALTH_CENTRE",
  "SURGERY", "LABORATORY", "PHARMACY", "RADIOLOGY_CENTRE", "OTHER",
];
const OWNERSHIPS = ["GOVERNMENT", "COUNCIL", "MISSION", "PRIVATE", "NGO", "UNIFORMED_FORCES", "OTHER"];
const OPERATING_STATUSES = ["OPERATIONAL", "TEMPORARILY_CLOSED", "UNDER_CONSTRUCTION", "NOT_OPERATIONAL"];

export default function FacilityCompleteProfilePage() {
  const params = useParams();
  const facilityId = params.id as string;

  const completenessQuery = useFacilityCompleteness(facilityId);
  const facilityQuery = useQuery({
    queryKey: ["facility", facilityId],
    enabled: !!facilityId,
    queryFn: async () => {
      const resp = await apiClient.get<{ data: FacilityResource }>(
        `/internal/v1/facilities/${encodeURIComponent(facilityId)}`,
      );
      return resp.data;
    },
  });
  const mutation = useCompleteFacilityProfile(facilityId);

  const missing = useMemo(
    () => new Set<FacilityMissingField>(completenessQuery.data?.missingFields ?? []),
    [completenessQuery.data],
  );

  const [form, setForm] = useState({
    facilityType: "",
    ownership: "",
    operationalStatus: "",
    addressLine1: "",
    addressLine2: "",
    city: "",
    ward: "",
    postalCode: "",
  });
  const [coords, setCoords] = useState<NdilaCoordinate | null>(null);
  const [captureMeta, setCaptureMeta] = useState<FacilityLocationCaptureMeta | null>(null);
  const [prefilled, setPrefilled] = useState(false);

  // Pre-fill once from the registry read (missing fields stay blank + highlighted).
  useEffect(() => {
    const attrs = facilityQuery.data?.attributes;
    if (!attrs || prefilled) return;
    setForm((f) => ({
      ...f,
      facilityType: attrs.facilityType ?? "",
      ownership: attrs.ownership ?? "",
      operationalStatus: attrs.operationalStatus ?? "",
    }));
    if (attrs.latitude != null && attrs.longitude != null) {
      setCoords({ latitude: attrs.latitude, longitude: attrs.longitude });
    }
    setPrefilled(true);
  }, [facilityQuery.data, prefilled]);

  const setField = (key: keyof typeof form) => (value: string) =>
    setForm((f) => ({ ...f, [key]: value }));

  const highlight = (field: FacilityMissingField) =>
    missing.has(field)
      ? "border-warning ring-1 ring-warning/50"
      : "border-border";

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const input: CompleteFacilityProfileInput = {};
    if (form.facilityType) input.facilityType = form.facilityType;
    if (form.ownership) input.ownership = form.ownership;
    if (form.operationalStatus) input.operationalStatus = form.operationalStatus;
    if (form.addressLine1) input.addressLine1 = form.addressLine1;
    if (form.addressLine2) input.addressLine2 = form.addressLine2;
    if (form.city) input.city = form.city;
    if (form.ward) input.ward = form.ward;
    if (form.postalCode) input.postalCode = form.postalCode;
    if (coords && captureMeta) {
      input.latitude = coords.latitude;
      input.longitude = coords.longitude;
      const provenance: GeocodeProvenance = { source: captureMeta.source };
      if (captureMeta.result?.provider) provenance.provider = captureMeta.result.provider;
      if (captureMeta.result?.confidence != null) provenance.confidence = captureMeta.result.confidence;
      if (captureMeta.accuracyMeters != null) provenance.accuracyMeters = captureMeta.accuracyMeters;
      input.geocodeProvenance = provenance;
    }
    mutation.mutate(input);
  };

  const facilityName = facilityQuery.data?.attributes?.name ?? `Facility ${facilityId}`;
  const complete = mutation.data?.complete ?? completenessQuery.data?.complete;

  return (
    <AppLayout>
      <PageShell
        title="Complete facility profile"
        subtitle="Governed registry fields — every change is audited and versioned"
      >
        <div className="mb-4">
          <Link
            href={`/facility/${facilityId}/cockpit`}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to cockpit
          </Link>
        </div>

        {completenessQuery.isLoading ? (
          <div className="flex items-center justify-center py-16" data-testid="completeness-loading">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading profile completeness…</span>
          </div>
        ) : (
          <div className="max-w-3xl space-y-6">
            <section className="rounded-2xl border border-border bg-card p-4">
              <h2 className="text-sm font-semibold text-foreground">{facilityName}</h2>
              {missing.size > 0 ? (
                <p className="mt-1 text-sm text-muted-foreground" data-testid="missing-summary">
                  Missing governed fields:{" "}
                  {[...missing].map((m) => MISSING_FIELD_LABELS[m] ?? m).join(", ")}
                </p>
              ) : (
                <p className="mt-1 inline-flex items-center gap-1 text-sm text-success" data-testid="profile-complete">
                  <CheckCircle2 className="h-4 w-4" /> Profile is complete
                </p>
              )}
            </section>

            {mutation.isSuccess && complete ? (
              <div
                data-testid="completion-success"
                className="rounded-2xl border border-success/30 bg-success-soft/40 p-4 text-sm text-success"
              >
                <CheckCircle2 className="mr-1 inline h-4 w-4" />
                Profile completed — the registry record has been updated and versioned.
              </div>
            ) : null}

            {mutation.isError ? (
              <div
                data-testid="completion-error"
                className="rounded-2xl border border-danger/30 bg-danger-soft p-4 text-sm text-red-600"
              >
                <ShieldAlert className="mr-1 inline h-4 w-4" />
                {(mutation.error as Error | undefined)?.message?.includes("403")
                  ? "You are not an active facility administrator or practitioner-in-charge for this facility, so this governed write was refused."
                  : `Could not save the profile: ${(mutation.error as Error | undefined)?.message ?? "unknown error"}`}
              </div>
            ) : null}

            <form onSubmit={onSubmit} className="space-y-6" data-testid="complete-profile-form">
              <section className="rounded-2xl border border-border bg-card p-4">
                <h3 className="mb-3 text-sm font-medium text-foreground">Governed facility fields</h3>
                <div className="grid gap-4 sm:grid-cols-3">
                  <label className="block text-xs text-muted-foreground">
                    Facility type
                    <select
                      data-testid="field-facility-type"
                      value={form.facilityType}
                      onChange={(e) => setField("facilityType")(e.target.value)}
                      className={`mt-1 w-full rounded-lg border bg-background px-2 py-2 text-sm text-foreground ${highlight("missing_facility_type")}`}
                    >
                      <option value="">— select —</option>
                      {FACILITY_TYPES.map((t) => (
                        <option key={t} value={t}>{t.replaceAll("_", " ")}</option>
                      ))}
                    </select>
                  </label>
                  <label className="block text-xs text-muted-foreground">
                    Ownership
                    <select
                      data-testid="field-ownership"
                      value={form.ownership}
                      onChange={(e) => setField("ownership")(e.target.value)}
                      className={`mt-1 w-full rounded-lg border bg-background px-2 py-2 text-sm text-foreground ${highlight("missing_ownership")}`}
                    >
                      <option value="">— select —</option>
                      {OWNERSHIPS.map((t) => (
                        <option key={t} value={t}>{t.replaceAll("_", " ")}</option>
                      ))}
                    </select>
                  </label>
                  <label className="block text-xs text-muted-foreground">
                    Operating status
                    <select
                      data-testid="field-operating-status"
                      value={form.operationalStatus}
                      onChange={(e) => setField("operationalStatus")(e.target.value)}
                      className={`mt-1 w-full rounded-lg border bg-background px-2 py-2 text-sm text-foreground ${highlight("missing_operating_status")}`}
                    >
                      <option value="">— select —</option>
                      {OPERATING_STATUSES.map((t) => (
                        <option key={t} value={t}>{t.replaceAll("_", " ")}</option>
                      ))}
                    </select>
                  </label>
                </div>
              </section>

              <section className="rounded-2xl border border-border bg-card p-4">
                <h3 className="mb-3 text-sm font-medium text-foreground">Address</h3>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="block text-xs text-muted-foreground sm:col-span-2">
                    Street address
                    <input
                      data-testid="field-address-line1"
                      value={form.addressLine1}
                      onChange={(e) => setField("addressLine1")(e.target.value)}
                      className={`mt-1 w-full rounded-lg border bg-background px-2 py-2 text-sm text-foreground ${highlight("missing_address_line1")}`}
                      placeholder="e.g. 12 Chaminuka Road"
                    />
                  </label>
                  <label className="block text-xs text-muted-foreground sm:col-span-2">
                    Address line 2 (optional)
                    <input
                      value={form.addressLine2}
                      onChange={(e) => setField("addressLine2")(e.target.value)}
                      className="mt-1 w-full rounded-lg border border-border bg-background px-2 py-2 text-sm text-foreground"
                    />
                  </label>
                  <label className="block text-xs text-muted-foreground">
                    City / town
                    <input
                      data-testid="field-city"
                      value={form.city}
                      onChange={(e) => setField("city")(e.target.value)}
                      className={`mt-1 w-full rounded-lg border bg-background px-2 py-2 text-sm text-foreground ${highlight("missing_city")}`}
                    />
                  </label>
                  <label className="block text-xs text-muted-foreground">
                    Ward
                    <input
                      data-testid="field-ward"
                      value={form.ward}
                      onChange={(e) => setField("ward")(e.target.value)}
                      className={`mt-1 w-full rounded-lg border bg-background px-2 py-2 text-sm text-foreground ${highlight("missing_ward")}`}
                    />
                  </label>
                  <label className="block text-xs text-muted-foreground">
                    Postal code (optional)
                    <input
                      value={form.postalCode}
                      onChange={(e) => setField("postalCode")(e.target.value)}
                      className="mt-1 w-full rounded-lg border border-border bg-background px-2 py-2 text-sm text-foreground"
                    />
                  </label>
                </div>
              </section>

              <div
                className={missing.has("missing_latitude") || missing.has("missing_longitude")
                  ? "rounded-2xl ring-2 ring-warning/50"
                  : undefined}
              >
                <FacilityLocationCapture
                  value={coords}
                  onChange={(next, meta) => {
                    setCoords(next);
                    setCaptureMeta(next && meta ? meta : null);
                  }}
                  label="Facility geocode (address search, device GPS, or manual)"
                  purposeOfUse="FACILITY_ADMINISTRATION"
                />
                {captureMeta?.source === "MANUAL" ? (
                  <p className="mt-2 text-xs text-warning-foreground" data-testid="manual-geocode-note">
                    Manually entered coordinates are recorded as UNVERIFIED until field verification.
                  </p>
                ) : null}
              </div>

              <div className="flex items-center gap-3">
                <button
                  type="submit"
                  data-testid="complete-profile-submit"
                  disabled={mutation.isPending}
                  className="inline-flex items-center gap-2 rounded-xl bg-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover disabled:opacity-50"
                >
                  {mutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                  Save governed profile
                </button>
                <p className="text-xs text-muted-foreground">
                  Only active facility administrators or the practitioner-in-charge can save.
                </p>
              </div>
            </form>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
