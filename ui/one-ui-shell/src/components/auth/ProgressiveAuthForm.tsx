"use client";

/**
 * Single Impilo sign-in door. Identity intent is collected here; every password,
 * passkey, TOTP and recovery-code interaction is hosted and verified by Keycloak.
 * The shell never receives credentials or OAuth tokens.
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { type FormEvent, type ReactNode, useState } from "react";
import { ArrowRight, BadgeCheck, Building2, KeyRound, Stethoscope } from "lucide-react";
import { INTENT_QUERY_PARAM } from "@/lib/gateway-intent";
import { safePublicHref } from "@/components/public/ContinueWithoutSignIn";
import { buildOidcLoginUrl } from "@/lib/auth/web-session";
import { SignInModal } from "@/components/auth/SignInModal";

type EntryIntent = "personal" | "work" | "regulatory";

interface ProgressiveAuthFormProps {
  returnTo?: string | null;
}

export function ProgressiveAuthForm({ returnTo }: ProgressiveAuthFormProps) {
  const searchParams = useSearchParams();
  const intentToken = searchParams?.get(INTENT_QUERY_PARAM) || null;
  const defaultIntent: EntryIntent =
    returnTo?.includes("/work") || returnTo?.includes("/provider") ||
    returnTo?.includes("/ehr") || returnTo?.includes("/pharmacy")
      ? "work"
      : returnTo?.includes("/regulatory") || returnTo?.includes("/organization-admin")
        ? "regulatory"
        : "personal";
  const [intent, setIntent] = useState<EntryIntent>(defaultIntent);
  const [identifier, setIdentifier] = useState("");
  // Non-null while the in-page sign-in modal is open. The credential step runs inside it
  // rather than as a full-page redirect, so the person never leaves the shell.
  const [signInUrl, setSignInUrl] = useState<string | null>(null);

  function destination(): string {
    if (returnTo && returnTo.startsWith("/") && !returnTo.startsWith("//")) return returnTo;
    if (intent === "regulatory") return "/organization-admin";
    return "/home";
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!identifier.trim()) return;
    // returnTo points at /auth/complete, which signals this window and closes the modal.
    // The real destination rides along so the opener — not the frame — does the navigating.
    setSignInUrl(buildOidcLoginUrl({
      returnTo: `/auth/complete?to=${encodeURIComponent(destination())}`,
      loginHint: identifier,
      requiredAcr: intent === "personal" ? null : "urn:impilo:aal2",
    }));
  }

  return (
    <div className="w-full space-y-6" data-testid="progressive-auth-form">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">Sign in to Impilo</h1>
        <p className="mt-1 text-sm text-slate-600">
          One identity entry for personal care, healthcare practice, and facility operations.
        </p>
      </div>

      <div className="space-y-1.5" data-testid="express-intent-selector">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Sign-in context</span>
        <div className="grid grid-cols-3 gap-1.5 rounded-xl border border-slate-200 bg-slate-100/80 p-1">
          <IntentButton selected={intent === "personal"} onClick={() => setIntent("personal")}
            title="Personal & Family" subtitle="My Impilo" />
          <IntentButton selected={intent === "work"} onClick={() => setIntent("work")}
            title="Work & Practice" subtitle="Shift & Facility" icon={<Stethoscope className="h-3 w-3" />} tone="amber" />
          <IntentButton selected={intent === "regulatory"} onClick={() => setIntent("regulatory")}
            title="Regulatory" subtitle="Oversight" icon={<Building2 className="h-3 w-3" />} tone="slate" />
        </div>
        {intent !== "personal" && (
          <p className="flex items-center gap-1.5 rounded-lg border border-amber-200/80 bg-amber-50 p-2 text-[11px] text-amber-800">
            <BadgeCheck className="h-4 w-4 shrink-0 text-amber-600" />
            Workforce sign-in requires multi-factor verification before access is granted.
          </p>
        )}
      </div>

      <form onSubmit={submit} className="space-y-4">
        <div className="space-y-1.5">
          <label htmlFor="identifier-input" className="text-xs font-semibold text-slate-700">
            Email, phone number, or Impilo ID / Provider ID
          </label>
          <input id="identifier-input" type="text" required autoComplete="username"
            placeholder="Enter your Impilo sign-in identifier" value={identifier}
            onChange={(event) => setIdentifier(event.target.value)}
            className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-emerald-600 focus:outline-none focus:ring-2 focus:ring-emerald-600/20" />
        </div>

        <button type="submit" disabled={!identifier.trim()}
          className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-3 text-sm font-semibold text-white shadow-md transition-all hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:ring-offset-2 disabled:opacity-50">
          Continue to secure sign-in <ArrowRight className="h-4 w-4" />
        </button>

        <div className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50/70 p-3 text-xs text-emerald-900">
          <KeyRound className="mt-0.5 h-4 w-4 shrink-0" />
          <span>Password, passkey, authenticator and recovery-code checks happen on the protected Impilo identity service. This page never receives them.</span>
        </div>

        <div className="flex items-center justify-between pt-1 text-xs">
          <Link href={registrationHref(returnTo, intentToken)} className="font-medium text-emerald-700 hover:underline">
            Create an account
          </Link>
          <Link href={safePublicHref(returnTo || "/")} className="text-slate-500 hover:text-slate-800">
            Continue as guest
          </Link>
        </div>
      </form>

      {/* Credential step, in place. Same governed OIDC flow, same identity service, same
          trust boundary — the shell still never sees a password, because the frame is a
          separate document it cannot read into. Only the window changed. */}
      {signInUrl && (
        <SignInModal authorizeUrl={signInUrl} onClose={() => setSignInUrl(null)} />
      )}
    </div>
  );
}

function registrationHref(returnTo: string | null | undefined, intentToken: string | null): string {
  const query = new URLSearchParams();
  if (returnTo) query.set("returnTo", returnTo);
  if (intentToken) query.set(INTENT_QUERY_PARAM, intentToken);
  const suffix = query.toString();
  return suffix ? `/auth/register/contact?${suffix}` : "/auth/register/contact";
}

function IntentButton({ selected, onClick, title, subtitle, icon, tone = "emerald" }: {
  selected: boolean;
  onClick: () => void;
  title: string;
  subtitle: string;
  icon?: ReactNode;
  tone?: "emerald" | "amber" | "slate";
}) {
  const selectedClass = tone === "amber" ? "bg-amber-600 text-white ring-amber-500" :
    tone === "slate" ? "bg-slate-900 text-white ring-slate-700" :
      "bg-white text-emerald-900 ring-emerald-500/30";
  return (
    <button type="button" onClick={onClick}
      className={`flex flex-col items-center justify-center rounded-lg px-1 py-2 text-center transition-all ${selected ? `${selectedClass} font-bold shadow-sm ring-1` : "font-medium text-slate-600 hover:text-slate-900"}`}>
      <span className="flex items-center gap-1 text-xs">{icon}{title}</span>
      <span className={`text-[10px] font-normal ${selected && tone !== "emerald" ? "text-white/75" : "text-slate-400"}`}>{subtitle}</span>
    </button>
  );
}
