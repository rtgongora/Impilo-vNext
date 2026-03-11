"use client";

/**
 * Biometric Verification — Simulated fingerprint scan UI.
 * Route: /auth/login/biometric | pageTitle: "Biometric Verification"
 */

import { useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Fingerprint, Loader2, CheckCircle2, XCircle } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { useLogin } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";

type ScanState = "idle" | "scanning" | "success" | "error";

export default function BiometricLoginPage() {
  const router = useRouter();
  const login = useLogin();
  const setAuth = useAuthStore((s) => s.setAuth);

  const [scanState, setScanState] = useState<ScanState>("idle");
  const [error, setError] = useState<string | null>(null);

  const handleVerify = useCallback(() => {
    setScanState("scanning");
    setError(null);

    // Simulate a fingerprint scan delay
    setTimeout(() => {
      setScanState("success");

      // Retrieve provider number if coming from provider-id flow
      const providerNumber =
        typeof window !== "undefined"
          ? sessionStorage.getItem("exp:pending_provider_number")
          : null;

      // After scan success, call auth login
      login.mutate(
        {
          email: providerNumber || "biometric@impilo.local",
          password: "biometric-token",
        },
        {
          onSuccess: (res) => {
            const { token, user } = res.data.attributes;
            setAuth(
              {
                id: user.id,
                email: user.email,
                displayName: user.displayName,
                roles: user.roles,
                actorType: user.actorType as "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM",
              },
              token,
            );

            // Clean up
            if (typeof window !== "undefined") {
              sessionStorage.removeItem("exp:pending_provider_number");
            }

            router.push("/facility");
          },
          onError: () => {
            setScanState("error");
            setError("Biometric verification failed. Please try again.");
          },
        },
      );
    }, 2000);
  }, [login, setAuth, router]);

  const scanColors: Record<ScanState, string> = {
    idle: "border-gray-300 text-gray-400 bg-gray-50",
    scanning: "border-blue-400 text-blue-500 bg-blue-50 animate-pulse",
    success: "border-green-400 text-green-500 bg-green-50",
    error: "border-red-400 text-red-500 bg-red-50",
  };

  return (
    <AuthLayout>
      <div className="mb-4">
        <Link
          href="/auth/login"
          className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to sign in options
        </Link>
      </div>

      <h2 className="text-xl font-semibold text-gray-900 mb-1">Biometric Verification</h2>
      <p className="text-sm text-gray-500 mb-6">
        Place your finger on the scanner to verify your identity
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="flex flex-col items-center py-6">
        {/* Fingerprint scanner visualization */}
        <div
          className={`w-32 h-32 rounded-full border-4 flex items-center justify-center transition-all duration-500 ${scanColors[scanState]}`}
        >
          {scanState === "success" ? (
            <CheckCircle2 className="w-16 h-16" />
          ) : scanState === "error" ? (
            <XCircle className="w-16 h-16" />
          ) : scanState === "scanning" ? (
            <Loader2 className="w-16 h-16 animate-spin" />
          ) : (
            <Fingerprint className="w-16 h-16" />
          )}
        </div>

        <p className="mt-4 text-sm text-gray-500">
          {scanState === "idle" && "Tap the button below to start scanning"}
          {scanState === "scanning" && "Scanning... keep your finger still"}
          {scanState === "success" && "Verification successful! Redirecting..."}
          {scanState === "error" && "Scan failed. Please try again."}
        </p>
      </div>

      <button
        type="button"
        onClick={handleVerify}
        disabled={scanState === "scanning" || scanState === "success"}
        className="w-full py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
      >
        {scanState === "scanning" ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            Verifying...
          </>
        ) : scanState === "success" ? (
          <>
            <CheckCircle2 className="w-4 h-4" />
            Verified
          </>
        ) : (
          "Verify"
        )}
      </button>
    </AuthLayout>
  );
}
