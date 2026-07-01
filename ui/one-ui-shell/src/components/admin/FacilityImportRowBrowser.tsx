"use client";

/**
 * Tabbed browser for persisted facility import rows. Each tab renders REAL rows from the row-level
 * staging API (no fabrication). Duplicate tabs group rows by their duplicate group key.
 */

import { useState } from "react";
import { Loader2, AlertTriangle } from "lucide-react";
import {
  useFacilityImportRows,
  useFacilityImportDuplicates,
  useSupplyCode,
  useRejectRow,
  useSkipRow,
  useMatchExisting,
  useResolveDistinct,
  useApproveRow,
  useApplyApproved,
  type FacilityImportRow,
  type RowFilters,
} from "@/hooks/queries/useFacilityImports";

const TERMINAL = new Set(["IMPORTED", "REJECTED", "SKIPPED"]);

function errorMessage(e: unknown): string {
  if (e && typeof e === "object" && "message" in e) return String((e as { message: unknown }).message);
  return "Action failed";
}

function RowActions({ runId, row }: { runId: string; row: FacilityImportRow }) {
  const supply = useSupplyCode(runId);
  const reject = useRejectRow(runId);
  const skip = useSkipRow(runId);
  const match = useMatchExisting(runId);
  const resolve = useResolveDistinct(runId);
  const approve = useApproveRow(runId);
  const [msg, setMsg] = useState<string | null>(null);
  const pending =
    supply.isPending ||
    reject.isPending ||
    skip.isPending ||
    match.isPending ||
    resolve.isPending ||
    approve.isPending;

  if (TERMINAL.has(row.decisionStatus ?? "")) {
    return (
      <span className="text-xs text-muted-foreground">
        {row.decisionStatus}
        {row.resultFacilityId ? ` · #${row.resultFacilityId}` : ""}
      </span>
    );
  }

  const runAction = (p: Promise<unknown>) => {
    setMsg(null);
    p.then(() => setMsg("Saved")).catch((e) => setMsg(errorMessage(e)));
  };

  const btn = "px-2 py-0.5 text-xs rounded border border-border hover:bg-background disabled:opacity-40";

  return (
    <div className="flex flex-wrap items-center gap-1">
      {!row.facilityCode && (
        <button
          className={btn}
          disabled={pending}
          onClick={() => {
            const code = window.prompt("Supply facility code (external/admin code):");
            if (code) runAction(supply.mutateAsync({ rowId: row.id, facilityCode: code }));
          }}
        >
          Supply code
        </button>
      )}
      {row.duplicateType && (
        <>
          <button
            className={btn}
            disabled={pending}
            onClick={() => {
              const id = window.prompt("Match to existing facility ID (internal numeric ID):");
              if (id) runAction(match.mutateAsync({ rowId: row.id, facilityId: Number(id) }));
            }}
          >
            Match existing
          </button>
          <button
            className={btn}
            disabled={pending}
            onClick={() => {
              const reason = window.prompt("Reason this is a genuinely distinct facility:");
              if (reason) runAction(resolve.mutateAsync({ rowId: row.id, reason }));
            }}
          >
            Resolve distinct
          </button>
        </>
      )}
      <button className={btn} disabled={pending} onClick={() => runAction(approve.mutateAsync({ rowId: row.id }))}>
        Approve
      </button>
      <button
        className={btn}
        disabled={pending}
        onClick={() => {
          const reason = window.prompt("Reason for rejecting this row:") ?? undefined;
          runAction(reject.mutateAsync({ rowId: row.id, reason }));
        }}
      >
        Reject
      </button>
      <button
        className={btn}
        disabled={pending}
        onClick={() => {
          const reason = window.prompt("Reason for skipping this row:") ?? undefined;
          runAction(skip.mutateAsync({ rowId: row.id, reason }));
        }}
      >
        Skip
      </button>
      {pending && <Loader2 className="w-3 h-3 animate-spin text-muted-foreground" />}
      {msg && <span className="text-xs text-muted-foreground">{msg}</span>}
    </div>
  );
}

