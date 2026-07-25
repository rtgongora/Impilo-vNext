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
import { PublicVisualAsset } from "./PublicVisualAsset";

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
 * The Impilo living canvas: need-first Nompilo command mode on the left and a
 * contextual visual/result scene on the right. The public entry remains useful
 * without the photograph, JavaScript guidance response, account or location.
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
          : "Nompilo guidance is temporarily unavailable. Public services below are still available.",
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
      className="public-living-canvas overflow-hidden rounded-[2rem] border border-emerald-100 bg-white shadow-[0_24px_70px_-36px_rgba(6,78,59,.45)]"
      aria-labelledby="living-canvas-title"
    >
      <div className="grid min-h-[34rem] lg:grid-cols-[minmax(0,1.08fr)_minmax(22rem,.92fr)]">
        <div className="relative z-10 flex flex-col justify-center p-6 sm:p-9 lg:p-12">
          <p className="inline-flex w-fit items-center gap-2 rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.08em] text-emerald-800">
            <ShieldCheck className="h-4 w-4" aria-hidden />
            {t("public.welcome.eyebrow")}
          </p>
          <h1
            id="living-canvas-title"
            className="mt-5 max-w-3xl text-[clamp(2.15rem,5.5vw,4.5rem)] font-extrabold leading-[1.02] tracking-[-0.035em] text-slate-950"
          >
            {t("public.welcome.needFirstTitle")}
          </h1>
          <p className="mt-4 max-w-2xl text-[clamp(1rem,1.7vw,1.2rem)] leading-7 text-slate-600">
            {t("public.welcome.needFirstIntro")}
          </p>
          <p className="mt-3 inline-flex items-center gap-2 text-sm font-medium text-emerald-800">
            <ShieldCheck className="h-4 w-4" aria-hidden />
            Many services are available without signing in.
          </p>

          <form
            onSubmit={submit}
            role="search"
            aria-label="Ask Nompilo about health, services or support"
            className="mt-7"
          >
            <label htmlFor="public-nompilo-intent" className="text-sm font-semibold text-slate-900">
              Ask Nompilo
            </label>
            <div className="mt-2 flex items-center gap-2 rounded-2xl border border-emerald-200 bg-white p-2 shadow-lg shadow-emerald-950/5 focus-within:border-emerald-500 focus-within:ring-2 focus-within:ring-emerald-500/20">
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
                className="min-h-9 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-left text-xs font-medium text-slate-700 hover:border-emerald-300 hover:bg-emerald-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600"
              >
                {prompt}
              </button>
            ))}
          </div>

          <div className="mt-7 flex flex-wrap items-center gap-2.5" aria-label="Primary Impilo actions">
            <Link
              href="/welcome/find-care"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-emerald-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-700 focus-visible:ring-offset-2"
            >
              <HeartHandshake className="h-4 w-4" aria-hidden />
              Get Health Services
            </Link>
            <Link
              href="/welcome/emergency"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-red-300 bg-red-50 px-4 py-2.5 text-sm font-bold text-red-800 hover:bg-red-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2"
            >
              <Siren className="h-4 w-4" aria-hidden />
              Emergency
            </Link>
            <Link
              href="/welcome/report"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl px-3 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600"
            >
              <MessageSquareHeart className="h-4 w-4" aria-hidden />
              Give Feedback
            </Link>
          </div>

          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-sm">
            <Link
              href="/verify/practitioner"
              className="inline-flex min-h-10 items-center gap-1.5 font-semibold text-emerald-800 hover:text-emerald-950"
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
              className="inline-flex min-h-10 items-center gap-1.5 font-semibold text-slate-700 hover:text-slate-950"
            >
              <UserRound className="h-4 w-4" aria-hidden />
              My Impilo
            </IntentLink>
            <Link
              href="/provider/get-access"
              className="inline-flex min-h-10 items-center gap-1.5 font-semibold text-slate-700 hover:text-slate-950"
            >
              <BriefcaseMedical className="h-4 w-4" aria-hidden />
              Work on Impilo
            </Link>
          </div>
        </div>

        <div className="min-h-80 p-3 sm:p-5 lg:p-6 lg:pl-0">
          {!hasResponse ? (
            <PublicVisualAsset
              src="/experience/hero/hero-clinic-nurse.webp"
              compactSrc="/experience/hero/hero-clinic-nurse-960.webp"
              alt="A Zimbabwean health worker using Impilo at a clinic"
              objectPosition="center 35%"
              priority
              className="h-full rounded-[1.6rem]"
              fallback={
                <>
                  <p className="text-xs font-semibold uppercase tracking-[0.08em] text-emerald-200">
                    One national health operating system
                  </p>
                  <p className="mt-2 max-w-md text-2xl font-bold leading-tight">
                    Public help now. Protected personal services when trust is needed.
                  </p>
                </>
              }
            />
          ) : (
            <div
              className={`flex h-full min-h-72 flex-col rounded-[1.6rem] border p-6 sm:p-8 ${
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
              <p className="mt-4 text-xs font-medium text-slate-500">You asked</p>
              <p className="mt-1 font-semibold text-slate-950">{askedQuestion}</p>

              {suggestion && (
                <div className="mt-5 rounded-2xl border border-white/80 bg-white/80 p-5 shadow-sm">
                  <h2
                    className={`text-xl font-bold ${
                      suggestion.tone === "emergency" ? "text-red-900" : "text-slate-950"
                    }`}
                  >
                    {suggestion.title}
                  </h2>
                  <p className="mt-2 text-sm leading-6 text-slate-700">{suggestion.description}</p>
                  <Link
                    href={suggestion.href}
                    className={`mt-4 inline-flex min-h-11 items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-bold text-white ${
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
                <p className="mt-5 flex items-center gap-2 text-sm text-violet-800">
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                  Checking trusted public guidance…
                </p>
              )}

              {guidance && (
                <div className="mt-5 rounded-2xl border border-violet-100 bg-white p-5">
                  <p className="whitespace-pre-line text-sm leading-6 text-slate-800">
                    {guidance.answer ||
                      "I do not have trusted information about that yet. Please use the public services below or ask a health worker."}
                  </p>
                  {guidance.sources && guidance.sources.length > 0 && (
                    <p className="mt-3 text-xs text-slate-500">
                      Sources: {guidance.sources.slice(0, 3).join(" · ")}
                    </p>
                  )}
                  <p className="mt-3 border-t border-slate-100 pt-3 text-xs leading-5 text-slate-500">
                    {guidance.disclaimer ||
                      "Nompilo provides general guidance, not a diagnosis or professional care."}
                  </p>
                </div>
              )}

              {error && (
                <p role="alert" className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                  {error}
                </p>
              )}

              {!suggestion && !loading && (
                <div className="mt-5 flex flex-wrap gap-3">
                  <Link
                    href="/welcome/health-info"
                    className="inline-flex min-h-10 items-center rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white"
                  >
                    Browse trusted health information
                  </Link>
                  <Link
                    href="/welcome/find-care"
                    className="inline-flex min-h-10 items-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800"
                  >
                    Find care
                  </Link>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
