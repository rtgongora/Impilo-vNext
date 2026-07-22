"use client";

import Link from "next/link";
import { Bell } from "lucide-react";
import { KhulumaWorkspace } from "@/components/khuluma/KhulumaWorkspace";
import { KhulumaSection } from "@/components/khuluma/KhulumaSection";
import { KhulumaEmptyState } from "@/components/khuluma/KhulumaEmptyState";
import { useNotifications, useMarkNotificationRead } from "@/hooks/queries/useNotifications";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { rows, readStr, formatWhen } from "@/components/khuluma/util";

function unread(n: Record<string, unknown>): boolean {
  const s = readStr(n, "status", "state").toUpperCase();
  return !(n.read === true || n.readAt || s === "READ" || s === "ACKNOWLEDGED");
}

/**
 * Khuluma Updates & Actions — the full notification feed (referral outcomes, appointment
 * changes, reminders, results, service updates). Real notification-service data; actionable
 * items get a Nompilo handoff in KH3.
 */
export default function KhulumaUpdatesPage() {
  const q = useNotifications();
  const markRead = useMarkNotificationRead();
  const items = rows(q.data);

  return (
    <KhulumaWorkspace title="Khuluma — Updates & Actions" subtitle="Everything that needs your attention">
      <NompiloContextualGuidance routePath="/khuluma/updates" />
      <KhulumaSection
        title="Updates"
        icon={<Bell className="h-4 w-4 text-emerald-700" />}
        actions={<Link href="/citizen/wallet/comms" className="text-xs font-medium text-emerald-700 hover:text-emerald-900">Notification preferences →</Link>}
        isLoading={q.isLoading}
        isError={q.isError}
        onRetry={() => q.refetch()}
        isEmpty={!items.length}
        emptyState={
          <KhulumaEmptyState
            title="No updates yet"
            explanation="Referral status, appointment changes, teleconsultation reminders, medicine readiness, results and programme updates appear here — each with a next step."
            when="Updates arrive automatically as your care and services progress."
          />
        }
      >
        <ul className="divide-y divide-border">
          {items.map((n, i) => {
            const id = readStr(n, "id", "notificationId");
            const link = readStr(n, "deepLink", "actionUrl", "href");
            return (
              <li key={id || i} className="flex items-start justify-between gap-3 py-3 text-sm">
                <div className="min-w-0">
                  <p className="truncate font-medium text-foreground">{readStr(n, "title", "subject", "summary") || "Update"}</p>
                  <p className="mt-0.5 text-muted-foreground">{readStr(n, "body", "message", "preview") || "—"}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{formatWhen(readStr(n, "createdAt", "sentAt"))}</p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  {link ? <Link href={link} className="rounded-md bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-700">View</Link> : null}
                  {unread(n) && id ? (
                    <button type="button" onClick={() => markRead.mutate(id)} className="rounded-md border border-border px-2.5 py-1 text-xs font-medium hover:bg-muted">Mark read</button>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      </KhulumaSection>
    </KhulumaWorkspace>
  );
}
