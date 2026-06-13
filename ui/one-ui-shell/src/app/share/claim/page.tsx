"use client";

import { useState } from "react";
import {
  useValidatePublicShare,
  useVerifyShareOtp,
  useVerifyShareStepUp,
  useCompleteShareIdentity,
  useShareWorkspace,
  useAddShareContribution,
  useCollaborationCouncils,
  type ShareWorkspace,
  type ShareContribution,
} from "@/hooks/queries/useVitoPatientShares";

type Step = "validate" | "otp" | "step-up" | "identity" | "workspace";

export default function ShareClaimPage() {
  const [step, setStep] = useState<Step>("validate");
  const [shareToken, setShareToken] = useState("");
  const [sessionToken, setSessionToken] = useState<string | undefined>(undefined);
  const [otp, setOtp] = useState("");
  const [stepUpCode, setStepUpCode] = useState("");
  const [stepUpType, setStepUpType] = useState("TOTP");
  const [councilId, setCouncilId] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [contributionType, setContributionType] = useState("");
  const [contributionContent, setContributionContent] = useState("");
  const [error, setError] = useState("");

  const validateMutation = useValidatePublicShare();
  const verifyOtpMutation = useVerifyShareOtp();
  const verifyStepUpMutation = useVerifyShareStepUp();
  const completeIdentityMutation = useCompleteShareIdentity();
  const addContributionMutation = useAddShareContribution();

  const councilsQuery = useCollaborationCouncils(tenantId || undefined);
  const workspaceQuery = useShareWorkspace(step === "workspace" ? sessionToken : undefined);

  function clearError() {
    setError("");
  }

  async function handleValidate(e: React.FormEvent) {
    e.preventDefault();
    clearError();
    try {
      const res = await validateMutation.mutateAsync({ shareToken: shareToken.trim() });
      setSessionToken(res.data.sessionToken);
      setStep("otp");
    } catch {
      setError("Share token invalid or expired. Please check the link and try again.");
    }
  }

  async function handleOtp(e: React.FormEvent) {
    e.preventDefault();
    clearError();
    try {
      await verifyOtpMutation.mutateAsync({ sessionToken: sessionToken!, otp: otp.trim() });
      setStep("step-up");
    } catch {
      setError("OTP verification failed. Please check the code and retry.");
    }
  }

  async function handleStepUp(e: React.FormEvent) {
    e.preventDefault();
    clearError();
    try {
      await verifyStepUpMutation.mutateAsync({
        sessionToken: sessionToken!,
        body: { stepUpType, code: stepUpCode.trim() },
      });
      setStep("identity");
    } catch {
      setError("Step-up verification failed. Please retry.");
    }
  }

  async function handleIdentity(e: React.FormEvent) {
    e.preventDefault();
    clearError();
    try {
      await completeIdentityMutation.mutateAsync({
        sessionToken: sessionToken!,
        body: { councilId: councilId || undefined },
      });
      setStep("workspace");
    } catch {
      setError("Identity completion failed. Please retry.");
    }
  }

  async function handleAddContribution(e: React.FormEvent) {
    e.preventDefault();
    clearError();
    if (!contributionType.trim() || !contributionContent.trim()) {
      setError("Please fill in contribution type and content.");
      return;
    }
    try {
      await addContributionMutation.mutateAsync({
        sessionToken: sessionToken!,
        body: { type: contributionType.trim(), contentSummary: contributionContent.trim() },
      });
      setContributionType("");
      setContributionContent("");
    } catch {
      setError("Failed to submit contribution. Please retry.");
    }
  }

  const stepLabels: Record<Step, string> = {
    validate: "1. Validate share link",
    otp: "2. Verify OTP",
    "step-up": "3. Step-up verification",
    identity: "4. Complete identity",
    workspace: "5. Workspace",
  };

  const stepOrder: Step[] = ["validate", "otp", "step-up", "identity", "workspace"];

  return (
    <div className="space-y-4">
      <div className="flex gap-2 flex-wrap">
        {stepOrder.map((s) => (
          <span
            key={s}
            className={`text-xs px-2 py-1 rounded-full border ${
              s === step
                ? "bg-blue-600 text-white border-blue-600"
                : stepOrder.indexOf(s) < stepOrder.indexOf(step)
                ? "bg-green-50 text-green-700 border-green-200"
                : "bg-background text-muted-foreground border-border"
            }`}
          >
            {stepLabels[s]}
          </span>
        ))}
      </div>

      {error && (
        <div className="rounded-lg border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </div>
      )}

      {step === "validate" && (
        <form onSubmit={handleValidate} className="bg-card border border-border rounded-lg p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">Share token</label>
            <input
              value={shareToken}
              onChange={(e) => setShareToken(e.target.value)}
              placeholder="Paste the share token from your notification"
              className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <button
            type="submit"
            disabled={validateMutation.isPending}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50"
          >
            {validateMutation.isPending ? "Validating…" : "Continue"}
          </button>
        </form>
      )}

      {step === "otp" && (
        <form onSubmit={handleOtp} className="bg-card border border-border rounded-lg p-6 space-y-4">
          <p className="text-sm text-muted-foreground">
            An OTP has been sent to the contact details associated with this share. Enter it below.
          </p>
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">One-time passcode</label>
            <input
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              placeholder="6-digit code"
              className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
              maxLength={8}
            />
          </div>
          <button
            type="submit"
            disabled={verifyOtpMutation.isPending}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50"
          >
            {verifyOtpMutation.isPending ? "Verifying…" : "Verify OTP"}
          </button>
        </form>
      )}

      {step === "step-up" && (
        <form onSubmit={handleStepUp} className="bg-card border border-border rounded-lg p-6 space-y-4">
          <p className="text-sm text-muted-foreground">
            Step-up verification is required for this share. Provide your secondary credential.
          </p>
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">Verification type</label>
            <select
              value={stepUpType}
              onChange={(e) => setStepUpType(e.target.value)}
              className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="TOTP">TOTP (authenticator app)</option>
              <option value="SMS_OTP">SMS OTP</option>
              <option value="BIOMETRIC">Biometric token</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">Code / token</label>
            <input
              value={stepUpCode}
              onChange={(e) => setStepUpCode(e.target.value)}
              placeholder="Enter your code"
              className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <button
            type="submit"
            disabled={verifyStepUpMutation.isPending}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50"
          >
            {verifyStepUpMutation.isPending ? "Verifying…" : "Confirm step-up"}
          </button>
        </form>
      )}

      {step === "identity" && (
        <form onSubmit={handleIdentity} className="bg-card border border-border rounded-lg p-6 space-y-4">
          <p className="text-sm text-muted-foreground">
            Optionally select a collaboration council for this session.
          </p>
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">
              Tenant ID <span className="text-muted-foreground">(optional — filter councils)</span>
            </label>
            <input
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              placeholder="e.g. ZWE"
              className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          {councilsQuery.data?.data && councilsQuery.data.data.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-foreground mb-1">Collaboration council</label>
              <select
                value={councilId}
                onChange={(e) => setCouncilId(e.target.value)}
                className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">— None —</option>
                {councilsQuery.data.data.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.type})
                  </option>
                ))}
              </select>
            </div>
          )}
          <button
            type="submit"
            disabled={completeIdentityMutation.isPending}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50"
          >
            {completeIdentityMutation.isPending ? "Completing…" : "Enter workspace"}
          </button>
        </form>
      )}

      {step === "workspace" && (
        <div className="space-y-4">
          {workspaceQuery.isLoading && (
            <div className="bg-card border border-border rounded-lg p-6 text-sm text-muted-foreground">
              Loading workspace…
            </div>
          )}
          {workspaceQuery.isError && (
            <div className="rounded-lg border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-danger">
              Failed to load workspace. Please refresh.
            </div>
          )}
          {workspaceQuery.data?.data && (
            <WorkspaceView
              workspace={workspaceQuery.data.data}
              sessionToken={sessionToken!}
              contributionType={contributionType}
              contributionContent={contributionContent}
              onContributionTypeChange={setContributionType}
              onContributionContentChange={setContributionContent}
              onSubmitContribution={handleAddContribution}
              submitting={addContributionMutation.isPending}
              submitError={error}
            />
          )}
        </div>
      )}
    </div>
  );
}

