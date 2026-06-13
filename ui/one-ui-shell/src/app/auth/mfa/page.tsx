"use client";

/**
 * Multi-Factor Authentication — 6-digit verification code input.
 * Route: /auth/mfa | pageTitle: "Multi-Factor Authentication"
 */

import { useState, useRef, useEffect, type FormEvent, type KeyboardEvent, type ClipboardEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ShieldCheck, Loader2 } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
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

  const [digits, setDigits] = useState<string[]>(Array(CODE_LENGTH).fill(""));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    inputRefs.current[0]?.focus();
  }, []);

  function handleChange(index: number, value: string) {
    if (!/^\d*$/.test(value)) return;

    const newDigits = [...digits];
    newDigits[index] = value.slice(-1);
    setDigits(newDigits);
    setError(null);

    // Auto-advance to next input
    if (value && index < CODE_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus();
    }

    // Auto-submit when all digits are filled
    const code = newDigits.join("");
    if (code.length === CODE_LENGTH && newDigits.every((d) => d !== "")) {
      submitCode(code);
    }
  }

  function handleKeyDown(index: number, e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Backspace" && !digits[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  }

  function handlePaste(e: ClipboardEvent<HTMLInputElement>) {
    e.preventDefault();
    const pasted = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, CODE_LENGTH);
    if (!pasted) return;

    const newDigits = [...digits];
    for (let i = 0; i < pasted.length; i++) {
      newDigits[i] = pasted[i];
    }
    setDigits(newDigits);

    // Focus the next empty input or the last one
    const nextEmpty = newDigits.findIndex((d) => d === "");
    inputRefs.current[nextEmpty >= 0 ? nextEmpty : CODE_LENGTH - 1]?.focus();

    if (pasted.length === CODE_LENGTH) {
      submitCode(pasted);
    }
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
      setDigits(Array(CODE_LENGTH).fill(""));
      inputRefs.current[0]?.focus();
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const code = digits.join("");
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
        <div className="flex justify-center gap-2">
          {digits.map((digit, index) => (
            <input
              key={index}
              ref={(el) => {
                inputRefs.current[index] = el;
              }}
              type="text"
              inputMode="numeric"
              maxLength={1}
              value={digit}
              onChange={(e) => handleChange(index, e.target.value)}
              onKeyDown={(e) => handleKeyDown(index, e)}
              onPaste={index === 0 ? handlePaste : undefined}
              disabled={isSubmitting}
              className="w-12 h-14 text-center text-xl font-semibold border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400 disabled:opacity-50 disabled:cursor-not-allowed"
              aria-label={`Digit ${index + 1}`}
            />
          ))}
        </div>

        <button
          type="submit"
          disabled={isSubmitting || digits.some((d) => d === "")}
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
