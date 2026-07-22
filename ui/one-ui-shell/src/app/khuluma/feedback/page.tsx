"use client";

import Link from "next/link";
import { MessagesSquare, LifeBuoy, Siren } from "lucide-react";
import { KhulumaWorkspace } from "@/components/khuluma/KhulumaWorkspace";
import { KhulumaSection } from "@/components/khuluma/KhulumaSection";
import { KhulumaEmptyState } from "@/components/khuluma/KhulumaEmptyState";
import { useNotifications } from "@/hooks/queries/useNotifications";
import { rows, readStr } from "@/components/khuluma/util";

function isFeedback(n: Record<string, unknown>): boolean {
  const hay = `${readStr(n, "category", "type", "kind", "title", "subject")}`.toUpperCase();
  return hay.includes("FEEDBACK") || hay.includes("VISIT") || hay.includes("SURVEY");
}

/**
 * Khuluma Feedback & Support — surfaces post-visit feedback prompts (dispatched by
 * khuluma-service's Rito consumer, delivered into the notification feed) and routes to
 * support. No invented feedback history; prompts come from the real notification feed.
 */
export default function KhulumaFeedbackPage() {
  const q = useNotifications();
  const prompts = rows(q.data).filter(isFeedback);

  return (
    <KhulumaWorkspace title="Khuluma — Feedback & Support" subtitle="Share your experience and get help">
      <KhulumaSection
        title="Feedback requests"
        icon={<MessagesSquare className="h-4 w-4 text-emerald-700" />}
        isLoading={q.isLoading}
        isError={q.isError}
        onRetry={() => q.refetch()}
        isEmpty={!prompts.length}
        emptyState={
          <KhulumaEmptyState
            title="No feedback requests right now"
            explanation="After a visit or teleconsultation, a short feedback request appears here so you can tell us how it went."
            when="Requests arrive after your care encounters complete."
          />
        }
      >
        <ul className="divide-y divide-border">
          {prompts.map((n, i) => {
            const link = readStr(n, "deepLink", "actionUrl", "href");
            return (
              <li key={readStr(n, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                <span className="truncate text-foreground">{readStr(n, "title", "subject") || "How was your visit?"}</span>
                {link ? <Link href={link} className="shrink-0 rounded-md bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-700">Give feedback</Link> : null}
              </li>
            );
          })}
        </ul>
      </KhulumaSection>

      <KhulumaSection title="Get help" icon={<LifeBuoy className="h-4 w-4 text-emerald-700" />}>
        <div className="flex flex-wrap gap-2">
          <Link href="/support" className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700">Contact support</Link>
          <Link href="/welcome/emergency" className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 hover:bg-red-100">
            <Siren className="h-4 w-4" /> Emergency help
          </Link>
        </div>
        <p className="mt-2 text-xs text-muted-foreground">Support requests you raise are tracked in Khuluma so staff see one continuous conversation.</p>
      </KhulumaSection>
    </KhulumaWorkspace>
  );
}
