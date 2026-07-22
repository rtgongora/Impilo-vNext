"use client";

/**
 * Sign In — Person-centred primary login page.
 * Route: /auth/login | pageTitle: "Sign In"
 *
 * Health OS Identity Doctrine: everyone signs in as a person.
 * The Health ID IS the person's ID — providers use the same identity.
 * After successful login, navigates to /auth/resolving for identity
 * resolution before reaching /home.
 *
 * Features: email-or-phone + password, show/hide toggle, validation,
 * auth redirect, method selection (Health ID, Biometric), forgot password,
 * guest/browse mode, Health ID request link.
 */

import { useState, useEffect, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  Mail,
  Lock,
  Loader2,
  BadgeCheck,
  Fingerprint,
  Eye,
  EyeOff,
  IdCard,
  ArrowRight,
  ScanFace,
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { GatewayEscalationExplainer } from "@/components/intelligent/GatewayEscalationExplainer";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { useLogin } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore, CURRENT_CONSENT_VERSION } from "@/hooks/useConsentStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import {
  INTENT_QUERY_PARAM,
  captureIntentFromToken,
  isSafeIntentDestination,
  peekIntent,
  type GatewayIntent,
} from "@/lib/gateway-intent";
import { buildPostLoginResolvingPath } from "@/lib/resolve-post-login-destination";

const PREVIEW_VASHANDI_PASSWORD = "Vashandi@2024!";

