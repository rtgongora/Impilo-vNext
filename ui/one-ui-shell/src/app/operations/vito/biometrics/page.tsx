"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useEvaluateBiometricPolicy } from "@/hooks/queries/useBiometricPolicy";
import {
  useBiometricTemplates,
  useBiometricProfile,
  useBiometricEnroll,
  useBiometricVerify,
  useBiometricIdentify,
  useBiometricDedupAssist,
  useBiometricException,
  type BiometricTemplate,
} from "@/hooks/queries/useVitoBiometric";
import { Fingerprint, ShieldAlert, ShieldCheck } from "lucide-react";

const SUBJECTS = ["CLIENT", "PROVIDER"] as const;
const INTENTS = ["ENROLL", "VERIFY", "IDENTIFY", "DEDUP_SUPPORT", "STEP_UP"] as const;
const CONTEXTS = ["FACILITY", "COMMUNITY", "VIRTUAL", "LOGISTICS"] as const;

const BLANK_TEMPLATE: BiometricTemplate = {
  position: "RIGHT_INDEX",
  type: "FINGERPRINT",
  format: "ISO_19794_2",
  version: "1.0",
  template: "",
};

export default function VitoBiometricsGovernancePage() {
  const evaluate = useEvaluateBiometricPolicy();
  const [subjectType, setSubjectType] = useState<(typeof SUBJECTS)[number]>("CLIENT");
  const [workflowType, setWorkflowType] = useState("POINT_OF_CARE");
  const [contextType, setContextType] = useState<(typeof CONTEXTS)[number]>("FACILITY");
  const [modality, setModality] = useState("FINGERPRINT");
  const [biometricIntent, setBiometricIntent] = useState<(typeof INTENTS)[number]>("VERIFY");

  const [lookupHealthId, setLookupHealthId] = useState("");
  const [committedHealthId, setCommittedHealthId] = useState<string | undefined>(undefined);
  const biometricTemplates = useBiometricTemplates(committedHealthId);
  const biometricProfile = useBiometricProfile(committedHealthId);

  const [enrollHealthId, setEnrollHealthId] = useState("");
  const [enrollTemplateJson, setEnrollTemplateJson] = useState(
    JSON.stringify([BLANK_TEMPLATE], null, 2)
  );
  const [enrollError, setEnrollError] = useState<string | null>(null);
  const enroll = useBiometricEnroll();

  const [verifyHealthId, setVerifyHealthId] = useState("");
  const [verifyTemplateJson, setVerifyTemplateJson] = useState(
    JSON.stringify(BLANK_TEMPLATE, null, 2)
  );
  const [verifyError, setVerifyError] = useState<string | null>(null);
  const verify = useBiometricVerify();

  const [identifyTemplateJson, setIdentifyTemplateJson] = useState(
    JSON.stringify(BLANK_TEMPLATE, null, 2)
  );
  const [identifyTopN, setIdentifyTopN] = useState("5");
  const [identifyError, setIdentifyError] = useState<string | null>(null);
  const identify = useBiometricIdentify();

  const [dedupHealthId, setDedupHealthId] = useState("");
  const [dedupCandidates, setDedupCandidates] = useState("");
  const dedupAssist = useBiometricDedupAssist();

  const [exceptionHealthId, setExceptionHealthId] = useState("");
  const [exceptionReason, setExceptionReason] = useState("");
  const [exceptionEvidence, setExceptionEvidence] = useState("");
  const exception = useBiometricException();

  const outcomeBadge = useMemo(() => {
    const o = evaluate.data?.policyOutcome?.toUpperCase() ?? "";
    if (o === "PROHIBITED") return "bg-red-50 text-red-800 border-red-100";
    if (o === "REQUIRED") return "bg-amber-50 text-amber-900 border-amber-100";
    if (o === "RESTRICTED") return "bg-orange-50 text-orange-900 border-orange-100";
    return "bg-emerald-50 text-emerald-900 border-emerald-100";
  }, [evaluate.data?.policyOutcome]);

  function handleEnroll() {
    setEnrollError(null);
    let templates: BiometricTemplate[];
    try {
      templates = JSON.parse(enrollTemplateJson) as BiometricTemplate[];
    } catch {
      setEnrollError("Invalid JSON for templates array.");
      return;
    }
    enroll.mutate({ healthId: enrollHealthId.trim(), templates });
  }

  function handleVerify() {
    setVerifyError(null);
    let template: BiometricTemplate;
    try {
      template = JSON.parse(verifyTemplateJson) as BiometricTemplate;
    } catch {
      setVerifyError("Invalid JSON for template object.");
      return;
    }
    verify.mutate({ healthId: verifyHealthId.trim(), template });
  }

  function handleIdentify() {
    setIdentifyError(null);
    let template: BiometricTemplate;
    try {
      template = JSON.parse(identifyTemplateJson) as BiometricTemplate;
    } catch {
      setIdentifyError("Invalid JSON for template object.");
      return;
    }
    const topN = parseInt(identifyTopN, 10);
    identify.mutate({ template, topN: isNaN(topN) ? undefined : topN });
  }

  return (
    <AppLayout>
      <PageShell
        title="Biometric governance"
        subtitle="Policy preview for client and provider workflows (Tshepo evaluation via BFF). This page does not capture or display biometric samples."
        icon={<Fingerprint className="h-6 w-6" />}
      >
        <div className="space-y-8">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          {/* ── Policy evaluation ── */}
          <div className="grid gap-6 lg:grid-cols-2">
            <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
              <h2 className="text-sm font-semibold text-gray-900">Evaluate policy</h2>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Subject type
                  <select
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={subjectType}
                    onChange={(e) => setSubjectType(e.target.value as (typeof SUBJECTS)[number])}
                  >
                    {SUBJECTS.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Context
                  <select
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={contextType}
                    onChange={(e) => setContextType(e.target.value as (typeof CONTEXTS)[number])}
                  >
                    {CONTEXTS.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1 sm:col-span-2">
                  Workflow type
                  <input
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={workflowType}
                    onChange={(e) => setWorkflowType(e.target.value)}
                    placeholder="e.g. POINT_OF_CARE, CLIENT_REGISTRATION, PROVIDER_STEP_UP"
                  />
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Modality (optional)
                  <input
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={modality}
                    onChange={(e) => setModality(e.target.value)}
                  />
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Intent
                  <select
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={biometricIntent}
                    onChange={(e) => setBiometricIntent(e.target.value as (typeof INTENTS)[number])}
                  >
                    {INTENTS.map((i) => (
                      <option key={i} value={i}>
                        {i}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                disabled={evaluate.isPending}
                onClick={() =>
                  evaluate.mutate({
                    subjectType,
                    workflowType,
                    contextType,
                    modality: modality.trim() || null,
                    biometricIntent,
                  })
                }
              >
                {evaluate.isPending ? "Evaluating…" : "Evaluate"}
              </button>
              {evaluate.isError && (
                <p className="text-sm text-red-600">
                  Request failed. Ensure the Experience BFF and Tshepo service are reachable for your session.
                </p>
              )}
            </div>

            <div className={`rounded-2xl border p-5 space-y-3 ${outcomeBadge}`}>
              <div className="flex items-center gap-2 text-sm font-semibold">
                {evaluate.data?.policyOutcome === "PROHIBITED" ? (
                  <ShieldAlert className="h-5 w-5" />
                ) : (
                  <ShieldCheck className="h-5 w-5" />
                )}
                Policy outcome
              </div>
              {evaluate.data ? (
                <>
                  <p className="text-2xl font-bold tracking-tight">{evaluate.data.policyOutcome}</p>
                  <ul className="text-xs space-y-1 text-gray-700">
                    <li>Enrollment: {String(evaluate.data.enrollmentAllowed)}</li>
                    <li>Verification / step-up lane: {String(evaluate.data.verificationAllowed)}</li>
                    <li>Identification (1:N): {String(evaluate.data.identificationAllowed)}</li>
                    <li>Dedup assist: {String(evaluate.data.dedupSupportAllowed)}</li>
                    <li>Fallback allowed: {String(evaluate.data.fallbackAllowed)}</li>
                    {evaluate.data.matchedRuleId != null && <li>Matched rule id: {evaluate.data.matchedRuleId}</li>}
                  </ul>
                  {evaluate.data.reasons?.length ? (
                    <div className="text-xs text-gray-600 border-t border-black/5 pt-2 mt-2">
                      {evaluate.data.reasons.map((r) => (
                        <p key={r}>{r}</p>
                      ))}
                    </div>
                  ) : null}
                </>
              ) : (
                <p className="text-sm text-gray-600">Run an evaluation to see Tshepo&apos;s decision for this slice.</p>
              )}
            </div>
          </div>

          {/* ── Profile & Templates viewer ── */}
          <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
            <h2 className="text-sm font-semibold text-gray-900">Profile &amp; templates viewer</h2>
            <div className="flex gap-2">
              <input
                className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm flex-1"
                placeholder="Health ID"
                value={lookupHealthId}
                onChange={(e) => setLookupHealthId(e.target.value)}
              />
              <button
                type="button"
                className="rounded-xl bg-impilo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                disabled={!lookupHealthId.trim()}
                onClick={() => setCommittedHealthId(lookupHealthId.trim())}
              >
                Load
              </button>
            </div>
            {committedHealthId && (
              <div className="grid gap-4 lg:grid-cols-2">
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-widest text-gray-400">Profile</p>
                  {biometricProfile.isLoading && <p className="text-sm text-gray-500">Loading…</p>}
                  {biometricProfile.isError && <p className="text-sm text-red-600">Failed to load profile.</p>}
                  {biometricProfile.data?.data && (
                    <div className="rounded-xl border border-gray-100 bg-gray-50 p-4 text-xs space-y-1">
                      <p><span className="font-medium">Status:</span> {biometricProfile.data.data.status}</p>
                      <p><span className="font-medium">Enrolled:</span> {biometricProfile.data.data.enrollmentDate}</p>
                      {biometricProfile.data.data.lastVerifiedDate && (
                        <p><span className="font-medium">Last verified:</span> {biometricProfile.data.data.lastVerifiedDate}</p>
                      )}
                      {biometricProfile.data.data.exceptionReason && (
                        <p><span className="font-medium">Exception:</span> {biometricProfile.data.data.exceptionReason}</p>
                      )}
                      <p><span className="font-medium">Templates on file:</span> {biometricProfile.data.data.templates.length}</p>
                    </div>
                  )}
                </div>
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-widest text-gray-400">Templates</p>
                  {biometricTemplates.isLoading && <p className="text-sm text-gray-500">Loading…</p>}
                  {biometricTemplates.isError && <p className="text-sm text-red-600">Failed to load templates.</p>}
                  {biometricTemplates.data?.data && biometricTemplates.data.data.length === 0 && (
                    <p className="text-sm text-gray-500">No templates enrolled.</p>
                  )}
                  {biometricTemplates.data?.data?.map((t, idx) => (
                    <div key={idx} className="rounded-xl border border-gray-100 bg-gray-50 p-3 text-xs space-y-0.5">
                      <p><span className="font-medium">Position:</span> {t.position}</p>
                      <p><span className="font-medium">Type:</span> {t.type}</p>
                      <p><span className="font-medium">Format:</span> {t.format} v{t.version}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* ── Enroll form ── */}
          <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
            <h2 className="text-sm font-semibold text-gray-900">Enroll biometric</h2>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="text-xs text-gray-500 flex flex-col gap-1 sm:col-span-2">
                Health ID
                <input
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm"
                  placeholder="Health ID"
                  value={enrollHealthId}
                  onChange={(e) => setEnrollHealthId(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1 sm:col-span-2">
                Templates (JSON array)
                <textarea
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-mono h-32 resize-y"
                  value={enrollTemplateJson}
                  onChange={(e) => setEnrollTemplateJson(e.target.value)}
                />
              </label>
            </div>
            {enrollError && <p className="text-sm text-red-600">{enrollError}</p>}
            {enroll.isError && <p className="text-sm text-red-600">Enroll request failed.</p>}
            {enroll.isSuccess && <p className="text-sm text-emerald-600">Enrollment submitted successfully.</p>}
            <button
              type="button"
              className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
              disabled={enroll.isPending || !enrollHealthId.trim()}
              onClick={handleEnroll}
            >
              {enroll.isPending ? "Enrolling…" : "Submit enrollment"}
            </button>
          </div>

          {/* ── Verify & Identify panels ── */}
          <div className="grid gap-6 lg:grid-cols-2">
            <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
              <h2 className="text-sm font-semibold text-gray-900">Verify (1:1)</h2>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Health ID
                <input
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm"
                  placeholder="Health ID"
                  value={verifyHealthId}
                  onChange={(e) => setVerifyHealthId(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Template (JSON object)
                <textarea
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-mono h-28 resize-y"
                  value={verifyTemplateJson}
                  onChange={(e) => setVerifyTemplateJson(e.target.value)}
                />
              </label>
              {verifyError && <p className="text-sm text-red-600">{verifyError}</p>}
              {verify.isError && <p className="text-sm text-red-600">Verify request failed.</p>}
              {verify.data?.data && (
                <div className="rounded-xl border border-gray-100 bg-gray-50 p-3 text-xs space-y-1">
                  <p className={verify.data.data.verified ? "text-emerald-700 font-semibold" : "text-red-700 font-semibold"}>
                    {verify.data.data.verified ? "Verified ✓" : "Not verified ✗"}
                  </p>
                  <p>Score: {verify.data.data.score}</p>
                </div>
              )}
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                disabled={verify.isPending || !verifyHealthId.trim()}
                onClick={handleVerify}
              >
                {verify.isPending ? "Verifying…" : "Verify"}
              </button>
            </div>

            <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
              <h2 className="text-sm font-semibold text-gray-900">Identify (1:N)</h2>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Template (JSON object)
                <textarea
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-mono h-28 resize-y"
                  value={identifyTemplateJson}
                  onChange={(e) => setIdentifyTemplateJson(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Top-N results
                <input
                  type="number"
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm w-24"
                  value={identifyTopN}
                  onChange={(e) => setIdentifyTopN(e.target.value)}
                  min={1}
                  max={20}
                />
              </label>
              {identifyError && <p className="text-sm text-red-600">{identifyError}</p>}
              {identify.isError && <p className="text-sm text-red-600">Identify request failed.</p>}
              {identify.data?.data && identify.data.data.length > 0 && (
                <div className="rounded-xl border border-gray-100 bg-gray-50 p-3 text-xs space-y-1">
                  {identify.data.data.map((r, idx) => (
                    <p key={idx}>{r.healthId} — score {r.score}</p>
                  ))}
                </div>
              )}
              {identify.data?.data && identify.data.data.length === 0 && (
                <p className="text-sm text-gray-500">No candidates found.</p>
              )}
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                disabled={identify.isPending}
                onClick={handleIdentify}
              >
                {identify.isPending ? "Identifying…" : "Identify"}
              </button>
            </div>
          </div>

          {/* ── Dedup-assist & Exception panels ── */}
          <div className="grid gap-6 lg:grid-cols-2">
            <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
              <h2 className="text-sm font-semibold text-gray-900">Dedup assist</h2>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Source Health ID
                <input
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm"
                  placeholder="Health ID"
                  value={dedupHealthId}
                  onChange={(e) => setDedupHealthId(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Candidate Health IDs (comma-separated)
                <input
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm"
                  placeholder="HID-001, HID-002"
                  value={dedupCandidates}
                  onChange={(e) => setDedupCandidates(e.target.value)}
                />
              </label>
              {dedupAssist.isError && <p className="text-sm text-red-600">Dedup assist request failed.</p>}
              {dedupAssist.isSuccess && <p className="text-sm text-emerald-600">Dedup assist submitted.</p>}
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                disabled={dedupAssist.isPending || !dedupHealthId.trim() || !dedupCandidates.trim()}
                onClick={() =>
                  dedupAssist.mutate({
                    healthId: dedupHealthId.trim(),
                    candidates: dedupCandidates
                      .split(",")
                      .map((c) => c.trim())
                      .filter(Boolean),
                  })
                }
              >
                {dedupAssist.isPending ? "Submitting…" : "Submit dedup assist"}
              </button>
            </div>

            <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
              <h2 className="text-sm font-semibold text-gray-900">Exception reporting</h2>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Health ID
                <input
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm"
                  placeholder="Health ID"
                  value={exceptionHealthId}
                  onChange={(e) => setExceptionHealthId(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Reason
                <input
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm"
                  placeholder="e.g. MISSING_DIGITS, UNABLE_TO_CAPTURE"
                  value={exceptionReason}
                  onChange={(e) => setExceptionReason(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Evidence reference (optional)
                <input
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm"
                  placeholder="Document or case reference"
                  value={exceptionEvidence}
                  onChange={(e) => setExceptionEvidence(e.target.value)}
                />
              </label>
              {exception.isError && <p className="text-sm text-red-600">Exception request failed.</p>}
              {exception.isSuccess && <p className="text-sm text-emerald-600">Exception recorded.</p>}
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                disabled={exception.isPending || !exceptionHealthId.trim() || !exceptionReason.trim()}
                onClick={() =>
                  exception.mutate({
                    healthId: exceptionHealthId.trim(),
                    reason: exceptionReason.trim(),
                    evidenceReference: exceptionEvidence.trim() || undefined,
                  })
                }
              >
                {exception.isPending ? "Recording…" : "Record exception"}
              </button>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
