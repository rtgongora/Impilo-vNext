"use client";

/**
 * Contact-first account creation — R1 "Reachable" rung (gateway doctrine §4).
 * Route: /auth/register/contact | pageTitle: "Create account with phone or email"
 *
 * Two steps, one contact channel:
 *   1. Full name + a single phone/email + Privacy/Terms → request an OTP.
 *   2. Enter the code + choose a password → verify REGISTER.
 *
 * On verify the BFF returns the same `auth_token` envelope as login (HTTP 200,
 * auto-login) or a `registration` envelope (HTTP 201) when a token could not be
 * minted. Journey context (a pending gateway intent) survives the whole flow so
 * the person returns to the exact step they were on — never restarted.
 */

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  AtSign,
  CheckCircle2,
  Loader2,
  Lock,
  Mail,
  Phone,
  User,
  Eye,
  EyeOff,
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { OtpCodeInput } from "@/components/auth/OtpCodeInput";
import { GatewayEscalationExplainer } from "@/components/intelligent/GatewayEscalationExplainer";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import {
  useRequestContactOtp,
  useVerifyContactOtp,
  type ContactRegistrationResource,
} from "@/hooks/queries/useContactOtp";
import type { AuthTokenResource } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore, CURRENT_CONSENT_VERSION } from "@/hooks/useConsentStore";
import { useAcceptPolicyConsent } from "@/hooks/queries/usePolicyConsent";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import {
  INTENT_QUERY_PARAM,
  captureIntentFromToken,
  isSafeIntentDestination,
  peekIntent,
  type GatewayIntent,
} from "@/lib/gateway-intent";
import { buildPostLoginResolvingPath } from "@/lib/resolve-post-login-destination";

const CODE_LENGTH = 6;
const RESEND_COOLDOWN_SECONDS = 60;

/** Infer the delivery channel the same way the BFF does: contains "@" → EMAIL. */
function inferChannel(value: string): "PHONE" | "EMAIL" {
  return value.includes("@") ? "EMAIL" : "PHONE";
}

function errorShape(err: unknown): { status?: number; code?: string; message?: string } {
  const e = err as { status?: number; error?: { code?: string; message?: string } };
  return { status: e?.status, code: e?.error?.code, message: e?.error?.message };
}

