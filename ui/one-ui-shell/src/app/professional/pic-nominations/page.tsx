"use client";

/**
 * Practitioner-in-charge nominations — practitioner view.
 * Route: /professional/pic-nominations (auth)
 *
 * The regulated professional's own PIC ledger: nominations awaiting their
 * acceptance (accept / decline / dispute), active PIC responsibilities, and
 * historical nominations. Identity comes from the session's linked Provider ID
 * (Health OS overlay doctrine — professional capacity attaches to the person
 * anchor). The backend enforces who may respond; this page only surfaces the
 * practitioner's own ledger via /pic-nominations/by-provider/{providerPublicId}.
 */

import { useState } from "react";
import Link from "next/link";
import { BadgeCheck, Building2, Clock, History, Loader2, UserCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useLinkedIds } from "@/hooks/queries/useLinkedIds";
import {
  useProviderPicNominations,
  useRespondPicNomination,
  type PicNominationView,
} from "@/hooks/queries/useHpaRegulatory";

function errMsg(e: unknown, fallback: string): string {
  return (
    (e as { body?: { error?: { message?: string } } })?.body?.error?.message ??
    (e as Error)?.message ??
    fallback
  );
}

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return iso.slice(0, 10);
}

function eligibilityChipClass(result: string | null | undefined): string {
  switch (result) {
    case "ELIGIBLE":
      return "bg-emerald-500/10 text-emerald-600";
    case "CONDITIONAL":
      return "bg-amber-500/10 text-amber-600";
    case "INELIGIBLE":
      return "bg-red-500/10 text-red-600";
    default:
      return "bg-muted text-muted-foreground";
  }
}