const PREVIEW_VASHANDI_ACCOUNTS = [
  { email: "vashandi.national@mohcc.gov.zw", label: "National Admin", desc: "Workforce registry + analytics", color: "bg-indigo-50 border-indigo-200 text-indigo-800" },
  { email: "vashandi.facility@mohcc.gov.zw", label: "Facility Manager", desc: "Rosters + assignments", color: "bg-sky-50 border-sky-200 text-sky-800" },
  { email: "vashandi.worker@mohcc.gov.zw", label: "Ordinary Worker", desc: "My roster + attendance", color: "bg-emerald-50 border-emerald-200 text-emerald-800" },
  { email: "vashandi.hsc@mohcc.gov.zw", label: "HSC Workforce", desc: "HSC postings", color: "bg-violet-50 border-violet-200 text-violet-800" },
  { email: "vashandi.reviewer@mohcc.gov.zw", label: "Access Reviewer", desc: "Access review + analytics", color: "bg-amber-50 border-amber-200 text-amber-800" },
  { email: "tatenda.moyo@example.com", label: "Citizen (negative)", desc: "No Vashandi access", color: "bg-primary-soft border-primary/25 text-primary-hover" },
] as const;

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const login = useLogin();
  const { isAuthenticated, setAuth } = useAuthStore();

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Gateway intent (doctrine §4.1 law 3): capture a ?gwi= token arriving with the
  // redirect so the journey survives sign-in even across storage loss, and read any
  // pending intent so Nompilo can explain WHY sign-in is being requested (§9).
  const intentToken = searchParams.get(INTENT_QUERY_PARAM);
  const [pendingIntent, setPendingIntent] = useState<GatewayIntent | null>(null);
  useEffect(() => {
    const fromToken = intentToken ? captureIntentFromToken(intentToken) : null;
    setPendingIntent(fromToken ?? peekIntent());
  }, [intentToken]);

  // Redirect already-authenticated users
  useEffect(() => {
    if (isAuthenticated) {
      router.push(buildPostLoginResolvingPath(returnTo));
    }
  }, [isAuthenticated, returnTo, router]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!identifier.trim()) {
      setError("Please enter your email or phone number.");
      return;
    }
    if (!password.trim()) {
      setError("Please enter your password.");
      return;
    }

    login.mutate(
      { email: identifier.trim(), password },
      {
        onSuccess: (res) => {
          const attrs = res.data.attributes;
          const { token, user } = attrs;
          const expiresAt = (attrs as Record<string, unknown>).expiresAt as string | undefined;
          setAuth(
            {
              id: user.id,
              healthId: (user as { healthId?: string }).healthId ?? user.id,
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
              loginMethod: "email",
            },
            token,
            null,
            expiresAt
          );
          useWorkModeStore.getState().deriveFromRoles(user.roles);

          // Hydrate consent from localStorage immediately — if the user
          // already accepted, this prevents the consent gate from firing.
          useConsentStore.getState().hydrate(user.id);

          router.push(buildPostLoginResolvingPath(returnTo));
        },
        onError: (err: unknown) => {
          const status = (err as { status?: number })?.status;
          if (status === 503) {
            setError("Authentication service is temporarily unavailable. Please try again later.");
          } else {
            setError("Invalid email or password. Please try again.");
          }
        },
      }
    );
  }

  function handleQuickLogin(email: string, quickPassword: string) {
    setError(null);
    setIdentifier(email);
    setPassword(quickPassword);
    login.mutate(
      { email, password: quickPassword },
      {
        onSuccess: (res) => {
          const attrs = res.data.attributes;
          const { token, user: u } = attrs;
          setAuth(
            {
              id: u.id,
              healthId: (u as { healthId?: string }).healthId ?? u.id,
              email: u.email,
              displayName: u.displayName,
              roles: u.roles,
              actorType: u.actorType as "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM",
              assuranceLevel: "VERIFIED",
              providerActivated: false,
              loginMethod: "email",
            },
            token,
            null,
            (attrs as Record<string, unknown>).expiresAt as string | undefined,
          );
          useWorkModeStore.getState().deriveFromRoles(u.roles);
          useConsentStore.getState().hydrate(u.id);
          router.push(buildPostLoginResolvingPath(returnTo));
        },
        onError: () => {
          setError(`Login failed for ${email}. Check Keycloak is running.`);
        },
      },
    );
  }

  const showPreviewVashandiQuickLogin = process.env.NEXT_PUBLIC_IMPILO_ENV === "full-preview";

  return (
    <AuthLayout>
      <h2 className="text-xl font-semibold text-foreground mb-1">Welcome back</h2>
      <p className="text-sm text-muted-foreground mb-6">
        Sign in to continue to Impilo
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger">
          {error}
        </div>
      )}

      {/* Nompilo mediates the escalation (doctrine §9): when the person arrives with a
          gateway intent, explain why sign-in is being asked and how their journey is
          preserved — with "Continue without signing in" back to the public page they
          came from where the activity permits it. */}
      {pendingIntent ? (
        <GatewayEscalationExplainer
          stepKey={pendingIntent.pillar === "get-care" ? "signin-to-book" : "signin-to-personal"}
          continueWithoutHref={
            isSafeIntentDestination(pendingIntent.params.from) ? pendingIntent.params.from : null
          }
          className="mb-4"
        />
      ) : null}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="identifier" className="block text-sm font-medium text-foreground mb-1">
            Email or phone
          </label>
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              id="identifier"
              type="text"
              autoComplete="email"
              required
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="you@example.com or +263..."
              className="w-full pl-10 pr-4 py-3 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
            />
          </div>
        </div>

        <div>
          <div className="flex items-center justify-between mb-1">
            <label htmlFor="password" className="text-sm font-medium text-foreground">
              Password
            </label>
            <Link
              href="/auth/forgot-password"
              className="text-xs text-primary hover:text-primary-hover transition-colors"
            >
              Forgot password?
            </Link>
          </div>
          <div className="relative">
            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              id="password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
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

        <button
          type="submit"
          disabled={login.isPending}
          className="w-full py-3 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
        >
          {login.isPending ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Signing in...
            </>
          ) : (
            "Sign In"
          )}
        </button>
      </form>

      {/* Health Provider Login — the permanent workforce entrance (PJ5, D-P4).
          Same accounts, same trust platform — a different front door, never a
          different identity silo. */}
      <div className="mt-6 pt-6 border-t border-border">
        <Link
          href="/auth/login/provider-id"
          className="w-full flex items-center justify-center gap-2 py-2.5 border-2 border-impilo-500 text-primary text-sm font-medium rounded-lg hover:bg-primary-soft transition-colors"
        >
          <BadgeCheck className="w-4 h-4" />
          Health Provider Login — Work now
        </Link>
      </div>

      {/* Alternative sign-in methods */}
      <div className="mt-6 pt-6 border-t border-border">
        <p className="text-xs text-muted-foreground text-center mb-3">
          Other sign-in methods
        </p>
        <div className="flex flex-col gap-3">
          <Link
            href="/auth/login/biometric"
            data-testid="passkey-signin-link"
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2.5 border border-border rounded-lg text-sm text-foreground hover:border-primary/25 hover:bg-primary-soft transition-colors"
          >
            <Fingerprint className="w-4 h-4 text-primary" />
            Sign in with a passkey
          </Link>
          {/* L3 — ABIS 1:N scan-to-login. A DISTINCT path from device passkeys: no
              password, no enrolled device — the person is found by their biometric.
              Flag-gated in the BFF; the page shows an honest "not enabled" state when off. */}
          <Link
            href="/auth/login/scan"
            data-testid="biometric-scan-signin-link"
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2.5 border border-border rounded-lg text-sm text-foreground hover:border-primary/25 hover:bg-primary-soft transition-colors"
          >
            <ScanFace className="w-4 h-4 text-primary" />
            Sign in with your fingerprint
          </Link>
        </div>
      </div>

      {/* New to Impilo */}
      <div className="mt-6 pt-6 border-t border-border">
        <p className="text-xs text-muted-foreground text-center mb-3">
          New to Impilo?
        </p>
        <Link
          href={(() => {
            // R1 "Reachable" is the low-friction front door (doctrine: help before
            // identity). Carry any gateway intent + returnTo into the contact flow.
            const qs = new URLSearchParams();
            if (returnTo) qs.set("returnTo", returnTo);
            if (intentToken) qs.set(INTENT_QUERY_PARAM, intentToken);
            const suffix = qs.toString();
            return suffix ? `/auth/register/contact?${suffix}` : "/auth/register/contact";
          })()}
          className="w-full flex items-center justify-center gap-2 py-2.5 border-2 border-impilo-500 text-primary text-sm font-medium rounded-lg hover:bg-primary-soft transition-colors"
        >
          Create an account
        </Link>
      </div>

      {/* Other ways to access */}
      <div className="mt-4 pt-4 border-t border-border">
        <p className="text-xs text-muted-foreground text-center mb-3">
          Other ways to access
        </p>
        <div className="space-y-2">
          <Link
            href="/citizen/health-id/request"
            className="w-full flex items-center justify-center gap-2 px-3 py-2.5 border border-border rounded-lg text-sm text-foreground hover:border-primary/25 hover:bg-primary-soft transition-colors"
          >
            <IdCard className="w-4 h-4 text-primary" />
            Request an Impilo ID
          </Link>
          <Link
            href="/home"
            className="w-full flex items-center justify-center gap-2 px-3 py-2.5 border border-border rounded-lg text-sm text-muted-foreground hover:border-border hover:bg-background transition-colors"
          >
            Continue without an account
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>
      </div>

      <p className="mt-4 text-center text-xs text-muted-foreground">
        By signing in, you agree to the{" "}
        <Link href="/terms" className="text-blue-500 hover:text-primary-hover underline">
          Terms of Use
        </Link>{" "}
        and{" "}
        <Link href="/privacy" className="text-blue-500 hover:text-primary-hover underline">
          Privacy Policy
        </Link>
        .
      </p>
      <p className="mt-1 text-center text-[11px] text-muted-foreground">
        Consent policy version: {CURRENT_CONSENT_VERSION}
      </p>

      {/* ── Dev/test quick-login panel ─────────────────────────── */}
      {process.env.NODE_ENV === "development" && (
        <div className="mt-6 pt-4 border-t-2 border-dashed border-amber-300">
          <p className="text-xs font-semibold text-amber-600 text-center mb-3">
            DEV — Quick sign-in as:
          </p>
          <div className="grid grid-cols-2 gap-2">
            {[
              { email: "super@mohcc.gov.zw", label: "Super Admin", desc: "All roles", color: "bg-danger-soft border-danger/28 text-danger" },
              { email: "mapfumo@mohcc.gov.zw", label: "Dr Mapfumo", desc: "Clinician + Facility Admin", color: "bg-info-soft border-info/25 text-primary-hover" },
              { email: "chienda@mohcc.gov.zw", label: "Sr Chienda", desc: "Nurse", color: "bg-green-50 border-green-200 text-green-700" },
              { email: "zenda@mohcc.gov.zw", label: "Pharmacist", desc: "Pharmacist", color: "bg-warning-soft border-warning/35 text-warning-foreground" },
              { email: "finance.ndlovu@mohcc.gov.zw", label: "Finance", desc: "Finance", color: "bg-warning-soft border-warning/35 text-warning-foreground" },
              { email: "tatenda.moyo@example.com", label: "Citizen", desc: "Citizen only", color: "bg-primary-soft border-primary/25 text-primary-hover" },
              { email: "admin@mohcc.gov.zw", label: "Sys Admin", desc: "System Admin", color: "bg-background border-border text-foreground" },
              { email: "support@mohcc.gov.zw", label: "Support", desc: "Support Agent", color: "bg-teal-50 border-teal-200 text-teal-700" },
            ].map((acct) => (
              <button
                key={acct.email}
                type="button"
                disabled={login.isPending}
                onClick={() => handleQuickLogin(acct.email, "test123")}
                className={`text-left px-3 py-2 rounded-lg border text-xs transition-colors hover:shadow-sm ${acct.color} ${login.isPending ? "opacity-50" : ""}`}
              >
                <p className="font-semibold">{acct.label}</p>
                <p className="opacity-70">{acct.desc}</p>
              </button>
            ))}
          </div>
          <p className="text-[10px] text-amber-400 text-center mt-2">
            All test accounts use password: test123
          </p>
        </div>
      )}

      {showPreviewVashandiQuickLogin && (
        <div className="mt-6 pt-4 border-t-2 border-dashed border-violet-300">
          <p className="text-xs font-semibold text-violet-700 text-center mb-3">
            PREVIEW — Vashandi validation sign-in
          </p>
          <div className="grid grid-cols-2 gap-2">
            {PREVIEW_VASHANDI_ACCOUNTS.map((acct) => (
              <button
                key={acct.email}
                type="button"
                disabled={login.isPending}
                onClick={() => handleQuickLogin(acct.email, PREVIEW_VASHANDI_PASSWORD)}
                className={`text-left px-3 py-2 rounded-lg border text-xs transition-colors hover:shadow-sm ${acct.color} ${login.isPending ? "opacity-50" : ""}`}
              >
                <p className="font-semibold">{acct.label}</p>
                <p className="opacity-70">{acct.desc}</p>
              </button>
            ))}
          </div>
          <p className="text-[10px] text-violet-500 text-center mt-2">
            Preview Vashandi personas use password: {PREVIEW_VASHANDI_PASSWORD}
          </p>
        </div>
      )}

      <NompiloHint
        message="Welcome to Impilo. Sign in with your email, phone number, or Impilo ID. If you don't have an account yet, you can create one below."
        suggestions={["Forgot your password? Use the link below the form", "New here? Tap 'Create an account' to get started"]}
      />
    </AuthLayout>
  );
}
