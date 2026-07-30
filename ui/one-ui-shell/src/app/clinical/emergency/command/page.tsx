"use client";

/**
 * Emergency command and alerts board — /clinical/emergency/command
 *
 * pct-service has served `command-summary` since W10 and the alert ack/respond/close routes since
 * W5b; nothing consumed any of them. This is the surface a charge nurse or emergency coordinator
 * stands in front of: state counts, the alert queue, and the three distinct authorities on an alert.
 *
 * Reads the BFF's composed `command-board`, which returns whatever answered plus a list of sources
 * that did not, so a dead upstream costs one tile rather than blanking the board.
 */

import { useState } from "react";
import Link from "next/link";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CommandBoardView } from "@/features/emergency/command/CommandBoardView";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useEmergencyAlertActions,
  useEmergencyCommandBoard,
  type EmergencyAlertRow,
} from "@/hooks/queries/useEmergencyEpisode";
import { bffErrorMessage } from "@/lib/bff-errors";

export default function EmergencyCommandPage() {
  const facility = useFacilityStore((s) => s.facility);
  const { user } = useAuthStore();
  const board = useEmergencyCommandBoard(facility?.id);
  const alertActions = useEmergencyAlertActions(facility?.id);

  const [pendingAlertId, setPendingAlertId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // The Provider ID is the regulated professional identity when one is activated; the Health ID is
  // the person anchor otherwise. Never a display name — pct records who acted, not what to call them.
  const actor = user?.providerId ?? user?.staffId ?? user?.id ?? "";

  const run = async (alert: EmergencyAlertRow, action: () => Promise<unknown>) => {
    if (!actor) {
      // pct records who acted. Sending a blank actor would write an audit row naming nobody, which
      // is worse than refusing the write.
      setActionError(
        "This session has no resolvable actor identity, so the action cannot be attributed. Sign in again or activate your provider role.",
      );
      return;
    }
    setPendingAlertId(String(alert.alert_id));
    setActionError(null);
    try {
      await action();
    } catch (error) {
      // pct's own refusal is the useful message here — a generic "something went wrong" would
      // discard the reason the write was rejected.
      setActionError(bffErrorMessage(error, "this alert action"));
    } finally {
      setPendingAlertId(null);
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="Emergency command"
        subtitle="Facility-wide emergency state and the alert queue — pct.emergency_episode and pct.emergency_alert"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex gap-4 text-sm">
            <Link href="/clinical/emergency" className="text-primary underline">
              ED trackboard
            </Link>
            <Link href="/clinical/emergency/board" className="text-primary underline">
              Episode board
            </Link>
          </div>
          {board.data?.meta?.as_of ? (
            <span className="text-xs text-muted-foreground">
              As of {new Date(board.data.meta.as_of).toLocaleTimeString()}
            </span>
          ) : null}
        </div>

        {!facility?.id ? (
          <p className="rounded-lg border border-border px-3 py-4 text-sm text-muted-foreground">
            Select a facility to see its emergency command board.
          </p>
        ) : (
          <>
            {actionError ? (
              <p
                className="mb-3 rounded-lg border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger"
                data-testid="command-action-error"
              >
                {actionError}
              </p>
            ) : null}

            <CommandBoardView
              board={board.data}
              isLoading={board.isLoading}
              isError={board.isError}
              pendingAlertId={pendingAlertId}
              onAcknowledge={(alert) =>
                void run(alert, () =>
                  alertActions.acknowledge.mutateAsync({
                    alertId: String(alert.alert_id),
                    acknowledgedBy: actor,
                  }),
                )
              }
              onRespond={(alert) => {
                const responseNote = window.prompt("What was done in response to this alert?");
                if (!responseNote) return;
                void run(alert, () =>
                  alertActions.respond.mutateAsync({
                    alertId: String(alert.alert_id),
                    respondedBy: actor,
                    responseNote,
                  }),
                );
              }}
              onClose={(alert) => {
                const closureReason = window.prompt(
                  "Closing states the condition that raised this alert is gone, and lets the same hazard raise again. Why is it resolved?",
                );
                if (!closureReason) return;
                void run(alert, () =>
                  alertActions.close.mutateAsync({
                    alertId: String(alert.alert_id),
                    closedBy: actor,
                    closureReason,
                  }),
                );
              }}
            />
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}
