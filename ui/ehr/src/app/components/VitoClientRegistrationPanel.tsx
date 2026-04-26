"use client";

import { useState } from "react";
import type { Patient } from "@/stores/ehrStore";
import { apiClient, patientResourceToPatient } from "@/lib/apiClient";
import clientVs from "@registry-templates/terminology/client-registration-value-sets.schema.json";
import { CountryPicker } from "./registry/CountryPicker";
import { ZimbabweLocationCascader } from "./registry/ZimbabweLocationCascader";

type Props = {
  onRegistered: (patient: Patient) => void;
};

function options(list: { code: string; display: string }[]) {
  return list.map((e) => (
    <option key={e.code} value={e.code}>
      {e.display}
    </option>
  ));
}

/**
 * EHR shell: Vito-backed new client registration (no local patient-first model).
 * Delegates to Experience BFF → Vito; surfaces registry mode / assurance badges via returned attributes.
 */
export function VitoClientRegistrationPanel({ onRegistered }: Props) {
  const [familyName, setFamilyName] = useState("");
  const [givenName, setGivenName] = useState("");
  const [dob, setDob] = useState("");
  const [sex, setSex] = useState("male");
  const [registrationMode, setRegistrationMode] = useState("HEALTH_WORKER_INITIATED");
  const [initiatingContext, setInitiatingContext] = useState("FACILITY");
  const [assuranceLevel, setAssuranceLevel] = useState("ASSISTED_UNVERIFIED");
  const [identityState, setIdentityState] = useState("PROVISIONAL");
  const [offlineProvisional, setOfflineProvisional] = useState(false);
  const [country, setCountry] = useState("ZW");
  const [provinceCode, setProvinceCode] = useState("");
  const [districtCode, setDistrictCode] = useState("");
  const [wardCode, setWardCode] = useState("");
  const [localityGazetteerId, setLocalityGazetteerId] = useState("");
  const [localityProposal, setLocalityProposal] = useState("");
  const [consentStatus, setConsentStatus] = useState("OBTAINED");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function submit() {
    setBusy(true);
    setErr(null);
    try {
      const res = await apiClient.registerPatient({
        family_name: familyName,
        given_name: givenName,
        date_of_birth: dob,
        sex,
        registration_mode: registrationMode,
        initiating_actor: "PROVIDER",
        initiating_context: initiatingContext,
        assurance_level: assuranceLevel,
        identity_state: identityState,
        offline_provisional: offlineProvisional,
        consent_status: consentStatus,
        country_alpha2: country,
        province_code: country === "ZW" ? provinceCode : undefined,
        district_code: country === "ZW" ? districtCode : undefined,
        ward_code: country === "ZW" ? wardCode : undefined,
        locality_gazetteer_id: localityGazetteerId || undefined,
        locality_proposal_text: localityProposal || undefined,
        purpose_of_use: "TREATMENT",
        source_workflow: "EHR_STUB",
      });
      onRegistered(patientResourceToPatient(res.data));
    } catch {
      setErr("Registration failed (check BFF/Vito availability and trust headers).");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mt-4 rounded-md border border-impilo-primary/30 bg-slate-50 p-3 space-y-2 text-sm">
      <p className="font-semibold text-impilo-primary">New client (Vito registry)</p>
      <p className="text-xs text-gray-600">
        This flow does not create a purely local patient record first. The BFF issues or falls back with explicit
        registry sync metadata.
      </p>
      <div className="flex flex-wrap gap-1 text-[10px]">
        <span className="rounded bg-amber-100 px-1.5 py-0.5 text-amber-900">{registrationMode}</span>
        <span className="rounded bg-violet-100 px-1.5 py-0.5 text-violet-900">{assuranceLevel}</span>
        <span className="rounded bg-slate-200 px-1.5 py-0.5 text-slate-800">{identityState}</span>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <input
          placeholder="Family name"
          value={familyName}
          onChange={(e) => setFamilyName(e.target.value)}
          className="px-2 py-1 border rounded text-sm"
        />
        <input
          placeholder="Given name"
          value={givenName}
          onChange={(e) => setGivenName(e.target.value)}
          className="px-2 py-1 border rounded text-sm"
        />
        <input type="date" value={dob} onChange={(e) => setDob(e.target.value)} className="px-2 py-1 border rounded text-sm" />
        <select value={sex} onChange={(e) => setSex(e.target.value)} className="px-2 py-1 border rounded text-sm">
          {options(clientVs.entries.administrativeSex)}
        </select>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <select
          value={registrationMode}
          onChange={(e) => setRegistrationMode(e.target.value)}
          className="px-2 py-1 border rounded text-xs"
        >
          {options(clientVs.entries.registrationMode)}
        </select>
        <select
          value={initiatingContext}
          onChange={(e) => setInitiatingContext(e.target.value)}
          className="px-2 py-1 border rounded text-xs"
        >
          {options(clientVs.entries.initiatingContext)}
        </select>
        <select
          value={assuranceLevel}
          onChange={(e) => setAssuranceLevel(e.target.value)}
          className="px-2 py-1 border rounded text-xs"
        >
          {options(clientVs.entries.assuranceLevel)}
        </select>
        <select
          value={identityState}
          onChange={(e) => setIdentityState(e.target.value)}
          className="px-2 py-1 border rounded text-xs"
        >
          {options(clientVs.entries.identityState)}
        </select>
      </div>
      <label className="flex items-center gap-2 text-xs">
        <input type="checkbox" checked={offlineProvisional} onChange={(e) => setOfflineProvisional(e.target.checked)} />
        Offline provisional
      </label>
      <div>
        <label className="text-xs text-gray-600">Country</label>
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
      <select value={consentStatus} onChange={(e) => setConsentStatus(e.target.value)} className="w-full px-2 py-1 border rounded text-xs">
        {options(clientVs.entries.consentStatus)}
      </select>
      {err ? <p className="text-xs text-red-600">{err}</p> : null}
      <button
        type="button"
        disabled={busy || !familyName || !givenName || !dob}
        onClick={() => void submit()}
        className="w-full py-1.5 rounded bg-impilo-primary text-white text-sm disabled:opacity-50"
      >
        {busy ? "Saving…" : "Register via Vito"}
      </button>
    </div>
  );
}
