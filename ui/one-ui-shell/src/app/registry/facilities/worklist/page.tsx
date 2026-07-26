"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, ArrowLeft, ClipboardList, Loader2, UserCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import {
  useBulkTransitionDataGapTasks,
  useDataGapWorklist,
  useDataGapWorklistSummary,
  usePicNominationQueue,
} from "@/hooks/queries/useFacilityRegulatory";

const PAGE_SIZE = 50;

const PROVINCES = [
  "Bulawayo", "Harare", "Manicaland", "Mashonaland Central", "Mashonaland East",
  "Mashonaland West", "Masvingo", "Matabeleland North", "Matabeleland South", "Midlands",
];

const PRIORITY_STYLES: Record<string, string> = {
  HIGH: "bg-danger-soft text-danger border border-danger/28",
  MEDIUM: "bg-warning-soft text-warning-foreground border border-warning/35",
  LOW: "bg-neutral-100 text-muted-foreground border border-border",
};

type Tab = "data-gaps" | "pic-nominations";

export default function FacilityWorklistPage() {
  const [tab, setTab] = useState<Tab>("data-gaps");

  return (
    <AppLayout>
      <PageShell
        title="Registry worklist"
        subtitle="Outstanding data gaps and practitioner-in-charge nominations across the whole register"
        serviceSlug="tuso"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href="/registry/facilities"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to facility registry
          </Link>
        </div>

        <div className="mb-5 flex gap-2 border-b border-border">
          <button
            type="button"
            onClick={() => setTab("data-gaps")}
            className={`-mb-px flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
              tab === "data-gaps"
                ? "border-emerald-600 text-primary-hover"
                : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            <ClipboardList className="h-4 w-4" />
            Data gaps
          </button>
          <button
            type="button"
            onClick={() => setTab("pic-nominations")}
            className={`-mb-px flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
              tab === "pic-nominations"
                ? "border-emerald-600 text-primary-hover"
                : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            <UserCheck className="h-4 w-4" />
            Practitioner-in-charge nominations
          </button>
        </div>

        {tab === "data-gaps" ? <DataGapTab /> : <PicNominationTab />}
      </PageShell>
    </AppLayout>
  );
}

function DataGapTab() {
  const [dimension, setDimension] = useState("");
  const [priority, setPriority] = useState("");
  const [province, setProvince] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number[]>([]);
  const [note, setNote] = useState("");

  const { data, isLoading, error } = useDataGapWorklist({
    dimension: dimension || undefined,
    priority: priority || undefined,
    province: province || undefined,
    status: "OPEN",
    page,
    size: PAGE_SIZE,
  });
  const summary = useDataGapWorklistSummary("OPEN");
  const bulk = useBulkTransitionDataGapTasks();

  const rows = data?.data.items ?? [];
  const total = data?.data.totalElements ?? 0;
  const dimensions = useMemo(
    () =>
      Array.from(
        new Set((summary.data?.data.byDimension ?? []).map((d) => d.dimension).filter(Boolean)),
      ) as string[],
    [summary.data],
  );

  function resetTo(fn: () => void) {
    fn();
    setPage(0);
    setSelected([]);
  }

  async function transition(status: string) {
    if (selected.length === 0) return;
    await bulk.mutateAsync({ taskIds: selected, status, note: note || undefined });
    setSelected([]);
    setNote("");
  }

  return (
    <>
      <div className="mb-5 grid gap-4 md:grid-cols-3">
        <div className="rounded-2xl border border-border bg-card p-5">
          <p className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
            Open tasks
          </p>
          <p className="mt-3 text-3xl font-semibold text-foreground">{total.toLocaleString()}</p>
        </div>
        <div className="rounded-2xl border border-border bg-card p-5 md:col-span-2">
          <p className="mb-2 text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
            Where the work is
          </p>
          <div className="flex flex-wrap gap-2">
            {(summary.data?.data.byProvince ?? []).slice(0, 6).map((p) => (
              <span
                key={p.province ?? "unknown"}
                className="rounded-full border border-border px-2.5 py-1 text-xs text-muted-foreground"
              >
                {p.province ?? "Not stated"} · {p.taskCount.toLocaleString()} across{" "}
                {p.facilityCount.toLocaleString()} facilities
              </span>
            ))}
          </div>
        </div>
      </div>

      <div className="mb-5 flex flex-wrap items-center gap-3 rounded-2xl border border-border bg-card p-4">
        <select
          value={dimension}
          onChange={(e) => resetTo(() => setDimension(e.target.value))}
          className="rounded-xl border border-border px-3 py-2.5 text-sm"
        >
          <option value="">All dimensions</option>
          {dimensions.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
        <select
          value={priority}
          onChange={(e) => resetTo(() => setPriority(e.target.value))}
          className="rounded-xl border border-border px-3 py-2.5 text-sm"
        >
          <option value="">All priorities</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
        <select
          value={province}
          onChange={(e) => resetTo(() => setProvince(e.target.value))}
          className="rounded-xl border border-border px-3 py-2.5 text-sm"
        >
          <option value="">All provinces</option>
          {PROVINCES.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </div>

      {selected.length > 0 ? (
        <div className="mb-4 flex flex-wrap items-center gap-3 rounded-2xl border border-emerald-200 bg-success-soft p-4">
          <span className="text-sm font-medium text-primary-hover">
            {selected.length} selected
          </span>
          <input
            type="text"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Note for the audit trail (optional)"
            className="min-w-[220px] flex-1 rounded-xl border border-border px-3 py-2 text-sm"
          />
          <button
            type="button"
            onClick={() => transition("IN_PROGRESS")}
            disabled={bulk.isPending}
            className="rounded-lg border border-border bg-card px-3 py-2 text-xs font-medium transition-colors hover:border-emerald-300 disabled:opacity-40"
          >
            Mark in progress
          </button>
          <button
            type="button"
            onClick={() => transition("RESOLVED")}
            disabled={bulk.isPending}
            className="rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-emerald-700 disabled:opacity-40"
          >
            Mark resolved
          </button>
          {/*
            Deliberately no "mark verified" action. A data gap closes when evidence arrives, not
            when a steward clears a row — the backend refuses any status beyond RESOLVED for the
            same reason.
          */}
          <span className="text-xs text-muted-foreground">
            Resolving records who and when; it does not mark the facility verified.
          </span>
        </div>
      ) : null}

      {isLoading ? (
        <div className="flex items-center justify-center rounded-2xl border border-border bg-card p-12">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : error ? (
        <div className="rounded-2xl border border-danger/28 bg-danger-soft p-6 text-sm text-danger">
          The worklist could not be loaded right now.
        </div>
      ) : rows.length === 0 ? (
        <div className="rounded-2xl border border-border bg-card p-12 text-center">
          <ClipboardList className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
          <p className="text-sm text-muted-foreground">No open tasks match these filters.</p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-border bg-card">
          <table className="w-full text-sm">
            <thead className="bg-background text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
              <tr>
                <th className="w-10 px-4 py-3">
                  <input
                    type="checkbox"
                    aria-label="Select all on this page"
                    checked={selected.length === rows.length && rows.length > 0}
                    onChange={(e) =>
                      setSelected(e.target.checked ? rows.map((r) => r.id) : [])
                    }
                  />
                </th>
                <th className="px-4 py-3 font-medium">Facility</th>
                <th className="px-4 py-3 font-medium">What is needed</th>
                <th className="px-4 py-3 font-medium">Who can supply it</th>
                <th className="px-4 py-3 font-medium">Priority</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((task) => (
                <tr key={task.id} className="border-t border-border align-top hover:bg-background/60">
                  <td className="px-4 py-4">
                    <input
                      type="checkbox"
                      aria-label={`Select task ${task.id}`}
                      checked={selected.includes(task.id)}
                      onChange={(e) =>
                        setSelected((cur) =>
                          e.target.checked ? [...cur, task.id] : cur.filter((id) => id !== task.id),
                        )
                      }
                    />
                  </td>
                  <td className="px-4 py-4">
                    <Link
                      href={`/registry/facilities/${task.facilityId}`}
                      className="font-medium text-foreground hover:text-primary-hover"
                    >
                      {task.facilityName ?? `Facility ${task.facilityId}`}
                    </Link>
                    <div className="mt-1 text-xs text-muted-foreground">
                      {[task.district, task.province].filter(Boolean).join(", ") || "Location not stated"}
                    </div>
                  </td>
                  <td className="px-4 py-4">
                    <div className="text-foreground">{task.requiredInformation}</div>
                    {task.whyRequired ? (
                      <div className="mt-1 text-xs text-muted-foreground">{task.whyRequired}</div>
                    ) : null}
                  </td>
                  <td className="px-4 py-4 text-muted-foreground">
                    <div>{task.responsibleActor}</div>
                    {task.acceptedEvidence ? (
                      <div className="mt-1 text-xs">{task.acceptedEvidence}</div>
                    ) : null}
                  </td>
                  <td className="px-4 py-4">
                    <span
                      className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${
                        PRIORITY_STYLES[task.priority] ?? PRIORITY_STYLES.LOW
                      }`}
                    >
                      {task.priority}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager
            page={page}
            size={PAGE_SIZE}
            count={rows.length}
            total={total}
            onPage={(p) => {
              setPage(p);
              setSelected([]);
            }}
          />
        </div>
      )}
    </>
  );
}

function PicNominationTab() {
  const [state, setState] = useState("ELIGIBILITY_CHECKED");
  const [province, setProvince] = useState("");
  const [page, setPage] = useState(0);

  const { data, isLoading, error } = usePicNominationQueue({
    state: state || undefined,
    province: province || undefined,
    page,
    size: PAGE_SIZE,
  });

  const rows = data?.data.content ?? [];
  const total = data?.data.totalElements ?? 0;

  return (
    <>
      <div className="mb-4 rounded-2xl border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground">
        <AlertTriangle className="mr-2 inline h-4 w-4" />
        A nomination parked at <strong>ELIGIBILITY_CHECKED</strong> is the state machine working, not
        a fault: the practitioner has not claimed their profile, so no one can accept the
        appointment. It advances by itself when they claim — nothing here creates an assignment.
      </div>

      <div className="mb-5 flex flex-wrap items-center gap-3 rounded-2xl border border-border bg-card p-4">
        <select
          value={state}
          onChange={(e) => {
            setState(e.target.value);
            setPage(0);
          }}
          className="rounded-xl border border-border px-3 py-2.5 text-sm"
        >
          <option value="">All states</option>
          <option value="ELIGIBILITY_CHECKED">Awaiting practitioner claim</option>
          <option value="PRACTITIONER_ACCEPTANCE_PENDING">Awaiting practitioner acceptance</option>
          <option value="REGULATOR_REVIEW_PENDING">Awaiting regulator review</option>
          <option value="ACCEPTED">Accepted</option>
          <option value="ACTIVATED">Activated</option>
        </select>
        <select
          value={province}
          onChange={(e) => {
            setProvince(e.target.value);
            setPage(0);
          }}
          className="rounded-xl border border-border px-3 py-2.5 text-sm"
        >
          <option value="">All provinces</option>
          {PROVINCES.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        <span className="text-sm text-muted-foreground">{total.toLocaleString()} nominations</span>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center rounded-2xl border border-border bg-card p-12">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : error ? (
        <div className="rounded-2xl border border-danger/28 bg-danger-soft p-6 text-sm text-danger">
          The nomination queue could not be loaded right now.
        </div>
      ) : rows.length === 0 ? (
        <div className="rounded-2xl border border-border bg-card p-12 text-center">
          <UserCheck className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
          <p className="text-sm text-muted-foreground">No nominations match these filters.</p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-border bg-card">
          <table className="w-full text-sm">
            <thead className="bg-background text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Facility</th>
                <th className="px-4 py-3 font-medium">Nominated practitioner</th>
                <th className="px-4 py-3 font-medium">State</th>
                <th className="px-4 py-3 font-medium">Eligibility</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr
                  key={row.nomination.nominationId}
                  className="border-t border-border align-top hover:bg-background/60"
                >
                  <td className="px-4 py-4">
                    <Link
                      href={`/registry/facilities/${row.nomination.facilityId}`}
                      className="font-medium text-foreground hover:text-primary-hover"
                    >
                      {row.facilityName ?? `Facility ${row.nomination.facilityId}`}
                    </Link>
                    <div className="mt-1 text-xs text-muted-foreground">
                      {row.province ?? "Province not stated"}
                      {row.facilityRegulatoryStatus === "IMPORTED_PENDING_CONFIGURATION"
                        ? " · registered with HPA, awaiting configuration"
                        : ""}
                    </div>
                  </td>
                  <td className="px-4 py-4 text-muted-foreground">{row.nomination.providerPublicId}</td>
                  <td className="px-4 py-4 text-muted-foreground">
                    {row.nomination.state.replaceAll("_", " ")}
                  </td>
                  <td className="px-4 py-4 text-muted-foreground">
                    {row.nomination.eligibilityResult ?? "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={page} size={PAGE_SIZE} count={rows.length} total={total} onPage={setPage} />
        </div>
      )}
    </>
  );
}

function Pager({
  page,
  size,
  count,
  total,
  onPage,
}: {
  page: number;
  size: number;
  count: number;
  total: number;
  onPage: (page: number) => void;
}) {
  const start = total === 0 ? 0 : page * size + 1;
  const end = page * size + count;
  const hasNext = end < total;
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3 text-sm">
      <p className="text-muted-foreground">
        Showing {start}–{end} of {total.toLocaleString()}
      </p>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onPage(Math.max(0, page - 1))}
          disabled={page === 0}
          className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium transition-colors hover:border-emerald-300 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Previous
        </button>
        <button
          type="button"
          onClick={() => onPage(page + 1)}
          disabled={!hasNext}
          className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium transition-colors hover:border-emerald-300 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  );
}
