"use client";

/**
 * Progressive Auth Form — Unified "Sign in to Impilo" entry experience
 * with Express Entry Intent Selection and Progressive Credential Disclosure.
 *
 * Implements the doctrine:
 * 1. Single Door: "Sign in to Impilo" (no separate login products).
 * 2. Express Work Intent: Providers logging in for work select "Work & Practice" or arrive via work URLs,
 *    and land DIRECTLY in their work workspace (/work/facility, /ehr, /pharmacy) without personal citizen tabs.
 * 3. Progressive Disclosure: Step 1 Identifier -> Step 2 Credential Resolution.
 */

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { type FormEvent, useState } from "react";
import {
  ArrowRight,
  BadgeCheck,
  Building2,
  ChevronDown,
  ChevronUp,
  Fingerprint,
  KeyRound,
  ScanFace,
  Stethoscope,
  UserCheck,
} from "lucide-react";
import { useLogin } from "@/hooks/queries/useAuth";
import { INTENT_QUERY_PARAM } from "@/lib/gateway-intent";
import { safePublicHref } from "@/components/public/ContinueWithoutSignIn";
import { useAuthStore } from "@/hooks/useAuthStore";
import { writeReturnHint } from "@/lib/return-hint";

type EntryIntent = "personal" | "work" | "regulatory";

interface ProgressiveAuthFormProps {
  returnTo?: string | null;
}

