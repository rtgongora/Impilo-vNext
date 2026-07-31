"use client";

/**
 * W14-B clinician panels — reproductive intention, preconception, fertility and delivery
 * on the confidential reproductive lane.
 */

import { useState } from "react";
import { Loader2 } from "lucide-react";
import {
  isReproductiveReadUnavailable,
  useActivePreconceptionPlan,
  useCurrentFertilityEpisode,
  useCurrentReproductiveIntention,
  useDeliveryRecordsForMother,
  useOpenPreconceptionPlan,
  useRecordDelivery,
  useRecordReproductiveIntention,
  useStartFertilityEpisode,
  useUpdatePreconceptionPlan,
  type DeliveryRecordView,
  type FertilityEpisodeView,
  type PreconceptionPlanView,
} from "@/hooks/queries/useConfidentialReproductive";
import {
  REPRODUCTIVE_EMPTY_HINT,
  REPRODUCTIVE_UNAVAILABLE_BODY,
  REPRODUCTIVE_UNAVAILABLE_TITLE,
} from "./reproductiveWording";

function humanise(code: string | null | undefined): string {
  if (!code) return "Not recorded";
  return code.replace(/_/g, " ").toLowerCase();
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "Not recorded";
  try {
    return new Date(iso).toLocaleDateString();
  } catch {
    return iso;
  }
}

function UnavailableBanner({ testId }: { testId: string }) {
  return (
    <div
      className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-2 text-sm text-red-900"
      data-testid={testId}
      role="alert"
    >
      <div>
        <p className="font-semibold">{REPRODUCTIVE_UNAVAILABLE_TITLE}</p>
        <p className="mt-1 text-xs">{REPRODUCTIVE_UNAVAILABLE_BODY}</p>
      </div>
    </div>
  );
}

export function ReproductiveIntentionPanel({
  patientCpid,
  recordedBy,
}: {
  patientCpid: string;
  recordedBy: string;
}) {
  const current = useCurrentReproductiveIntention(patientCpid);
  const record = useRecordReproductiveIntention();
  const [intention, setIntention] = useState("UNDECIDED");

  if (current.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading reproductive intention…
      </p>
    );
  }
  if (current.isError && isReproductiveReadUnavailable(current.error)) {
    return <UnavailableBanner testId="intention-unavailable" />;
  }

  const row = current.data;

  return (
    <div data-testid="intention-panel">
      {row ? (
        <p className="text-sm">
          Current: <span className="font-medium">{humanise(row.intention)}</span>
          {row.timeframe_months != null ? ` · within ${row.timeframe_months} months` : ""}
          <span className="block text-xs text-muted-foreground">
            Recorded {formatDate(row.recorded_at)}
          </span>
        </p>
      ) : (
        <p className="text-xs text-muted-foreground">{REPRODUCTIVE_EMPTY_HINT}</p>
      )}

      <form
        className="mt-3 flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void record.mutateAsync({ subjectCpid: patientCpid, intention, recordedBy });
        }}
      >
        <label className="text-xs font-medium text-foreground">
          Record intention
          <select
            value={intention}
            onChange={(ev) => setIntention(ev.target.value)}
            className="mt-1 block rounded border border-border px-2 py-1 text-sm"
            data-testid="intention-select"
          >
            <option value="WANTS_PREGNANCY_NOW">Wants pregnancy now</option>
            <option value="WANTS_PREGNANCY_LATER">Wants pregnancy later</option>
            <option value="WANTS_NO_MORE_CHILDREN">Wants no more children</option>
            <option value="UNDECIDED">Undecided</option>
            <option value="DECLINED_TO_STATE">Declined to state</option>
          </select>
        </label>
        <button
          type="submit"
          disabled={record.isPending}
          className="rounded bg-violet-700 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
          data-testid="intention-record-submit"
        >
          {record.isPending ? "Saving…" : "Save"}
        </button>
      </form>
    </div>
  );
}

