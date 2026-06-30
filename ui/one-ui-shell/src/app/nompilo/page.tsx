"use client";

/**
 * Nompilo — your on-platform guide.
 *
 * Nompilo walks with the user once they land in Impilo: it explains where they are, what's next,
 * why something is locked, and routes to the owning service. This page is the dedicated guide
 * surface; the contextual guidance panel also appears in-context on key pages (e.g. the Mushe
 * Personal Health Wallet). Everything here is backed by the real BFF /internal/v1/nompilo/**
 * endpoints (guidance-service is the Nompilo SoR) — no mock/demo guidance.
 */

import { useState } from "react";
import { Sparkles, Lock, MessageCircleQuestion, BookOpen } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useExplainLocked } from "@/hooks/queries/useNompilo";

function LockedExplainer() {
  const explain = useExplainLocked();
  const [reason, setReason] = useState("TRUST_TOO_LOW");

  const reasons = [
    { code: "TRUST_TOO_LOW", label: "I can't open my full record" },
    { code: "REQUIRES_CONSENT", label: "I'm asked for consent" },
    { code: "NO_DELEGATION", label: "I can't act for someone else" },
    { code: "REQUIRES_FACILITY", label: "I need to check in to a facility" },
  ];

  return (
    <section className="rounded-lg border border-border bg-card p-4" data-testid="nompilo-locked-explainer">
      <div className="mb-2 flex items-center gap-2">
        <Lock className="h-4 w-4 text-amber-700" aria-hidden />
        <span className="text-sm font-semibold text-foreground">Why can&apos;t I do this?</span>
      </div>
      <p className="mb-3 text-xs text-muted-foreground">
        Nompilo explains restrictions in plain language and shows the safe next step. It never reveals
        the protected information itself.
      </p>
      <div className="flex flex-wrap gap-2">
        {reasons.map((r) => (
          <button
            key={r.code}
            type="button"
            onClick={() => {
              setReason(r.code);
              explain.mutate({ reasonCode: r.code, routePath: "/nompilo" });
            }}
            className={`rounded-md border px-2.5 py-1 text-xs font-medium ${
              reason === r.code ? "border-primary bg-primary-soft text-primary" : "border-border bg-card text-foreground hover:bg-neutral-50"
            }`}
          >
            {r.label}
          </button>
        ))}
      </div>

      {explain.isPending ? (
        <p className="mt-3 text-xs text-muted-foreground">Resolving…</p>
      ) : null}
      {explain.data?.data ? (
        <div className="mt-3 rounded-md border border-amber-200 bg-warning-soft p-3" data-testid="nompilo-locked-result">
          <p className="text-sm font-medium text-amber-900">{explain.data.data.title}</p>
          <p className="mt-1 text-xs text-amber-800">{explain.data.data.explanation}</p>
          {explain.data.data.ctaRoute ? (
            <a
              href={explain.data.data.ctaRoute}
              className="mt-2 inline-flex text-xs font-medium text-primary hover:text-primary-hover"
            >
              {explain.data.data.ctaLabel ?? "Continue"} →
            </a>
          ) : null}
          <p className="mt-2 text-[10px] text-amber-700">{explain.data.data.safetyBoundary}</p>
        </div>
      ) : null}
    </section>
  );
}

export default function NompiloPage() {
  return (
    <AppLayout>
      <PageShell
        title="Nompilo"
        subtitle="Your on-platform guide — what's happening, what's next, and how to do it safely"
        icon={<Sparkles className="h-6 w-6" />}
      >
        <div className="mx-auto grid max-w-2xl gap-4">
          <NompiloContextualGuidance routePath="/citizen/wallet" title="What to do next" />
          <LockedExplainer />
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <a href="/guidance" className="rounded-xl border border-border bg-card p-4 hover:border-impilo-400 hover:shadow-md">
              <div className="mb-1 flex items-center gap-2">
                <MessageCircleQuestion className="h-5 w-5 text-primary" aria-hidden />
                <span className="font-semibold text-foreground">Ask &amp; learn</span>
              </div>
              <p className="text-sm text-muted-foreground">Reminders, education, and the guidance assistant.</p>
            </a>
            <a href="/learning/catalog" className="rounded-xl border border-border bg-card p-4 hover:border-impilo-400 hover:shadow-md">
              <div className="mb-1 flex items-center gap-2">
                <BookOpen className="h-5 w-5 text-primary" aria-hidden />
                <span className="font-semibold text-foreground">Protocols &amp; SOPs</span>
              </div>
              <p className="text-sm text-muted-foreground">Open Impilo Fundo learning content and protocols.</p>
            </a>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
