"use client";

/**
 * Identity Services Hub — Full Lovable parity.
 * 5 tabs: Generate, Validate, Recovery, Batch, Architecture.
 * Backed by VITO + TSHEPO-IDENTITY + VARAPI.
 */

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import {
  Shield, UserPlus, Search, RefreshCw, Layers, BookOpen,
  Loader2, CheckCircle2, AlertCircle, User, Building2, Stethoscope,
  Copy, Mail, Download, Fingerprint, FileText, Phone, AtSign, UserCheck,
} from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type ActiveTab = "generate" | "validate" | "recovery" | "batch" | "architecture";

const TABS: { key: ActiveTab; label: string; icon: typeof Shield }[] = [
  { key: "generate", label: "Generate", icon: UserPlus },
  { key: "validate", label: "Validate", icon: Search },
  { key: "recovery", label: "Recovery", icon: RefreshCw },
  { key: "batch", label: "Batch", icon: Layers },
  { key: "architecture", label: "Architecture", icon: BookOpen },
];

const PROVINCES = [
  { code: "ZW", label: "National" }, { code: "HA", label: "Harare" }, { code: "BU", label: "Bulawayo" },
  { code: "MA", label: "Manicaland" }, { code: "MW", label: "Mashonaland West" }, { code: "ME", label: "Mashonaland East" },
  { code: "MC", label: "Mashonaland Central" }, { code: "MT", label: "Matabeleland North" },
  { code: "MS", label: "Matabeleland South" }, { code: "MV", label: "Masvingo" }, { code: "MD", label: "Midlands" },
];

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text);
}

