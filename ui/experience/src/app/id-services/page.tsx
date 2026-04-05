"use client";

/**
 * Identity Services Hub — Generate, validate, and recover health IDs.
 * Route: /id-services
 *
 * Lovable reference: IdServices with 5 tabs (generate, validate,
 * batch, recovery, architecture). Backed by VITO + TSHEPO-IDENTITY + VARAPI.
 *
 * Tabs:
 * 1. Generate — Create patient or provider health IDs
 * 2. Validate — Verify an existing health ID
 * 3. Recovery — Recover a lost or forgotten ID
 * 4. Batch — Bulk ID generation
 */

import { useState, type FormEvent } from "react";
import {
  Shield, UserPlus, Search, RefreshCw, Layers,
  Loader2, CheckCircle2, AlertCircle, User, Building2, Stethoscope,
} from "lucide-react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type ActiveTab = "generate" | "validate" | "recovery" | "batch";

const TABS: { key: ActiveTab; label: string; icon: typeof Shield }[] = [
  { key: "generate", label: "Generate", icon: UserPlus },
  { key: "validate", label: "Validate", icon: Search },
  { key: "recovery", label: "Recovery", icon: RefreshCw },
  { key: "batch", label: "Batch", icon: Layers },
];

export default function IdServicesPage() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("generate");

  return (
    <AppLayout>
      <PageShell title="Identity Services" subtitle="Generate, validate, and recover health IDs">
        {/* Tab bar */}
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
      </PageShell>
    </AppLayout>
  );
}

// ── GENERATE TAB ─────────────────────────────────────────────────
function GenerateTab() {
  const [idType, setIdType] = useState<"patient" | "provider">("patient");
  const [givenName, setGivenName] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [gender, setGender] = useState("MALE");

  const registerPatient = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/identity/patient/register", body),
  });

  function handleGenerate(e: FormEvent) {
    e.preventDefault();
    if (idType === "patient") {
      registerPatient.mutate({ givenName, familyName, dateOfBirth, gender });
    }
  }

  return (
    <div className="space-y-6">
      {/* ID Type selector */}
      <div className="flex gap-3">
        <button onClick={() => setIdType("patient")}
          className={`flex-1 flex items-center justify-center gap-2 p-4 rounded-lg border-2 transition-colors ${
            idType === "patient" ? "border-blue-500 bg-blue-50 text-blue-700" : "border-gray-200 text-gray-600 hover:border-gray-300"
          }`}>
          <User className="w-5 h-5" /> Patient PHID
        </button>
        <button onClick={() => setIdType("provider")}
          className={`flex-1 flex items-center justify-center gap-2 p-4 rounded-lg border-2 transition-colors ${
            idType === "provider" ? "border-teal-500 bg-teal-50 text-teal-700" : "border-gray-200 text-gray-600 hover:border-gray-300"
          }`}>
          <Stethoscope className="w-5 h-5" /> Provider ID
        </button>
      </div>

      <form onSubmit={handleGenerate} className="bg-white rounded-lg border border-gray-200 p-6 space-y-4">
        <h3 className="text-base font-semibold text-gray-900">
          {idType === "patient" ? "Generate Patient Health ID" : "Generate Provider ID"}
        </h3>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Given Name</label>
            <input type="text" value={givenName} onChange={(e) => setGivenName(e.target.value)} required
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" placeholder="First name" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Family Name</label>
            <input type="text" value={familyName} onChange={(e) => setFamilyName(e.target.value)} required
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" placeholder="Last name" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Date of Birth</label>
            <input type="date" value={dateOfBirth} onChange={(e) => setDateOfBirth(e.target.value)} required
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Gender</label>
            <select value={gender} onChange={(e) => setGender(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg">
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
        </div>
        <button type="submit" disabled={registerPatient.isPending}
          className="w-full py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2">
          {registerPatient.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <UserPlus className="w-4 h-4" />}
          Generate {idType === "patient" ? "PHID" : "Provider ID"}
        </button>
        {registerPatient.isSuccess && (
          <div className="p-4 bg-green-50 border border-green-200 rounded-lg flex items-start gap-2">
            <CheckCircle2 className="w-5 h-5 text-green-600 shrink-0" />
            <div>
              <p className="text-sm font-medium text-green-800">Identity Created Successfully</p>
              <p className="text-xs text-green-600 mt-1">
                The health ID has been generated and registered in the system.
              </p>
            </div>
          </div>
        )}
        {registerPatient.isError && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-red-500" />
            <p className="text-sm text-red-700">Failed to generate identity. Please try again.</p>
          </div>
        )}
      </form>
    </div>
  );
}