function writeErrorMessage(err: unknown): string {
  const e = err as { error?: { message?: string }; message?: string } | null;
  return e?.error?.message ?? e?.message ?? "The record was not saved — try again.";
}

export function PreconceptionPanel({
  patientCpid,
  recordedBy,
}: {
  patientCpid: string;
  recordedBy: string;
}) {
  const active = useActivePreconceptionPlan(patientCpid);
  const open = useOpenPreconceptionPlan();
  const update = useUpdatePreconceptionPlan();
  const [folicAcidToday, setFolicAcidToday] = useState(false);

  if (active.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading preconception plan…
      </p>
    );
  }
  if (active.isError && isReproductiveReadUnavailable(active.error)) {
    return <UnavailableBanner testId="preconception-unavailable" />;
  }

  const plan = active.data;
  const today = new Date().toISOString().slice(0, 10);

  return (
    <div data-testid="preconception-panel">
      {plan ? (
        <>
          <PreconceptionRow plan={plan} />
          {!plan.folic_acid_started_on && (
            <button
              type="button"
              disabled={update.isPending}
              onClick={() =>
                void update.mutateAsync({
                  planId: plan.preconception_plan_id,
                  subjectCpid: patientCpid,
                  folicAcidStartedOn: today,
                  updatedBy: recordedBy,
                })
              }
              className="mt-2 rounded border border-violet-300 px-3 py-1.5 text-xs font-medium text-violet-800 hover:bg-violet-50 disabled:opacity-50"
              data-testid="preconception-folic-acid-start"
            >
              {update.isPending ? "Saving…" : "Record folic acid started today"}
            </button>
          )}
          {update.isError && (
            <p className="mt-2 text-xs text-red-700" role="alert">
              {writeErrorMessage(update.error)}
            </p>
          )}
        </>
      ) : (
        <>
          <p className="text-xs text-muted-foreground">{REPRODUCTIVE_EMPTY_HINT}</p>
          <form
            className="mt-3 flex flex-wrap items-center gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              void open.mutateAsync({
                subjectCpid: patientCpid,
                folicAcidStartedOn: folicAcidToday ? today : undefined,
                recordedBy,
              });
            }}
          >
            <label className="flex items-center gap-1.5 text-xs text-foreground">
              <input
                type="checkbox"
                checked={folicAcidToday}
                onChange={(ev) => setFolicAcidToday(ev.target.checked)}
                data-testid="preconception-folic-acid-checkbox"
              />
              Folic acid started today
            </label>
            <button
              type="submit"
              disabled={open.isPending}
              className="rounded bg-violet-700 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
              data-testid="preconception-open-submit"
            >
              {open.isPending ? "Opening…" : "Open preconception plan"}
            </button>
          </form>
          {open.isError && (
            <p className="mt-2 text-xs text-red-700" role="alert">
              {writeErrorMessage(open.error)}
            </p>
          )}
        </>
      )}
    </div>
  );
}

function PreconceptionRow({ plan }: { plan: PreconceptionPlanView }) {
  return (
    <p className="text-sm">
      {humanise(plan.status)} plan opened {formatDate(plan.opened_on)}
      {plan.folic_acid_started_on ? (
        <span className="block text-xs text-muted-foreground">
          Folic acid from {formatDate(plan.folic_acid_started_on)}
        </span>
      ) : null}
    </p>
  );
}

