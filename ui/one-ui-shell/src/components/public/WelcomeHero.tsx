"use client";

import type { FormEvent } from "react";
import { useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  BriefcaseMedical,
  Building2,
  HeartHandshake,
  Loader2,
  MessageSquareHeart,
  Search,
  ShieldCheck,
  Siren,
  Sparkles,
  UserRound,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { useI18n } from "@/lib/i18n/useI18n";
import { IntentLink } from "./IntentLink";
import { HeroDiscoverySurface } from "./HeroDiscoverySurface";
import { ReturningUserCard } from "./ReturningUserCard";

interface GuidanceAnswer {
  answer?: string;
  answerSource?: string;
  sources?: string[];
  disclaimer?: string;
}

export interface PublicIntentSuggestion {
  title: string;
  description: string;
  href: string;
  action: string;
  tone?: "emergency" | "standard";
}

/**
 * Deterministic, safety-first route suggestion. It does not diagnose or invent
 * availability; it only maps plain-language intent to an existing public journey.
 */
export function classifyPublicIntent(raw: string): PublicIntentSuggestion | null {
  const value = raw.trim().toLowerCase();
  if (!value) return null;

  if (
    /(accident|emergency|unconscious|not breathing|severe bleeding|chest pain|danger|urgent)/.test(
      value,
    )
  ) {
    return {
      title: "Start emergency help now",
      description:
        "Emergency guidance, call actions and guest assistance are available without signing in.",
      href: "/welcome/emergency",
      action: "Open Emergency Help",
      tone: "emergency",
    };
  }

  if (/(feedback|complaint|unsafe care|patient safety|compliment|report)/.test(value)) {
    return {
      title: "Give feedback or report a concern",
      description:
        "Start anonymously, receive a claim code and return for status updates without an account.",
      href: "/welcome/report",
      action: "Start feedback journey",
    };
  }

  if (/(renew|licen[cs]e|registration|regulator|professional standing|practice)/.test(value)) {
    return {
      title: "Registration and licensing",
      description:
        "Read requirements and council information publicly. Sign in only when you start or track an application.",
      href: "/welcome/regulatory",
      action: "View regulatory guidance",
    };
  }

  if (/(anxiety|mental health|depress|wellbeing|health information|symptom|learn about)/.test(value)) {
    return {
      title: "Trusted health information",
      description:
        "Browse Ministry health guidance without signing in. Personal advice still belongs with a health worker.",
      href: "/welcome/health-info",
      action: "Browse health information",
    };
  }

  if (/(medicine|product|supplier|equipment|marketplace|price)/.test(value)) {
    return {
      title: "Health products and approved suppliers",
      description:
        "Browse published listings and indicative prices. Ordering or payment begins at a clear sign-in boundary.",
      href: "/welcome/marketplace",
      action: "Browse the marketplace",
    };
  }

  if (/(cover|insurance|medical aid|benefit|payer|payment)/.test(value)) {
    return {
      title: "Health cover and payments",
      description:
        "Compare published plans and benefits without signing in. Member information remains protected.",
      href: "/welcome/coverage",
      action: "Compare health cover",
    };
  }

  if (/(course|learning|first aid|caregiver education|cpd|training)/.test(value)) {
    return {
      title: "Learning on Impilo",
      description:
        "Browse public health courses freely. Sign in only to enrol, track progress or receive a certificate.",
      href: "/welcome/learning",
      action: "Browse learning",
    };
  }

  if (/(clinic|doctor|provider|facility|pharmacy|maternity|x-ray|dialysis|care|service)/.test(value)) {
    return {
      title: "Find the right care",
      description:
        "Search the national facility directory by the service you need. Location sharing is optional.",
      href: `/welcome/find-care?q=${encodeURIComponent(raw.trim())}`,
      action: "Find care",
    };
  }

  return null;
}

const QUICK_PROMPTS = [
  "I need a clinic open now",
  "There has been an accident",
  "Where can I get help for anxiety?",
  "I want to give feedback",
  "I need to renew my professional registration",
] as const;

/**
 * The Impilo living canvas hero: need-first Nompilo command mode on the left
 * (with inline public guidance), and a live "Get Health Services" care-discovery
 * surface on the right — real Tuso/Ndila facilities, not a passive photograph.
 * The public entry stays useful without an account, location or the map bundle.
 */
export function WelcomeHero() {
  const { t } = useI18n();
  const [question, setQuestion] = useState("");
  const [askedQuestion, setAskedQuestion] = useState("");
  const [suggestion, setSuggestion] = useState<PublicIntentSuggestion | null>(null);
  const [guidance, setGuidance] = useState<GuidanceAnswer | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function askNompilo(raw: string) {
    const value = raw.trim();
    if (value.length < 3 || loading) return;

    const nextSuggestion = classifyPublicIntent(value);
    setQuestion(value);
    setAskedQuestion(value);
    setSuggestion(nextSuggestion);
    setGuidance(null);
    setError("");

    // Safety-critical intent is routed immediately. Do not delay emergency
    // access behind an advisory network request.
    if (nextSuggestion?.tone === "emergency") return;

    setLoading(true);
    try {
      const response = await apiClient.post<GuidanceAnswer>(
        "/internal/v1/public/gateway/guidance/ask",
        { question: value },
      );
      setGuidance(response);
    } catch (cause) {
      const status = (cause as { status?: number })?.status;
      setError(
        status === 429
          ? "Nompilo is busy right now. Use the suggested public journey, or wait a moment and try again."
          : "Nompilo guidance is temporarily unavailable. Public services beside and below are still available.",
      );
    } finally {
      setLoading(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void askNompilo(question);
  }

  const hasResponse = Boolean(askedQuestion);

  return (
    <section
      className="public-living-canvas relative overflow-hidden rounded-[2rem] border border-white/10 bg-[radial-gradient(70%_55%_at_78%_-8%,rgba(45,212,191,.55),transparent_62%),radial-gradient(55%_45%_at_100%_35%,rgba(103,232,249,.34),transparent_60%),radial-gradient(60%_55%_at_2%_108%,rgba(52,211,153,.5),transparent_62%),radial-gradient(45%_38%_at_38%_100%,rgba(45,212,191,.3),transparent_62%),linear-gradient(135deg,#04211c_0%,#062f2b_38%,#053c40_70%,#04252e_100%)] shadow-[0_40px_110px_-40px_rgba(2,30,26,.85)]"
      aria-labelledby="living-canvas-title"
    >
      {/* Hairline top highlight — the sleek edge where the light catches. */}
      <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/35 to-transparent" />
      <div className="relative z-10 grid min-h-[34rem] lg:grid-cols-[minmax(0,1.02fr)_minmax(24rem,.98fr)]">
        {/* Left: need-first intent + inline Nompilo guidance */}
        {/* min-w-0: grid children default to min-width:auto and refuse to shrink below
            their content, which clipped the hero text on narrow screens (the section's
            overflow-hidden hid the symptom instead of scrolling). */}
        <div className="relative z-10 flex min-w-0 flex-col justify-center p-6 sm:p-9 lg:p-12">
          <p className="inline-flex w-fit items-center gap-2 rounded-full border border-white/15 bg-white/10 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.08em] text-emerald-100 backdrop-blur-sm">
            <ShieldCheck className="h-4 w-4" aria-hidden />
            {t("public.welcome.eyebrow")}
          </p>
          <h1
            id="living-canvas-title"
            className="mt-5 max-w-3xl text-[clamp(2.15rem,5vw,4rem)] font-extrabold leading-[1.03] tracking-[-0.035em] text-white [text-shadow:0_1px_40px_rgba(45,212,191,.25)]"
          >
            {t("public.welcome.needFirstTitle")}
          </h1>
          <p className="mt-4 max-w-2xl text-[clamp(1rem,1.6vw,1.15rem)] leading-7 text-emerald-50/85">
            {t("public.welcome.needFirstIntro")}
          </p>
          <p className="mt-3 inline-flex items-center gap-2 text-sm font-medium text-teal-200">
            <ShieldCheck className="h-4 w-4" aria-hidden />
            Many services are available without signing in.
          </p>

          <form
            onSubmit={submit}
            role="search"
            aria-label="Ask Nompilo about health, services or support"
            className="mt-7"
          >
            <label htmlFor="public-nompilo-intent" className="text-sm font-semibold text-white">
              Ask Nompilo
            </label>
            <div className="mt-2 flex items-center gap-2 rounded-2xl border border-white/25 bg-white/95 p-2 shadow-[0_18px_50px_-18px_rgba(0,0,0,.6)] focus-within:border-teal-300 focus-within:ring-2 focus-within:ring-teal-300/30">
              <Sparkles className="ml-2 h-5 w-5 shrink-0 text-violet-600" aria-hidden />
              <input
                id="public-nompilo-intent"
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                type="search"
                maxLength={500}
                placeholder={t("public.welcome.nompiloPrompt")}
                className="min-h-11 min-w-0 flex-1 bg-transparent px-1 text-[15px] text-slate-950 placeholder:text-slate-400 focus:outline-none"
              />
              <button
                type="submit"
                disabled={loading || question.trim().length < 3}
                className="inline-flex min-h-11 shrink-0 items-center gap-2 rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-700 focus-visible:ring-offset-2"
              >
                {loading ? (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                ) : (
                  <Search className="h-4 w-4" aria-hidden />
                )}
                <span className="hidden sm:inline">Ask</span>
              </button>
            </div>
          </form>

          <div className="mt-3 flex flex-wrap gap-2" aria-label="Example questions">
            {QUICK_PROMPTS.map((prompt) => (
              <button
                key={prompt}
                type="button"
                onClick={() => void askNompilo(prompt)}
                className="min-h-9 rounded-full border border-white/20 bg-white/10 px-3 py-1.5 text-left text-xs font-medium text-emerald-50 backdrop-blur-sm hover:border-teal-300/60 hover:bg-white/20 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-300"
              >
                {prompt}
              </button>
            ))}
          </div>

          {/* Returning-user greeting (only when a masked opt-in hint exists). */}
          <ReturningUserCard />

          {/* Inline Nompilo public guidance (kept beside the live care surface, never over it). */}
          {hasResponse && (
            <div
              className={`mt-5 rounded-2xl border p-4 sm:p-5 ${
                suggestion?.tone === "emergency"
                  ? "border-red-200 bg-gradient-to-br from-red-50 to-white"
                  : "border-violet-200 bg-gradient-to-br from-violet-50 via-white to-emerald-50"
              }`}
              aria-live="polite"
              data-testid="public-intent-result"
            >
              <div className="flex items-center gap-2 text-sm font-semibold text-violet-800">
                <Sparkles className="h-4 w-4" aria-hidden />
                Nompilo · public guidance
              </div>
              <p className="mt-2 text-xs font-medium text-slate-500">You asked</p>
              <p className="mt-0.5 font-semibold text-slate-950">{askedQuestion}</p>

              {suggestion && (
                <div className="mt-4 rounded-xl border border-white/80 bg-white/80 p-4 shadow-sm">
                  <h2
                    className={`text-lg font-bold ${
                      suggestion.tone === "emergency" ? "text-red-900" : "text-slate-950"
                    }`}
                  >
                    {suggestion.title}
                  </h2>
                  <p className="mt-1.5 text-sm leading-6 text-slate-700">{suggestion.description}</p>
                  <Link
                    href={suggestion.href}
                    className={`mt-3 inline-flex min-h-11 items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-bold text-white ${
                      suggestion.tone === "emergency"
                        ? "bg-red-700 hover:bg-red-800"
                        : "bg-emerald-700 hover:bg-emerald-800"
                    }`}
                  >
                    {suggestion.action}
                    <ArrowRight className="h-4 w-4" aria-hidden />
                  </Link>
                </div>
              )}

              {loading && (
                <p className="mt-4 flex items-center gap-2 text-sm text-violet-800">
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                  Checking trusted public guidance…
                </p>
              )}

              {guidance && (
                <div className="mt-4 rounded-xl border border-violet-100 bg-white p-4">
                  <p className="whitespace-pre-line text-sm leading-6 text-slate-800">
                    {guidance.answer ||
                      "I do not have trusted information about that yet. Please use the public services here or ask a health worker."}
                  </p>
                  {guidance.sources && guidance.sources.length > 0 && (
                    <p className="mt-2 text-xs text-slate-500">
                      Sources: {guidance.sources.slice(0, 3).join(" · ")}
                    </p>
                  )}
                  <p className="mt-2 border-t border-slate-100 pt-2 text-xs leading-5 text-slate-500">
                    {guidance.disclaimer ||
                      "Nompilo provides general guidance, not a diagnosis or professional care."}
                  </p>
                </div>
              )}

              {error && (
                <p role="alert" className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                  {error}
                </p>
              )}
            </div>
          )}

          <div className="mt-7 flex flex-wrap items-center gap-2.5" aria-label="Primary Impilo actions">
            <Link
              href="/welcome/find-care"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-emerald-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-700 focus-visible:ring-offset-2"
            >
              <HeartHandshake className="h-4 w-4" aria-hidden />
              Get care
            </Link>
            <Link
              href="/welcome/emergency"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-red-400/60 bg-red-500/90 px-4 py-2.5 text-sm font-bold text-white hover:bg-red-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-300 focus-visible:ring-offset-2 focus-visible:ring-offset-emerald-950"
            >
              <Siren className="h-4 w-4" aria-hidden />
              Emergency
            </Link>
            <Link
              href="/welcome/report"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl px-3 py-2.5 text-sm font-semibold text-emerald-50 hover:bg-white/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-300"
            >
              <MessageSquareHeart className="h-4 w-4" aria-hidden />
              Give feedback
            </Link>
          </div>

          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-sm">
            <Link
              href="/verify/practitioner"
              className="inline-flex min-h-10 items-center gap-1.5 font-semibold text-teal-200 hover:text-white"
            >
              <Building2 className="h-4 w-4" aria-hidden />
              Find or verify
            </Link>
            <IntentLink
              pillar="my-health"
              goal="open-my-impilo"
              dest="/home"
              from="/"
              href="/auth/login?returnTo=%2Fhome"
              className="inline-flex min-h-10 items-center gap-1.5 font-semibold text-emerald-100 hover:text-white"
            >
              <UserRound className="h-4 w-4" aria-hidden />
              My Impilo
            </IntentLink>
            <Link
              href="/provider/get-access"
              className="inline-flex min-h-10 items-center gap-1.5 font-semibold text-emerald-100 hover:text-white"
            >
              <BriefcaseMedical className="h-4 w-4" aria-hidden />
              Work on Impilo
            </Link>
          </div>
        </div>

        {/* Right: unified Get Health Services discovery (care, medicines, wellness — honest, real). */}
        <div className="min-w-0 p-3 sm:p-5 lg:p-6 lg:pl-0">
          <HeroDiscoverySurface />
        </div>
      </div>
    </section>
  );
}
