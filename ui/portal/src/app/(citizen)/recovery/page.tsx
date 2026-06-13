"use client";

import { useState } from "react";
import { portalApi } from "@/lib/portalApi";

type Step = "enter-id" | "verify" | "done";

export default function RecoveryPage() {
  const [step, setStep] = useState<Step>("enter-id");
  const [impiloId, setImpiloId] = useState("");
  const [stepUpToken, setStepUpToken] = useState("");
  const [proof, setProof] = useState<Record<string, string>>({
    answer1: "",
    answer2: "",
  });
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

      await portalApi.startRecovery(impiloId, stepUpToken);
      setStep("verify");
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "An unexpected error occurred";
      if (message.includes("401")) {
        setStepUpRequired(true);
      } else {
        // Generic anti-enumeration message
        setStep("verify");
      }
    } finally {
      setLoading(false);
    }
  }

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      await portalApi.verifyRecovery(proof, stepUpToken);
      setStep("done");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Verification failed. Please try again.",
      );
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

  // Step 1: Enter Impilo ID
  if (step === "enter-id") {
    return (
      <div className="max-w-lg mx-auto">
        <div className="bg-card rounded-xl shadow-sm border border-neutral-200 p-6">
          <h1 className="text-xl font-semibold text-neutral-900 mb-1">
            ID Recovery
          </h1>
          <p className="text-sm text-neutral-500 mb-6">
            Recover access to your Impilo Health ID. You will need to verify
            your identity to proceed.
          </p>

          {/* Step-up required notice */}
          {stepUpRequired && (
            <div className="bg-warning-soft border border-warning/35 rounded-lg p-4 mb-4">
              <h3 className="text-sm font-medium text-warning-foreground mb-1">
                Additional Verification Required
              </h3>
              <p className="text-sm text-warning-foreground mb-3">
                ID recovery requires additional identity verification. Please
                enter your step-up verification token below.
              </p>
              <input
                type="text"
                value={stepUpToken}
                onChange={(e) => setStepUpToken(e.target.value)}
                placeholder="Step-up verification token"
                className="w-full px-3 py-2 border border-amber-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-amber-500 focus:border-amber-500"
              />
            </div>
          )}

          <form onSubmit={handleStartRecovery} className="space-y-4">
            <div>
              <label
                htmlFor="impiloId"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Impilo ID
              </label>
              <input
                id="impiloId"
                type="text"
                value={impiloId}
                onChange={(e) => setImpiloId(e.target.value)}
                placeholder="Enter your Impilo ID"
                required
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            {error && (
              <div className="bg-danger-soft border border-danger/28 rounded-lg p-3">
                <p className="text-sm text-danger">{error}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={loading || !impiloId}
              className="w-full bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? "Processing..." : "Start Recovery"}
            </button>
          </form>

          <p className="mt-4 text-xs text-neutral-400 text-center">
            If your ID exists in our system, you will be guided through the
            recovery process.
          </p>
        </div>
      </div>
    );
  }

  // Step 2: Verify recovery
  if (step === "verify") {
    return (
      <div className="max-w-lg mx-auto">
        <div className="bg-card rounded-xl shadow-sm border border-neutral-200 p-6">
          <h1 className="text-xl font-semibold text-neutral-900 mb-1">
            Verify Your Identity
          </h1>
          <p className="text-sm text-neutral-500 mb-6">
            Please answer the security questions to verify your identity and
            complete the recovery process.
          </p>

          <form onSubmit={handleVerify} className="space-y-4">
            <div>
              <label
                htmlFor="answer1"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Security Question 1
              </label>
              <input
                id="answer1"
                type="text"
                value={proof.answer1}
                onChange={(e) =>
                  setProof((prev) => ({ ...prev, answer1: e.target.value }))
                }
                placeholder="Enter your answer"
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label
                htmlFor="answer2"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Security Question 2
              </label>
              <input
                id="answer2"
                type="text"
                value={proof.answer2}
                onChange={(e) =>
                  setProof((prev) => ({ ...prev, answer2: e.target.value }))
                }
                placeholder="Enter your answer"
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            {error && (
              <div className="bg-danger-soft border border-danger/28 rounded-lg p-3">
                <p className="text-sm text-danger">{error}</p>
              </div>
            )}

            <div className="flex gap-3">
              <button
                type="button"
                onClick={resetFlow}
                className="flex-1 bg-neutral-100 text-neutral-700 px-4 py-2 rounded-lg text-sm font-medium hover:bg-neutral-200 transition-colors"
              >
                Back
              </button>
              <button
                type="submit"
                disabled={loading}
                className="flex-1 bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {loading ? "Verifying..." : "Verify"}
              </button>
            </div>
          </form>
        </div>
      </div>
    );
  }

  // Step 3: Done
  return (
    <div className="max-w-lg mx-auto">
      <div className="bg-green-50 border border-green-200 rounded-xl p-6 text-center">
        <h2 className="text-lg font-semibold text-green-800 mb-2">
          Recovery Complete
        </h2>
        <p className="text-sm text-green-700">
          Your identity has been verified and your Impilo Health ID access has
          been restored. You can now use your Health ID for services.
        </p>
        <button
          type="button"
          onClick={resetFlow}
          className="mt-4 bg-neutral-100 text-neutral-700 px-4 py-2 rounded-lg text-sm font-medium hover:bg-neutral-200 transition-colors"
        >
          Done
        </button>
      </div>
    </div>
  );
}