// ── VALIDATE TAB ─────────────────────────────────────────────────
function ValidateTab() {
  const [healthId, setHealthId] = useState("");

  const resolve = useMutation({
    mutationFn: (id: string) =>
      apiClient.post<{ data: Record<string, unknown> }>("/internal/v1/identity/patient/resolve", { healthId: id }),
  });

  function handleValidate(e: FormEvent) {
    e.preventDefault();
    resolve.mutate(healthId);
  }

  return (
    <div className="space-y-6">
      <form onSubmit={handleValidate} className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-base font-semibold text-gray-900 mb-4">Validate Health ID</h3>
        <p className="text-sm text-gray-500 mb-4">Enter a PHID, CPID, or national ID to verify its authenticity and resolve to the canonical patient record.</p>
        <div className="flex gap-3">
          <input type="text" value={healthId} onChange={(e) => setHealthId(e.target.value)}
            placeholder="Enter PHID, CPID, or national ID" required
            className="flex-1 px-4 py-3 text-sm border border-gray-300 rounded-lg" />
          <button type="submit" disabled={resolve.isPending || !healthId}
            className="px-6 py-3 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2">
            {resolve.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
            Validate
          </button>
        </div>
      </form>

      {resolve.isSuccess && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-5">
          <div className="flex items-center gap-2 mb-3">
            <CheckCircle2 className="w-5 h-5 text-green-600" />
            <h4 className="text-sm font-semibold text-green-800">Identity Verified</h4>
          </div>
          <pre className="text-xs text-green-700 bg-green-100 rounded p-3 overflow-auto">
            {JSON.stringify(resolve.data, null, 2)}
          </pre>
        </div>
      )}
      {resolve.isError && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center gap-2">
          <AlertCircle className="w-5 h-5 text-red-500" />
          <p className="text-sm text-red-700">Identity not found or invalid.</p>
        </div>
      )}
    </div>
  );
}

// ── RECOVERY TAB ─────────────────────────────────────────────────
function RecoveryTab() {
  const [step, setStep] = useState<"start" | "verify">("start");
  const [fullName, setFullName] = useState("");
  const [dob, setDob] = useState("");
  const [contact, setContact] = useState("");
  const [otp, setOtp] = useState("");

  const startRecovery = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/identity/patient/recovery/start", body),
    onSuccess: () => setStep("verify"),
  });

  const verifyRecovery = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/identity/patient/recovery/verify", body),
  });

  return (
    <div className="space-y-6">
      {step === "start" && (
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h3 className="text-base font-semibold text-gray-900 mb-2">Recover Lost Health ID</h3>
          <p className="text-sm text-gray-500 mb-4">Provide your personal details to start the recovery process.</p>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Full Name</label>
              <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} required
                className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Date of Birth</label>
              <input type="date" value={dob} onChange={(e) => setDob(e.target.value)} required
                className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-medium text-gray-600 mb-1">Contact (phone or email)</label>
              <input type="text" value={contact} onChange={(e) => setContact(e.target.value)} required
                placeholder="For OTP verification"
                className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            </div>
          </div>
          <button onClick={() => startRecovery.mutate({ fullName, dateOfBirth: dob, contact })}
            disabled={startRecovery.isPending || !fullName || !dob}
            className="mt-4 w-full py-2.5 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700 disabled:opacity-50 flex items-center justify-center gap-2">
            {startRecovery.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
            Start Recovery
          </button>
        </div>
      )}

      {step === "verify" && (
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h3 className="text-base font-semibold text-gray-900 mb-2">Verify Your Identity</h3>
          <p className="text-sm text-gray-500 mb-4">Enter the verification code sent to your contact.</p>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Verification Code</label>
            <input type="text" value={otp} onChange={(e) => setOtp(e.target.value)}
              placeholder="6-digit code" maxLength={6}
              className="w-full px-4 py-3 text-lg text-center tracking-widest border border-gray-300 rounded-lg font-mono" />
          </div>
          <div className="flex gap-3 mt-4">
            <button onClick={() => setStep("start")} className="flex-1 py-2.5 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200">
              Back
            </button>
            <button onClick={() => verifyRecovery.mutate({ contact, otp })}
              disabled={verifyRecovery.isPending || otp.length < 4}
              className="flex-1 py-2.5 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 disabled:opacity-50 flex items-center justify-center gap-2">
              {verifyRecovery.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
              Verify & Recover
            </button>
          </div>
          {verifyRecovery.isSuccess && (
            <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded-lg">
              <p className="text-sm font-medium text-green-800">ID Recovered Successfully</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── BATCH TAB ────────────────────────────────────────────────────
function BatchTab() {
  const [count, setCount] = useState(10);
  const [prefix, setPrefix] = useState("");

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-base font-semibold text-gray-900 mb-2">Batch ID Generation</h3>
        <p className="text-sm text-gray-500 mb-4">Generate multiple health IDs in bulk for registration drives or facility onboarding.</p>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Batch Size</label>
            <input type="number" min={1} max={1000} value={count} onChange={(e) => setCount(Number(e.target.value))}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">ID Prefix (optional)</label>
            <input type="text" value={prefix} onChange={(e) => setPrefix(e.target.value)}
              placeholder="e.g. ZW-HRE" maxLength={10}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
        </div>
        <button className="mt-4 w-full py-2.5 bg-purple-600 text-white text-sm font-medium rounded-lg hover:bg-purple-700 flex items-center justify-center gap-2">
          <Layers className="w-4 h-4" /> Generate {count} IDs
        </button>
        <p className="mt-2 text-xs text-gray-400 text-center">
          Batch generation requires admin or registrar permissions.
        </p>
      </div>
    </div>
  );
}
