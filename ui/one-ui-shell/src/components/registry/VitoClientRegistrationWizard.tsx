"use client";

import { useState } from "react";
import { Loader2, ShieldAlert, UserPlus } from "lucide-react";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { PatientResource } from "@/hooks/queries/usePatients";
import clientVs from "@registry-templates/terminology/client-registration-value-sets.schema.json";
import coverageVs from "@registry-templates/terminology/coverage-payment-value-sets.schema.json";
import { CountryPicker } from "./CountryPicker";
import { ZimbabweLocationCascader } from "./ZimbabweLocationCascader";

type Props = {
  facilityId?: string;
  sourceWorkflow?: string;
  onRegistered: (patient: PatientResource, meta: Record<string, unknown>) => void;
  onCancel?: () => void;
};

function entries(list: { code: string; display: string }[]) {
  return list.map((e) => (
    <option key={e.code} value={e.code}>
      {e.display}
    </option>
  ));
}

/** Parse duplicate-search payload shapes from Vito / BFF. */
function extractDuplicateRows(data: unknown): unknown[] {
  if (!data || typeof data !== "object") return [];
  const d = data as Record<string, unknown>;
  if (Array.isArray(d.candidates)) return d.candidates;
  if (Array.isArray(d.items)) return d.items;
  if (Array.isArray(d.content)) return d.content;
  if (Array.isArray(d)) return d;
  return [];
}

/**
 * Registry-backed client registration for health-worker contexts (EHR, queue, reception).
 * Posts to Experience BFF → Vito issuance path with registration-mode metadata.
 */