const PAGE_SIZE = 25;

type TabDef =
  | { key: string; label: string; kind: "rows"; filters: RowFilters }
  | { key: string; label: string; kind: "duplicates"; duplicateType: string };

const TABS: TabDef[] = [
  { key: "imported", label: "Imported", kind: "rows", filters: { outcome: "IMPORTED" } },
  { key: "ready", label: "Ready (dry-run)", kind: "rows", filters: { outcome: "READY_FOR_IMPORT" } },
  {
    key: "missing-code",
    label: "Missing code",
    kind: "rows",
    filters: { outcome: "EXCLUDED_MISSING_FACILITY_CODE" },
  },
  { key: "dup-code", label: "Duplicate code", kind: "duplicates", duplicateType: "FACILITY_CODE" },
  { key: "dup-name", label: "Duplicate name", kind: "duplicates", duplicateType: "FACILITY_NAME" },
  {
    key: "acceptable-missing",
    label: "Acceptable-missing",
    kind: "rows",
    filters: { hasAcceptableMissingFields: true },
  },
  { key: "failed", label: "Failed", kind: "rows", filters: { outcome: "FAILED" } },
];

function missingList(row: FacilityImportRow): string[] {
  const m = row.acceptableMissing;
  if (!m) return [];
  const out: string[] = [];
  if (m.missing_latitude) out.push("lat");
  if (m.missing_longitude) out.push("long");
  if (m.missing_facility_type) out.push("type");
  if (m.missing_ownership) out.push("ownership");
  if (m.missing_operating_status) out.push("status");
  return out;
}

