"use client";

/**
 * ED pre-arrival — inbound patients, before they are here.
 *
 * The board itself has been readable since WU4. What did not exist was any way to add to it: pct has
 * served POST /v1/ed/pre-arrival and /arrival all along with no BFF route, so a notification called
 * in by radio had nowhere to be typed, and an EMS arrival could not be converted into an ED visit.
 */

import Link from "next/link";
import { useState } from "react";
import { EventTimeline, StalenessBadge, type TimelineEntry } from "shared-ui";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useEdPreArrival, useEdPreArrivalActions } from "@/hooks/queries/useDaidzai";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { bffErrorMessage } from "@/lib/bff-errors";

const ARRIVAL_MODES = ["AMBULANCE", "AIR_AMBULANCE", "PRIVATE_VEHICLE", "POLICE", "WALK_IN", "OTHER"] as const;

export default function EdPreArrivalPage() {
  const facility = useFacilityStore((s) => s.facility);
  const board = useEdPreArrival(facility?.id);
  const actions = useEdPreArrivalActions(facility?.id);

  const [arrivalMode, setArrivalMode] = useState<string>(ARRIVAL_MODES[0]);
  const [complaint, setComplaint] = useState("");
  const [etaMinutes, setEtaMinutes] = useState("");
  const [serverError, setServerError] = useState<string | null>(null);

  const rows = board.data ?? [];

  const entries: TimelineEntry[] = rows.map((row, index) => ({
    id: String(row.pre_arrival_id ?? row.id ?? index),
    occurredAt: String(row.notified_at ?? row.created_at ?? ""),
    label: String(row.presenting_complaint ?? "Inbound patient"),
    detail: row.eta_minutes ? `ETA ${String(row.eta_minutes)} min` : undefined,
    actor: String(row.arrival_mode ?? ""),
    source: "pct-service",
    tone: "info",
  }));

  return (
    <AppLayout>
      <PageShell title="ED pre-arrival" subtitle="Inbound patients notified before arrival — pct.ed_pre_arrival">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex gap-4 text-sm">
            <Link href="/clinical/emergency" className="text-primary underline">
              ED trackboard
            </Link>
            <Link href="/clinical/emergency/command" className="text-primary underline">
              Emergency command
            </Link>
          </div>
          <StalenessBadge
            state={board.isError ? "UNAVAILABLE" : board.dataUpdatedAt ? "LIVE" : "VERSION_UNKNOWN"}
            asOf={board.dataUpdatedAt ? new Date(board.dataUpdatedAt).toISOString() : undefined}
            subject="the pre-arrival board"
          />
        </div>

        <div className="grid gap-6 lg:grid-cols-[380px_1fr]">
          <form
            className="space-y-3 rounded-lg border border-border bg-card p-4"
            data-testid="pre-arrival-form"
            onSubmit={async (event) => {
              event.preventDefault();
              setServerError(null);
              try {
                await actions.notify.mutateAsync({
                  facilityId: facility?.id,
                  arrivalMode,
                  presentingComplaint: complaint,
                  etaMinutes: etaMinutes ? Number(etaMinutes) : undefined,
                });
                setComplaint("");
                setEtaMinutes("");
              } catch (error) {
                setServerError(bffErrorMessage(error, "this pre-arrival notification"));
              }
            }}
          >
            <h2 className="text-sm font-semibold text-foreground">Record a notification</h2>

            <label className="block text-xs text-muted-foreground">
              Arrival mode
              <select
                className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={arrivalMode}
                onChange={(event) => setArrivalMode(event.target.value)}
                data-testid="pre-arrival-mode"
              >
                {ARRIVAL_MODES.map((mode) => (
                  <option key={mode} value={mode}>
                    {mode.replaceAll("_", " ").toLowerCase()}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-xs text-muted-foreground">
              Presenting complaint, as reported
              <input
                className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={complaint}
                onChange={(event) => setComplaint(event.target.value)}
                data-testid="pre-arrival-complaint"
              />
            </label>

            <label className="block text-xs text-muted-foreground">
              ETA in minutes, if given
              <input
                type="number"
                min={0}
                className="mt-1 block w-32 rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={etaMinutes}
                onChange={(event) => setEtaMinutes(event.target.value)}
                data-testid="pre-arrival-eta"
              />
            </label>

            <p className="text-xs text-muted-foreground">
              A pre-arrival notification is what was reported over the radio, not an assessment. It
              becomes an ED visit only when the patient actually arrives.
            </p>

            {serverError ? (
              <p className="rounded border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger" data-testid="pre-arrival-error">
                {serverError}
              </p>
            ) : null}

            <button
              type="submit"
              className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
              disabled={actions.notify.isPending || !complaint.trim() || !facility?.id}
              data-testid="pre-arrival-submit"
            >
              {actions.notify.isPending ? "Recording…" : "Record notification"}
            </button>
          </form>

          <section>
            <h2 className="mb-2 text-sm font-semibold text-foreground">Inbound</h2>
            {board.isError ? (
              <p className="rounded-lg border border-danger/30 bg-danger-soft p-3 text-sm text-danger" data-testid="pre-arrival-unreadable">
                The pre-arrival board could not be retrieved. Do not treat this as an absence of
                inbound patients.
              </p>
            ) : board.isLoading ? (
              <p className="text-sm text-muted-foreground">Loading inbound patients…</p>
            ) : (
              <>
                <EventTimeline
                  entries={entries}
                  emptyMeaning="No inbound patients have been notified to this facility."
                />
                {rows.length > 0 ? (
                  <ul className="mt-4 space-y-2" data-testid="pre-arrival-rows">
                    {rows.map((row, index) => (
                      <li
                        key={String(row.pre_arrival_id ?? row.id ?? index)}
                        className="flex flex-wrap items-center gap-3 rounded-lg border border-border bg-card p-3 text-sm"
                      >
                        <span className="font-medium text-foreground">
                          {String(row.presenting_complaint ?? "Inbound patient")}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {String(row.arrival_mode ?? "mode not stated")}
                        </span>
                        <button
                          type="button"
                          className="ml-auto rounded border border-border px-2 py-1 text-xs disabled:opacity-50"
                          disabled={actions.arrive.isPending}
                          data-testid="pre-arrival-arrive"
                          onClick={async () => {
                            setServerError(null);
                            try {
                              await actions.arrive.mutateAsync({
                                preArrivalId: row.pre_arrival_id ?? row.id,
                                facilityId: facility?.id,
                              });
                            } catch (error) {
                              setServerError(bffErrorMessage(error, "this arrival"));
                            }
                          }}
                        >
                          Patient has arrived
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
