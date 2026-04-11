"use client";

/**
 * Consent Gate — Full-page interstitial requiring acceptance of
 * Privacy Policy and Terms of Use before proceeding.
 *
 * Route: /consent
 *
 * Shown to authenticated users who have not yet accepted the
 * current policy version. The AuthGuardProvider redirects here
 * when consent is missing.
 */

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { Shield, FileText, CheckCircle, LogOut } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore, CURRENT_CONSENT_VERSION } from "@/hooks/useConsentStore";
import { useLogout } from "@/hooks/queries/useAuth";
import { apiClient } from "@/lib/api-client";

export default function ConsentPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo") || "/home";

  const { user, clearAuth } = useAuthStore();
  const { acceptConsent } = useConsentStore();
  const logout = useLogout();

  const [privacyChecked, setPrivacyChecked] = useState(false);
  const [termsChecked, setTermsChecked] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const canProceed = privacyChecked && termsChecked;

  async function handleAccept() {
    if (!canProceed || !user) return;
    setSubmitting(true);

    try {
      // Record consent server-side (fire-and-forget — don't block on failure)
      apiClient
        .post("/internal/v1/consent/accept", {
          version: CURRENT_CONSENT_VERSION,
          privacyPolicyAccepted: true,
          termsOfUseAccepted: true,
        })
        .catch(() => {
          // Consent is persisted client-side regardless; server-side is best-effort
        });

      acceptConsent(user.id);
      router.replace(returnTo);
    } catch {
      // Fallback: still accept client-side
      acceptConsent(user.id);
      router.replace(returnTo);
    }
  }

  function handleDecline() {
    logout.mutate(undefined, {
      onSettled: () => {
        clearAuth();
        router.replace("/auth/login");
      },
    });
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-lg">
        {/* Header */}
        <div className="text-center mb-6">
          <h1 className="text-xl font-bold text-gray-900">Impilo</h1>
          <p className="text-xs text-gray-500 mt-1">Health Operating System</p>
        </div>

        <div className="bg-white rounded-xl shadow-lg border border-gray-200 p-6 sm:p-8">
          <div className="text-center mb-6">
            <div className="w-12 h-12 rounded-full bg-blue-50 flex items-center justify-center mx-auto mb-3">
              <Shield className="w-6 h-6 text-blue-600" />
            </div>
            <h2 className="text-lg font-semibold text-gray-900">
              Review Our Policies
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Please review and accept the Privacy Policy and Terms of Use to continue
              using Impilo.
            </p>
          </div>

          {/* Policy cards */}
          <div className="space-y-3 mb-6">
            <div className="border border-gray-200 rounded-lg p-4">
              <div className="flex items-start gap-3">
                <div className="w-9 h-9 rounded-lg bg-blue-50 flex items-center justify-center shrink-0 mt-0.5">
                  <Shield className="w-4 h-4 text-blue-600" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="text-sm font-medium text-gray-900">Privacy Policy</h3>
                  <p className="text-xs text-gray-500 mt-0.5">
                    How we collect, use, and protect your personal data and health information.
                  </p>
                  <Link
                    href="/privacy"
                    target="_blank"
                    className="inline-block mt-2 text-xs text-blue-600 hover:text-blue-800 font-medium"
                  >
                    Read Privacy Policy
                  </Link>
                </div>
              </div>
            </div>

            <div className="border border-gray-200 rounded-lg p-4">
              <div className="flex items-start gap-3">
                <div className="w-9 h-9 rounded-lg bg-indigo-50 flex items-center justify-center shrink-0 mt-0.5">
                  <FileText className="w-4 h-4 text-indigo-600" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="text-sm font-medium text-gray-900">Terms of Use</h3>
                  <p className="text-xs text-gray-500 mt-0.5">
                    The rules governing your use of the Impilo platform and services.
                  </p>
                  <Link
                    href="/terms"
                    target="_blank"
                    className="inline-block mt-2 text-xs text-blue-600 hover:text-blue-800 font-medium"
                  >
                    Read Terms of Use
                  </Link>
                </div>
              </div>
            </div>
          </div>

          {/* Checkboxes */}
          <div className="space-y-3 mb-6">
            <label className="flex items-start gap-3 cursor-pointer group">
              <input
                type="checkbox"
                checked={privacyChecked}
                onChange={(e) => setPrivacyChecked(e.target.checked)}
                className="mt-0.5 w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              <span className="text-sm text-gray-700 group-hover:text-gray-900">
                I have read and accept the{" "}
                <Link href="/privacy" target="_blank" className="text-blue-600 hover:text-blue-800 underline">
                  Privacy Policy
                </Link>
              </span>
            </label>

            <label className="flex items-start gap-3 cursor-pointer group">
              <input
                type="checkbox"
                checked={termsChecked}
                onChange={(e) => setTermsChecked(e.target.checked)}
                className="mt-0.5 w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              <span className="text-sm text-gray-700 group-hover:text-gray-900">
                I have read and accept the{" "}
                <Link href="/terms" target="_blank" className="text-blue-600 hover:text-blue-800 underline">
                  Terms of Use
                </Link>
              </span>
            </label>
          </div>

          {/* Actions */}
          <div className="space-y-3">
            <button
              onClick={handleAccept}
              disabled={!canProceed || submitting}
              className="w-full py-3 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
            >
              <CheckCircle className="w-4 h-4" />
              {submitting ? "Continuing..." : "Accept and Continue"}
            </button>

            <button
              onClick={handleDecline}
              className="w-full py-2.5 text-sm text-gray-500 hover:text-gray-700 flex items-center justify-center gap-2 transition-colors"
            >
              <LogOut className="w-4 h-4" />
              Decline and Sign Out
            </button>
          </div>

          <p className="mt-4 text-center text-xs text-gray-400">
            Policy version: {CURRENT_CONSENT_VERSION}
          </p>
        </div>

        {/* Signed in as */}
        {user && (
          <p className="mt-4 text-center text-xs text-gray-400">
            Signed in as {user.email}
          </p>
        )}
      </div>
    </div>
  );
}