function RowTable({ rows, runId }: { rows: FacilityImportRow[]; runId: string }) {
  if (rows.length === 0) {
    return <p className="text-sm text-muted-foreground py-6 text-center">No rows in this bucket.</p>;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-background text-left">
            <th className="px-3 py-2 font-medium text-muted-foreground">Row</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">Name</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">Code</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">Province</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">District</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">Type</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">Ownership</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">Status</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">Decision</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">Detail</th>
            <th className="px-3 py-2 font-medium text-muted-foreground">Actions</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {rows.map((r) => {
            const missing = missingList(r);
            return (
              <tr key={r.id} className="hover:bg-background transition-colors align-top">
                <td className="px-3 py-2 text-muted-foreground">{r.sourceRow ?? "—"}</td>
                <td className="px-3 py-2 text-foreground">{r.facilityName ?? "—"}</td>
                <td className="px-3 py-2 text-foreground">{r.facilityCode ?? "—"}</td>
                <td className="px-3 py-2 text-muted-foreground">{r.province ?? "—"}</td>
                <td className="px-3 py-2 text-muted-foreground">{r.district ?? "—"}</td>
                <td className="px-3 py-2 text-muted-foreground">{r.facilityType ?? "—"}</td>
                <td className="px-3 py-2 text-muted-foreground">{r.ownership ?? "—"}</td>
                <td className="px-3 py-2 text-muted-foreground">{r.operatingStatus ?? "—"}</td>
                <td className="px-3 py-2">
                  <span className="px-2 py-0.5 text-xs rounded-full bg-neutral-100 text-foreground">
                    {r.decisionStatus ?? r.outcome}
                  </span>
                </td>
                <td className="px-3 py-2 text-xs text-muted-foreground space-y-0.5">
                  {r.exclusionReason ? <div>{r.exclusionReason}</div> : null}
                  {r.conflictReason ? <div className="text-red-600">{r.conflictReason}</div> : null}
                  {missing.length > 0 ? (
                    <div className="text-red-600">missing: {missing.join(", ")}</div>
                  ) : null}
                </td>
                <td className="px-3 py-2">
                  <RowActions runId={runId} row={r} />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function RowsPanel({ runId, filters }: { runId: string; filters: RowFilters }) {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useFacilityImportRows(runId, { ...filters, page, size: PAGE_SIZE });
  const paged = data?.data;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-10">
        <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (isError || !paged) {
    return (
      <div className="flex items-center gap-2 text-red-600 py-6 justify-center text-sm">
        <AlertTriangle className="w-4 h-4" /> Failed to load rows
      </div>
    );
  }
  return (
    <div>
      <RowTable rows={paged.content} runId={runId} />
      <div className="flex items-center justify-between mt-3 text-sm text-muted-foreground">
        <span>
          {paged.totalElements} row{paged.totalElements === 1 ? "" : "s"} · page {paged.page + 1} of{" "}
          {Math.max(paged.totalPages, 1)}
        </span>
        <div className="flex gap-2">
          <button
            disabled={paged.page <= 0}
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
            className="px-2 py-1 rounded border border-border disabled:opacity-40"
          >
            Prev
          </button>
          <button
            disabled={paged.page + 1 >= paged.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="px-2 py-1 rounded border border-border disabled:opacity-40"
          >
            Next
          </button>
        </div>
      </div>
    </div>
  );
}

function DuplicatesPanel({ runId, type }: { runId: string; type: string }) {
  const { data, isLoading, isError } = useFacilityImportDuplicates(runId, type);
  const resp = data?.data;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-10">
        <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (isError || !resp) {
    return (
      <div className="flex items-center gap-2 text-red-600 py-6 justify-center text-sm">
        <AlertTriangle className="w-4 h-4" /> Failed to load duplicates
      </div>
    );
  }
  if (resp.groups.length === 0) {
    return <p className="text-sm text-muted-foreground py-6 text-center">No duplicate groups.</p>;
  }
  return (
    <div className="space-y-4">
      {resp.groups.map((g) => (
        <div key={g.duplicateGroupKey} className="border border-border rounded-lg p-3">
          <div className="flex items-center justify-between mb-2">
            <p className="text-sm font-medium text-foreground">{g.duplicateGroupKey}</p>
            <span className="text-xs text-muted-foreground">
              {g.duplicateType} · {g.size} rows · review required (no auto-merge)
            </span>
          </div>
          <RowTable rows={g.rows} runId={runId} />
        </div>
      ))}
    </div>
  );
}

export function FacilityImportRowBrowser({ runId }: { runId: string }) {
  const [active, setActive] = useState(TABS[0].key);
  const tab = TABS.find((t) => t.key === active) ?? TABS[0];
  const applyApproved = useApplyApproved(runId);
  const [applyMsg, setApplyMsg] = useState<string | null>(null);

  return (
    <div className="bg-card rounded-lg border border-border p-4">
      <div className="flex items-center justify-between mb-3">
        <p className="text-sm text-muted-foreground">
          Resolve rows below; approved rows import into TUSO as pending-configuration facilities.
        </p>
        <div className="flex items-center gap-2">
          {applyMsg && <span className="text-xs text-muted-foreground">{applyMsg}</span>}
          <button
            className="px-3 py-1 text-sm rounded border border-border hover:bg-background disabled:opacity-40"
            disabled={applyApproved.isPending}
            onClick={() => {
              setApplyMsg(null);
              applyApproved
                .mutateAsync(undefined)
                .then((r) => setApplyMsg(`Applied ${r.data?.applied ?? 0}, skipped ${r.data?.skipped ?? 0}`))
                .catch((e) => setApplyMsg(errorMessage(e)));
            }}
          >
            {applyApproved.isPending ? "Applying…" : "Apply approved rows"}
          </button>
        </div>
      </div>
      <div className="flex flex-wrap gap-1 mb-4 border-b border-border">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setActive(t.key)}
            className={`px-3 py-1.5 text-sm rounded-t-md ${
              active === t.key
                ? "bg-primary-soft text-primary font-medium border-b-2 border-primary"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab.kind === "rows" ? (
        <RowsPanel runId={runId} filters={tab.filters} />
      ) : (
        <DuplicatesPanel runId={runId} type={tab.duplicateType} />
      )}
    </div>
  );
}
