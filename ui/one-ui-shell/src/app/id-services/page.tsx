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
        <div className="flex gap-1 mb-6 border-b border-border">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === tab.key ? "border-impilo-500 text-primary" : "border-transparent text-muted-foreground hover:text-foreground"
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
  const [sendSuccess, setSendSuccess] = useState(false);

  const sendIds = useMutation({
    mutationFn: (payload: { email: string; ids: Record<string, string> }) =>
      apiClient.post("/internal/v1/notifications/send", {
        channel: "EMAIL",
        recipient: payload.email,
        subject: "Your Impilo Health IDs",
        body: Object.entries(payload.ids).map(([k, v]) => `${k}: ${v}`).join("\n"),
      }),
    onSuccess: () => {
      setSendSuccess(true);
      setTimeout(() => setSendSuccess(false), 4000);
    },
  });

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
    <div data-testid={`generate-card-${type}`} className={`bg-card rounded-lg border-2 border-${color}-200 p-5 space-y-3`}>
      <div className="flex items-center gap-2 mb-1">
        <div className={`w-8 h-8 rounded-lg bg-${color}-100 flex items-center justify-center`}>
          <Icon className={`w-4 h-4 text-${color}-600`} />
        </div>
        <div>
          <h4 className="text-sm font-semibold text-foreground">{label}</h4>
          <p className="text-[10px] font-mono text-muted-foreground">{format}</p>
        </div>
      </div>

      <div className="space-y-2">
        <input type="text" value={name} onChange={(e) => setName(e.target.value)}
          placeholder={type === "facility" ? "Facility name" : "Given name"}
          className="w-full px-2.5 py-1.5 text-xs border border-border rounded-lg" />
        {type !== "facility" && (
          <input type="text" value={familyName} onChange={(e) => setFamilyName(e.target.value)}
            placeholder="Family name" className="w-full px-2.5 py-1.5 text-xs border border-border rounded-lg" />
        )}
        {(type === "provider" || type === "facility") && (
          <select value={province} onChange={(e) => setProvince(e.target.value)}
            className="w-full px-2.5 py-1.5 text-xs border border-border rounded-lg">
            {PROVINCES.map((p) => <option key={p.code} value={p.code}>{p.label}</option>)}
          </select>
        )}
        {type === "patient" && (
          <input type="date" value={dob} onChange={(e) => setDob(e.target.value)}
            className="w-full px-2.5 py-1.5 text-xs border border-border rounded-lg" />
        )}
      </div>

      <button onClick={() => { setGeneratedIds(null); if (!unsupportedFacilityCreation) register.mutate(payload); }}
        disabled={register.isPending || !canGenerate}
        className={`w-full py-2 bg-${color}-600 text-white text-xs font-medium rounded-lg hover:bg-${color}-700 disabled:opacity-50 flex items-center justify-center gap-1`}>
        {register.isPending ? <Loader2 className="w-3 h-3 animate-spin" /> : <UserPlus className="w-3 h-3" />}
        {unsupportedFacilityCreation ? "Unsupported" : "Generate"}
      </button>

      {unsupportedFacilityCreation && (
        <div className="bg-warning-soft border border-warning/35 rounded-lg p-3 text-[11px] text-warning-foreground">
          Facility identity creation is not proxied through the Experience BFF yet. Keep this flow unsupported
          until a real facility-registry create contract exists.
        </div>
      )}

      {generatedIds && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 space-y-1.5">
          {Object.entries(generatedIds).map(([k, v]) => (
            <div key={k} className="flex items-center justify-between">
              <div>
                <p className="text-[9px] text-muted-foreground uppercase">{k}</p>
                <p className="text-xs font-mono font-bold text-foreground">{v}</p>
              </div>
              <button onClick={() => copyToClipboard(v)} className="p-1 text-muted-foreground hover:text-muted-foreground">
                <Copy className="w-3 h-3" />
              </button>
            </div>
          ))}
        </div>
      )}

      {generatedIds && (
        <>
          <div className="flex gap-1">
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              placeholder="Email to send" className="flex-1 px-2 py-1 text-[10px] border border-border rounded" />
            <button
              onClick={() => { if (email && generatedIds) sendIds.mutate({ email, ids: generatedIds }); }}
              disabled={sendIds.isPending || !email}
              className="px-2 py-1 text-[10px] bg-neutral-100 text-muted-foreground rounded hover:bg-neutral-100 disabled:opacity-50 flex items-center gap-0.5" title="Send IDs via email">
              {sendIds.isPending ? <Loader2 className="w-3 h-3 animate-spin" /> : <Mail className="w-3 h-3" />}
            </button>
          </div>
          {sendSuccess && (
            <p className="text-[10px] text-green-700">IDs sent to {email} successfully.</p>
          )}
          {sendIds.isError && (
            <p className="text-[10px] text-red-600">Failed to send IDs. Please try again.</p>
          )}
        </>
      )}

      <p className="text-[9px] text-center text-muted-foreground">Cryptographically secure · Biometric linking available</p>
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
      <div className="bg-card rounded-lg border border-border p-6">
        <h3 className="text-base font-semibold text-foreground mb-4">Validate Health ID</h3>
        <div className="flex gap-3 mb-4">
          {(["patient", "provider", "facility"] as const).map((t) => (
            <button key={t} onClick={() => setIdType(t)}
              className={`px-3 py-1.5 text-xs font-medium rounded-full transition-colors ${idType === t ? "bg-primary text-white" : "bg-neutral-100 text-muted-foreground hover:bg-neutral-100"}`}>
              {t === "patient" ? "Patient PHID" : t === "provider" ? "Provider ID" : "Facility ID"}
            </button>
          ))}
        </div>
        <div className="flex gap-3">
          <input type="text" value={healthId} onChange={(e) => setHealthId(e.target.value)}
            placeholder={`Enter ${idType === "patient" ? "PHID or CPID" : idType === "provider" ? "Provider ID" : "Facility ID"}`}
            className="flex-1 px-4 py-3 text-sm border border-border rounded-lg font-mono" />
          <button onClick={() => resolve.mutate(healthId)} disabled={resolve.isPending || !healthId || idType === "facility"}
            className="px-6 py-3 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50 flex items-center gap-2">
            {resolve.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />} Validate
          </button>
        </div>
        {idType === "facility" && (
          <p className="mt-3 text-xs text-warning-foreground">
            Facility validation stays unsupported until the facility registry exposes a canonical Experience BFF read contract.
          </p>
        )}
      </div>
      {resolve.isSuccess && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-5">
          <div className="flex items-center gap-2 mb-3"><CheckCircle2 className="w-5 h-5 text-green-600" /><h4 className="text-sm font-semibold text-green-800">Identity Verified</h4></div>
          <div className="grid grid-cols-2 gap-3">
            {Object.entries(resolve.data?.data ?? {}).filter(([, v]) => v != null && typeof v !== "object").map(([k, v]) => (
              <div key={k} className="bg-card rounded border border-green-200 p-2">
                <p className="text-[10px] text-muted-foreground uppercase">{k.replace(/_/g, " ")}</p>
                <p className="text-sm font-mono text-foreground">{String(v)}</p>
              </div>
            ))}
          </div>
        </div>
      )}
      {resolve.isError && (
        <div className="bg-danger-soft border border-danger/28 rounded-lg p-4 flex items-center gap-2">
          <AlertCircle className="w-5 h-5 text-red-500" />
          <p className="text-sm text-danger">
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
            className="px-4 py-2 text-sm font-medium rounded-lg bg-neutral-100 text-muted-foreground">
            Patient PHID Recovery
          </button>
          <button onClick={() => { setRecoveryType("provider"); setMethod("phone_otp"); setStep("form"); }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-teal-600 text-white">
            Provider ID Recovery
          </button>
        </div>

        <div className="rounded-lg border border-warning/35 bg-warning-soft p-5 text-sm text-warning-foreground">
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
          className={`px-4 py-2 text-sm font-medium rounded-lg ${recoveryType === "patient" ? "bg-primary text-white" : "bg-neutral-100 text-muted-foreground"}`}>
          Patient PHID Recovery
        </button>
        <button onClick={() => { setRecoveryType("provider"); setMethod("phone_otp"); setStep("form"); }}
          className="px-4 py-2 text-sm font-medium rounded-lg bg-neutral-100 text-muted-foreground">
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
                  method === m.key ? "bg-primary-soft text-primary font-medium" : "text-muted-foreground hover:bg-background"
                }`}>
                <MIcon className="w-4 h-4" /> {m.label}
              </button>
            );
          })}
        </div>

        {/* Form area */}
        <div className="col-span-2 bg-card rounded-lg border border-border p-5">
          {step === "form" && (
            <div className="space-y-4">
              <h4 className="text-sm font-semibold text-foreground">Recovery via {methods.find((m) => m.key === method)?.label}</h4>

              {method === "biometric" && (
                <div className="text-center py-8">
                  <Fingerprint className="w-12 h-12 text-muted-foreground mx-auto mb-3" />
                  <p className="text-sm text-muted-foreground">Place finger on scanner or position face for camera</p>
                  <button disabled className="mt-3 px-4 py-2 bg-neutral-100 text-muted-foreground text-sm rounded-lg">Waiting for hardware...</button>
                </div>
              )}

              {(method === "phone_otp" || method === "email_otp") && (
                <>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">{method === "phone_otp" ? "Phone Number" : "Email Address"}</label>
                    <input type={method === "email_otp" ? "email" : "tel"} value={contact} onChange={(e) => setContact(e.target.value)}
                      className="w-full px-3 py-2 text-sm border border-border rounded-lg" />
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
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Document Number</label>
                      <input type="text" value={docNumber} onChange={(e) => setDocNumber(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" placeholder="National ID or passport" /></div>
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Full Name</label>
                      <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Date of Birth</label>
                      <input type="date" value={dob} onChange={(e) => setDob(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                  </div>
                  <button onClick={() => startRecovery.mutate({ documentNumber: docNumber, fullName, dateOfBirth: dob, method })} disabled={startRecovery.isPending}
                    className="w-full py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">Recover</button>
                </>
              )}

              {method === "provider_verify" && (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Provider ID</label>
                      <input type="text" value={docNumber} onChange={(e) => setDocNumber(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Patient Name</label>
                      <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Date of Birth</label>
                      <input type="date" value={dob} onChange={(e) => setDob(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                  </div>
                  <button onClick={() => startRecovery.mutate({ providerId: docNumber, patientName: fullName, dateOfBirth: dob, method })} disabled={startRecovery.isPending}
                    className="w-full py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">Verify & Recover</button>
                </>
              )}

              {method === "professional_license" && (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">License Number</label>
                      <input type="text" value={licenseNumber} onChange={(e) => setLicenseNumber(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Full Name</label>
                      <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                  </div>
                  <button onClick={() => startRecovery.mutate({ licenseNumber, fullName, method })} disabled={startRecovery.isPending}
                    className="w-full py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">Recover via License</button>
                </>
              )}

              {method === "facility_verify" && (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Facility ID</label>
                      <input type="text" value={facilityId} onChange={(e) => setFacilityId(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Employee ID</label>
                      <input type="text" value={docNumber} onChange={(e) => setDocNumber(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                    <div><label className="block text-xs font-medium text-muted-foreground mb-1">Full Name</label>
                      <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full px-3 py-2 text-sm border border-border rounded-lg" /></div>
                  </div>
                  <button onClick={() => startRecovery.mutate({ facilityId, employeeId: docNumber, fullName, method })} disabled={startRecovery.isPending}
                    className="w-full py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">Recover via Facility</button>
                </>
              )}
            </div>
          )}

          {step === "verify" && (
            <div className="space-y-4">
              <h4 className="text-sm font-semibold text-foreground">Enter Verification Code</h4>
              <input type="text" value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="6-digit code" maxLength={6}
                className="w-full px-4 py-3 text-lg text-center tracking-widest border border-border rounded-lg font-mono" />
              <div className="flex gap-3">
                <button onClick={() => setStep("form")} className="flex-1 py-2 bg-neutral-100 text-foreground text-sm rounded-lg">Back</button>
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
              <h4 className="text-base font-semibold text-foreground">ID Recovered Successfully</h4>
              <p className="text-sm text-muted-foreground mt-1">Your health ID has been recovered and verified.</p>
              <button onClick={() => { setStep("form"); setOtp(""); }} className="mt-4 px-4 py-2 bg-primary text-white text-sm rounded-lg hover:bg-primary-hover">Start New Recovery</button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ── BATCH TAB ────────────────────────────────────────────────────
// Batch issuance stays blocked until a real BFF contract exists for reserve,
// issue, and audited download. Identity must never be fabricated in the browser.
function BatchTab() {
  return (
    <div className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
      <div className="flex items-start gap-3">
        <Layers className="mt-0.5 h-5 w-5 shrink-0 text-warning-foreground" />
        <div>
          <h3 className="text-base font-semibold">Batch identity issuance is intentionally unsupported</h3>
          <p className="mt-2">
            Experience will not deliver browser-generated PHIDs or CSV exports. The accepted surface now blocks this
            until a real backend contract exists for reserve, issue, and audited download.
          </p>
          <p className="mt-3 text-xs text-warning-foreground/80">
            Required contract: a canonical Experience BFF route for batch issuance with facility scope, audit
            metadata, and server-generated identifiers.
          </p>
        </div>
      </div>
    </div>
  );
}

// ── ARCHITECTURE TAB ─────────────────────────────────────────────
function ArchitectureTab() {
  const cards = [
    { title: "Patient PHID Architecture", color: "border-primary/25 bg-primary-soft",
      points: ["Portable Token — PHID travels with the patient across facilities", "Biometric = PHID — fingerprint/face binds to unique identity", "Multi-Recovery — 5 recovery methods for lost IDs"],
      flow: "PHID → Client Registry ID → SHR ID → Health Record" },
    { title: "Provider ID Architecture", color: "border-teal-200 bg-teal-50",
      points: ["VARAPI Format — province-coded provider identifier", "Biometric Binding — practitioner biometric links to provider ID", "Facility Verification — employment-based identity confirmation"],
      flow: "Provider ID → iHRIS Registry → Professional License → Access" },
    { title: "Facility ID Architecture", color: "border-warning/35 bg-warning-soft",
      points: ["THUSO Format — province-coded facility identifier", "GOFR Integration — linked to national facility registry"],
      flow: "Facility ID → GOFR → Location Data → Services" },
    { title: "Security Model", color: "border-border bg-background",
      points: ["Luhn Check Digit — format validation at entry point", "Web Crypto API — cryptographic ID generation", "Database Sequences — guaranteed uniqueness", "Audit Logging — every generation/validation tracked"],
      flow: "" },
  ];

  return (
    <div className="grid grid-cols-2 gap-4">
      {cards.map((card) => (
        <div key={card.title} className={`rounded-lg border-2 p-5 ${card.color}`}>
          <h4 className="text-sm font-semibold text-foreground mb-3">{card.title}</h4>
          <ul className="space-y-2 mb-3">
            {card.points.map((point) => {
              const [bold, ...rest] = point.split(" — ");
              return (
                <li key={bold} className="text-xs text-foreground">
                  <span className="font-medium">{bold}</span>{rest.length > 0 && ` — ${rest.join(" — ")}`}
                </li>
              );
            })}
          </ul>
          {card.flow && (
            <div className="bg-card/60 rounded p-2 text-[10px] font-mono text-muted-foreground text-center">{card.flow}</div>
          )}
        </div>
      ))}
    </div>
  );
}
