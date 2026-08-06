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
  Sparkles,
  UserRound,
  Video,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { useI18n } from "@/lib/i18n/useI18n";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { IntentLink } from "./IntentLink";
import { HeroDiscoverySurface } from "./HeroDiscoverySurface";
import { HeroContinuity } from "./HeroContinuity";
import { HeroServiceStatus } from "./HeroServiceStatus";

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
      // Full-bleed living canvas beneath the white shell — not an inset card. One vertical
      // story: near-white mint at the top so the colourful brand mark sits on its natural
      // ground, ramping through living teal to a deep calm floor. The base gradient uses
      // PX-ANCHORED stops (not percentages): the section's height changes when Nompilo
      // guidance expands, and a percentage ramp would drift through the text — growth
      // extends the dark floor instead. Baked into the background because the section is
      // overflow-hidden and would clip blurred orb divs.
      className="public-living-canvas relative overflow-hidden border-y border-white/10 bg-[radial-gradient(56%_58%_at_-6%_104%,rgba(16,185,160,.35),transparent_66%),linear-gradient(180deg,#2E8C84_0px,#1A6E68_240px,#0B4A4D_430px,#063139_620px,#03222A_100%)] shadow-[0_30px_90px_-46px_rgba(2,30,26,.8)]"
      aria-labelledby="living-canvas-title"
    >
      {/* The light band the brand needs, as a FIXED-HEIGHT fading wash over the dark base —
          the Wave-1 lesson kept: it must stay behind the brand/headline/intro block and the
          discovery panel's top edge, never grow into a decorative band of its own. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 h-[26rem] sm:h-[24rem] lg:h-[21rem] bg-[linear-gradient(180deg,#F7FCFA_0%,#EDF7F1_35%,#CFE9DD_62%,rgba(148,208,192,.55)_82%,rgba(148,208,192,0)_100%)]"
      />
      {/* Fine grain so the teal ramp reads as material, not banded screen. */}
      <div aria-hidden className="impilo-grain pointer-events-none absolute inset-0 opacity-50" />
      {/* 38/62 split: intent and continuity left, service discovery right. Both zones start
          at the same top edge and run to the same depth — the right side must never begin
          halfway down the viewport. */}
      {/* The first viewport IS the hero: fill the screen below the header instead of
          stopping at an arbitrary cap that let the next section jut in. The floor
          protects short laptops from crushing the panel; the map's flex-1 absorbs
          every pixel the fill recovers. */}
      <div className="relative z-10 mx-auto grid max-w-[110rem] gap-0 px-4 py-6 sm:px-6 lg:min-h-[max(36rem,calc(100vh-4.6rem))] lg:grid-cols-[minmax(0,38fr)_minmax(26rem,62fr)] lg:gap-8 lg:px-8 lg:py-4">
        {/* Left: need-first intent + inline Nompilo guidance */}
        {/* min-w-0: grid children default to min-width:auto and refuse to shrink below
            their content, which clipped the hero text on narrow screens (the section's
            overflow-hidden hid the symptom instead of scrolling). */}
        <div className="relative z-10 flex min-w-0 flex-col">
          {/* Wordmark + tagline + question read as ONE compact composition, not a floating
              brand block with the message far below it. The COLOURFUL mark, at roughly twice
              the old scale — the light band above exists so its flag colours read true. */}
          <div className="flex flex-col gap-1">
            <ImpiloBrandLogo variant="full" tone="brand" size={64} />
            <p className="text-sm font-semibold text-[#23564B]">One Health OS. For everyone.</p>
          </div>
          <h1
            id="living-canvas-title"
            // Smaller than the earlier concept: still the loudest thing on the canvas,
            // but no longer consuming the vertical space the discovery surface needs.
            // Dark forest→emerald clip: this sits on the light band, not the teal floor.
            className="mt-4 max-w-xl bg-gradient-to-b from-[#0A3A30] to-[#0C7A5B] bg-clip-text text-[clamp(1.5rem,2.2vw,2.1rem)] font-extrabold leading-[1.1] tracking-[-0.03em] text-transparent"
          >
            {t("public.welcome.needFirstTitle")}
          </h1>
          <p className="mt-3 max-w-xl text-[15px] leading-6 text-[#245549]">
            {t("public.welcome.needFirstIntro")}
          </p>

          <form
            onSubmit={submit}
            role="search"
            aria-label="Ask Nompilo about health, services or support"
            className="mt-5"
          >
            {/* The label sits exactly where the light wash hands over to the teal floor, so
                it carries its own ground — a translucent pill — instead of gambling on
                whichever tone the ramp happens to be at this viewport width. */}
            <label
              htmlFor="public-nompilo-intent"
              className="inline-flex items-center gap-1.5 rounded-full bg-emerald-950/35 px-2.5 py-0.5 text-sm font-semibold text-white backdrop-blur-sm"
            >
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
                className="inline-flex min-h-11 shrink-0 items-center gap-2 rounded-xl bg-gradient-to-br from-emerald-500 to-emerald-700 px-4 py-2.5 text-sm font-bold text-white shadow-[inset_0_1px_0_rgba(255,255,255,.35),0_8px_20px_-8px_rgba(6,95,70,.7)] hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-700 focus-visible:ring-offset-2"
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
            {QUICK_PROMPTS.slice(0, 3).map((prompt) => (
              <button
                key={prompt}
                type="button"
                onClick={() => void askNompilo(prompt)}
                className="min-h-9 rounded-full border border-white/20 bg-emerald-950/30 px-3 py-1.5 text-left text-xs font-medium text-emerald-50 backdrop-blur-sm hover:border-teal-300/60 hover:bg-emerald-950/45 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-300"
              >
                {prompt}
              </button>
            ))}
          </div>

          {/* Continuity: welcome-back when recognised, a compact sign-in invitation when
              not. It always renders — the previous card showed nothing to a first-time
              visitor, leaving a hole where the continuation belongs. */}
          <HeroContinuity />

          {/* Journey continuity now lives inside the discovery surface on the right, where
              it uses the space the fill recovered instead of lengthening this column (which
              is what pushed the right column taller than its own content in the first place). */}

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

          {/* Live service status. Carries the lg:mt-auto so it and the action row settle
              together at the foot of the column — the wide-viewport space below the
              continuity card gets a real signal instead of more canvas. */}
          <div className="lg:mt-auto">
            <HeroServiceStatus />
          </div>

          <div className="mt-7 flex flex-wrap items-center gap-2.5 lg:mt-4" aria-label="Primary Impilo actions">
            <Link
              href="/welcome/find-care"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-gradient-to-br from-emerald-500 to-emerald-700 px-4 py-2.5 text-sm font-bold text-white shadow-[inset_0_1px_0_rgba(255,255,255,.35),0_10px_24px_-10px_rgba(6,95,70,.8)] hover:brightness-110 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-700 focus-visible:ring-offset-2"
            >
              <HeartHandshake className="h-4 w-4" aria-hidden />
              Get care
            </Link>
            <Link
              href="/welcome/find-care?q=virtual%20care"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-teal-200/40 bg-white/10 px-4 py-2.5 text-sm font-bold text-white hover:bg-white/15 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-300 focus-visible:ring-offset-2 focus-visible:ring-offset-emerald-950"
            >
              <Video className="h-4 w-4" aria-hidden />
              Virtual care
            </Link>
            <Link
              href="/welcome/report"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl px-3 py-2.5 text-sm font-semibold text-emerald-50 hover:bg-white/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-300"
            >
              <MessageSquareHeart className="h-4 w-4" aria-hidden />
              Give feedback
            </Link>
            {/* The hero fills the viewport, so nothing peeks in from below to hint at more
                content — this cue does that job explicitly, folded into the action row so it
                costs zero height. Anchor, not JS: works before hydration and lands on the
                services section's own scroll margin. Desktop-only: the stacked mobile flow
                scrolls naturally past the fold. */}
            <a
              href="#services"
              aria-label="Scroll down to explore all of Impilo"
              className="ml-auto hidden min-h-11 items-center gap-1.5 rounded-full px-3 py-2.5 text-[12px] font-bold uppercase tracking-[0.14em] text-emerald-50/80 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-300 lg:inline-flex"
            >
              Explore
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.4"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden
                className="h-4 w-4 animate-bounce motion-reduce:animate-none"
              >
                <path d="M6 9l6 6 6-6" />
              </svg>
            </a>
          </div>

        </div>

        {/* Right: unified Get Health Services discovery (care, medicines, wellness — honest, real). */}
        {/* flex + flex-col so the surface's own h-full resolves against a definite box.
            Relying on grid stretch alone left the surface sized by its content, which is
            how the map and list ended up capped with dead space beneath them. */}
        <div className="mt-6 flex min-w-0 flex-col lg:mt-0">
          <HeroDiscoverySurface />
        </div>
      </div>

    </section>
  );
}