export default function IdServicesPage() {
  const searchParams = useSearchParams();
  const [activeTab, setActiveTab] = useState<ActiveTab>("generate");

  useEffect(() => {
    const requestedTab = searchParams.get("tab");
    if (requestedTab && TABS.some((tab) => tab.key === requestedTab)) {
      setActiveTab(requestedTab as ActiveTab);
    }
  }, [searchParams]);

  return (
    <AppLayout>
      <PageShell
        title="Identity Services"
        subtitle="Use the real identity contracts that exist today. Unsupported provider recovery, facility identity, and batch issuance remain visible but are not simulated."
      >
        <RegistryPlaneContextBar />
        <div className="flex gap-1 mb-6 border-b border-gray-200">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === tab.key ? "border-blue-600 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700"
                }`}>
                <Icon className="w-4 h-4" /> {tab.label}
              </button>
            );
          })}
        </div>

        {activeTab === "generate" && <GenerateTab />}
        {activeTab === "validate" && <ValidateTab />}
        {activeTab === "recovery" && <RecoveryTab />}
        {activeTab === "batch" && <BatchTab />}
        {activeTab === "architecture" && <ArchitectureTab />}
      </PageShell>
    </AppLayout>
  );
}

// ── GENERATE TAB ─────────────────────────────────────────────────
function GenerateTab() {
  return (
    <div className="grid grid-cols-3 gap-4">
      <IdGenerationCard type="patient" label="Patient PHID" format="DDDSDDDX" icon={User} color="blue" />
      <IdGenerationCard type="provider" label="Provider ID" format="VARAPI-YYYY-PP" icon={Stethoscope} color="teal" />
      <IdGenerationCard type="facility" label="Facility ID" format="THUSO-PP-NNNNNN" icon={Building2} color="purple" />
    </div>
  );
}

function IdGenerationCard({ type, label, format, icon: Icon, color }: {
  type: string; label: string; format: string;
  icon: React.ComponentType<{ className?: string }>; color: string;
}) {
  const [name, setName] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [dob, setDob] = useState("");
  const [province, setProvince] = useState("HA");
  const [email, setEmail] = useState("");
  const [generatedIds, setGeneratedIds] = useState<Record<string, string> | null>(null);

  const register = useMutation({
    mutationFn: (body: Record<string, unknown>) => {
      if (type === "patient") {
        return apiClient.post<ApiResponse<Record<string, unknown>>>(
          "/internal/v1/identity/patient/register",
          body,
        );
      }

      if (type === "provider") {
        return apiClient.post<ApiResponse<Record<string, unknown>>>(
          "/internal/v1/identity/provider/create",
          body,
        );
      }

      throw new Error("Facility identity creation is not supported on the Experience BFF.");
    },
    onSuccess: (res) => {
      const data = res?.data ?? {};
      const ids: Record<string, string> = {};
      for (const [k, v] of Object.entries(data)) {
        if (v != null && typeof v !== "object") ids[k] = String(v);
      }
      if (Object.keys(ids).length === 0) ids["ID"] = "Generated";
      setGeneratedIds(ids);
    },
  });
  const unsupportedFacilityCreation = type === "facility";
  const canGenerate = !unsupportedFacilityCreation && Boolean(name);
  const payload =
    type === "provider"
      ? {
          title: "Dr",
          givenName: name,
          familyName,
          dateOfBirth: dob || undefined,
          email: email || undefined,
          profession: "GENERAL_PRACTITIONER",
          cadre: "CLINICIAN",
          nationality: "ZW",
          gender: "UNKNOWN",
        }
      : {
          givenName: name,
          familyName,
          dateOfBirth: dob,
          province,
          idType: type,
        };

  return (
    <div data-testid={`generate-card-${type}`} className={`bg-white rounded-lg border-2 border-${color}-200 p-5 space-y-3`}>
      <div className="flex items-center gap-2 mb-1">
        <div className={`w-8 h-8 rounded-lg bg-${color}-100 flex items-center justify-center`}>
          <Icon className={`w-4 h-4 text-${color}-600`} />
        </div>
        <div>
          <h4 className="text-sm font-semibold text-gray-900">{label}</h4>
          <p className="text-[10px] font-mono text-gray-400">{format}</p>
        </div>
      </div>

      <div className="space-y-2">
        <input type="text" value={name} onChange={(e) => setName(e.target.value)}
          placeholder={type === "facility" ? "Facility name" : "Given name"}
          className="w-full px-2.5 py-1.5 text-xs border border-gray-300 rounded-lg" />
        {type !== "facility" && (
          <input type="text" value={familyName} onChange={(e) => setFamilyName(e.target.value)}
            placeholder="Family name" className="w-full px-2.5 py-1.5 text-xs border border-gray-300 rounded-lg" />
        )}
        {(type === "provider" || type === "facility") && (
          <select value={province} onChange={(e) => setProvince(e.target.value)}
            className="w-full px-2.5 py-1.5 text-xs border border-gray-300 rounded-lg">
            {PROVINCES.map((p) => <option key={p.code} value={p.code}>{p.label}</option>)}
          </select>
        )}
        {type === "patient" && (
          <input type="date" value={dob} onChange={(e) => setDob(e.target.value)}
            className="w-full px-2.5 py-1.5 text-xs border border-gray-300 rounded-lg" />
        )}
      </div>

      <button onClick={() => { setGeneratedIds(null); if (!unsupportedFacilityCreation) register.mutate(payload); }}
        disabled={register.isPending || !canGenerate}
        className={`w-full py-2 bg-${color}-600 text-white text-xs font-medium rounded-lg hover:bg-${color}-700 disabled:opacity-50 flex items-center justify-center gap-1`}>
        {register.isPending ? <Loader2 className="w-3 h-3 animate-spin" /> : <UserPlus className="w-3 h-3" />}
        {unsupportedFacilityCreation ? "Unsupported" : "Generate"}
      </button>

      {unsupportedFacilityCreation && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-[11px] text-amber-900">
          Facility identity creation is not proxied through the Experience BFF yet. Keep this flow unsupported
          until a real facility-registry create contract exists.
        </div>
      )}

      {generatedIds && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 space-y-1.5">
          {Object.entries(generatedIds).map(([k, v]) => (
            <div key={k} className="flex items-center justify-between">
              <div>
                <p className="text-[9px] text-gray-500 uppercase">{k}</p>
                <p className="text-xs font-mono font-bold text-gray-900">{v}</p>
              </div>
              <button onClick={() => copyToClipboard(v)} className="p-1 text-gray-400 hover:text-gray-600">
                <Copy className="w-3 h-3" />
              </button>
            </div>
          ))}
        </div>
      )}

      {generatedIds && (
        <div className="flex gap-1">
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
            placeholder="Email to send" className="flex-1 px-2 py-1 text-[10px] border border-gray-300 rounded" />
          <button onClick={() => { if (email && generatedIds) { navigator.clipboard.writeText(Object.entries(generatedIds).map(([k,v]) => `${k}: ${v}`).join("\n")); alert(`IDs copied. Send to ${email} via your email client.`); } }}
            className="px-2 py-1 text-[10px] bg-gray-100 text-gray-600 rounded hover:bg-gray-200" title="Copy IDs for email">
            <Mail className="w-3 h-3" />
          </button>
        </div>
      )}

      <p className="text-[9px] text-center text-gray-400">Cryptographically secure · Biometric linking available</p>
    </div>
  );
}

// ── VALIDATE TAB ─────────────────────────────────────────────────
function ValidateTab() {
  const [idType, setIdType] = useState<"patient" | "provider" | "facility">("patient");
  const [healthId, setHealthId] = useState("");

  const resolve = useMutation({
    mutationFn: async (id: string) => {
      if (idType === "patient") {
        return apiClient.post<ApiResponse<Record<string, unknown>>>(
          "/internal/v1/identity/patient/resolve",
          { healthId: id, idType },
        );
      }

      if (idType === "provider") {
        return apiClient.get<ApiResponse<Record<string, unknown>>>(
          `/internal/v1/identity/provider/${encodeURIComponent(id)}`,
        );
      }

      throw new Error("Facility identity validation is not supported on the Experience BFF.");
    },
  });

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-base font-semibold text-gray-900 mb-4">Validate Health ID</h3>
        <div className="flex gap-3 mb-4">
          {(["patient", "provider", "facility"] as const).map((t) => (
            <button key={t} onClick={() => setIdType(t)}
              className={`px-3 py-1.5 text-xs font-medium rounded-full transition-colors ${idType === t ? "bg-blue-600 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>
              {t === "patient" ? "Patient PHID" : t === "provider" ? "Provider ID" : "Facility ID"}
            </button>
          ))}
        </div>
        <div className="flex gap-3">
          <input type="text" value={healthId} onChange={(e) => setHealthId(e.target.value)}
            placeholder={`Enter ${idType === "patient" ? "PHID or CPID" : idType === "provider" ? "Provider ID" : "Facility ID"}`}
            className="flex-1 px-4 py-3 text-sm border border-gray-300 rounded-lg font-mono" />
          <button onClick={() => resolve.mutate(healthId)} disabled={resolve.isPending || !healthId || idType === "facility"}
            className="px-6 py-3 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2">
            {resolve.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />} Validate
          </button>
        </div>
        {idType === "facility" && (
          <p className="mt-3 text-xs text-amber-700">
            Facility validation stays unsupported until the facility registry exposes a canonical Experience BFF read contract.
          </p>
        )}
      </div>
      {resolve.isSuccess && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-5">
          <div className="flex items-center gap-2 mb-3"><CheckCircle2 className="w-5 h-5 text-green-600" /><h4 className="text-sm font-semibold text-green-800">Identity Verified</h4></div>
          <div className="grid grid-cols-2 gap-3">
            {Object.entries(resolve.data?.data ?? {}).filter(([, v]) => v != null && typeof v !== "object").map(([k, v]) => (
              <div key={k} className="bg-white rounded border border-green-200 p-2">
                <p className="text-[10px] text-gray-500 uppercase">{k.replace(/_/g, " ")}</p>
                <p className="text-sm font-mono text-gray-900">{String(v)}</p>
              </div>
            ))}
          </div>
        </div>
      )}
      {resolve.isError && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center gap-2">
          <AlertCircle className="w-5 h-5 text-red-500" />
          <p className="text-sm text-red-700">
            {(resolve.error as Error | null)?.message ?? "Identity not found or invalid."}
          </p>
        </div>
      )}
    </div>
  );
}

