"use client";

import { useState } from "react";
import { useStepUp } from "@/hooks/useStepUp";
import { stepUpMethodLabel } from "@/lib/stepUp";

/**
 * Reusable "verify your identity to continue" prompt (G-CZO-04).
 *
 * Bound to the trust plane's STEP_UP_REQUIRED response: a sensitive action that returns
 * 401 + stepUpMethods renders this instead of failing silently. The citizen picks a method,
 * enters the verification code, and on success {@link onCompleted} retries the original action
 * (PolicyEngine then sees a recently-completed step-up). No backend was mocked — this drives the
 * real BFF → tshepo-authz step-up engine.
 */
export function StepUpPrompt({
  methods,
  onCompleted,
  onCancel,
  title = "Verify your identity to continue",
  description = "This is a protected action. Confirm it's really you to continue.",
}: {
  methods: string[];
  onCompleted: () => void;
  onCancel: () => void;
  title?: string;
  description?: string;
}) {
  const available = methods.length > 0 ? methods : ["MFA"];
  const [method, setMethod] = useState<string>(available[0]);
  const [code, setCode] = useState("");
  const { phase, error, startChallenge, submitCode, reset } = useStepUp();

  async function handleStart() {
    await startChallenge(method);
  }

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    const ok = await submitCode(code.trim());
    if (ok) {
      setCode("");
      onCompleted();
    }
  }

  function handleCancel() {
    reset();
    setCode("");
    onCancel();
  }

  const challengeStarted = phase === "ready" || phase === "verifying";

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className="rounded-xl border border-amber-300 bg-amber-50 p-5"
    >
      <h2 className="text-lg font-semibold text-amber-900">{title}</h2>
      <p className="mt-1 text-sm text-amber-800">{description}</p>

      {!challengeStarted ? (
        <div className="mt-4 space-y-3">
          <label className="block text-sm font-medium text-slate-700">
            How would you like to verify?
            <select
              className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
              value={method}
              onChange={(e) => setMethod(e.target.value)}
              aria-label="Verification method"
            >
              {available.map((m) => (
                <option key={m} value={m}>
                  {stepUpMethodLabel(m)}
                </option>
              ))}
            </select>
          </label>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleStart}
              disabled={phase === "challenging"}
              className="rounded-md bg-amber-600 px-4 py-2 text-sm font-semibold text-white hover:bg-amber-700 disabled:opacity-60"
            >
              {phase === "challenging" ? "Starting…" : "Send code"}
            </button>
            <button
              type="button"
              onClick={handleCancel}
              className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <form className="mt-4 space-y-3" onSubmit={handleVerify}>
          <label className="block text-sm font-medium text-slate-700">
            Enter your {stepUpMethodLabel(method).toLowerCase()}
            <input
              className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              inputMode="numeric"
              autoComplete="one-time-code"
              aria-label="Verification code"
              required
            />
          </label>
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={phase === "verifying" || code.trim().length === 0}
              className="rounded-md bg-amber-600 px-4 py-2 text-sm font-semibold text-white hover:bg-amber-700 disabled:opacity-60"
            >
              {phase === "verifying" ? "Verifying…" : "Verify"}
            </button>
            <button
              type="button"
              onClick={handleCancel}
              className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {error && (
        <p role="alert" className="mt-3 text-sm font-medium text-red-700">
          {error}
        </p>
      )}
    </div>
  );
}