export default function ContactRegisterPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");

  const { setAuth } = useAuthStore();
  const { acceptConsent } = useConsentStore();
  const acceptPolicyConsent = useAcceptPolicyConsent();

  const requestOtp = useRequestContactOtp();
  const verifyOtp = useVerifyContactOtp();

  const [step, setStep] = useState<1 | 2>(1);
  const [fullName, setFullName] = useState("");
  const [contact, setContact] = useState("");
  const [acceptedPrivacy, setAcceptedPrivacy] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);

  const [maskedValue, setMaskedValue] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [registered, setRegistered] = useState(false);
  const [resendIn, setResendIn] = useState(0);

  const channel = useMemo(() => inferChannel(contact.trim()), [contact]);

  // Gateway intent (doctrine §4.1 law 3): capture a ?gwi= token so the journey
  // survives this trust escalation, and read any pending intent so Nompilo can
  // explain WHY verification is asked and offer a public off-ramp.
  const intentToken = searchParams.get(INTENT_QUERY_PARAM);
  const [pendingIntent, setPendingIntent] = useState<GatewayIntent | null>(null);
  useEffect(() => {
    const fromToken = intentToken ? captureIntentFromToken(intentToken) : null;
    setPendingIntent(fromToken ?? peekIntent());
  }, [intentToken]);

  // Resend cooldown ticker.
  useEffect(() => {
    if (resendIn <= 0) return;
    const t = setInterval(() => setResendIn((s) => (s > 0 ? s - 1 : 0)), 1000);
    return () => clearInterval(t);
  }, [resendIn]);

  const continueWithoutHref =
    pendingIntent && isSafeIntentDestination(pendingIntent.params.from)
      ? pendingIntent.params.from
      : "/welcome";

  function issueOtp() {
    setError(null);
    requestOtp.mutate(
      { channel, value: contact.trim() },
      {
        onSuccess: (res) => {
          setMaskedValue(res.data.attributes.maskedValue);
          setResendIn(RESEND_COOLDOWN_SECONDS);
          setStep(2);
        },
        onError: (err) => {
          const { status, code: errCode, message } = errorShape(err);
          if (status === 429) {
            setError("Too many code requests. Please wait a moment and try again.");
          } else if (status === 503 || errCode === "OTP_DELIVERY_UNAVAILABLE") {
            setError("Verification codes are temporarily unavailable. Please try again shortly.");
          } else if (status === 400 || errCode === "VALIDATION") {
            setError(message ?? "Please enter a valid phone number or email address.");
          } else {
            setError("We couldn't send a verification code. Please try again.");
          }
        },
      },
    );
  }

  function handleRequest(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!fullName.trim()) {
      setError("Please enter your full name.");
      return;
    }
    if (!contact.trim()) {
      setError("Please enter your phone number or email address.");
      return;
    }
    if (!acceptedPrivacy || !acceptedTerms) {
      setError("You must accept the Privacy Policy and Terms of Use to create an account.");
      return;
    }
    issueOtp();
  }

  function handleResend() {
    if (resendIn > 0 || requestOtp.isPending) return;
    setCode("");
    issueOtp();
  }

  function completeAutoLogin(resource: AuthTokenResource) {
    const attrs = resource.attributes;
    const { token, user } = attrs;
    const expiresAt = (attrs as Record<string, unknown>).expiresAt as string | undefined;
    setAuth(
      {
        id: user.id,
        healthId: (user as { healthId?: string }).healthId ?? user.id,
        email: user.email,
        displayName: user.displayName,
        roles: user.roles,
        actorType: user.actorType as "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM",
        // R1 "Reachable" = verified contact only, no identity proofing and no
        // temporary Health ID — that is UNVERIFIED (LOA1). The person moves to
        // TEMPORARY/VERIFIED only when a stronger rung (R2+, LOA2+) is reached.
        assuranceLevel: "UNVERIFIED",
        providerActivated: false,
        // Contact-OTP (phone-otp/email-otp) is a contact-channel login; the auth
        // store's loginMethod enum has no phone variant, so map to the closest.
        loginMethod: "email",
      },
      token,
      null,
      expiresAt,
    );
    useWorkModeStore.getState().deriveFromRoles(user.roles);

    // Consent captured on step 1 — record both client-side and server-side for audit.
    acceptConsent(user.id);
    acceptPolicyConsent.mutate({
      version: CURRENT_CONSENT_VERSION,
      privacyPolicyAccepted: true,
      termsOfUseAccepted: true,
      channel: "WEB",
    });
    useConsentStore.getState().hydrate(user.id);

    router.push(buildPostLoginResolvingPath(returnTo));
  }

  function handleVerify(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (code.length !== CODE_LENGTH) {
      setError("Please enter the full 6-digit code.");
      return;
    }
    if (password.length < 12) {
      setError("Password must be at least 12 characters and include upper, lower, digit, and special character.");
      return;
    }
    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    const nameParts = fullName.trim().split(/\s+/);
    const firstName = nameParts[0];
    const lastName = nameParts.length > 1 ? nameParts.slice(1).join(" ") : nameParts[0];

    verifyOtp.mutate(
      {
        value: contact.trim(),
        code,
        purpose: "REGISTER",
        password,
        firstName,
        lastName,
        fullName: fullName.trim(),
      },
      {
        onSuccess: (res) => {
          if (res.data.type === "auth_token") {
            completeAutoLogin(res.data as AuthTokenResource);
          } else {
            // 201 REGISTERED — account created, but no session minted. Sign in next.
            const attrs = (res.data as ContactRegistrationResource).attributes;
            setRegistered(true);
            setError(null);
            void attrs; // status/message available if needed
          }
        },
        onError: (err) => {
          const { status, code: errCode, message } = errorShape(err);
          if (status === 409 || errCode === "USER_EXISTS") {
            setError("An account with this contact already exists. Please sign in instead.");
          } else if (status === 429 || errCode === "OTP_LOCKED") {
            setError("Too many incorrect attempts. Request a new code and try again.");
            setCode("");
          } else if (errCode === "OTP_EXPIRED") {
            setError("This code has expired. Request a new one.");
            setCode("");
          } else if (status === 401 || errCode === "OTP_INVALID") {
            setError("Incorrect code. Please check and try again.");
            setCode("");
          } else if (status === 503) {
            setError("Registration is temporarily unavailable. Please try again shortly.");
          } else if (status === 400 || errCode === "VALIDATION") {
            setError(message ?? "Please check your details and try again.");
          } else {
            setError("Verification failed. Please try again.");
          }
        },
      },
    );
  }

  // ── Success (201 REGISTERED) ───────────────────────────────────
  if (registered) {
    return (
      <AuthLayout>
        <div className="text-center">
          <CheckCircle2 className="mx-auto h-10 w-10 text-primary" />
          <h2 className="mt-3 text-xl font-semibold text-foreground">Account created</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Your account for {maskedValue || "your contact"} is ready. Please sign in to continue.
          </p>
          <Link
            href="/auth/login"
            className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
          >
            Go to sign in
          </Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      <div className="mb-4">
        {step === 1 ? (
          <Link
            href="/auth/register"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            More sign-up options
          </Link>
        ) : (
          <button
            type="button"
            onClick={() => {
              setStep(1);
              setError(null);
            }}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Change contact details
          </button>
        )}
      </div>

      <h2 className="text-xl font-semibold text-foreground mb-1">
        {step === 1 ? "Create your account" : "Verify your contact"}
      </h2>
      <p className="text-sm text-muted-foreground mb-6">
        {step === 1
          ? "Start with your name and a phone number or email — we'll send a code to confirm it."
          : `Enter the 6-digit code we sent to ${maskedValue} and choose a password.`}
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger">
          {error}
        </div>
      )}

      {step === 1 ? (
        <form onSubmit={handleRequest} className="space-y-4">
          <div>
            <label htmlFor="fullName" className="block text-sm font-medium text-foreground mb-1">
              Full name
            </label>
            <div className="relative">
              <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <input
                id="fullName"
                type="text"
                autoComplete="name"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Your full name"
                className="w-full pl-10 pr-4 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
              />
            </div>
          </div>

          <div>
            <label htmlFor="contact" className="block text-sm font-medium text-foreground mb-1">
              Phone or email
            </label>
            <div className="relative">
              {channel === "EMAIL" ? (
                <AtSign className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              ) : (
                <Phone className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              )}
              <input
                id="contact"
                type="text"
                inputMode="text"
                autoComplete="username"
                required
                value={contact}
                onChange={(e) => setContact(e.target.value)}
                placeholder="+263... or you@example.com"
                className="w-full pl-10 pr-4 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
              />
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              We&apos;ll send a verification code by {channel === "EMAIL" ? "email" : "SMS"}.
            </p>
          </div>

          <div className="space-y-2 pt-1">
            <label className="flex items-start gap-2.5 cursor-pointer">
              <input
                type="checkbox"
                checked={acceptedPrivacy}
                onChange={(e) => setAcceptedPrivacy(e.target.checked)}
                className="mt-0.5 w-4 h-4 rounded border-border text-primary focus:ring-blue-500"
              />
              <span className="text-xs text-muted-foreground">
                I have read and accept the{" "}
                <Link href="/privacy" target="_blank" className="text-primary hover:text-blue-800 underline">
                  Privacy Policy
                </Link>
              </span>
            </label>
            <label className="flex items-start gap-2.5 cursor-pointer">
              <input
                type="checkbox"
                checked={acceptedTerms}
                onChange={(e) => setAcceptedTerms(e.target.checked)}
                className="mt-0.5 w-4 h-4 rounded border-border text-primary focus:ring-blue-500"
              />
              <span className="text-xs text-muted-foreground">
                I have read and accept the{" "}
                <Link href="/terms" target="_blank" className="text-primary hover:text-blue-800 underline">
                  Terms of Use
                </Link>
              </span>
            </label>
          </div>

          <button
            type="submit"
            disabled={requestOtp.isPending || !acceptedPrivacy || !acceptedTerms}
            className="w-full py-3 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
          >
            {requestOtp.isPending ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                Sending code...
              </>
            ) : (
              "Send verification code"
            )}
          </button>
        </form>
      ) : (
        <>
          <GatewayEscalationExplainer
            stepKey="verify-contact"
            continueWithoutHref={continueWithoutHref}
            className="mb-4"
          />

          <form onSubmit={handleVerify} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-foreground mb-2 text-center">
                Verification code
              </label>
              <OtpCodeInput
                length={CODE_LENGTH}
                value={code}
                onChange={(v) => {
                  setCode(v);
                  setError(null);
                }}
                disabled={verifyOtp.isPending}
                autoFocus
              />
              <div className="mt-2 text-center">
                <button
                  type="button"
                  onClick={handleResend}
                  disabled={resendIn > 0 || requestOtp.isPending}
                  className="text-xs text-primary hover:text-primary-hover disabled:text-muted-foreground disabled:cursor-not-allowed transition-colors"
                >
                  {resendIn > 0 ? `Resend code in ${resendIn}s` : "Resend code"}
                </button>
              </div>
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-foreground mb-1">
                Create a password
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="new-password"
                  minLength={12}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="At least 12 characters with upper, lower, digit, and symbol"
                  className="w-full pl-10 pr-12 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-muted-foreground transition-colors"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div>
              <label htmlFor="confirmPassword" className="block text-sm font-medium text-foreground mb-1">
                Confirm password
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                <input
                  id="confirmPassword"
                  type={showPassword ? "text" : "password"}
                  autoComplete="new-password"
                  required
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Repeat password"
                  className="w-full pl-10 pr-4 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={verifyOtp.isPending || code.length !== CODE_LENGTH}
              className="w-full py-3 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
            >
              {verifyOtp.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Verifying...
                </>
              ) : (
                "Verify & create account"
              )}
            </button>
          </form>
        </>
      )}

      <div className="mt-6 pt-6 border-t border-border text-center">
        <p className="text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link href="/auth/login" className="text-primary hover:text-primary-hover font-medium">
            Sign in
          </Link>
        </p>
      </div>

      <div className="mt-4 flex items-center justify-center gap-2 text-xs text-muted-foreground">
        <Mail className="h-3.5 w-3.5" />
        <span>Prefer a password sign-up? </span>
        <Link href="/auth/register" className="text-primary hover:text-primary-hover">
          Use the full form
        </Link>
      </div>

      <NompiloHint
        message="We only need one way to reach you to get started — a phone number or an email. You can add more details and verify your identity later."
        suggestions={[
          "Use a number you can receive an SMS on right now",
          "The code expires in 5 minutes — request a new one if it lapses",
        ]}
      />
    </AuthLayout>
  );
}
