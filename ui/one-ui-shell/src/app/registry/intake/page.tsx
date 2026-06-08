"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  ArrowLeft,
  ClipboardList,
  ExternalLink,
  LifeBuoy,
  Loader2,
  MapPin,
  Radar,
  Upload,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { ContextualLearningPanel } from "@/components/learning/ContextualLearningPanel";
import { PageShell } from "@/components/PageShell";
import {
  useBootstrapSnapshot,
  useCancelImportJob,
  useCreateImportJob,
  useCreateIntakeSession,
  useCreateRecoveryRequest,
  useExecuteImportJob,
  useRegistryImportJobs,
  useSearchIndawoSites,
} from "@/hooks/queries/useRegistryIntake";
import {
  isLikelyLandelaDocumentUuid,
  landelaDocumentAccessHref,
  registryIntakeLandelaPrefillHref,
} from "@/lib/landela-routes";
import { FacilitiesGeoMapPanel } from "@/components/maps/FacilitiesGeoMapPanel";
import { SiteRegistryGeoMapPanel } from "@/components/maps/SiteRegistryGeoMapPanel";

const FACILITY_SAMPLE_CSV =
  "name,facilityCode,facilityType,province,district\nSample Import Clinic,ZW-IMP-DEMO-001,CLINIC,Harare,Harare";

const SITE_SAMPLE_CSV =
  "name,type,status\nSample PHC Site,PUBLIC_HEALTH_SITE,PROVISIONAL_IMPORTED";

