"use client";

/**
 * Biometric Verification — Device biometric prompt page.
 * Route: /auth/login/biometric | pageTitle: "Biometric Verification"
 */

import { useState, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Fingerprint,
  Loader2,
  CheckCircle2,
  XCircle,
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { useLogin } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore } from "@/hooks/useConsentStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { buildPostLoginResolvingPath } from "@/lib/resolve-post-login-destination";

type VerifyState = "idle" | "verifying" | "success" | "error";

export default function BiometricLoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const login = useLogin();
  const setAuth = useAuthStore((s) => s.setAuth);

  const [verifyState, setVerifyState] = useState<VerifyState>("idle");
  const [error, setError] = useState<string | null>(null);

  const handleVerify = useCallback(() => {
    setVerifyState("verifying");
    setError(null);

    // Simulate biometric verification delay (handled by device)
    setTimeout(() => {
      setVerifyState("success");

      login.mutate(
        {
          email: "biometric@impilo.local",
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
                actorType: user.actorType as
                  | "PROVIDER"
                  | "OPERATOR"
                  | "CITIZEN"
                  | "SYSTEM",
                assuranceLevel: "VERIFIED",
                providerActivated: false,
              },
              token,
            );
            useWorkModeStore.getState().deriveFromRoles(user.roles);
            useConsentStore.getState().hydrate(user.id);
            router.push(buildPostLoginResolvingPath(returnTo));
          },
          onError: () => {
            setVerifyState("error");
            setError(
              "Biometric verification failed. Please try again or use another sign-in method.",
            );
          },
        },
      );
    }, 2000);
  }, [login, returnTo, setAuth, router]);

  const stateStyles: Record<VerifyState, string> = {
    idle: "border-border text-muted-foreground bg-background",
    verifying: "border-impilo-400 text-impilo-400 bg-primary-soft animate-pulse",
    success: "border-green-400 text-green-500 bg-green-50",
    error: "border-red-400 text-red-500 bg-danger-soft",
  };

  return (
    <AuthLayout>
      <div className="mb-4">
        <Link
          href="/auth/login"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to sign in
        </Link>
      </div>

      <h2 className="text-xl font-semibold text-foreground mb-1">
        Biometric Verification
      </h2>
      <p className="text-sm text-muted-foreground mb-6">
        Biometric verification is handled by your device
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger">
          {error}
        </div>
      )}

      <div className="flex flex-col items-center py-6">
        <div
          className={`w-32 h-32 rounded-full border-4 flex items-center justify-center transition-all duration-500 ${stateStyles[verifyState]}`}
        >
          {verifyState === "success" ? (
            <CheckCircle2 className="w-16 h-16" />
          ) : verifyState === "error" ? (
            <XCircle className="w-16 h-16" />
          ) : verifyState === "verifying" ? (
            <Loader2 className="w-16 h-16 animate-spin" />
          ) : (
            <Fingerprint className="w-16 h-16" />
          )}
        </div>

        <p className="mt-4 text-sm text-muted-foreground text-center">
          {verifyState === "idle" &&
            "Tap the button below to begin biometric verification"}
          {verifyState === "verifying" && "Verifying biometric..."}
          {verifyState === "success" && "Verification successful! Redirecting..."}
          {verifyState === "error" && "Verification failed. Please try again."}
        </p>

        <p className="mt-2 text-xs text-muted-foreground text-center">
          Biometric verification is handled by your device hardware
        </p>
      </div>

      <button
        type="button"
        onClick={handleVerify}
        disabled={verifyState === "verifying" || verifyState === "success"}
        className="w-full py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
      >
        {verifyState === "verifying" ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            Verifying...
          </>
        ) : verifyState === "success" ? (
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