export function ProgressiveAuthForm({ returnTo }: ProgressiveAuthFormProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const intentToken = searchParams?.get(INTENT_QUERY_PARAM) || null;

  // Auto-detect intent from return URL or query params
  const defaultIntent: EntryIntent = (() => {
    if (returnTo?.includes("/work") || returnTo?.includes("/provider") || returnTo?.includes("/ehr") || returnTo?.includes("/pharmacy")) {
      return "work";
    }
    if (returnTo?.includes("/regulatory") || returnTo?.includes("/organization-admin")) {
      return "regulatory";
    }
    return "personal";
  })();

  const [intent, setIntent] = useState<EntryIntent>(defaultIntent);
  const [step, setStep] = useState<"identifier" | "credential">("identifier");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [showDevAccounts, setShowDevAccounts] = useState(false);
  const [showAltMethods, setShowAltMethods] = useState(false);
  const [rememberDevice, setRememberDevice] = useState(false);

  const login = useLogin();

  const handleIdentifierSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!identifier.trim()) return;
    setStep("credential");
  };

  const getTargetDestination = () => {
    let target = returnTo;
    if (intent === "work" && (!target || target === "/home" || target === "/")) {
      target = "/home/workplace";
    } else if (intent === "regulatory" && (!target || target === "/home" || target === "/")) {
      target = "/organization-admin";
    }
    return target || "/home";
  };

  const handleFinalSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!identifier || !password) return;

    login.mutate(
      {
        email: identifier.trim(),
        password,
      },
      {
        onSuccess: () => {
          if (rememberDevice) writeReturnHint(useAuthStore.getState().user?.displayName);
          router.push(getTargetDestination());
        },
      }
    );
  };

  const handleQuickLogin = (email: string, pass: string) => {
    setIdentifier(email);
    setPassword(pass);
    login.mutate(
      { email, password: pass },
      {
        onSuccess: () => {
          if (rememberDevice) writeReturnHint(useAuthStore.getState().user?.displayName);
          router.push(getTargetDestination());
        },
      }
    );
  };

  return (
    <div className="w-full space-y-6" data-testid="progressive-auth-form">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          Sign in to Impilo
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          One identity entry for personal care, healthcare practice, and facility operations.
        </p>
      </div>

      {/* Express Entry Intent Selector */}
      <div className="space-y-1.5" data-testid="express-intent-selector">
        <label className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Sign-in Context
        </label>
        <div className="grid grid-cols-3 gap-1.5 rounded-xl border border-slate-200 bg-slate-100/80 p-1">
          <button
            type="button"
            onClick={() => setIntent("personal")}
            className={`flex flex-col items-center justify-center rounded-lg py-2 px-1 text-center transition-all ${
              intent === "personal"
                ? "bg-white text-emerald-900 shadow-sm font-bold ring-1 ring-emerald-500/30"
                : "text-slate-600 hover:text-slate-900 font-medium"
            }`}
          >
            <span className="text-xs">Personal & Family</span>
            <span className="text-[10px] text-slate-400 font-normal">My Impilo</span>
          </button>

          <button
            type="button"
            onClick={() => setIntent("work")}
            className={`flex flex-col items-center justify-center rounded-lg py-2 px-1 text-center transition-all ${
              intent === "work"
                ? "bg-amber-600 text-white shadow-sm font-bold ring-1 ring-amber-500"
                : "text-slate-600 hover:text-slate-900 font-medium"
            }`}
          >
            <span className="text-xs flex items-center gap-1">
              <Stethoscope className="h-3 w-3 shrink-0" />
              Work & Practice
            </span>
            <span className={intent === "work" ? "text-amber-100 text-[10px] font-normal" : "text-slate-400 text-[10px] font-normal"}>
              Shift & Facility
            </span>
          </button>

          <button
            type="button"
            onClick={() => setIntent("regulatory")}
            className={`flex flex-col items-center justify-center rounded-lg py-2 px-1 text-center transition-all ${
              intent === "regulatory"
                ? "bg-slate-900 text-white shadow-sm font-bold ring-1 ring-slate-700"
                : "text-slate-600 hover:text-slate-900 font-medium"
            }`}
          >
            <span className="text-xs flex items-center gap-1">
              <Building2 className="h-3 w-3 shrink-0" />
              Regulatory
            </span>
            <span className={intent === "regulatory" ? "text-slate-300 text-[10px] font-normal" : "text-slate-400 text-[10px] font-normal"}>
              Oversight
            </span>
          </button>
        </div>

        {intent === "work" && (
          <p className="text-[11px] text-amber-800 bg-amber-50 border border-amber-200/80 rounded-lg p-2 flex items-center gap-1.5">
            <BadgeCheck className="h-4 w-4 text-amber-600 shrink-0" />
            <span><strong>Work Context Active:</strong> After sign-in, you will land directly in your clinical workplace without personal citizen tabs.</span>
          </p>
        )}
      </div>

      {/* Step 1: Identifier Input */}
      {step === "identifier" ? (
        <form onSubmit={handleIdentifierSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="identifier-input" className="text-xs font-semibold text-slate-700">
              Email, phone number, or Impilo ID
            </label>
            <input
              id="identifier-input"
              type="text"
              required
              placeholder="e.g. mapfumo@mohcc.gov.zw or 0771234567"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-emerald-600 focus:outline-none focus:ring-2 focus:ring-emerald-600/20"
            />
          </div>

          <button
            type="submit"
            disabled={!identifier.trim()}
            className="w-full flex items-center justify-center gap-2 rounded-xl bg-emerald-600 py-3 px-4 text-sm font-semibold text-white shadow-md hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:ring-offset-2 disabled:opacity-50 transition-all"
          >
            Continue
            <ArrowRight className="h-4 w-4" />
          </button>

          <div className="flex items-center justify-between pt-2 text-xs">
            <Link
              href={(() => {
                const qs = new URLSearchParams();
                if (returnTo) qs.set("returnTo", returnTo);
                if (intentToken) qs.set(INTENT_QUERY_PARAM, intentToken);
                const s = qs.toString();
                return s ? `/auth/register/contact?${s}` : "/auth/register/contact";
              })()}
              className="font-medium text-emerald-700 hover:underline"
            >
              Create an account
            </Link>
            <Link href={safePublicHref(returnTo || "/")} className="text-slate-500 hover:text-slate-800">
              Continue as guest
            </Link>
          </div>
        </form>
      ) : (
        /* Step 2: Credential Resolution */
        <form onSubmit={handleFinalSubmit} className="space-y-4">
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 flex items-center justify-between text-xs">
            <div className="flex items-center gap-2 truncate">
              <UserCheck className="h-4 w-4 text-emerald-600 shrink-0" />
              <span className="font-semibold text-slate-800 truncate">{identifier}</span>
            </div>
            <button
              type="button"
              onClick={() => setStep("identifier")}
              className="text-emerald-700 hover:underline font-medium shrink-0 ml-2"
            >
              Change
            </button>
          </div>

          {login.isError && (
            <div className="rounded-xl bg-red-50 border border-red-200 p-3 text-xs text-red-800" role="alert">
              {(login.error as Error)?.message || "Authentication failed. Please verify your credentials."}
            </div>
          )}

          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs font-semibold text-slate-700">
              <label htmlFor="password-input">Password</label>
              <Link href="/auth/forgot-password" className="text-emerald-700 hover:underline font-normal text-[11px]">
                Forgot password?
              </Link>
            </div>
            <input
              id="password-input"
              type="password"
              required
              autoFocus
              placeholder="Enter your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-emerald-600 focus:outline-none focus:ring-2 focus:ring-emerald-600/20"
            />
          </div>

          <label className="flex items-center gap-2 text-xs font-medium text-slate-600">
            <input
              type="checkbox"
              checked={rememberDevice}
              onChange={(e) => setRememberDevice(e.target.checked)}
              className="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
            />
            Remember me on this device
          </label>

          <button
            type="submit"
            disabled={login.isPending || !password}
            className="w-full flex items-center justify-center gap-2 rounded-xl bg-emerald-600 py-3 px-4 text-sm font-semibold text-white shadow-md hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:ring-offset-2 disabled:opacity-50 transition-all"
          >
            {login.isPending ? "Signing in..." : "Sign in"}
          </button>

          {/* Progressive disclosure: Alternative sign-in methods */}
          <div className="border-t border-slate-200 pt-3">
            <button
              type="button"
              onClick={() => setShowAltMethods(!showAltMethods)}
              className="w-full flex items-center justify-between text-xs text-slate-600 font-medium hover:text-slate-900"
            >
              <span className="flex items-center gap-1.5">
                <KeyRound className="h-3.5 w-3.5 text-slate-500" />
                Use another sign-in method
              </span>
              {showAltMethods ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
            </button>

            {showAltMethods && (
              <div className="mt-2.5 space-y-2 pt-1">
                <Link
                  href="/auth/login/biometric"
                  className="flex items-center gap-2.5 rounded-lg border border-slate-200 p-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  <Fingerprint className="h-4 w-4 text-emerald-600" />
                  Sign in with Passkey
                </Link>
                <Link
                  href="/auth/login/scan"
                  className="flex items-center gap-2.5 rounded-lg border border-slate-200 p-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  <ScanFace className="h-4 w-4 text-emerald-600" />
                  Sign in with Fingerprint scan
                </Link>
              </div>
            )}
          </div>
        </form>
      )}

      {/* Dev Quick-Login Drawer */}
      {process.env.NODE_ENV === "development" && (
        <div className="border-t border-dashed border-amber-300 pt-3">
          <button
            type="button"
            onClick={() => setShowDevAccounts(!showDevAccounts)}
            className="w-full flex items-center justify-between text-xs font-semibold text-amber-700 hover:text-amber-900"
          >
            <span>DEV — Quick Test Personas</span>
            {showDevAccounts ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
          </button>

          {showDevAccounts && (
            <div className="mt-2.5 grid grid-cols-2 gap-1.5 text-xs">
              {[
                { email: "mapfumo@mohcc.gov.zw", label: "Dr Mapfumo", desc: "Clinician & Facility Admin" },
                { email: "chienda@mohcc.gov.zw", label: "Sr Chienda", desc: "Nurse Operator" },
                { email: "zenda@mohcc.gov.zw", label: "Pharmacist", desc: "Pharmacy Manager" },
                { email: "tatenda.moyo@example.com", label: "Citizen", desc: "Citizen PHR" },
              ].map((acct) => (
                <button
                  key={acct.email}
                  type="button"
                  onClick={() => handleQuickLogin(acct.email, "test123")}
                  className="text-left rounded-lg border border-amber-200 bg-amber-50/60 p-2 hover:bg-amber-100/80 transition-colors"
                >
                  <p className="font-bold text-amber-900 text-[11px]">{acct.label}</p>
                  <p className="text-[10px] text-amber-700/80 truncate">{acct.desc}</p>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