export default function PicNominationsPage() {
  const { user } = useAuthStore();
  const { data: linkedData, isLoading: linkedLoading } = useLinkedIds();
  const providerId = linkedData?.data?.attributes?.providerId ?? user?.providerId;

  const nominationsQuery = useProviderPicNominations(providerId);
  const nominations = nominationsQuery.data?.data ?? [];

  const pending = nominations.filter((n) => n.state === "PRACTITIONER_ACCEPTANCE_PENDING");
  const active = nominations.filter((n) => n.state === "ACTIVATED");
  const history = nominations.filter(
    (n) => n.state !== "PRACTITIONER_ACCEPTANCE_PENDING" && n.state !== "ACTIVATED",
  );

  return (
    <AppLayout>
      <PageShell
        title="Practitioner-in-charge nominations"
        subtitle="Facilities that have nominated you as their practitioner in charge"
      >
        {linkedLoading && !providerId && (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        )}

        {!linkedLoading && !providerId && (
          <div className="rounded-lg border border-border bg-card p-6 text-center">
            <UserCheck className="mx-auto mb-2 h-8 w-8 text-muted-foreground" />
            <p className="text-sm font-medium text-foreground">No Provider ID linked to this account</p>
            <p className="mt-1 text-sm text-muted-foreground">
              PIC nominations attach to your regulated Provider ID. Link or claim your provider
              profile to see nominations addressed to you.
            </p>
            <Link
              href="/citizen/provider-claim"
              className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
            >
              <BadgeCheck className="h-4 w-4" /> Claim provider profile
            </Link>
          </div>
        )}

        {providerId && (
          <div className="space-y-6">
            {nominationsQuery.isLoading && (
              <div className="flex items-center justify-center py-16">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            )}
            {nominationsQuery.isError && (
              <p className="text-sm text-red-600">
                Your nominations could not be loaded — the facility registry is unreachable.
              </p>
            )}

            {nominationsQuery.isSuccess && (
              <>
                <Section
                  title="Awaiting your response"
                  icon={Clock}
                  emptyText="No nominations awaiting your response."
                >
                  {pending.map((n) => (
                    <NominationCard key={n.nominationId} nomination={n} providerId={providerId} respondable />
                  ))}
                </Section>

                <Section
                  title="Active responsibilities"
                  icon={Building2}
                  emptyText="You are not currently the practitioner in charge of any facility."
                >
                  {active.map((n) => (
                    <NominationCard key={n.nominationId} nomination={n} providerId={providerId} />
                  ))}
                </Section>

                <Section title="History" icon={History} emptyText="No past nominations.">
                  {history.map((n) => (
                    <NominationCard key={n.nominationId} nomination={n} providerId={providerId} />
                  ))}
                </Section>
              </>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function Section({
  title,
  icon: Icon,
  emptyText,
  children,
}: {
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  emptyText: string;
  children: React.ReactNode;
}) {
  const hasChildren = Array.isArray(children) ? children.length > 0 : !!children;
  return (
    <section className="rounded-lg border border-border bg-card p-4">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-medium text-foreground">
        <Icon className="h-4 w-4" /> {title}
      </h3>
      {hasChildren ? (
        <ul className="space-y-2">{children}</ul>
      ) : (
        <p className="text-xs text-muted-foreground">{emptyText}</p>
      )}
    </section>
  );
}

function NominationCard({
  nomination,
  providerId,
  respondable,
}: {
  nomination: PicNominationView;
  providerId: string;
  respondable?: boolean;
}) {
  const respond = useRespondPicNomination();
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = (response: "ACCEPTED" | "DECLINED" | "DISPUTED") => {
    setError(null);
    respond.mutate(
      {
        nominationId: nomination.nominationId,
        response,
        notes: notes.trim() || undefined,
        providerPublicId: providerId,
        facilityId: nomination.facilityId,
      },
      { onError: (e) => setError(errMsg(e, "Your response could not be recorded.")) },
    );
  };

  return (
    <li className="rounded-md border border-border/60 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-medium text-foreground">
            Facility #{nomination.facilityId ?? "—"}
            {nomination.facilityUnitId ? ` · unit #${nomination.facilityUnitId}` : ""}
          </p>
          <p className="text-xs text-muted-foreground">
            Nominated {fmtDate(nomination.nominatedAt)}
            {nomination.reviewState ? ` · review ${nomination.reviewState.replace(/_/g, " ")}` : ""}
            {nomination.reviewAuthority ? ` (${nomination.reviewAuthority})` : ""}
            {nomination.state === "ACTIVATED" && nomination.activatedAssignmentId !== null
              ? ` · assignment #${nomination.activatedAssignmentId}`
              : ""}
          </p>
        </div>
        <div className="flex items-center gap-1.5">
          <span
            className={`rounded-full px-2 py-0.5 text-[10px] uppercase tracking-wide ${eligibilityChipClass(nomination.eligibilityResult)}`}
          >
            {nomination.eligibilityResult ?? "UNKNOWN"}
          </span>
          <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">
            {nomination.state.replace(/_/g, " ")}
          </span>
        </div>
      </div>

      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}

      {respondable && (
        <div className="mt-2 space-y-2 border-t border-border/60 pt-2">
          <input
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Notes (optional — required context if disputing)"
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          />
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={respond.isPending}
              onClick={() => submit("ACCEPTED")}
              className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
            >
              Accept
            </button>
            <button
              type="button"
              disabled={respond.isPending}
              onClick={() => submit("DECLINED")}
              className="rounded-md border border-red-300 px-3 py-1.5 text-xs font-medium text-red-600 disabled:opacity-50"
            >
              Decline
            </button>
            <button
              type="button"
              disabled={respond.isPending}
              onClick={() => submit("DISPUTED")}
              className="rounded-md border border-amber-300 px-3 py-1.5 text-xs font-medium text-amber-700 disabled:opacity-50"
            >
              Dispute
            </button>
          </div>
          <p className="text-[11px] text-muted-foreground">
            Accepting confirms you take professional responsibility for the facility. Disputes are
            routed to the regulator for resolution.
          </p>
        </div>
      )}
    </li>
  );
}