interface WorkspaceViewProps {
  workspace: ShareWorkspace;
  sessionToken: string;
  contributionType: string;
  contributionContent: string;
  onContributionTypeChange: (v: string) => void;
  onContributionContentChange: (v: string) => void;
  onSubmitContribution: (e: React.FormEvent) => Promise<void>;
  submitting: boolean;
  submitError: string;
}

function WorkspaceView({
  workspace,
  contributionType,
  contributionContent,
  onContributionTypeChange,
  onContributionContentChange,
  onSubmitContribution,
  submitting,
}: WorkspaceViewProps) {
  return (
    <div className="space-y-4">
      <div className="bg-card border border-border rounded-lg p-6">
        <h2 className="text-base font-semibold text-foreground mb-1">{workspace.patientName}</h2>
        <p className="text-xs text-muted-foreground">Health ID: {workspace.healthId}</p>

        {workspace.activeShares.length > 0 && (
          <div className="mt-4">
            <h3 className="text-sm font-medium text-foreground mb-2">Active shares</h3>
            <ul className="space-y-2">
              {workspace.activeShares.map((share) => (
                <li key={share.id} className="flex items-center justify-between text-xs text-muted-foreground bg-background rounded px-3 py-2">
                  <span>{share.purpose}</span>
                  <span
                    className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                      share.status === "ACTIVE"
                        ? "bg-green-100 text-green-700"
                        : share.status === "PENDING"
                        ? "bg-yellow-100 text-yellow-700"
                        : "bg-neutral-100 text-muted-foreground"
                    }`}
                  >
                    {share.status}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {workspace.recentContributions.length > 0 && (
          <div className="mt-4">
            <h3 className="text-sm font-medium text-foreground mb-2">Recent contributions</h3>
            <ContributionList contributions={workspace.recentContributions} />
          </div>
        )}
      </div>

      <div className="bg-card border border-border rounded-lg p-6">
        <h3 className="text-sm font-semibold text-foreground mb-3">Add contribution</h3>
        <form onSubmit={onSubmitContribution} className="space-y-3">
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">Contribution type</label>
            <input
              value={contributionType}
              onChange={(e) => onContributionTypeChange(e.target.value)}
              placeholder="e.g. OBSERVATION, LAB_RESULT"
              className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-muted-foreground mb-1">Content summary</label>
            <textarea
              value={contributionContent}
              onChange={(e) => onContributionContentChange(e.target.value)}
              rows={3}
              placeholder="Describe the contributed data…"
              className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
              required
            />
          </div>
          <button
            type="submit"
            disabled={submitting}
            className="bg-blue-600 hover:bg-blue-700 text-white rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50"
          >
            {submitting ? "Submitting…" : "Submit contribution"}
          </button>
        </form>
      </div>
    </div>
  );
}

function ContributionList({ contributions }: { contributions: ShareContribution[] }) {
  return (
    <ul className="space-y-2">
      {contributions.map((c) => (
        <li key={c.id} className="text-xs text-muted-foreground bg-background rounded px-3 py-2">
          <span className="font-medium">{c.type}</span>
          {" — "}
          {c.contentSummary}
          <span className="ml-2 text-muted-foreground">{c.contributorName}</span>
        </li>
      ))}
    </ul>
  );
}