export function FertilityPanel({
  patientCpid,
  recordedBy,
}: {
  patientCpid: string;
  recordedBy: string;
}) {
  const current = useCurrentFertilityEpisode(patientCpid);
  const start = useStartFertilityEpisode();
  const [monthsTrying, setMonthsTrying] = useState("");

  if (current.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading fertility episode…
      </p>
    );
  }
  if (current.isError && isReproductiveReadUnavailable(current.error)) {
    return <UnavailableBanner testId="fertility-unavailable" />;
  }

  const episode = current.data;
  return (
    <div data-testid="fertility-panel">
      {episode ? (
        <FertilityRow episode={episode} />
      ) : (
        <>
          <p className="text-xs text-muted-foreground">{REPRODUCTIVE_EMPTY_HINT}</p>
          <form
            className="mt-3 flex flex-wrap items-end gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              const months = monthsTrying.trim();
              void start.mutateAsync({
                subjectCpid: patientCpid,
                monthsTrying: /^\d+$/.test(months) ? Number(months) : undefined,
                recordedBy,
              });
            }}
          >
            <label className="text-xs font-medium text-foreground">
              Months trying
              <input
                type="number"
                min={0}
                value={monthsTrying}
                onChange={(ev) => setMonthsTrying(ev.target.value)}
                className="mt-1 block w-28 rounded border border-border px-2 py-1 text-sm"
                data-testid="fertility-months-trying"
              />
            </label>
            <button
              type="submit"
              disabled={start.isPending}
              className="rounded bg-violet-700 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
              data-testid="fertility-start-submit"
            >
              {start.isPending ? "Starting…" : "Start fertility episode"}
            </button>
          </form>
          {start.isError && (
            <p className="mt-2 text-xs text-red-700" role="alert">
              {writeErrorMessage(start.error)}
            </p>
          )}
        </>
      )}
    </div>
  );
}

function FertilityRow({ episode }: { episode: FertilityEpisodeView }) {
  return (
    <p className="text-sm">
      {humanise(episode.status)} since {formatDate(episode.opened_on)}
      {episode.months_trying != null ? ` · ${episode.months_trying} months trying` : ""}
      <span className="block text-xs text-muted-foreground">
        Female factor: {humanise(episode.female_factor_assessed)} · Male factor:{" "}
        {humanise(episode.male_factor_assessed)}
      </span>
    </p>
  );
}

export function DeliveryRecordPanel({
  patientCpid,
  recordedBy,
}: {
  patientCpid: string;
  recordedBy: string;
}) {
  const deliveries = useDeliveryRecordsForMother(patientCpid);
  const record = useRecordDelivery();
  const [mode, setMode] = useState("SPONTANEOUS_VAGINAL");

  if (deliveries.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading delivery records…
      </p>
    );
  }
  if (deliveries.isError && isReproductiveReadUnavailable(deliveries.error)) {
    return <UnavailableBanner testId="delivery-unavailable" />;
  }

  const rows = deliveries.data ?? [];

  return (
    <div data-testid="delivery-panel">
      {rows.length === 0 ? (
        <p className="text-xs text-muted-foreground">{REPRODUCTIVE_EMPTY_HINT}</p>
      ) : (
        <ul className="space-y-1 text-sm">
          {rows.map((d) => (
            <DeliveryRow key={d.delivery_record_id} item={d} />
          ))}
        </ul>
      )}

      <form
        className="mt-3 flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void record.mutateAsync({
            motherCpid: patientCpid,
            deliveredAt: new Date().toISOString(),
            deliveryMode: mode,
            babiesDelivered: 1,
            recordedBy,
          });
        }}
      >
        <label className="text-xs font-medium text-foreground">
          Record delivery
          <select
            value={mode}
            onChange={(ev) => setMode(ev.target.value)}
            className="mt-1 block rounded border border-border px-2 py-1 text-sm"
            data-testid="delivery-mode-select"
          >
            <option value="SPONTANEOUS_VAGINAL">Spontaneous vaginal</option>
            <option value="CAESAREAN">Caesarean</option>
          </select>
        </label>
        <button
          type="submit"
          disabled={record.isPending}
          className="rounded bg-violet-700 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
          data-testid="delivery-record-submit"
        >
          {record.isPending ? "Saving…" : "Record birth"}
        </button>
      </form>
    </div>
  );
}

function DeliveryRow({ item }: { item: DeliveryRecordView }) {
  return (
    <li className="border-b border-border py-1 last:border-0">
      {humanise(item.delivery_mode)} · {formatDate(item.delivered_at)}
      {item.babies_delivered != null && item.babies_delivered > 1
        ? ` · ${item.babies_delivered} babies`
        : ""}
    </li>
  );
}
