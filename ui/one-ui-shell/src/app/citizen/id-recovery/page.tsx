"use client";

import { useState } from "react";
import { citizenPortalApi } from "@/lib/citizenPortalClient";

type Step = "enter-id" | "verify" | "done";

/** Migrated from ui/portal/(citizen)/recovery */
export default function CitizenIdRecoveryPage() {
  const [step, setStep] = useState<Step>("enter-id");
  const [impiloId, setImpiloId] = useState("");
  const [stepUpToken, setStepUpToken] = useState("");
  const [proof, setProof] = useState<Record<string, string>>({ answer1: "", answer2: "" });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [stepUpRequired, setStepUpRequired] = useState(false);

  async function handleStartRecovery(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setStepUpRequired(false);
    try {
      if (!stepUpToken) {
        setStepUpRequired(true);
        setLoading(false);
        return;
      }
      await citizenPortalApi.startRecovery(impiloId, stepUpToken);
      setStep("verify");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unexpected error";
      if (message.includes("401")) setStepUpRequired(true);
      else setStep("verify");
    } finally {
      setLoading(false);
    }
  }

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await citizenPortalApi.verifyRecovery(proof, stepUpToken);
      setStep("done");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Verification failed.");
    } finally {
      setLoading(false);
    }
  }

  function resetFlow() {
    setStep("enter-id");
    setImpiloId("");
    setStepUpToken("");
    setProof({ answer1: "", answer2: "" });
    setError(null);
    setStepUpRequired(false);
  }

  if (step === "enter-id") {
    return (
      <div className="max-w-lg mx-auto bg-card rounded-xl border border-border p-6">
        <h1 className="text-xl font-semibold text-foreground mb-1">ID recovery</h1>
        <p className="text-sm text-muted-foreground mb-6">Recover access to your Impilo Health ID (step-up required).</p>
        {stepUpRequired && (
          <div className="bg-warning-soft border border-warning/35 rounded-lg p-4 mb-4 text-sm text-warning-foreground">
            <p className="mb-2">Enter your step-up verification token.</p>
            <input
              type="text"
              value={stepUpToken}
              onChange={(e) => setStepUpToken(e.target.value)}
              placeholder="Step-up token"
              className="w-full px-3 py-2 border border-amber-300 rounded-lg text-sm"
            />
          </div>
        )}
        <form onSubmit={handleStartRecovery} className="space-y-4">
          <div>
            <label htmlFor="impiloId" className="block text-sm font-medium text-foreground mb-1">
              Impilo ID
            </label>
            <input
              id="impiloId"
              value={impiloId}
              onChange={(e) => setImpiloId(e.target.value)}
              required
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          {!stepUpRequired && (
            <div>
              <label htmlFor="stepUp" className="block text-sm font-medium text-foreground mb-1">
                Step-up token
              </label>
              <input
                id="stepUp"
                value={stepUpToken}
                onChange={(e) => setStepUpToken(e.target.value)}
                className="w-full px-3 py-2 border border-border rounded-lg text-sm"
              />
            </div>
          )}
          {error && (
            <div className="bg-danger-soft border border-danger/28 rounded-lg p-3 text-sm text-danger">{error}</div>
          )}
          <button
            type="submit"
            disabled={loading || !impiloId}
            className="w-full bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium disabled:opacity-50"
          >
            {loading ? "Processing…" : "Start recovery"}
          </button>
        </form>
      </div>
    );
  }

  if (step === "verify") {
    return (
      <div className="max-w-lg mx-auto bg-card rounded-xl border border-border p-6">
        <h1 className="text-xl font-semibold text-foreground mb-1">Verify identity</h1>
        <p className="text-sm text-muted-foreground mb-6">Answer security questions as required by your enrollment.</p>
        <form onSubmit={handleVerify} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">Security answer 1</label>
            <input
              value={proof.answer1}
              onChange={(e) => setProof((p) => ({ ...p, answer1: e.target.value }))}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-foreground mb-1">Security answer 2</label>
            <input
              value={proof.answer2}
              onChange={(e) => setProof((p) => ({ ...p, answer2: e.target.value }))}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm"
            />
          </div>
          {error && <div className="bg-danger-soft border border-danger/28 rounded-lg p-3 text-sm text-danger">{error}</div>}
          <div className="flex gap-3">
            <button type="button" onClick={resetFlow} className="flex-1 bg-neutral-100 text-foreground px-4 py-2 rounded-lg text-sm">
              Back
            </button>
            <button type="submit" disabled={loading} className="flex-1 bg-primary text-white px-4 py-2 rounded-lg text-sm disabled:opacity-50">
              {loading ? "Verifying…" : "Verify"}
            </button>
          </div>
        </form>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto bg-green-50 border border-green-200 rounded-xl p-6 text-center">
      <h2 className="text-lg font-semibold text-green-800 mb-2">Recovery complete</h2>
      <p className="text-sm text-green-700">Your session can proceed per VITO outcome.</p>
      <button type="button" onClick={resetFlow} className="mt-4 bg-neutral-100 text-foreground px-4 py-2 rounded-lg text-sm">
        Done
      </button>
    </div>
  );
}