// ── RECOVERY TAB ─────────────────────────────────────────────────
type RecoveryType = "patient" | "provider";
type RecoveryMethod = "phone_otp" | "email_otp" | "id_document" | "biometric" | "provider_verify" | "professional_license" | "facility_verify";

const PATIENT_METHODS: { key: RecoveryMethod; label: string; icon: typeof Phone }[] = [
  { key: "phone_otp", label: "Phone OTP", icon: Phone },
  { key: "email_otp", label: "Email OTP", icon: AtSign },
  { key: "id_document", label: "ID Document", icon: FileText },
  { key: "biometric", label: "Biometric", icon: Fingerprint },
  { key: "provider_verify", label: "Provider Verify", icon: UserCheck },
];
const PROVIDER_METHODS: { key: RecoveryMethod; label: string; icon: typeof Phone }[] = [
  { key: "phone_otp", label: "Phone OTP", icon: Phone },
  { key: "email_otp", label: "Email OTP", icon: AtSign },
  { key: "professional_license", label: "License", icon: FileText },
  { key: "facility_verify", label: "Facility Verify", icon: Building2 },
  { key: "biometric", label: "Biometric", icon: Fingerprint },
];

function RecoveryTab() {
  const [recoveryType, setRecoveryType] = useState<RecoveryType>("patient");
  const [method, setMethod] = useState<RecoveryMethod>("phone_otp");
  const [contact, setContact] = useState("");
  const [otp, setOtp] = useState("");
  const [docNumber, setDocNumber] = useState("");
  const [fullName, setFullName] = useState("");
  const [dob, setDob] = useState("");
  const [licenseNumber, setLicenseNumber] = useState("");
  const [facilityId, setFacilityId] = useState("");
  const [step, setStep] = useState<"form" | "verify" | "result">("form");

  const startRecovery = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/identity/patient/recovery/start", body),
    onSuccess: () => setStep("verify"),
  });
  const verifyRecovery = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/identity/patient/recovery/verify", body),
    onSuccess: () => setStep("result"),
  });

  const methods = recoveryType === "patient" ? PATIENT_METHODS : PROVIDER_METHODS;

  if (recoveryType === "provider") {
    return (
      <div className="space-y-6">
        <div className="flex gap-2 mb-2">
          <button onClick={() => { setRecoveryType("patient"); setMethod("phone_otp"); setStep("form"); }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-gray-100 text-gray-600">
            Patient PHID Recovery
          </button>
          <button onClick={() => { setRecoveryType("provider"); setMethod("phone_otp"); setStep("form"); }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-teal-600 text-white">
            Provider ID Recovery
          </button>
        </div>

        <div className="rounded-lg border border-amber-200 bg-amber-50 p-5 text-sm text-amber-950">
          <p className="font-semibold">Provider recovery is not delivered in Experience yet</p>
          <p className="mt-2">
            The current Experience BFF only proxies patient recovery endpoints. Do not simulate provider recovery
            until a canonical VARAPI or trust-layer recovery contract exists.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex gap-2 mb-2">
        <button onClick={() => { setRecoveryType("patient"); setMethod("phone_otp"); setStep("form"); }}
          className={`px-4 py-2 text-sm font-medium rounded-lg ${recoveryType === "patient" ? "bg-blue-600 text-white" : "bg-gray-100 text-gray-600"}`}>
          Patient PHID Recovery
        </button>
        <button onClick={() => { setRecoveryType("provider"); setMethod("phone_otp"); setStep("form"); }}
          className="px-4 py-2 text-sm font-medium rounded-lg bg-gray-100 text-gray-600">
          Provider ID Recovery
        </button>
      </div>

      <div className="grid grid-cols-3 gap-4">
        {/* Method selector */}
        <div className="space-y-1">
          {methods.map((m) => {
            const MIcon = m.icon;
            return (
              <button key={m.key} onClick={() => { setMethod(m.key); setStep("form"); }}
                className={`w-full flex items-center gap-2 px-3 py-2.5 text-sm rounded-lg transition-colors ${
                  method === m.key ? "bg-blue-50 text-blue-700 font-medium" : "text-gray-600 hover:bg-gray-50"
                }`}>
                <MIcon className="w-4 h-4" /> {m.label}
              </button>
            );
          })}
        </div>

        {/* Form area */}
        <div className="col-span-2 bg-white rounded-lg border border-gray-200 p-5">
          {step === "form" && (
            <div className="space-y-4">
              <h4 className="text-sm font-semibold text-gray-900">Recovery via {methods.find((m) => m.key === method)?.label}</h4>

              {method === "biometric" && (
                <div className="text-center py-8">
                  <Fingerprint className="w-12 h-12 text-gray-300 mx-auto mb-3" />
                  <p className="text-sm text-gray-500">Place finger on scanner or position face for camera</p>
                  <button disabled className="mt-3 px-4 py-2 bg-gray-200 text-gray-500 text-sm rounded-lg">Waiting for hardware...</button>
                </div>
              )}

              {(method === "phone_otp" || method === "email_otp") && (
                <>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">{method === "phone_otp" ? "Phone Number" : "Email Address"}</label>
                    <input type={method === "email_otp" ? "email" : "tel"} value={contact} onChange={(e) => setContact(e.target.value)}
                      className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
                  </div>
                  <button onClick={() => startRecovery.mutate({ contact, method, recoveryType })} disabled={startRecovery.isPending || !contact}
                    className="w-full py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">
                    {startRecovery.isPending ? "Sending..." : "Request OTP"}
                  </button>
                </>
              )}

              {method === "id_document" && (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Document Number</label>
                      <input type="text" value={docNumber} onChange={(e) => setDocNumber(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" placeholder="National ID or passport" /></div>
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Full Name</label>
                      <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Date of Birth</label>
                      <input type="date" value={dob} onChange={(e) => setDob(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                  </div>
                  <button onClick={() => startRecovery.mutate({ documentNumber: docNumber, fullName, dateOfBirth: dob, method })} disabled={startRecovery.isPending}
                    className="w-full py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">Recover</button>
                </>
              )}

              {method === "provider_verify" && (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Provider ID</label>
                      <input type="text" value={docNumber} onChange={(e) => setDocNumber(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Patient Name</label>
                      <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Date of Birth</label>
                      <input type="date" value={dob} onChange={(e) => setDob(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                  </div>
                  <button onClick={() => startRecovery.mutate({ providerId: docNumber, patientName: fullName, dateOfBirth: dob, method })} disabled={startRecovery.isPending}
                    className="w-full py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">Verify & Recover</button>
                </>
              )}

              {method === "professional_license" && (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">License Number</label>
                      <input type="text" value={licenseNumber} onChange={(e) => setLicenseNumber(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Full Name</label>
                      <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                  </div>
                  <button onClick={() => startRecovery.mutate({ licenseNumber, fullName, method })} disabled={startRecovery.isPending}
                    className="w-full py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">Recover via License</button>
                </>
              )}

              {method === "facility_verify" && (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Facility ID</label>
                      <input type="text" value={facilityId} onChange={(e) => setFacilityId(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Employee ID</label>
                      <input type="text" value={docNumber} onChange={(e) => setDocNumber(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-gray-600 mb-1">Full Name</label>
                      <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" /></div>
                  </div>
                  <button onClick={() => startRecovery.mutate({ facilityId, employeeId: docNumber, fullName, method })} disabled={startRecovery.isPending}
                    className="w-full py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">Recover via Facility</button>
                </>
              )}
            </div>
          )}

          {step === "verify" && (
            <div className="space-y-4">
              <h4 className="text-sm font-semibold text-gray-900">Enter Verification Code</h4>
              <input type="text" value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="6-digit code" maxLength={6}
                className="w-full px-4 py-3 text-lg text-center tracking-widest border border-gray-300 rounded-lg font-mono" />
              <div className="flex gap-3">
                <button onClick={() => setStep("form")} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Back</button>
                <button onClick={() => verifyRecovery.mutate({ contact, otp, method })} disabled={verifyRecovery.isPending || otp.length < 4}
                  className="flex-1 py-2 bg-green-600 text-white text-sm rounded-lg hover:bg-green-700 disabled:opacity-50">
                  {verifyRecovery.isPending ? "Verifying..." : "Verify & Recover"}
                </button>
              </div>
            </div>
          )}

          {step === "result" && (
            <div className="text-center py-6">
              <CheckCircle2 className="w-12 h-12 text-green-500 mx-auto mb-3" />
              <h4 className="text-base font-semibold text-gray-900">ID Recovered Successfully</h4>
              <p className="text-sm text-gray-500 mt-1">Your health ID has been recovered and verified.</p>
              <button onClick={() => { setStep("form"); setOtp(""); }} className="mt-4 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700">Start New Recovery</button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ── BATCH TAB ────────────────────────────────────────────────────
function BatchTab() {
  const [count, setCount] = useState(10);
  const [facility, setFacility] = useState("Harare Central Hospital");
  const [generated, setGenerated] = useState<string[]>([]);
  const batchIssuanceSupported = false;

  if (!batchIssuanceSupported) {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
        <div className="flex items-start gap-3">
          <Layers className="mt-0.5 h-5 w-5 shrink-0 text-amber-700" />
          <div>
            <h3 className="text-base font-semibold">Batch identity issuance is intentionally unsupported</h3>
            <p className="mt-2">
              Experience will not deliver browser-generated PHIDs or CSV exports. The accepted surface now blocks this
              until a real backend contract exists for reserve, issue, and audited download.
            </p>
            <p className="mt-3 text-xs text-amber-900/80">
              Required contract: a canonical Experience BFF route for batch issuance with facility scope, audit
              metadata, and server-generated identifiers.
            </p>
          </div>
        </div>
      </div>
    );
  }

  const FACILITIES = ["Harare Central Hospital", "Parirenyatwa Group", "Chitungwiza Central", "Mpilo Central", "United Bulawayo"];

  function handleGenerate() {
    // Generate client-side batch of PHID-format IDs
    const ids: string[] = [];
    for (let i = 0; i < count; i++) {
      const d1 = String(Math.floor(Math.random() * 1000)).padStart(3, "0");
      const s = String.fromCharCode(65 + Math.floor(Math.random() * 26));
      const d2 = String(Math.floor(Math.random() * 1000)).padStart(3, "0");
      const check = (parseInt(d1 + d2) % 10);
      ids.push(`${d1}${s}${d2}${check}`);
    }
    setGenerated(ids);
  }

  function downloadCsv() {
    const csv = "PHID,Facility,GeneratedAt\n" + generated.map((id) => `${id},${facility},${new Date().toISOString()}`).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = `batch-phids-${generated.length}.csv`; a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg border border-gray-200 p-6 space-y-4">
        <h3 className="text-base font-semibold text-gray-900">Batch ID Generation</h3>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Facility</label>
            <select value={facility} onChange={(e) => setFacility(e.target.value)} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg">
              {FACILITIES.map((f) => <option key={f} value={f}>{f}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Batch Size</label>
            <input type="number" min={1} max={1000} value={count} onChange={(e) => setCount(Number(e.target.value))} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
        </div>
        <button onClick={handleGenerate} className="w-full py-2.5 bg-purple-600 text-white text-sm font-medium rounded-lg hover:bg-purple-700 flex items-center justify-center gap-2">
          <Layers className="w-4 h-4" /> Generate {count} IDs
        </button>
      </div>

      {generated.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <div className="flex items-center justify-between mb-3">
            <div>
              <p className="text-sm font-semibold text-gray-900">{generated.length} IDs Generated</p>
              <p className="text-xs text-gray-500">{facility} · {new Date().toLocaleDateString()}</p>
            </div>
            <button onClick={downloadCsv} className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-green-600 text-white rounded-lg hover:bg-green-700">
              <Download className="w-4 h-4" /> Download CSV
            </button>
          </div>
          <div className="max-h-48 overflow-y-auto bg-gray-50 rounded-lg p-3 font-mono text-xs space-y-0.5">
            {generated.slice(0, 100).map((id, i) => (
              <div key={i} className="flex items-center justify-between py-0.5">
                <span className="text-gray-700">{id}</span>
                <button onClick={() => copyToClipboard(id)} className="text-gray-400 hover:text-gray-600"><Copy className="w-3 h-3" /></button>
              </div>
            ))}
            {generated.length > 100 && <p className="text-gray-400 pt-1">+{generated.length - 100} more...</p>}
          </div>
        </div>
      )}
    </div>
  );
}

// ── ARCHITECTURE TAB ─────────────────────────────────────────────
function ArchitectureTab() {
  const cards = [
    { title: "Patient PHID Architecture", color: "border-blue-200 bg-blue-50",
      points: ["Portable Token — PHID travels with the patient across facilities", "Biometric = PHID — fingerprint/face binds to unique identity", "Multi-Recovery — 5 recovery methods for lost IDs"],
      flow: "PHID → Client Registry ID → SHR ID → Health Record" },
    { title: "Provider ID Architecture", color: "border-teal-200 bg-teal-50",
      points: ["VARAPI Format — province-coded provider identifier", "Biometric Binding — practitioner biometric links to provider ID", "Facility Verification — employment-based identity confirmation"],
      flow: "Provider ID → iHRIS Registry → Professional License → Access" },
    { title: "Facility ID Architecture", color: "border-purple-200 bg-purple-50",
      points: ["THUSO Format — province-coded facility identifier", "GOFR Integration — linked to national facility registry"],
      flow: "Facility ID → GOFR → Location Data → Services" },
    { title: "Security Model", color: "border-gray-200 bg-gray-50",
      points: ["Luhn Check Digit — format validation at entry point", "Web Crypto API — cryptographic ID generation", "Database Sequences — guaranteed uniqueness", "Audit Logging — every generation/validation tracked"],
      flow: "" },
  ];

  return (
    <div className="grid grid-cols-2 gap-4">
      {cards.map((card) => (
        <div key={card.title} className={`rounded-lg border-2 p-5 ${card.color}`}>
          <h4 className="text-sm font-semibold text-gray-900 mb-3">{card.title}</h4>
          <ul className="space-y-2 mb-3">
            {card.points.map((point) => {
              const [bold, ...rest] = point.split(" — ");
              return (
                <li key={bold} className="text-xs text-gray-700">
                  <span className="font-medium">{bold}</span>{rest.length > 0 && ` — ${rest.join(" — ")}`}
                </li>
              );
            })}
          </ul>
          {card.flow && (
            <div className="bg-white/60 rounded p-2 text-[10px] font-mono text-gray-500 text-center">{card.flow}</div>
          )}
        </div>
      ))}
    </div>
  );
}
