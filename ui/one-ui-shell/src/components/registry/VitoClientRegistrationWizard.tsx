"use client";

import { useState } from "react";
import { Loader2, ShieldAlert, UserPlus } from "lucide-react";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { PatientResource } from "@/hooks/queries/usePatients";
import { showBackendPendingToast } from "@/hooks/useBackendPendingToast";
import { PendingApiRoute } from "@/lib/experience/pending-api-routes";
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
  const [passportReference, setPassportReference] = useState("");
  const [primaryPhone, setPrimaryPhone] = useState("");
  const [email, setEmail] = useState("");
  const [addressLine1, setAddressLine1] = useState("");
  const [city, setCity] = useState("");
  const [districtLabel, setDistrictLabel] = useState("");
  const [provinceLabel, setProvinceLabel] = useState("");
  const [preferredLanguage, setPreferredLanguage] = useState("en");
  const [maritalStatus, setMaritalStatus] = useState("");
  const [emergencyContactName, setEmergencyContactName] = useState("");
  const [emergencyContactPhone, setEmergencyContactPhone] = useState("");
  const [lastFhirPreview, setLastFhirPreview] = useState<unknown>(null);

  const [coverageStatus, setCoverageStatus] = useState("NO_COVER");
  const [membershipNumber, setMembershipNumber] = useState("");
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

  function runCoveragePreview() {
    showBackendPendingToast(
      PendingApiRoute.RegistryCoveragePreview,
      "Coverage & COSTA eligibility preview",
    );
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
        middle_name: middleName.trim() || undefined,
        date_of_birth: dob,
        sex,
        email: email.trim() || undefined,
        passport_reference: passportReference.trim() || undefined,
        address_line1: addressLine1.trim() || undefined,
        city: city.trim() || undefined,
        district: districtLabel.trim() || undefined,
        province: provinceLabel.trim() || undefined,
        preferred_language: preferredLanguage,
        marital_status: maritalStatus.trim() || undefined,
        emergency_contact_name: emergencyContactName.trim() || undefined,
        emergency_contact_phone: emergencyContactPhone.trim() || undefined,
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
        facility_id: facilityId,
        source_workflow: sourceWorkflow ?? "EXPERIENCE_VITO_WIZARD",
        coverage: {
          coverage_status: coverageStatus,
          membership_number: membershipNumber || undefined,
          scheme_or_insurer_name: schemeName || undefined,
          plan_code: planCode || undefined,
          eligibility_check_consent: eligibilityConsent,
        },
      };

      const res = await apiClient.post<
        ApiResponse<PatientResource> & { fhir?: { Patient?: unknown }; meta?: Record<string, unknown> }
      >("/internal/v1/patients", body);
      const envelope = res as ApiResponse<PatientResource> & {
        fhir?: { Patient?: unknown };
        meta?: Record<string, unknown>;
      };
      if (envelope.fhir?.Patient) {
        setLastFhirPreview(envelope.fhir.Patient);
      }
      onRegistered(res.data, envelope.meta ?? {});
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
    <div className="space-y-8">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-amber-900 border border-amber-200">
          <ShieldAlert className="w-3 h-3" />
          {registrationMode.replaceAll("_", " ")}
        </span>
        <span className="inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-slate-800 border border-slate-200">
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
          <label className="block text-xs font-medium text-gray-600 mb-1">Registration mode</label>
          <select
            value={registrationMode}
            onChange={(e) => setRegistrationMode(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            {entries(clientVs.entries.registrationMode)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Initiating context</label>
          <select
            value={initiatingContext}
            onChange={(e) => setInitiatingContext(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            {entries(clientVs.entries.initiatingContext)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Assurance level</label>
          <select
            value={assuranceLevel}
            onChange={(e) => setAssuranceLevel(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            {entries(clientVs.entries.assuranceLevel)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Identity state</label>
          <select
            value={identityState}
            onChange={(e) => setIdentityState(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            {entries(clientVs.entries.identityState)}
          </select>
        </div>
      </div>

      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input type="checkbox" checked={offlineProvisional} onChange={(e) => setOfflineProvisional(e.target.checked)} />
        Offline provisional capture (does not upgrade assurance; queues sync / dedup)
      </label>

      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input type="checkbox" checked={unknownName} onChange={(e) => setUnknownName(e.target.checked)} />
        Emergency unknown name
      </label>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Family name</label>
          <input
            value={familyName}
            onChange={(e) => setFamilyName(e.target.value)}
            disabled={unknownName}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Given name</label>
          <input
            value={givenName}
            onChange={(e) => setGivenName(e.target.value)}
            disabled={unknownName}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Middle name (optional)</label>
          <input
            value={middleName}
            onChange={(e) => setMiddleName(e.target.value)}
            disabled={unknownName}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Date of birth</label>
          <input
            type="date"
            value={dob}
            onChange={(e) => setDob(e.target.value)}
            disabled={unknownName}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Administrative sex (FHIR Patient.gender)</label>
          <select value={sex} onChange={(e) => setSex(e.target.value)} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm">
            {entries(clientVs.entries.administrativeSex)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">DOB certainty</label>
          <select
            value={dobCertainty}
            onChange={(e) => setDobCertainty(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            {entries(clientVs.entries.dobCertainty)}
          </select>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">National ID (optional)</label>
          <input
            value={nationalId}
            onChange={(e) => setNationalId(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Primary phone (optional)</label>
          <input
            value={primaryPhone}
            onChange={(e) => setPrimaryPhone(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Email (FHIR telecom)</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Passport reference</label>
          <input
            value={passportReference}
            onChange={(e) => setPassportReference(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Marital status</label>
          <input
            value={maritalStatus}
            onChange={(e) => setMaritalStatus(e.target.value)}
            placeholder="e.g. M, S, W"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Preferred language</label>
          <select
            value={preferredLanguage}
            onChange={(e) => setPreferredLanguage(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            <option value="en">English</option>
            <option value="sn">Shona</option>
            <option value="nd">Ndebele</option>
          </select>
        </div>
      </div>

      <p className="text-sm font-semibold text-gray-900">Contact & address</p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="sm:col-span-2">
          <label className="block text-xs font-medium text-gray-600 mb-1">Address line</label>
          <input
            value={addressLine1}
            onChange={(e) => setAddressLine1(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">City / locality</label>
          <input
            value={city}
            onChange={(e) => setCity(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">District</label>
          <input
            value={districtLabel}
            onChange={(e) => setDistrictLabel(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Province / state</label>
          <input
            value={provinceLabel}
            onChange={(e) => setProvinceLabel(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Emergency contact name</label>
          <input
            value={emergencyContactName}
            onChange={(e) => setEmergencyContactName(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Emergency contact phone</label>
          <input
            value={emergencyContactPhone}
            onChange={(e) => setEmergencyContactPhone(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Country (ISO 3166-1)</label>
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

      <div className="rounded-lg border border-gray-200 bg-white p-3 space-y-2">
        <p className="text-sm font-medium text-gray-900">Coverage / medical aid (non-blocking)</p>
        <p className="text-xs text-gray-500">
          Canonical coverage is owned by Vito + MusheX + COSTA. Values here are forwarded as a summary payload on
          registration; eligibility checks require MusheX consent.
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          <div>
            <label className="block text-xs text-gray-600 mb-1">Coverage status</label>
            <select
              value={coverageStatus}
              onChange={(e) => setCoverageStatus(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              {entries(coverageVs.entries.coverageStatus)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Eligibility check consent</label>
            <select
              value={eligibilityConsent}
              onChange={(e) => setEligibilityConsent(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              {entries(coverageVs.entries.eligibilityCheckConsent)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Scheme / insurer name</label>
            <input
              value={schemeName}
              onChange={(e) => setSchemeName(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Membership number</label>
            <input
              value={membershipNumber}
              onChange={(e) => setMembershipNumber(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Plan code (eligibility)</label>
            <input
              value={planCode}
              onChange={(e) => setPlanCode(e.target.value)}
              placeholder="Falls back to scheme name if empty"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Patient CPID (for preview only)</label>
            <input
              value={patientCpidPreview}
              onChange={(e) => setPatientCpidPreview(e.target.value)}
              placeholder="Existing member CPID — optional"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Service / benefit code (optional)</label>
            <input
              value={serviceCodePreview}
              onChange={(e) => setServiceCodePreview(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2 pt-2">
          <button
            type="button"
            onClick={runCoveragePreview}
            className="text-xs px-3 py-2 rounded-lg border border-gray-300 bg-white hover:bg-gray-50"
          >
            Run eligibility + COSTA estimate preview
          </button>
        </div>
      </div>

      <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
        <p className="text-sm font-semibold text-gray-900">Downstream services (via Experience BFF)</p>
        <p className="text-xs text-gray-600 leading-relaxed">
          Identity is issued in VITO. Clinical SHR (HAPI FHIR R4 / BUTANO), PCT journey, and biometrics use separate
          governed routes — available after create when BFF endpoints are live.
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => showBackendPendingToast(PendingApiRoute.FhirShrPatientWrite, "FHIR SHR Patient write")}
            className="text-xs px-3 py-2 rounded-lg border bg-white hover:bg-gray-50"
          >
            Sync to HAPI FHIR SHR
          </button>
          <button
            type="button"
            onClick={() => showBackendPendingToast(PendingApiRoute.PctJourneyAutoStart, "PCT care journey")}
            className="text-xs px-3 py-2 rounded-lg border bg-white hover:bg-gray-50"
          >
            Start PCT journey
          </button>
          <button
            type="button"
            onClick={() => showBackendPendingToast(PendingApiRoute.PatientBiometricCapture, "Biometric capture")}
            className="text-xs px-3 py-2 rounded-lg border bg-white hover:bg-gray-50"
          >
            Biometrics
          </button>
          <button
            type="button"
            onClick={() => showBackendPendingToast(PendingApiRoute.PatientPhotoCapture, "Patient photo")}
            className="text-xs px-3 py-2 rounded-lg border bg-white hover:bg-gray-50"
          >
            Photo capture
          </button>
        </div>
        {lastFhirPreview ? (
          <pre className="max-h-36 overflow-auto rounded-lg bg-slate-900 text-slate-100 p-3 text-[10px]">
            {JSON.stringify(lastFhirPreview, null, 2)}
          </pre>
        ) : null}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Consent status</label>
          <select
            value={consentStatus}
            onChange={(e) => setConsentStatus(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            {entries(clientVs.entries.consentStatus)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Purpose of use (Tshepo)</label>
          <input
            value={purposeOfUse}
            onChange={(e) => setPurposeOfUse(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
      </div>
      {consentStatus === "DEFERRED" ? (
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Consent deferred reason</label>
          <input
            value={consentDeferredReason}
            onChange={(e) => setConsentDeferredReason(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
      ) : null}

      <div className="rounded-lg border border-gray-200 p-3 bg-gray-50">
        <p className="text-sm font-medium text-gray-900 mb-2">Duplicate search (search-before-create)</p>
        <div className="flex gap-2">
          <input
            value={dupQuery}
            onChange={(e) => setDupQuery(e.target.value)}
            placeholder="Name fragment or Impilo ID"
            className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
          <button
            type="button"
            onClick={() => void runDuplicateSearch()}
            disabled={dupLoading}
            className="px-3 py-2 text-sm rounded-lg bg-white border border-gray-300 hover:bg-gray-100"
          >
            {dupLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : "Search"}
          </button>
        </div>
        {dupHits.length > 0 ? (
          <ul className="mt-2 text-xs text-gray-700 space-y-1 max-h-32 overflow-y-auto">
            {dupHits.map((h, i) => (
              <li key={i} className="font-mono bg-white border border-gray-200 rounded px-2 py-1">
                {JSON.stringify(h)}
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 text-xs text-gray-500">No duplicates loaded for this query yet.</p>
        )}
      </div>

      {error ? <div className="text-sm text-red-600">{error}</div> : null}

      <div className="flex gap-2">
        {onCancel ? (
          <button type="button" onClick={onCancel} className="px-4 py-2 text-sm rounded-lg border border-gray-300 bg-white">
            Cancel
          </button>
        ) : null}
        <button
          type="button"
          disabled={!canSubmit || submitting}
          onClick={() => void submit()}
          className="inline-flex items-center gap-2 px-4 py-2 text-sm rounded-lg bg-impilo-500 text-white hover:bg-impilo-600 disabled:opacity-50"
        >
          {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <UserPlus className="w-4 h-4" />}
          Create Vito-backed client
        </button>
      </div>
    </div>
  );
}