export default function RegistryIntakePage() {
  const bootstrap = useBootstrapSnapshot();
  const createSession = useCreateIntakeSession();
  const createRecovery = useCreateRecoveryRequest();
  const createImport = useCreateImportJob();
  const importJobs = useRegistryImportJobs();
  const cancelImport = useCancelImportJob();
  const [importJobId, setImportJobId] = useState<string | null>(null);
  const executeImport = useExecuteImportJob();
  const searchIndawo = useSearchIndawoSites();

  const [targetRegistry, setTargetRegistry] = useState<"FACILITY" | "SITE">("FACILITY");
  const [importSource, setImportSource] = useState<"inline" | "landela" | "master-pack">("inline");
  const [landelaDocumentId, setLandelaDocumentId] = useState("");
  const [csvText, setCsvText] = useState(FACILITY_SAMPLE_CSV);

  const [indawoQ, setIndawoQ] = useState("");
  const [indawoCursor, setIndawoCursor] = useState(0);
  const [indawoLimit, setIndawoLimit] = useState(50);

  useEffect(() => {
    setCsvText(targetRegistry === "SITE" ? SITE_SAMPLE_CSV : FACILITY_SAMPLE_CSV);
  }, [targetRegistry]);

  /** Deep-link prefill: {@code /registry/intake?landelaDocumentId=…&importSource=landela} */
  useEffect(() => {
    if (typeof window === "undefined") return;
    const sp = new URLSearchParams(window.location.search);
    const id = sp.get("landelaDocumentId") ?? sp.get("documentId");
    const src = sp.get("importSource");
    if (id && isLikelyLandelaDocumentUuid(id)) {
      setLandelaDocumentId(id.trim());
      if (src === "landela") {
        setImportSource("landela");
      }
    }
  }, []);

  const importHint = useMemo(() => {
    if (targetRegistry === "SITE") {
      return "Header row: name, type (optional), status (optional). Rows upsert to Indawo with deterministic IDs.";
    }
    if (importSource === "master-pack") {
      return "Paste tuso_facility_registry_seed.json from docs/data/facility-master-2024-07-23/generated/. Idempotent Tuso import by facility_uid, then Ndila geospatial sync.";
    }
    return "Header row: name, facilityCode, facilityType, province, district. Tuso duplicate search by facility code.";
  }, [targetRegistry, importSource]);

  function submitImportPreview() {
    if (importSource === "master-pack") {
      if (!csvText.trim().startsWith("[")) {
        window.alert("Master pack import expects a JSON array (tuso_facility_registry_seed.json).");
        return;
      }
      createImport.mutate(
        {
          targetRegistry: "FACILITY",
          importType: "FACILITY_MASTER_PACK",
          inlineJson: csvText,
        },
        {
          onSuccess: (res) => {
            const idOut = (res as { data?: { id?: string } }).data?.id;
            if (idOut) setImportJobId(idOut);
          },
        },
      );
      return;
    }
    if (importSource === "landela") {
      const id = landelaDocumentId.trim();
      if (!id) {
        window.alert("Enter a Landela document UUID when using Landela as the CSV source.");
        return;
      }
      createImport.mutate(
        {
          targetRegistry,
          importType: targetRegistry === "SITE" ? "SITE_CSV" : "FACILITY_CSV",
          landelaDocumentId: id,
        },
        {
          onSuccess: (res) => {
            const idOut = (res as { data?: { id?: string } }).data?.id;
            if (idOut) setImportJobId(idOut);
          },
        },
      );
      return;
    }
    createImport.mutate(
      {
        targetRegistry,
        importType: targetRegistry === "SITE" ? "SITE_CSV" : "FACILITY_CSV",
        csv: csvText,
      },
      {
        onSuccess: (res) => {
          const idOut = (res as { data?: { id?: string } }).data?.id;
          if (idOut) setImportJobId(idOut);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Registry intake & onboarding"
        subtitle="Search-first, progressive intake, self-help tickets, governed import, and day-zero bootstrap signals"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4 space-y-3">
          <ContextualLearningPanel
            appCode="experience"
            routeRef="/registry/intake"
            workflowCode="registry_intake"
            title="Intake & onboarding learning"
          />
          <Link
            href="/registry"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to registry hub
          </Link>
        </div>

        <div className="mb-6">
          {targetRegistry === "FACILITY" ? (
            <FacilitiesGeoMapPanel
              title="Facility placement preview"
              subtitle="Coordinate truth for Tuso intake — capture on facility registration"
            />
          ) : (
            <SiteRegistryGeoMapPanel />
          )}
        </div>

        <div className="mb-6 grid gap-4 md:grid-cols-2">
          <div className="rounded-2xl border border-gray-200 bg-white p-5">
            <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
              <Radar className="h-4 w-4 text-impilo-500" />
              Day-zero bootstrap snapshot
            </div>
            <p className="mt-2 text-xs text-gray-500">
              Read-only probes of downstream registry reachability (Vito, Varapi, Tuso, Indawo). Use with registry
              admin runbooks.
            </p>
            {bootstrap.isLoading ? (
              <div className="mt-4 flex items-center gap-2 text-sm text-gray-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </div>
            ) : (
              <QueryResultPanel title="Bootstrap" isPending={bootstrap.isPending} isLoading={bootstrap.isPending} isError={bootstrap.isError} error={bootstrap.error} data={bootstrap.data?.data ?? {}} />
            )}
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white p-5">
            <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
              <ClipboardList className="h-4 w-4 text-emerald-600" />
              Guided intake session (shell)
            </div>
            <p className="mt-2 text-xs text-gray-500">
              Creates a Redis-backed intake session for progressive capture. Link to Vito/Varapi/Tuso flows from here in
              later UI passes.
            </p>
            <button
              type="button"
              className="mt-4 inline-flex items-center rounded-lg bg-impilo-500 px-3 py-2 text-xs font-medium text-white hover:bg-impilo-600 disabled:opacity-50"
              disabled={createSession.isPending}
              onClick={() =>
                createSession.mutate({
                  intakeType: "CLIENT_PROGRESSIVE",
                  targetRegistry: "VITO",
                  initiatedChannel: "WEB",
                  provenance: "REGISTRY_OPERATOR",
                  notes: "Started from Experience registry intake hub",
                })
              }
            >
              {createSession.isPending ? "Starting…" : "Start sample client intake session"}
            </button>
            {createSession.data?.data && (
              <p className="mt-2 text-xs text-emerald-700">
                Session id: {(createSession.data as { data: { id: string } }).data.id}
              </p>
            )}
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white p-5">
            <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
              <LifeBuoy className="h-4 w-4 text-sky-600" />
              Self-help recovery ticket
            </div>
            <p className="mt-2 text-xs text-gray-500">
              Governed queue item for lost reference / resume / correction routing.
            </p>
            <button
              type="button"
              className="mt-4 inline-flex items-center rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-xs font-medium text-sky-900 hover:bg-sky-100 disabled:opacity-50"
              disabled={createRecovery.isPending}
              onClick={() =>
                createRecovery.mutate({
                  requestKind: "RESUME_OR_RECOVER",
                  targetRegistry: "VITO",
                  contactHint: "self-help@example.com",
                  narrative: "Operator requested recovery from registry intake hub",
                })
              }
            >
              {createRecovery.isPending ? "Submitting…" : "File sample recovery request"}
            </button>
            {createRecovery.data?.data && (
              <p className="mt-2 text-xs text-sky-800">
                Request id: {(createRecovery.data as { data: { id: string } }).data.id}
              </p>
            )}
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white p-5 md:col-span-2">
            <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
              <Upload className="h-4 w-4 text-amber-600" />
              Governed CSV import (preview + execute)
            </div>
            <p className="mt-2 text-xs text-gray-500">{importHint}</p>

            <div className="mt-3 flex flex-wrap items-center gap-3">
              <label className="text-xs font-medium text-gray-600">Target registry</label>
              <select
                value={targetRegistry}
                onChange={(e) => setTargetRegistry(e.target.value as "FACILITY" | "SITE")}
                className="rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
              >
                <option value="FACILITY">Facility (Tuso)</option>
                <option value="SITE">Site (Indawo)</option>
              </select>
            </div>

            <div className="mt-3 flex flex-wrap gap-4">
              <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-gray-700">
                <input
                  type="radio"
                  name="importSource"
                  checked={importSource === "inline"}
                  onChange={() => setImportSource("inline")}
                />
                Inline CSV
              </label>
              <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-gray-700">
                <input
                  type="radio"
                  name="importSource"
                  checked={importSource === "landela"}
                  onChange={() => setImportSource("landela")}
                />
                Landela document (UUID)
              </label>
              {targetRegistry === "FACILITY" ? (
                <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-gray-700">
                  <input
                    type="radio"
                    name="importSource"
                    checked={importSource === "master-pack"}
                    onChange={() => setImportSource("master-pack")}
                  />
                  Master Health Facility pack (JSON)
                </label>
              ) : null}
            </div>

            {importSource === "landela" ? (
              <div className="mt-3">
                <label className="block text-xs font-medium text-gray-600 mb-1">Landela document UUID</label>
                <input
                  type="text"
                  value={landelaDocumentId}
                  onChange={(e) => setLandelaDocumentId(e.target.value)}
                  placeholder="e.g. 8b7c… (document stored in Landela; BFF downloads UTF-8 text)"
                  className="w-full rounded-lg border border-gray-200 p-2 font-mono text-xs"
                />
                <div className="mt-2 flex flex-wrap items-center gap-3 text-[11px]">
                  {isLikelyLandelaDocumentUuid(landelaDocumentId) ? (
                    <>
                      <Link
                        href={landelaDocumentAccessHref(landelaDocumentId)}
                        className="inline-flex items-center gap-1 font-medium text-violet-700 hover:text-violet-900 hover:underline"
                      >
                        <ExternalLink className="h-3 w-3" />
                        Open in Landela workspace
                      </Link>
                      <span className="text-gray-400">|</span>
                      <Link
                        href={registryIntakeLandelaPrefillHref(landelaDocumentId)}
                        className="text-gray-600 hover:text-gray-900 hover:underline"
                      >
                        Shareable link (prefills this form)
                      </Link>
                    </>
                  ) : (
                    <span className="text-gray-500">Enter a valid UUID to open the document workspace.</span>
                  )}
                </div>
                <p className="mt-1 text-[11px] text-gray-500">
                  The document body must be CSV text. Large files are fetched server-side from Landela (not pasted in
                  the browser).
                </p>
              </div>
            ) : (
              <textarea
                value={csvText}
                onChange={(e) => setCsvText(e.target.value)}
                className="mt-3 w-full rounded-lg border border-gray-200 p-3 font-mono text-xs"
                rows={5}
              />
            )}

            <div className="mt-3 flex flex-wrap gap-2">
              <button
                type="button"
                className="rounded-lg bg-amber-600 px-3 py-2 text-xs font-medium text-white hover:bg-amber-700 disabled:opacity-50"
                disabled={createImport.isPending}
                onClick={submitImportPreview}
              >
                {createImport.isPending ? "Creating job…" : "Create import job (preview)"}
              </button>
              <button
                type="button"
                className="rounded-lg border border-amber-200 px-3 py-2 text-xs font-medium text-amber-900 hover:bg-amber-50 disabled:opacity-50"
                disabled={!importJobId || executeImport.isPending}
                onClick={() => importJobId && executeImport.mutate({ jobId: importJobId, dryRun: true })}
              >
                Dry-run execute
              </button>
              <button
                type="button"
                className="rounded-lg border border-rose-200 px-3 py-2 text-xs font-medium text-rose-900 hover:bg-rose-50 disabled:opacity-50"
                disabled={!importJobId || executeImport.isPending}
                onClick={() => importJobId && executeImport.mutate({ jobId: importJobId, dryRun: false })}
              >
                Live execute
              </button>
              <button
                type="button"
                className="rounded-lg border border-gray-200 px-3 py-2 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                disabled={!importJobId || cancelImport.isPending}
                onClick={() =>
                  importJobId &&
                  cancelImport.mutate(importJobId, {
                    onSuccess: () => void importJobs.refetch(),
                  })
                }
              >
                Cancel job
              </button>
            </div>
            {createImport.isError && (
              <p className="mt-2 text-xs text-red-600">
                {(createImport.error as { error?: { message?: string } })?.error?.message ??
                  "Import job creation failed."}
              </p>
            )}
            {createImport.data?.data && (
              <p className="mt-2 text-xs text-amber-900">
                Job id: {(createImport.data as { data: { id: string } }).data.id}
              </p>
            )}
            {executeImport.data?.data && (
              <QueryResultPanel
                title="Execute import result"
                isPending={executeImport.isPending} isLoading={executeImport.isPending}
                isError={executeImport.isError}
                error={executeImport.error}
                data={(executeImport.data as { data?: unknown } | undefined)?.data}
              />
            )}
            <div className="mt-5 rounded-xl border border-gray-100 bg-gray-50 p-3">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-semibold text-gray-800">Recent import jobs</p>
                <button
                  type="button"
                  className="text-xs font-medium text-gray-600 hover:text-gray-900"
                  onClick={() => void importJobs.refetch()}
                >
                  Refresh
                </button>
              </div>
              {importJobs.isLoading ? <p className="mt-2 text-xs text-gray-500">Loading import jobs...</p> : null}
              {importJobs.data?.data?.length ? (
                <div className="mt-2 grid gap-2">
                  {importJobs.data.data.slice(0, 5).map((job) => (
                    <button
                      key={String(job.id)}
                      type="button"
                      onClick={() => setImportJobId(String(job.id))}
                      className="rounded-lg border border-gray-200 bg-white p-2 text-left text-xs hover:border-amber-300"
                    >
                      <span className="font-mono font-semibold text-gray-900">{String(job.id)}</span>
                      <span className="ml-2 text-gray-600">{String(job.status ?? "UNKNOWN")}</span>
                      <span className="ml-2 text-gray-500">{String(job.targetRegistry ?? "")}</span>
                    </button>
                  ))}
                </div>
              ) : !importJobs.isLoading ? (
                <p className="mt-2 text-xs text-gray-500">No recent import jobs.</p>
              ) : null}
            </div>
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white p-5 md:col-span-2">
            <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
              <MapPin className="h-4 w-4 text-violet-600" />
              Indawo site search (operator)
            </div>
            <p className="mt-2 text-xs text-gray-500">
              Lists a page of sites from Indawo, then optionally filters names containing your query (client-side on
              that page).
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Name contains</label>
                <input
                  type="text"
                  value={indawoQ}
                  onChange={(e) => setIndawoQ(e.target.value)}
                  className="w-48 rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
                  placeholder="optional"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Cursor</label>
                <input
                  type="number"
                  min={0}
                  value={indawoCursor}
                  onChange={(e) => setIndawoCursor(Number(e.target.value) || 0)}
                  className="w-20 rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Limit</label>
                <input
                  type="number"
                  min={1}
                  max={200}
                  value={indawoLimit}
                  onChange={(e) => setIndawoLimit(Math.min(200, Math.max(1, Number(e.target.value) || 50)))}
                  className="w-20 rounded-lg border border-gray-200 px-2 py-1.5 text-xs"
                />
              </div>
              <button
                type="button"
                className="rounded-lg bg-violet-600 px-3 py-2 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50"
                disabled={searchIndawo.isPending}
                onClick={() => searchIndawo.mutate({ q: indawoQ, cursor: indawoCursor, limit: indawoLimit })}
              >
                {searchIndawo.isPending ? "Searching…" : "Search"}
              </button>
            </div>
            {searchIndawo.isError && (
              <p className="mt-2 text-xs text-red-600">
                {(searchIndawo.error as { error?: { message?: string } })?.error?.message ?? "Search failed."}
              </p>
            )}
            {searchIndawo.data?.data != null && (
              <QueryResultPanel title="Search Indawo" isPending={searchIndawo.isPending} isLoading={searchIndawo.isPending} isError={searchIndawo.isError} error={searchIndawo.error} data={searchIndawo.data.data} />
            )}
          </div>
        </div>

        <div className="rounded-2xl border border-dashed border-gray-200 bg-gray-50 p-4 text-xs text-gray-600">
          <p className="font-medium text-gray-800">Entry points</p>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            <li>
              <Link className="text-impilo-600 hover:underline" href="/registry/clients/new">
                Register new client
              </Link>{" "}
              (Vito issuance + client registry)
            </li>
            <li>
              <Link className="text-impilo-600 hover:underline" href="/registry/providers/new">
                Register new provider
              </Link>{" "}
              (Varapi — or anchor on Impilo Health ID from that page)
            </li>
            <li>
              <Link className="text-impilo-600 hover:underline" href="/registry/facilities/new">
                Register new facility
              </Link>{" "}
              (Tuso)
            </li>
          </ul>
        </div>
      </PageShell>
    </AppLayout>
  );
}
