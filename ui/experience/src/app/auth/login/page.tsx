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
import { useRouter } from "next/navigation";
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
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { useLogin } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useConsentStore, CURRENT_CONSENT_VERSION } from "@/hooks/useConsentStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { apiClient } from "@/lib/api-client";

export default function LoginPage() {
  const router = useRouter();
  const login = useLogin();
  const { isAuthenticated, setAuth } = useAuthStore();

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Redirect already-authenticated users
  useEffect(() => {
    if (isAuthenticated) {
      router.push("/auth/resolving");
    }
  }, [isAuthenticated, router]);

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
          const refreshToken = (attrs as Record<string, unknown>).refreshToken as string | undefined;
          const expiresAt = (attrs as Record<string, unknown>).expiresAt as string | undefined;
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
            refreshToken,
            expiresAt
          );
          useWorkModeStore.getState().deriveFromRoles(user.roles);

          // Hydrate consent from localStorage immediately — if the user
          // already accepted, this prevents the consent gate from firing.
          useConsentStore.getState().hydrate(user.id);

          // Navigate to resolving screen for identity resolution
          router.push("/auth/resolving");
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

  return (
    <AuthLayout>
      <h2 className="text-xl font-semibold text-gray-900 mb-1">Welcome back</h2>
      <p className="text-sm text-gray-500 mb-6">
        Sign in to continue to Impilo
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="identifier" className="block text-sm font-medium text-gray-700 mb-1">
            Email or phone
          </label>
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              id="identifier"
              type="text"
              autoComplete="email"
              required
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="you@example.com or +263..."
              className="w-full pl-10 pr-4 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
            />
          </div>
        </div>

        <div>
          <div className="flex items-center justify-between mb-1">
            <label htmlFor="password" className="text-sm font-medium text-gray-700">
              Password
            </label>
            <Link
              href="/auth/forgot-password"
              className="text-xs text-impilo-500 hover:text-impilo-700 transition-colors"
            >
              Forgot password?
            </Link>
          </div>
          <div className="relative">
            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              id="password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              className="w-full pl-10 pr-12 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>

        <button
          type="submit"
          disabled={login.isPending}
          className="w-full py-3 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
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

      {/* Alternative sign-in methods */}
      <div className="mt-6 pt-6 border-t border-gray-200">
        <p className="text-xs text-gray-500 text-center mb-3">
          Other sign-in methods
        </p>
        <div className="flex gap-3">
          <Link
            href="/auth/login/provider-id"
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2.5 border border-gray-200 rounded-lg text-sm text-gray-700 hover:border-impilo-200 hover:bg-impilo-50 transition-colors"
          >
            <BadgeCheck className="w-4 h-4 text-impilo-500" />
            Health ID
          </Link>
          <Link
            href="/auth/login/biometric"
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2.5 border border-gray-200 rounded-lg text-sm text-gray-700 hover:border-impilo-200 hover:bg-impilo-50 transition-colors"
          >
            <Fingerprint className="w-4 h-4 text-impilo-500" />
            Biometric
          </Link>
        </div>
      </div>

      {/* New to Impilo */}
      <div className="mt-6 pt-6 border-t border-gray-200">
        <p className="text-xs text-gray-500 text-center mb-3">
          New to Impilo?
        </p>
        <Link
          href="/auth/register"
          className="w-full flex items-center justify-center gap-2 py-2.5 border-2 border-impilo-500 text-impilo-600 text-sm font-medium rounded-lg hover:bg-impilo-50 transition-colors"
        >
          Create an account
        </Link>
      </div>

      {/* Other ways to access */}
      <div className="mt-4 pt-4 border-t border-gray-200">
        <p className="text-xs text-gray-500 text-center mb-3">
          Other ways to access
        </p>
        <div className="space-y-2">
          <Link
            href="/citizen/health-id/request"
            className="w-full flex items-center justify-center gap-2 px-3 py-2.5 border border-gray-200 rounded-lg text-sm text-gray-700 hover:border-impilo-200 hover:bg-impilo-50 transition-colors"
          >
            <IdCard className="w-4 h-4 text-impilo-500" />
            Request a Health ID
          </Link>
          <Link
            href="/home"
            className="w-full flex items-center justify-center gap-2 px-3 py-2.5 border border-gray-200 rounded-lg text-sm text-gray-600 hover:border-gray-300 hover:bg-gray-50 transition-colors"
          >
            Continue without an account
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>
      </div>

      <p className="mt-4 text-center text-xs text-gray-400">
        By signing in, you agree to the{" "}
        <Link href="/terms" className="text-blue-500 hover:text-blue-700 underline">
          Terms of Use
        </Link>{" "}
        and{" "}
        <Link href="/privacy" className="text-blue-500 hover:text-blue-700 underline">
          Privacy Policy
        </Link>
        .
      </p>

      {/* ── Dev/test quick-login panel ─────────────────────────── */}
      {process.env.NODE_ENV === "development" && (
        <div className="mt-6 pt-4 border-t-2 border-dashed border-amber-300">
          <p className="text-xs font-semibold text-amber-600 text-center mb-3">
            DEV — Quick sign-in as:
          </p>
          <div className="grid grid-cols-2 gap-2">
            {[
              { email: "super@mohcc.gov.zw", label: "Super Admin", desc: "All roles", color: "bg-red-50 border-red-200 text-red-700" },
              { email: "mapfumo@mohcc.gov.zw", label: "Dr Mapfumo", desc: "Clinician + Facility Admin", color: "bg-blue-50 border-blue-200 text-blue-700" },
              { email: "chienda@mohcc.gov.zw", label: "Sr Chienda", desc: "Nurse", color: "bg-green-50 border-green-200 text-green-700" },
              { email: "zenda@mohcc.gov.zw", label: "Pharmacist", desc: "Pharmacist", color: "bg-purple-50 border-purple-200 text-purple-700" },
              { email: "finance.ndlovu@mohcc.gov.zw", label: "Finance", desc: "Finance", color: "bg-amber-50 border-amber-200 text-amber-700" },
              { email: "tatenda.moyo@example.com", label: "Citizen", desc: "Citizen only", color: "bg-impilo-50 border-impilo-200 text-impilo-700" },
              { email: "admin@mohcc.gov.zw", label: "Sys Admin", desc: "System Admin", color: "bg-gray-50 border-gray-200 text-gray-700" },
              { email: "support@mohcc.gov.zw", label: "Support", desc: "Support Agent", color: "bg-teal-50 border-teal-200 text-teal-700" },
            ].map((acct) => (
              <button
                key={acct.email}
                type="button"
                disabled={login.isPending}
                onClick={() => {
                  setError(null);
                  setIdentifier(acct.email);
                  setPassword("test123");
                  login.mutate(
                    { email: acct.email, password: "test123" },
                    {
                      onSuccess: (res) => {
                        const attrs = res.data.attributes;
                        const { token, user: u } = attrs;
                        setAuth(
                          {
                            id: u.id,
                            email: u.email,
                            displayName: u.displayName,
                            roles: u.roles,
                            actorType: u.actorType as "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM",
                            assuranceLevel: "VERIFIED",
                            providerActivated: false,
                          },
                          token,
                          (attrs as Record<string, unknown>).refreshToken as string | undefined,
                          (attrs as Record<string, unknown>).expiresAt as string | undefined,
                        );
                        useWorkModeStore.getState().deriveFromRoles(u.roles);
                        useConsentStore.getState().hydrate(u.id);
                        router.push("/auth/resolving");
                      },
                      onError: () => {
                        setError(`Login failed for ${acct.email}. Check Keycloak is running.`);
                      },
                    },
                  );
                }}
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

      <NompiloHint
        message="Welcome to Impilo. Sign in with your email, phone number, or Health ID. If you don't have an account yet, you can create one below."
        suggestions={["Forgot your password? Use the link below the form", "New here? Tap 'Create an account' to get started"]}
      />
    </AuthLayout>
  );
}
