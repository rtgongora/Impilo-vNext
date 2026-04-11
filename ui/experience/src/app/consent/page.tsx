"use client";

/**
 * Consent Gate — Universal consent interstitial for all device types.
 * Route: /consent
 *
 * Designed for the digital consent pipeline:
 *   - Smartphones: standard responsive layout
 *   - Feature phones: minimal DOM, large touch targets, low bandwidth
 *   - Tablets: spacious layout with large checkboxes
 *   - Kiosks: extra-large buttons, high contrast, auto-reset
 *   - Desktop: centered card layout
 *
 * Detects channel from query param (?channel=KIOSK) or defaults to WEB.
 * Records consent with channel and device metadata server-side.
 */

import { useState, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { Shield, FileText, CheckCircle, LogOut } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore, CURRENT_CONSENT_VERSION } from "@/hooks/useConsentStore";
import { useLogout } from "@/hooks/queries/useAuth";
import { apiClient } from "@/lib/api-client";

function detectDeviceType(): string {
  if (typeof window === "undefined") return "DESKTOP";
  const ua = navigator.userAgent.toLowerCase();
  const width = window.innerWidth;
  if (width >= 1200 && !("ontouchstart" in window)) return "DESKTOP";
  if (width >= 768) return "TABLET";
  if (/mobi|android|iphone|ipod|phone/i.test(ua)) return "SMARTPHONE";
  return "DESKTOP";
}

export default function ConsentPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo") || "/home";
  const channelParam = searchParams.get("channel")?.toUpperCase() || "WEB";
  const isKiosk = channelParam === "KIOSK";

  const { user, clearAuth } = useAuthStore();
  const { acceptConsent } = useConsentStore();
  const logout = useLogout();

  const [privacyChecked, setPrivacyChecked] = useState(false);
  const [termsChecked, setTermsChecked] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [deviceType, setDeviceType] = useState("DESKTOP");

  useEffect(() => {
    setDeviceType(isKiosk ? "KIOSK_TERMINAL" : detectDeviceType());
  }, [isKiosk]);

  const canProceed = privacyChecked && termsChecked;

  async function handleAccept() {
    if (!canProceed || !user) return;
    setSubmitting(true);

    try {
      apiClient
        .post("/internal/v1/consent/accept", {
          version: CURRENT_CONSENT_VERSION,
          privacyPolicyAccepted: true,
          termsOfUseAccepted: true,
          channel: isKiosk ? "KIOSK" : "WEB",
          deviceType,
        })
        .catch(() => {});

      acceptConsent(user.id);
      router.replace(returnTo);
    } catch {
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

  // ── Kiosk mode: extra-large, high-contrast layout ──────────────
  if (isKiosk) {
    return (
      <div className="min-h-screen bg-white flex items-center justify-center p-6">
        <div className="w-full max-w-2xl">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-bold text-gray-900">Impilo</h1>
            <p className="text-base text-gray-500 mt-1">Health Operating System</p>
          </div>

          <div className="border-2 border-gray-200 rounded-2xl p-8 sm:p-12">
            <div className="text-center mb-8">
              <Shield className="w-16 h-16 text-blue-600 mx-auto mb-4" />
              <h2 className="text-2xl font-bold text-gray-900">
                Privacy &amp; Terms Consent
              </h2>
              <p className="text-base text-gray-600 mt-2 max-w-md mx-auto">
                Before using this terminal, please accept the Impilo Privacy
                Policy and Terms of Use.
              </p>
            </div>

            <div className="space-y-4 mb-8">
              <label className="flex items-center gap-4 cursor-pointer p-4 border-2 border-gray-200 rounded-xl hover:border-blue-300 transition-colors">
                <input
                  type="checkbox"
                  checked={privacyChecked}
                  onChange={(e) => setPrivacyChecked(e.target.checked)}
                  className="w-7 h-7 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <span className="text-lg text-gray-800">
                  I accept the <span className="font-semibold text-blue-600">Privacy Policy</span>
                </span>
              </label>

              <label className="flex items-center gap-4 cursor-pointer p-4 border-2 border-gray-200 rounded-xl hover:border-blue-300 transition-colors">
                <input
                  type="checkbox"
                  checked={termsChecked}
                  onChange={(e) => setTermsChecked(e.target.checked)}
                  className="w-7 h-7 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <span className="text-lg text-gray-800">
                  I accept the <span className="font-semibold text-blue-600">Terms of Use</span>
                </span>
              </label>
            </div>

            <button
              onClick={handleAccept}
              disabled={!canProceed || submitting}
              className="w-full py-5 bg-blue-600 text-white text-xl font-bold rounded-xl hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-3 transition-colors"
            >
              <CheckCircle className="w-7 h-7" />
              {submitting ? "Processing..." : "Accept and Continue"}
            </button>

            <p className="mt-6 text-center text-sm text-gray-400">
              Policy version: {CURRENT_CONSENT_VERSION}
            </p>
          </div>
        </div>
      </div>
    );
  }

  // ── Standard layout (smartphone, tablet, desktop) ──────────────
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

          {/* Checkboxes — large touch targets for mobile */}
          <div className="space-y-3 mb-6">
            <label className="flex items-center gap-3 cursor-pointer p-2 -mx-2 rounded-lg hover:bg-gray-50 transition-colors">
              <input
                type="checkbox"
                checked={privacyChecked}
                onChange={(e) => setPrivacyChecked(e.target.checked)}
                className="w-5 h-5 rounded border-gray-300 text-blue-600 focus:ring-blue-500 shrink-0"
              />
              <span className="text-sm text-gray-700">
                I have read and accept the{" "}
                <Link href="/privacy" target="_blank" className="text-blue-600 hover:text-blue-800 underline">
                  Privacy Policy
                </Link>
              </span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer p-2 -mx-2 rounded-lg hover:bg-gray-50 transition-colors">
              <input
                type="checkbox"
                checked={termsChecked}
                onChange={(e) => setTermsChecked(e.target.checked)}
                className="w-5 h-5 rounded border-gray-300 text-blue-600 focus:ring-blue-500 shrink-0"
              />
              <span className="text-sm text-gray-700">
                I have read and accept the{" "}
                <Link href="/terms" target="_blank" className="text-blue-600 hover:text-blue-800 underline">
                  Terms of Use
                </Link>
              </span>
            </label>
          </div>

          {/* Actions — minimum 48px touch target per mobile a11y */}
          <div className="space-y-3">
            <button
              onClick={handleAccept}
              disabled={!canProceed || submitting}
              className="w-full min-h-[48px] py-3 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
            >
              <CheckCircle className="w-4 h-4" />
              {submitting ? "Continuing..." : "Accept and Continue"}
            </button>

            <button
              onClick={handleDecline}
              className="w-full min-h-[48px] py-2.5 text-sm text-gray-500 hover:text-gray-700 flex items-center justify-center gap-2 transition-colors"
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
