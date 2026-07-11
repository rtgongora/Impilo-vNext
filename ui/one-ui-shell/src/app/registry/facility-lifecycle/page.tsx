"use client";

/**
 * HPA facility regulatory lifecycle console.
 * Route: /registry/facility-lifecycle (REGULATORY_AUTHORITY)
 *
 * The presentation layer over the governed tuso regulatory engine:
 * dashboard truth, state-bucketed registry queues, and application intake.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import {
  Building2,
  ClipboardCheck,
  FileWarning,
  Gavel,
  Loader2,
  Plus,
  RefreshCw,
  Search,
  ShieldAlert,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useFacilityDashboardSummary,
  useFacilityRegistryFacilities,
  useCreateFacilityApplication,
} from "@/hooks/queries/useFacilityRegulatory";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";

const STATUS_BUCKETS: { key: string; label: string }[] = [
  { key: "", label: "All" },
  { key: "IMPORTED_PENDING_CONFIGURATION", label: "Imported" },
  { key: "APPLICATION_IN_PROGRESS", label: "Applications" },
  { key: "UNDER_INITIAL_REVIEW", label: "In review" },
  { key: "PENDING_INSPECTION", label: "Pending inspection" },
  { key: "PENDING_RECTIFICATION", label: "Rectification" },
  { key: "PENDING_COMMITTEE_REVIEW", label: "Committee" },
  { key: "REGISTERED_ACTIVE", label: "Registered" },
  { key: "RESTRICTED", label: "Restricted" },
  { key: "SUSPENDED", label: "Suspended" },
];

const APPLICATION_TYPES = ["NEW_REGISTRATION", "RENEWAL", "CHANGE_OF_PARTICULARS", "CHANGE_OF_PREMISES"];
const PATHWAYS = ["STANDARD", "FAST_TRACK", "LEGACY_REGULARISATION"];
const FACILITY_CLASSES = ["HOSPITAL", "CLINIC", "SURGERY", "LABORATORY", "PHARMACY", "RADIOLOGY", "OTHER"];

export default function FacilityLifecyclePage() {
  const dashboard = useFacilityDashboardSummary();
  const [bucket, setBucket] = useState("");
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search, 350);
  const [page, setPage] = useState(0);
  const [showIntake, setShowIntake] = useState(false);

  const listQuery = useFacilityRegistryFacilities({
    search: debouncedSearch || undefined,
    regulatoryStatus: bucket || undefined,
    page,
    size: 20,
  });

  const summary = dashboard.data?.data;
  const pageData = listQuery.data?.data;

  const metrics = useMemo(
    () =>
      summary
        ? [
            { label: "Facilities", value: summary.totalFacilities, icon: Building2 },
            { label: "Registered active", value: summary.activeFacilities, icon: ClipboardCheck },
            { label: "Pending inspection", value: summary.pendingInspection, icon: Search },
            { label: "Committee reviews", value: summary.pendingCommitteeReviews, icon: Gavel },
            { label: "Overdue actions", value: summary.overdueComplianceActions, icon: FileWarning },
            { label: "Renewals due", value: summary.renewalsDueSoon, icon: RefreshCw },
            { label: "Enforcement open", value: summary.openEnforcementCases, icon: ShieldAlert },
          ]
        : [],
    [summary],
  );

  return (
    <AppLayout>
      <PageShell
        title="Facility regulatory lifecycle"
        subtitle="Health Professions Authority — registration, inspection, committee and enforcement over the national registry"
      >
        {/* Dashboard truth */}
        <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
          {metrics.map((m) => (
            <div key={m.label} className="rounded-lg border border-border bg-card p-3">
              <m.icon className="h-4 w-4 text-muted-foreground" />
              <p className="mt-2 text-xl font-semibold tabular-nums text-foreground">{m.value}</p>
              <p className="text-[11px] text-muted-foreground">{m.label}</p>
            </div>
          ))}
          {dashboard.isLoading && (
            <div className="col-span-full flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading regulatory dashboard…
            </div>
          )}
          {dashboard.isError && (
            <p className="col-span-full text-sm text-red-600">
              Regulatory dashboard unavailable — the registry service may be unreachable.
            </p>
          )}
        </div>

        {/* Intake */}
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-1">
            {STATUS_BUCKETS.map((b) => {
              const count = b.key ? summary?.facilitiesByRegulatoryStatus?.[b.key] : summary?.totalFacilities;
              return (
                <button
                  key={b.key || "all"}
                  onClick={() => {
                    setBucket(b.key);
                    setPage(0);
                  }}
                  className={`rounded-full px-3 py-1 text-xs font-medium ${
                    bucket === b.key
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:text-foreground"
                  }`}
                >
                  {b.label}
                  {count !== undefined && count !== null ? ` · ${count}` : ""}
                </button>
              );
            })}
          </div>
          <button
            type="button"
            onClick={() => setShowIntake((v) => !v)}
            className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground"
          >
            <Plus className="h-4 w-4" /> New application
          </button>
        </div>

        {showIntake && <ApplicationIntake onDone={() => setShowIntake(false)} />}

        {/* Registry queue */}
        <div className="mb-3 flex items-center gap-2">
          <Search className="h-4 w-4 text-muted-foreground" />
          <input
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            placeholder="Search the national facility registry by name…"
            className="w-full max-w-md rounded-md border border-border bg-background px-3 py-1.5 text-sm"
          />
        </div>

        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                <th className="px-4 py-2">Facility</th>
                <th className="px-4 py-2">Type</th>
                <th className="px-4 py-2">Province</th>
                <th className="px-4 py-2">Regulatory status</th>
                <th className="px-4 py-2">Certificate</th>
                <th className="px-4 py-2">Open apps</th>
                <th className="px-4 py-2">Overdue</th>
              </tr>
            </thead>
            <tbody>
              {(pageData?.items ?? []).map((f) => (
                <tr key={f.facilityId} className="border-b border-border/60 last:border-0 hover:bg-muted/40">
                  <td className="px-4 py-2">
                    <Link
                      href={`/registry/facility-lifecycle/${f.facilityId}`}
                      className="font-medium text-primary hover:underline"
                    >
                      {f.name}
                    </Link>
                    <span className="ml-2 text-xs text-muted-foreground">{f.facilityCode}</span>
                  </td>
                  <td className="px-4 py-2 text-muted-foreground">{f.facilityType?.replace(/_/g, " ") ?? "—"}</td>
                  <td className="px-4 py-2 text-muted-foreground">{f.province ?? "—"}</td>
                  <td className="px-4 py-2">
                    <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">
                      {f.regulatoryStatus?.replace(/_/g, " ")}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-muted-foreground">
                    {f.latestCertificateNumber
                      ? `${f.latestCertificateNumber}${f.latestCertificateExpiryDate ? ` · exp ${f.latestCertificateExpiryDate}` : ""}`
                      : "—"}
                  </td>
                  <td className="px-4 py-2 tabular-nums text-muted-foreground">{f.openApplications}</td>
                  <td className="px-4 py-2 tabular-nums">
                    {f.overdueActions > 0 ? (
                      <span className="font-medium text-red-600">{f.overdueActions}</span>
                    ) : (
                      <span className="text-muted-foreground">0</span>
                    )}
                  </td>
                </tr>
              ))}
              {listQuery.isLoading && (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-sm text-muted-foreground">
                    <Loader2 className="mx-auto h-5 w-5 animate-spin" />
                  </td>
                </tr>
              )}
              {!listQuery.isLoading && (pageData?.items ?? []).length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-sm text-muted-foreground">
                    No facilities match this queue.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {pageData && pageData.totalPages > 1 && (
          <div className="mt-3 flex items-center justify-between text-sm text-muted-foreground">
            <span>
              Page {pageData.page + 1} of {pageData.totalPages} · {pageData.totalElements} facilities
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={pageData.page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="rounded-md border border-border px-3 py-1 disabled:opacity-50"
              >
                Previous
              </button>
              <button
                type="button"
                disabled={!pageData.hasNext}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-md border border-border px-3 py-1 disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function ApplicationIntake({ onDone }: { onDone: () => void }) {
  const create = useCreateFacilityApplication();
  const [facilityId, setFacilityId] = useState("");
  const [applicationType, setApplicationType] = useState(APPLICATION_TYPES[0]);
  const [pathway, setPathway] = useState(PATHWAYS[0]);
  const [facilityClass, setFacilityClass] = useState("");
  const [applicantName, setApplicantName] = useState("");
  const [facilityName, setFacilityName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const forExisting = facilityId.trim() !== "";

  return (
    <div className="mb-6 rounded-lg border border-border bg-card p-4">
      <h3 className="mb-1 text-sm font-medium text-foreground">New registration application</h3>
      <p className="mb-3 text-xs text-muted-foreground">
        Reference an existing registry facility by id (e.g. an imported site regularising its
        registration), or leave blank to register a brand-new facility shell.
      </p>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        <input
          value={facilityId}
          onChange={(e) => setFacilityId(e.target.value.replace(/[^0-9]/g, ""))}
          placeholder="Existing facility id (optional)"
          className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        />
        <input
          value={facilityName}
          onChange={(e) => setFacilityName(e.target.value)}
          placeholder={forExisting ? "Facility name (from registry)" : "New facility name"}
          disabled={forExisting}
          className="rounded-md border border-border bg-background px-2 py-1.5 text-sm disabled:opacity-50"
        />
        <input
          value={applicantName}
          onChange={(e) => setApplicantName(e.target.value)}
          placeholder="Applicant name"
          className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        />
        <select
          value={applicationType}
          onChange={(e) => setApplicationType(e.target.value)}
          className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        >
          {APPLICATION_TYPES.map((t) => (
            <option key={t} value={t}>
              {t.replace(/_/g, " ")}
            </option>
          ))}
        </select>
        <select
          value={pathway}
          onChange={(e) => setPathway(e.target.value)}
          className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        >
          {PATHWAYS.map((t) => (
            <option key={t} value={t}>
              {t.replace(/_/g, " ")}
            </option>
          ))}
        </select>
        <select
          value={facilityClass}
          onChange={(e) => setFacilityClass(e.target.value)}
          className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        >
          <option value="">Practice class (optional)</option>
          {FACILITY_CLASSES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </div>
      <div className="mt-3 flex items-center gap-2">
        <button
          type="button"
          disabled={create.isPending || !applicantName.trim() || (!forExisting && !facilityName.trim())}
          onClick={() => {
            setError(null);
            create.mutate(
              {
                facilityId: forExisting ? Number(facilityId) : undefined,
                applicationType,
                pathway,
                applicantName: applicantName.trim(),
                facilityName: forExisting ? undefined : facilityName.trim(),
                facilityClass: facilityClass || undefined,
              },
              {
                onSuccess: onDone,
                onError: (e: unknown) =>
                  setError(
                    (e as { body?: { error?: { message?: string } } })?.body?.error?.message ??
                      (e as Error)?.message ??
                      "Application could not be created.",
                  ),
              },
            );
          }}
          className="rounded-md bg-primary px-4 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          {create.isPending ? "Creating…" : "Create application"}
        </button>
        <button
          type="button"
          onClick={onDone}
          className="rounded-md border border-border px-4 py-1.5 text-sm text-muted-foreground"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}