export function VitoClientRegistrationWizard({ facilityId, sourceWorkflow, onRegistered, onCancel }: Props) {
  const [familyName, setFamilyName] = useState("");
  const [givenName, setGivenName] = useState("");
  const [middleName, setMiddleName] = useState("");
  const [dob, setDob] = useState("");
  const [sex, setSex] = useState("male");
  const [dobCertainty, setDobCertainty] = useState("EXACT");
  const [unknownName, setUnknownName] = useState(false);
  const [registrationMode, setRegistrationMode] = useState("HEALTH_WORKER_INITIATED");
  const [initiatingContext, setInitiatingContext] = useState("FACILITY");
  const [assuranceLevel, setAssuranceLevel] = useState("ASSISTED_UNVERIFIED");
  const [identityState, setIdentityState] = useState("PROVISIONAL");
  const [offlineProvisional, setOfflineProvisional] = useState(false);
  const [consentStatus, setConsentStatus] = useState("OBTAINED");
  const [consentDeferredReason, setConsentDeferredReason] = useState("");
  const [purposeOfUse, setPurposeOfUse] = useState("TREATMENT");
  const [country, setCountry] = useState("ZW");
  const [provinceCode, setProvinceCode] = useState("");
  const [districtCode, setDistrictCode] = useState("");
  const [wardCode, setWardCode] = useState("");
  const [localityGazetteerId, setLocalityGazetteerId] = useState("");
  const [localityProposal, setLocalityProposal] = useState("");
  const [nationalId, setNationalId] = useState("");
  const [primaryPhone, setPrimaryPhone] = useState("");
  const [email, setEmail] = useState("");
  const [passportReference, setPassportReference] = useState("");
  const [addressLine, setAddressLine] = useState("");
  const [city, setCity] = useState("");
  const [preferredLanguage, setPreferredLanguage] = useState("");
  const [maritalStatus, setMaritalStatus] = useState("");
  const [emergencyContactName, setEmergencyContactName] = useState("");
  const [emergencyContactPhone, setEmergencyContactPhone] = useState("");

  const [coverageStatus, setCoverageStatus] = useState("NO_COVER");
  const [membershipNumber, setMembershipNumber] = useState("");
  const [medicalAidNumber, setMedicalAidNumber] = useState("");
  const [schemeName, setSchemeName] = useState("");
  const [planCode, setPlanCode] = useState("");
  const [patientCpidPreview, setPatientCpidPreview] = useState("");
  const [serviceCodePreview, setServiceCodePreview] = useState("");
  const [coveragePreview, setCoveragePreview] = useState<unknown>(null);
  const [coveragePreviewBusy, setCoveragePreviewBusy] = useState(false);
  const [eligibilityConsent, setEligibilityConsent] = useState("DEFERRED");

  const [dupQuery, setDupQuery] = useState("");
  const [dupLoading, setDupLoading] = useState(false);
  const [dupHits, setDupHits] = useState<unknown[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function runCoveragePreview() {
    setCoveragePreviewBusy(true);
    setError(null);
    try {
      const res = await apiClient.post<ApiResponse<unknown>>("/internal/v1/registry/coverage/preview", {
        patient_cpid: patientCpidPreview.trim() || undefined,
        plan_code: planCode.trim() || schemeName.trim() || undefined,
        service_code: serviceCodePreview.trim() || undefined,
        msika_code: "GEN-CONSULT",
      });
      setCoveragePreview(res.data);
    } catch {
      setError("Coverage / tariff preview failed.");
      setCoveragePreview(null);
    } finally {
      setCoveragePreviewBusy(false);
    }
  }

  async function runDuplicateSearch() {
    const q = dupQuery.trim() || `${givenName} ${familyName}`.trim();
    if (q.length < 2) {
      setError("Duplicate search needs at least 2 characters.");
      return;
    }
    setDupLoading(true);
    setError(null);
    try {
      const res = await apiClient.post<ApiResponse<unknown>>("/internal/v1/patients/search", { query: q });
      let payload: unknown = res.data;
      if (payload && typeof payload === "object" && "data" in (payload as Record<string, unknown>)) {
        payload = (payload as Record<string, unknown>).data;
      }
      const rows = extractDuplicateRows(payload);
      setDupHits(rows);
    } catch {
      setError("Duplicate search failed.");
      setDupHits([]);
    } finally {
      setDupLoading(false);
    }
  }

  async function submit() {
    setSubmitting(true);
    setError(null);
    try {
      const body: Record<string, unknown> = {
        family_name: familyName,
        given_name: givenName,
        middle_name: middleName || undefined,
        date_of_birth: dob,
        sex,
        dob_certainty: dobCertainty,
        unknown_name_flag: unknownName,
        registration_mode: registrationMode,
        initiating_actor: "PROVIDER",
        initiating_context: initiatingContext,
        assurance_level: assuranceLevel,
        identity_state: identityState,
        offline_provisional: offlineProvisional,
        consent_status: consentStatus,
        consent_deferred_reason: consentStatus === "DEFERRED" ? consentDeferredReason : undefined,
        purpose_of_use: purposeOfUse,
        country_alpha2: country,
        province_code: country === "ZW" ? provinceCode : undefined,
        district_code: country === "ZW" ? districtCode : undefined,
        ward_code: country === "ZW" ? wardCode : undefined,
        locality_gazetteer_id: localityGazetteerId || undefined,
        locality_proposal_text: localityProposal || undefined,
        national_id: nationalId || undefined,
        phone: primaryPhone || undefined,
        email: email || undefined,
        passport_reference: passportReference || undefined,
        address_line: addressLine || undefined,
        city: city || undefined,
        preferred_language: preferredLanguage || undefined,
        marital_status: maritalStatus || undefined,
        emergency_contact_name: emergencyContactName || undefined,
        emergency_contact_phone: emergencyContactPhone || undefined,
        facility_id: facilityId,
        source_workflow: sourceWorkflow ?? "EXPERIENCE_VITO_WIZARD",
        medical_aid_number: medicalAidNumber || undefined,
        coverage: {
          coverage_status: coverageStatus,
          membership_number: membershipNumber || undefined,
          scheme_or_insurer_name: schemeName || undefined,
          plan_code: planCode || undefined,
          eligibility_check_consent: eligibilityConsent,
        },
      };

      const res = await apiClient.post<ApiResponse<PatientResource>>("/internal/v1/patients", body);
      onRegistered(res.data, (res as unknown as { meta?: Record<string, unknown> }).meta ?? {});
    } catch {
      setError("Registration failed. If you are offline, confirm provisional capture is enabled.");
    } finally {
      setSubmitting(false);
    }
  }

  const canSubmit =
    (unknownName || (familyName.trim() && givenName.trim())) &&
    (unknownName || dob) &&
    (consentStatus !== "DEFERRED" || consentDeferredReason.trim());

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className="inline-flex items-center gap-1 rounded-full bg-warning-soft px-2 py-0.5 text-warning-foreground border border-warning/35">
          <ShieldAlert className="w-3 h-3" />
          {registrationMode.replaceAll("_", " ")}
        </span>
        <span className="inline-flex rounded-full bg-neutral-100 px-2 py-0.5 text-foreground border border-border">
          Assurance: {assuranceLevel.replaceAll("_", " ")}
        </span>
        <span className="inline-flex rounded-full bg-violet-50 px-2 py-0.5 text-violet-900 border border-violet-200">
          Identity: {identityState}
        </span>
        {offlineProvisional ? (
          <span className="inline-flex rounded-full bg-orange-50 px-2 py-0.5 text-orange-900 border border-orange-200">
            Offline provisional — queued for Vito reconciliation
          </span>
        ) : null}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Registration mode</label>
          <select
            value={registrationMode}
            onChange={(e) => setRegistrationMode(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          >
            {entries(clientVs.entries.registrationMode)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Initiating context</label>
          <select
            value={initiatingContext}
            onChange={(e) => setInitiatingContext(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          >
            {entries(clientVs.entries.initiatingContext)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Assurance level</label>
          <select
            value={assuranceLevel}
            onChange={(e) => setAssuranceLevel(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          >
            {entries(clientVs.entries.assuranceLevel)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Identity state</label>
          <select
            value={identityState}
            onChange={(e) => setIdentityState(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          >
            {entries(clientVs.entries.identityState)}
          </select>
        </div>
      </div>

      <label className="flex items-center gap-2 text-sm text-foreground">
        <input type="checkbox" checked={offlineProvisional} onChange={(e) => setOfflineProvisional(e.target.checked)} />
        Offline provisional capture (does not upgrade assurance; queues sync / dedup)
      </label>

      <label className="flex items-center gap-2 text-sm text-foreground">
        <input type="checkbox" checked={unknownName} onChange={(e) => setUnknownName(e.target.checked)} />
        Emergency unknown name
      </label>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Family name</label>
          <input
            value={familyName}
            onChange={(e) => setFamilyName(e.target.value)}
            disabled={unknownName}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Given name</label>
          <input
            value={givenName}
            onChange={(e) => setGivenName(e.target.value)}
            disabled={unknownName}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Middle name (optional)</label>
          <input
            value={middleName}
            onChange={(e) => setMiddleName(e.target.value)}
            disabled={unknownName}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Date of birth</label>
          <input
            type="date"
            value={dob}
            onChange={(e) => setDob(e.target.value)}
            disabled={unknownName}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Administrative sex (FHIR Patient.gender)</label>
          <select value={sex} onChange={(e) => setSex(e.target.value)} className="w-full px-3 py-2 border border-border rounded-lg text-sm">
            {entries(clientVs.entries.administrativeSex)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">DOB certainty</label>
          <select
            value={dobCertainty}
            onChange={(e) => setDobCertainty(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          >
            {entries(clientVs.entries.dobCertainty)}
          </select>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">National ID (optional)</label>
          <input
            value={nationalId}
            onChange={(e) => setNationalId(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Primary phone (optional)</label>
          <input
            value={primaryPhone}
            onChange={(e) => setPrimaryPhone(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
      </div>

      <div className="rounded-lg border border-border bg-card p-3 space-y-3">
        <p className="text-sm font-medium text-foreground">Extended demographics (optional, persisted via Vito)</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">Passport reference</label>
            <input
              value={passportReference}
              onChange={(e) => setPassportReference(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">Address line</label>
            <input
              value={addressLine}
              onChange={(e) => setAddressLine(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">City</label>
            <input
              value={city}
              onChange={(e) => setCity(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">Preferred language</label>
            <input
              value={preferredLanguage}
              onChange={(e) => setPreferredLanguage(e.target.value)}
              placeholder="e.g. en-ZW"
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">Marital status</label>
            <input
              value={maritalStatus}
              onChange={(e) => setMaritalStatus(e.target.value)}
              placeholder="e.g. MARRIED"
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">Emergency contact name</label>
            <input
              value={emergencyContactName}
              onChange={(e) => setEmergencyContactName(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">Emergency contact phone</label>
            <input
              value={emergencyContactPhone}
              onChange={(e) => setEmergencyContactPhone(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium text-muted-foreground mb-1">Country (ISO 3166-1)</label>
        <CountryPicker value={country} onChange={setCountry} />
      </div>
      <ZimbabweLocationCascader
        countryAlpha2={country}
        provinceCode={provinceCode}
        districtCode={districtCode}
        wardCode={wardCode}
        localityGazetteerId={localityGazetteerId}
        localityFreeText={localityProposal}
        onProvince={setProvinceCode}
        onDistrict={setDistrictCode}
        onWard={setWardCode}
        onLocalityId={setLocalityGazetteerId}
        onLocalityFreeText={setLocalityProposal}
      />

      <div className="rounded-lg border border-border bg-card p-3 space-y-2">
        <p className="text-sm font-medium text-foreground">Medical aid number (Health ID identifier)</p>
        <p className="text-xs text-muted-foreground">
          Optional person identifier tied to the Health ID in VITO (like a national ID), separate from
          scheme membership below.
        </p>
        <input
          value={medicalAidNumber}
          onChange={(e) => setMedicalAidNumber(e.target.value)}
          placeholder="e.g. MA-123456"
          aria-label="Medical aid number"
          className="w-full px-3 py-2 border border-border rounded-lg text-sm"
        />
      </div>

      <div className="rounded-lg border border-border bg-card p-3 space-y-2">
        <p className="text-sm font-medium text-foreground">Coverage / medical aid (non-blocking)</p>
        <p className="text-xs text-muted-foreground">
          Canonical coverage is owned by Vito + MusheX + COSTA. Values here are forwarded as a summary payload on
          registration; eligibility checks require MusheX consent.
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          <div>
            <label className="block text-xs text-muted-foreground mb-1">Coverage status</label>
            <select
              value={coverageStatus}
              onChange={(e) => setCoverageStatus(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            >
              {entries(coverageVs.entries.coverageStatus)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-muted-foreground mb-1">Eligibility check consent</label>
            <select
              value={eligibilityConsent}
              onChange={(e) => setEligibilityConsent(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            >
              {entries(coverageVs.entries.eligibilityCheckConsent)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-muted-foreground mb-1">Scheme / insurer name</label>
            <input
              value={schemeName}
              onChange={(e) => setSchemeName(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-muted-foreground mb-1">Scheme membership number (optional)</label>
            <input
              value={membershipNumber}
              onChange={(e) => setMembershipNumber(e.target.value)}
              placeholder="Coverage membership — not the Health ID identifier"
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-muted-foreground mb-1">Plan code (eligibility)</label>
            <input
              value={planCode}
              onChange={(e) => setPlanCode(e.target.value)}
              placeholder="Falls back to scheme name if empty"
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-muted-foreground mb-1">Patient CPID (for preview only)</label>
            <input
              value={patientCpidPreview}
              onChange={(e) => setPatientCpidPreview(e.target.value)}
              placeholder="Existing member CPID — optional"
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-muted-foreground mb-1">Service / benefit code (optional)</label>
            <input
              value={serviceCodePreview}
              onChange={(e) => setServiceCodePreview(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2 pt-1">
          <button
            type="button"
            onClick={() => void runCoveragePreview()}
            disabled={coveragePreviewBusy}
            className="text-xs px-3 py-1.5 rounded-lg border border-border bg-card hover:bg-background"
          >
            {coveragePreviewBusy ? "Preview…" : "Run eligibility + COSTA estimate preview"}
          </button>
        </div>
        {coveragePreview ? (
          <pre className="mt-2 max-h-40 overflow-auto rounded bg-neutral-900 text-foreground p-2 text-[10px]">
            {JSON.stringify(coveragePreview, null, 2)}
          </pre>
        ) : null}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Consent status</label>
          <select
            value={consentStatus}
            onChange={(e) => setConsentStatus(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          >
            {entries(clientVs.entries.consentStatus)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Purpose of use (Tshepo)</label>
          <input
            value={purposeOfUse}
            onChange={(e) => setPurposeOfUse(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
      </div>
      {consentStatus === "DEFERRED" ? (
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Consent deferred reason</label>
          <input
            value={consentDeferredReason}
            onChange={(e) => setConsentDeferredReason(e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
      ) : null}

      <div className="rounded-lg border border-border p-3 bg-background">
        <p className="text-sm font-medium text-foreground mb-2">Duplicate search (search-before-create)</p>
        <div className="flex gap-2">
          <input
            value={dupQuery}
            onChange={(e) => setDupQuery(e.target.value)}
            placeholder="Name fragment or Impilo ID"
            className="flex-1 px-3 py-2 border border-border rounded-lg text-sm"
          />
          <button
            type="button"
            onClick={() => void runDuplicateSearch()}
            disabled={dupLoading}
            className="px-3 py-2 text-sm rounded-lg bg-card border border-border hover:bg-neutral-100"
          >
            {dupLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : "Search"}
          </button>
        </div>
        {dupHits.length > 0 ? (
          <ul className="mt-2 text-xs text-foreground space-y-1 max-h-32 overflow-y-auto">
            {dupHits.map((h, i) => (
              <li key={i} className="font-mono bg-card border border-border rounded px-2 py-1">
                {JSON.stringify(h)}
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 text-xs text-muted-foreground">No duplicates loaded for this query yet.</p>
        )}
      </div>

      {error ? <div className="text-sm text-red-600">{error}</div> : null}

      <div className="flex gap-2">
        {onCancel ? (
          <button type="button" onClick={onCancel} className="px-4 py-2 text-sm rounded-lg border border-border bg-card">
            Cancel
          </button>
        ) : null}
        <button
          type="button"
          disabled={!canSubmit || submitting}
          onClick={() => void submit()}
          className="inline-flex items-center gap-2 px-4 py-2 text-sm rounded-lg bg-primary text-white hover:bg-primary-hover disabled:opacity-50"
        >
          {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <UserPlus className="w-4 h-4" />}
          Create Vito-backed client
        </button>
      </div>
    </div>
  );
}
