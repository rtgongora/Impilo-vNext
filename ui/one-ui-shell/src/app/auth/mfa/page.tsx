"use client";

/**
 * Multi-Factor Authentication — 6-digit verification code input.
 * Route: /auth/mfa | pageTitle: "Multi-Factor Authentication"
 */

import { useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ShieldCheck, Loader2 } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { OtpCodeInput } from "@/components/auth/OtpCodeInput";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore, type AuthUser } from "@/hooks/useAuthStore";
import { useConsentStore } from "@/hooks/useConsentStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { buildPostLoginResolvingPath } from "@/lib/resolve-post-login-destination";

const CODE_LENGTH = 6;

export default function MfaPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const setAuth = useAuthStore((s) => s.setAuth);

  const [code, setCode] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleCodeChange(next: string) {
    setCode(next);
    setError(null);
  }

  async function submitCode(code: string) {
    setIsSubmitting(true);
    setError(null);

    try {
      const res = await apiClient.post<
        ApiResponse<{
          id: string;
          type: "auth_token";
          attributes: {
            token: string;
            expiresAt: string;
            user: {
              id: string;
              email: string;
              displayName: string;
              roles: string[];
              actorType: string;
            };
          };
        }>
      >("/internal/v1/auth/mfa/verify", { code });

      const { token, user } = res.data.attributes;
      setAuth(
        {
          id: user.id,
          email: user.email,
          displayName: user.displayName,
          roles: user.roles,
          actorType: user.actorType as AuthUser["actorType"],
          assuranceLevel: "VERIFIED",
          providerActivated: false,
        },
        token,
      );
      useWorkModeStore.getState().deriveFromRoles(user.roles);
      useConsentStore.getState().hydrate(user.id);
      router.push(buildPostLoginResolvingPath(returnTo));
    } catch {
      setError("Invalid verification code. Please try again.");
      setCode("");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (code.length !== CODE_LENGTH) {
      setError("Please enter the full 6-digit code.");
      return;
    }
    submitCode(code);
  }

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

      <div className="flex items-center gap-2 mb-1">
        <ShieldCheck className="w-5 h-5 text-primary" />
        <h2 className="text-xl font-semibold text-foreground">
          Verification Code
        </h2>
      </div>
      <p className="text-sm text-muted-foreground mb-6">
        Enter the 6-digit code from your authenticator app
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        <OtpCodeInput
          length={CODE_LENGTH}
          value={code}
          onChange={handleCodeChange}
          onComplete={submitCode}
          disabled={isSubmitting}
          autoFocus
        />

        <button
          type="submit"
          disabled={isSubmitting || code.length !== CODE_LENGTH}
          className="w-full py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
        >
          {isSubmitting ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Verifying...
            </>
          ) : (
            "Verify"
          )}
        </button>
      </form>

      <p className="mt-6 text-xs text-muted-foreground text-center">
        Didn&apos;t receive a code? Check your authenticator app or contact
        support.
      </p>
    </AuthLayout>
  );
}
