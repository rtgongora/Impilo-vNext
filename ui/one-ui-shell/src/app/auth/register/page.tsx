"use client";

/**
 * Register — Person-first sign-up (Health OS Identity Doctrine).
 * Route: /auth/register | pageTitle: "Sign up for Impilo"
 *
 * Everyone registers as a person. No role picker. Professional capacity
 * (Provider ID, Staff ID) is discovered post-login via linked IDs.
 * On success: auto-login and redirect to /home.
 */

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  UserPlus,
  Mail,
  Lock,
  User,
  Loader2,
  Eye,
  EyeOff,
  IdCard,
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore } from "@/hooks/useConsentStore";
import { useAcceptPolicyConsent } from "@/hooks/queries/usePolicyConsent";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { CURRENT_CONSENT_VERSION } from "@/hooks/useConsentStore";
import { useRegistrationReadiness } from "@/hooks/queries/useIdentityAssurance";
import { useFormDraft } from "@/hooks/useFormDraftStore";

/** Non-secret fields preserved across a sign-in interruption (passwords are never persisted). */
interface RegisterDraft extends Record<string, unknown> {
  fullName: string;
  identifier: string;
  hasHealthId: boolean | null;
  healthId: string;
}

export default function RegisterPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const { acceptConsent } = useConsentStore();
  const acceptPolicyConsent = useAcceptPolicyConsent();

  const [fullName, setFullName] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [hasHealthId, setHasHealthId] = useState<boolean | null>(null);
  const [healthId, setHealthId] = useState("");
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [acceptedPrivacy, setAcceptedPrivacy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const readiness = useRegistrationReadiness();
  const registrationReady = readiness.data?.data?.attributes?.ready !== false;

  // Preserve non-secret entries so an interrupted sign-up (or a bounce to sign-in) returns
  // the citizen to what they typed. Passwords are never persisted (stripped by the store).
  const { hydrated, draft, save, clear } = useFormDraft<RegisterDraft>("auth-register", {
    label: "Finish creating your account",
    href: "/auth/register",
  });

  useEffect(() => {
    if (!hydrated || !draft) return;
    if (typeof draft.fullName === "string") setFullName(draft.fullName);
    if (typeof draft.identifier === "string") setIdentifier(draft.identifier);
    if (typeof draft.hasHealthId === "boolean") setHasHealthId(draft.hasHealthId);
    if (typeof draft.healthId === "string") setHealthId(draft.healthId);
  }, [hydrated, draft]);

  useEffect(() => {
    if (!hydrated) return;
    if (!fullName && !identifier && !healthId && hasHealthId === null) return;
    save({ fullName, identifier, hasHealthId, healthId });
  }, [hydrated, fullName, identifier, hasHealthId, healthId, save]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!fullName.trim()) {
      setError("Please enter your full name.");
      return;
    }
    if (!identifier.trim()) {
      setError("Please enter your email or phone number.");
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
    if (!acceptedTerms || !acceptedPrivacy) {
      setError("You must accept the Privacy Policy and Terms of Use to create an account.");
      return;
    }

    setSubmitting(true);
    try {
      // Split full name into first/last for the BFF (Keycloak requires both)
      const nameParts = fullName.trim().split(/\s+/);
      const firstName = nameParts[0];
      const lastName = nameParts.length > 1 ? nameParts.slice(1).join(" ") : nameParts[0];

      const res = await apiClient.post<ApiResponse<{
        id: string;
        type: string;
        attributes: {
          token?: string;
          expiresAt?: string;
          user?: { id: string; email: string; displayName: string; roles: string[]; actorType: string };
          status?: string;
          message?: string;
        };
      }>>("/internal/v1/auth/register", {
        fullName: fullName.trim(),
        identifier: identifier.trim(),
        email: identifier.trim(),
        password,
        firstName,
        lastName,
        healthId: hasHealthId && healthId.trim() ? healthId.trim() : undefined,
      });

      const attrs = res.data.attributes;

      if (attrs.token && attrs.user) {
        // Auto-login succeeded
        setAuth(
          {
            id: attrs.user.id,
            email: attrs.user.email,
            displayName: attrs.user.displayName,
            roles: attrs.user.roles,
            actorType: attrs.user.actorType as "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM",
            assuranceLevel: "UNVERIFIED",
            providerActivated: false,
          },
          attrs.token,
          null,
          attrs.expiresAt
        );
        // Record consent accepted during registration — both client and server
        acceptConsent(attrs.user.id);
        acceptPolicyConsent.mutate({
          version: CURRENT_CONSENT_VERSION,
          privacyPolicyAccepted: true,
          termsOfUseAccepted: true,
          channel: "WEB",
        });
        clear();
        router.push("/auth/register/assurance");
      } else if (attrs.status === "REGISTERED") {
        clear();
        router.push(`/auth/login?registered=true&message=${encodeURIComponent(attrs.message ?? "Account created. Please sign in.")}`);
      } else {
        setError("Registration completed but sign-in could not start automatically. Please sign in manually.");
      }
    } catch (err: unknown) {
      const apiErr = err as { error?: { message?: string; code?: string } };
      const code = apiErr?.error?.code;
      if (code === "USER_EXISTS") {
        setError("An account with this email already exists. Please sign in instead.");
      } else if (code === "AUTH_SERVICE_UNAVAILABLE") {
        setError("Registration is temporarily unavailable. Please try again in a few minutes.");
      } else if (code === "ROLE_ASSIGNMENT_FAILED") {
        setError("Your account could not be activated. Please try again or contact support.");
      } else if (code === "VALIDATION") {
        setError(apiErr?.error?.message ?? "Please check your details and try again.");
      } else {
        setError(apiErr?.error?.message ?? "Registration failed. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout width="xl">
      <div>
        <h2 className="text-xl font-semibold text-foreground mb-1">Sign up for Impilo</h2>
        <p className="text-sm text-muted-foreground mb-6">
          Create your personal Impilo account
        </p>

        {!registrationReady && !readiness.isLoading ? (
          <div className="mb-4 rounded-lg border border-warning/35 bg-warning-soft p-3 text-sm text-warning-foreground">
            Sign-up is temporarily unavailable. The identity service is not ready — please try again shortly.
          </div>
        ) : null}

        {error && (
          <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="min-w-0">
            <label className="block text-sm font-medium text-foreground mb-1">Full name</label>
            <div className="relative">
              <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <input
                type="text"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Your full name"
                autoComplete="name"
                className="w-full pl-10 pr-3 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              />
            </div>
          </div>

          <div className="min-w-0">
            <label className="block text-sm font-medium text-foreground mb-1">Email or phone</label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <input
                type="text"
                required
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                placeholder="you@example.com or +263..."
                autoComplete="email"
                className="w-full pl-10 pr-3 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              />
            </div>
          </div>

          <div className="min-w-0">
            <label className="block text-sm font-medium text-foreground mb-1">Password</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <input
                type={showPassword ? "text" : "password"}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="At least 12 characters with upper, lower, digit, and symbol"
                autoComplete="new-password"
                minLength={12}
                className="w-full pl-10 pr-12 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-muted-foreground"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          <div className="min-w-0">
            <label className="block text-sm font-medium text-foreground mb-1">Confirm password</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <input
                type={showPassword ? "text" : "password"}
                required
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Repeat password"
                autoComplete="new-password"
                className="w-full pl-10 pr-3 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
              />
            </div>
          </div>
          </div>

          {/* Health ID — optional link */}
          <div className="rounded-lg border border-border p-4 space-y-3">
            <p className="text-sm font-medium text-foreground">Do you have a Health ID?</p>
            <div className="flex gap-4">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name="healthIdChoice"
                  checked={hasHealthId === true}
                  onChange={() => setHasHealthId(true)}
                  className="w-4 h-4 text-primary border-border focus:ring-primary/40"
                />
                <span className="text-sm text-foreground">I have a Health ID</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name="healthIdChoice"
                  checked={hasHealthId === false}
                  onChange={() => setHasHealthId(false)}
                  className="w-4 h-4 text-primary border-border focus:ring-primary/40"
                />
                <span className="text-sm text-foreground">I don&apos;t have one yet</span>
              </label>
            </div>

            {hasHealthId === true && (
              <div className="relative">
                <IdCard className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                <input
                  type="text"
                  value={healthId}
                  onChange={(e) => setHealthId(e.target.value)}
                  placeholder="Enter your Health ID"
                  className="w-full pl-10 pr-3 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                />
              </div>
            )}

            {hasHealthId === false && (
              <p className="text-xs text-muted-foreground">
                No problem. You can request or link a Health ID at any time from your profile.
              </p>
            )}
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
            disabled={submitting || !acceptedTerms || !acceptedPrivacy || readiness.isLoading || !registrationReady}
            className="w-full py-3 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50 flex items-center justify-center gap-2 transition-colors"
          >
            {submitting ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" /> Creating account...
              </>
            ) : (
              <>
                <UserPlus className="w-4 h-4" /> Create Account
              </>
            )}
          </button>
        </form>

        <div className="mt-4 text-center">
          <p className="text-sm text-muted-foreground">
            Already have an account?{" "}
            <Link href="/auth/login" className="text-primary hover:text-primary-hover font-medium">
              Sign in
            </Link>
          </p>
        </div>
      </div>

      <NompiloHint
        message="Everyone starts as a person on Impilo. You don't need to choose a role — professional access is activated later if you qualify."
        suggestions={["If you have a Health ID, enter it to link your records", "No Health ID? You can request one after signing up"]}
      />
    </AuthLayout>
  );
}
