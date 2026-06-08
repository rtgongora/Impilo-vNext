"use client";

import Link from "next/link";
import { useState } from "react";
import { PackageCheck, CheckCircle2, AlertTriangle, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useVerifyPickupToken,
  useConfirmHandover,
  type PickupVerifyResult,
} from "@/hooks/queries/useVitoPrint";

function fmt(iso: string | undefined) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-ZW", { dateStyle: "medium", timeStyle: "short" });
}

function VerifyPanel({
  onVerified,
}: {
  onVerified: (token: string, otp: string, result: PickupVerifyResult) => void;
}) {
  const [pickupToken, setPickupToken] = useState("");
  const [delegateOtp, setDelegateOtp] = useState("");
  const verifyMutation = useVerifyPickupToken();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    verifyMutation.mutate(
      { pickupToken: pickupToken.trim(), delegateOtp: delegateOtp.trim() },
      {
        onSuccess: (res) => {
          if (res) onVerified(pickupToken.trim(), delegateOtp.trim(), res);
        },
      }
    );
  }

  const canSubmit = pickupToken.trim().length > 0 && delegateOtp.trim().length > 0;

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
      <div className="flex items-center gap-2">
        <PackageCheck className="h-4 w-4 text-impilo-600" />
        <h2 className="text-sm font-semibold text-gray-900">Sub-panel A — Verify pickup token</h2>
      </div>
      <p className="text-sm text-gray-500">
        Enter the pickup token and delegate OTP to confirm delegate identity before handing over the card.
      </p>
      <form onSubmit={handleSubmit} className="space-y-3">
        <label className="text-xs text-gray-500 flex flex-col gap-1">
          Pickup token
          <input
            type="text"
            className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
            placeholder="e.g. PT-XXXX-YYYY"
            value={pickupToken}
            onChange={(e) => setPickupToken(e.target.value)}
            required
          />
        </label>
        <label className="text-xs text-gray-500 flex flex-col gap-1">
          Delegate OTP
          <input
            type="text"
            inputMode="numeric"
            maxLength={8}
            className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm tracking-widest focus:outline-none focus:ring-2 focus:ring-impilo-400"
            placeholder="6-digit code"
            value={delegateOtp}
            onChange={(e) => setDelegateOtp(e.target.value)}
            required
          />
        </label>
        <button
          type="submit"
          disabled={verifyMutation.isPending || !canSubmit}
          className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
        >
          {verifyMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
          {verifyMutation.isPending ? "Verifying…" : "Verify token"}
        </button>

        {verifyMutation.isError && (
          <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            <AlertTriangle className="h-4 w-4 flex-shrink-0" />
            Verification failed. Check the token and OTP, then try again.
          </div>
        )}
      </form>
    </div>
  );
}

function VerifiedDetails({
  result,
  pickupToken,
  delegateOtp,
  onReset,
}: {
  result: PickupVerifyResult;
  pickupToken: string;
  delegateOtp: string;
  onReset: () => void;
}) {
  const confirmMutation = useConfirmHandover();
  const [confirmed, setConfirmed] = useState(false);
  const [confirmedAt, setConfirmedAt] = useState<string | undefined>(undefined);

  function handleConfirm() {
    confirmMutation.mutate(
      { pickupToken, delegateOtp },
      {
        onSuccess: (res) => {
          if (res) {
            setConfirmed(true);
            setConfirmedAt(res.confirmedAt);
          }
        },
      }
    );
  }

  if (confirmed) {
    return (
      <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5 space-y-3">
        <div className="flex items-center gap-2">
          <CheckCircle2 className="h-5 w-5 text-emerald-600" />
          <h2 className="text-sm font-semibold text-emerald-900">Handover confirmed</h2>
        </div>
        <p className="text-sm text-emerald-800">
          Card <span className="font-medium">{result.cardId}</span> has been successfully handed over to{" "}
          <span className="font-medium">{result.delegateName}</span>.
        </p>
        <p className="text-xs text-emerald-700">Confirmed at: {fmt(confirmedAt)}</p>
        <button
          type="button"
          onClick={onReset}
          className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-300 bg-white px-3 py-1.5 text-xs font-medium text-emerald-800 hover:bg-emerald-100"
        >
          Process another pickup
        </button>
      </div>
    );
  }

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
      <div className="flex items-center gap-2">
        <CheckCircle2 className="h-4 w-4 text-emerald-600" />
        <h2 className="text-sm font-semibold text-gray-900">Sub-panel B — Confirm handover</h2>
      </div>

      <div className="rounded-xl border border-gray-100 bg-gray-50 p-4 space-y-2 text-sm">
        <div className="grid grid-cols-2 gap-x-4 gap-y-1.5">
          <span className="text-xs text-gray-500">Delegate</span>
          <span className="text-xs font-medium text-gray-900">{result.delegateName}</span>

          <span className="text-xs text-gray-500">Facility</span>
          <span className="text-xs font-medium text-gray-900">{result.facilityName}</span>

          <span className="text-xs text-gray-500">Card ID</span>
          <span className="text-xs font-medium text-gray-900">{result.cardId}</span>

          <span className="text-xs text-gray-500">Token expires</span>
          <span className="text-xs font-medium text-gray-900">{fmt(result.expiresAt)}</span>
        </div>
      </div>

      <p className="text-sm text-gray-600">
        Confirm you have physically verified the delegate&apos;s identity and are handing over the card.
      </p>

      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          disabled={confirmMutation.isPending}
          onClick={handleConfirm}
          className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
        >
          {confirmMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
          {confirmMutation.isPending ? "Confirming…" : "Confirm handover"}
        </button>
        <button
          type="button"
          onClick={onReset}
          className="rounded-xl border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:border-gray-400"
        >
          Cancel
        </button>
      </div>

      {confirmMutation.isError && (
        <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          <AlertTriangle className="h-4 w-4 flex-shrink-0" />
          Handover confirmation failed. Please try again or contact support.
        </div>
      )}
    </div>
  );
}

export default function CardPickupPage() {
  const [verified, setVerified] = useState<{
    result: PickupVerifyResult;
    token: string;
    otp: string;
  } | null>(null);

  function handleVerified(token: string, otp: string, result: PickupVerifyResult) {
    setVerified({ result, token, otp });
  }

  function handleReset() {
    setVerified(null);
  }

  return (
    <AppLayout>
      <PageShell
        title="Card Pickup Redemption"
        subtitle="Verify a delegate pickup token and confirm physical card handover at the facility"
        icon={<PackageCheck className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito/cards"
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
            >
              ← Smart card management
            </Link>
            <span className="text-gray-300">·</span>
            <Link
              href="/operations/vito"
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
            >
              Identity operations
            </Link>
          </div>

          {!verified ? (
            <VerifyPanel onVerified={handleVerified} />
          ) : (
            <>
              <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 flex-shrink-0" />
                Token verified for delegate <strong className="ml-1">{verified.result.delegateName}</strong>
              </div>
              <VerifiedDetails
                result={verified.result}
                pickupToken={verified.token}
                delegateOtp={verified.otp}
                onReset={handleReset}
              />
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
