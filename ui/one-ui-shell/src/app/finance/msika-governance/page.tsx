"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, DatabaseZap, Loader2, ShieldCheck, Upload } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useApproveMsikaCatalog,
  useApproveMsikaMapping,
  useCreateMsikaCatalog,
  useImportMsikaCatalogCsv,
  useMsikaCatalogs,
  useMsikaPendingMappings,
  usePublishMsikaCatalog,
  useRejectMsikaMapping,
  useSubmitMsikaCatalogReview,
} from "@/hooks/queries/useMsikaGovernance";

type CatalogRow = {
  catalogId: string;
  name: string;
  scope: string;
  status: string;
  version: string;
  itemCount: number | string;
  publishedAt?: string | null;
};

type MappingRow = {
  id: string;
  externalCode: string;
  externalName: string;
  internalItemId?: string | null;
  mapType?: string;
  confidence?: number;
  status?: string;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  return value != null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function extractItems(payload: unknown) {
  const root = asRecord(payload);
  if (!root) return [];
  const rootItems = root.items;
  if (Array.isArray(rootItems)) return rootItems;
  const data = asRecord(root.data);
  const dataItems = data?.items;
  if (Array.isArray(dataItems)) return dataItems;
  return [];
}

function extractCatalogs(payload: unknown): CatalogRow[] {
  return extractItems(payload).reduce<CatalogRow[]>((catalogs, item) => {
    const row = asRecord(item);
    if (!row) return catalogs;

    const catalogId = String(row.catalogId ?? row.catalog_id ?? "");
    if (!catalogId) return catalogs;

    const rawItemCount = row.itemCount ?? row.item_count;
    const itemCount =
      typeof rawItemCount === "number" || typeof rawItemCount === "string" ? rawItemCount : "—";
    const publishedAt =
      typeof row.publishedAt === "string" || row.publishedAt === null ? row.publishedAt : null;

    catalogs.push({
      catalogId,
      name: String(row.name ?? "Untitled catalog"),
      scope: String(row.scope ?? "UNKNOWN"),
      status: String(row.status ?? "UNKNOWN"),
      version: String(row.version ?? "—"),
      itemCount,
      publishedAt,
    });
    return catalogs;
  }, []);
}

function extractMappings(payload: unknown): MappingRow[] {
  return extractItems(payload).reduce<MappingRow[]>((mappings, item) => {
    const row = asRecord(item);
    if (!row) return mappings;

    const id = String(row.id ?? "");
    if (!id) return mappings;

    const internalItemId =
      typeof row.internalItemId === "string" || row.internalItemId === null
        ? row.internalItemId
        : typeof row.internal_item_id === "string" || row.internal_item_id === null
          ? (row.internal_item_id as string | null)
          : null;

    mappings.push({
      id,
      externalCode: String(row.externalCode ?? row.external_code ?? "—"),
      externalName: String(row.externalName ?? row.external_name ?? "Unnamed mapping"),
      internalItemId,
      mapType:
        typeof row.mapType === "string" ? row.mapType : typeof row.map_type === "string" ? row.map_type : undefined,
      confidence: typeof row.confidence === "number" ? row.confidence : undefined,
      status: typeof row.status === "string" ? row.status : undefined,
    });
    return mappings;
  }, []);
}

function stringifyJson(payload: unknown) {
  return JSON.stringify(payload, null, 2);
}

function statusTone(status: string) {
  if (status === "PUBLISHED") return "bg-emerald-100 text-primary-hover";
  if (status === "APPROVED") return "bg-teal-100 text-teal-800";
  if (status === "REVIEW") return "bg-amber-100 text-warning-foreground";
  return "bg-neutral-100 text-foreground";
}

export default function MsikaGovernancePage() {
  const catalogsQ = useMsikaCatalogs({ page: 0, size: 50 });
  const mappingsQ = useMsikaPendingMappings({ page: 0, size: 50 });
  const createCatalog = useCreateMsikaCatalog();
  const submitReview = useSubmitMsikaCatalogReview();
  const approveCatalog = useApproveMsikaCatalog();
  const publishCatalog = usePublishMsikaCatalog();
  const approveMapping = useApproveMsikaMapping();
  const rejectMapping = useRejectMsikaMapping();
  const importCsv = useImportMsikaCatalogCsv();

  const catalogs = useMemo(() => extractCatalogs(catalogsQ.data), [catalogsQ.data]);
  const mappings = useMemo(() => extractMappings(mappingsQ.data), [mappingsQ.data]);

  const [name, setName] = useState("");
  const [scope, setScope] = useState("NATIONAL");
  const [version, setVersion] = useState("2025.1.0");
  const [importCatalogId, setImportCatalogId] = useState("");
  const [importFile, setImportFile] = useState<File | null>(null);

  return (
    <AppLayout>
      <PageShell
        title="MSIKA governance"
        subtitle="Catalog governance, mapping decisions, publishing, and CSV import now run in Experience instead of the standalone MSIKA governance sidecar."
        icon={<DatabaseZap className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
          <Link href="/finance" className="inline-flex items-center gap-1 hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
          <Link href="/finance/commerce-integrations" className="inline-flex items-center gap-1 text-primary hover:text-primary-hover">
            Commerce & payer stack
          </Link>
        </div>

        <div className="space-y-6">
          <section className="rounded-xl border border-info/25 bg-info-soft/60 p-5">
            <div className="flex items-start gap-3">
              <div className="rounded-lg bg-indigo-100 p-2 text-primary-hover">
                <ShieldCheck className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-foreground">Governance scope</h2>
                <p className="mt-1 text-sm text-foreground">
                  This workspace consumes <code className="text-[11px]">/internal/v1/msika/*</code> through the
                  Experience BFF. Catalog creation, review, mapping approval, publishing, and CSV import are part of the
                  accepted Experience shell now.
                </p>
              </div>
            </div>
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-foreground">Catalog lifecycle</h2>
                <p className="mt-1 text-xs text-muted-foreground">
                  GET/POST <code className="text-[11px]">/internal/v1/msika/catalogs</code>, then submit-review,
                  approve, and publish per catalog.
                </p>
              </div>
            </div>

            <div className="mt-4 grid gap-3 lg:grid-cols-[1.2fr_0.6fr_0.6fr_auto] lg:items-end">
              <label className="text-xs text-muted-foreground">
                Catalog name
                <input
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </label>
              <label className="text-xs text-muted-foreground">
                Scope
                <select
                  value={scope}
                  onChange={(event) => setScope(event.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                >
                  <option value="NATIONAL">NATIONAL</option>
                  <option value="TENANT">TENANT</option>
                </select>
              </label>
              <label className="text-xs text-muted-foreground">
                Version
                <input
                  value={version}
                  onChange={(event) => setVersion(event.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </label>
              <button
                type="button"
                disabled={createCatalog.isPending || !name.trim()}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:opacity-50"
                onClick={() =>
                  createCatalog.mutate(
                    { name: name.trim(), scope, version: version.trim() || "2025.1.0" },
                    {
                      onSuccess: () => {
                        setName("");
                        setVersion("2025.1.0");
                      },
                    },
                  )
                }
              >
                Create catalog
              </button>
            </div>

            {catalogsQ.isLoading ? (
              <p className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading catalogs...
              </p>
            ) : catalogsQ.isError ? (
              <p className="mt-4 text-sm text-danger">Could not load MSIKA catalogs.</p>
            ) : catalogs.length > 0 ? (
              <div className="mt-4 overflow-x-auto rounded-xl border border-border">
                <table className="w-full text-sm">
                  <thead className="bg-background text-left">
                    <tr>
                      <th className="px-3 py-2 font-medium text-foreground">Name</th>
                      <th className="px-3 py-2 font-medium text-foreground">Scope</th>
                      <th className="px-3 py-2 font-medium text-foreground">Version</th>
                      <th className="px-3 py-2 font-medium text-foreground">Status</th>
                      <th className="px-3 py-2 font-medium text-foreground">Items</th>
                      <th className="px-3 py-2 font-medium text-foreground">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {catalogs.map((catalog) => (
                      <tr key={catalog.catalogId}>
                        <td className="px-3 py-3 align-top">
                          <p className="font-medium text-foreground">{catalog.name}</p>
                          <p className="mt-1 font-mono text-[11px] text-muted-foreground">{catalog.catalogId}</p>
                        </td>
                        <td className="px-3 py-3 align-top text-foreground">{catalog.scope}</td>
                        <td className="px-3 py-3 align-top font-mono text-xs text-foreground">{catalog.version}</td>
                        <td className="px-3 py-3 align-top">
                          <span className={`rounded-full px-2 py-1 text-xs font-medium ${statusTone(catalog.status)}`}>
                            {catalog.status}
                          </span>
                        </td>
                        <td className="px-3 py-3 align-top text-foreground">{String(catalog.itemCount)}</td>
                        <td className="px-3 py-3 align-top">
                          <div className="flex flex-wrap gap-2">
                            {catalog.status === "DRAFT" ? (
                              <button
                                type="button"
                                disabled={submitReview.isPending}
                                className="rounded-lg border border-border px-3 py-1.5 text-xs hover:bg-background disabled:opacity-50"
                                onClick={() => submitReview.mutate(catalog.catalogId)}
                              >
                                Submit review
                              </button>
                            ) : null}
                            {catalog.status === "REVIEW" ? (
                              <button
                                type="button"
                                disabled={approveCatalog.isPending}
                                className="rounded-lg border border-success/25 px-3 py-1.5 text-xs text-primary-hover hover:bg-success-soft disabled:opacity-50"
                                onClick={() => approveCatalog.mutate(catalog.catalogId)}
                              >
                                Approve
                              </button>
                            ) : null}
                            {catalog.status === "APPROVED" ? (
                              <button
                                type="button"
                                disabled={publishCatalog.isPending}
                                className="rounded-lg border border-info/25 px-3 py-1.5 text-xs text-primary-hover hover:bg-info-soft disabled:opacity-50"
                                onClick={() => publishCatalog.mutate(catalog.catalogId)}
                              >
                                Publish
                              </button>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="mt-4 text-sm text-muted-foreground">No catalogs are available yet.</p>
            )}
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="rounded-lg bg-neutral-100 p-2 text-foreground">
                <Upload className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-foreground">CSV import</h2>
                <p className="mt-1 text-xs text-muted-foreground">
                  POST multipart <code className="text-[11px]">/internal/v1/msika/import</code> with{" "}
                  <code className="text-[11px]">catalogId</code> and <code className="text-[11px]">file</code>.
                </p>
              </div>
            </div>

            <div className="mt-4 grid gap-3 lg:grid-cols-[0.8fr_1fr_auto] lg:items-end">
              <label className="text-xs text-muted-foreground">
                Target catalog id
                <input
                  value={importCatalogId}
                  onChange={(event) => setImportCatalogId(event.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm font-mono"
                />
              </label>
              <label className="text-xs text-muted-foreground">
                CSV file
                <input
                  type="file"
                  accept=".csv"
                  onChange={(event) => setImportFile(event.target.files?.[0] ?? null)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </label>
              <button
                type="button"
                disabled={importCsv.isPending || !importCatalogId.trim() || !importFile}
                className="rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
                onClick={() => {
                  if (!importFile) return;
                  importCsv.mutate({ catalogId: importCatalogId.trim(), file: importFile });
                }}
              >
                Upload & import
              </button>
            </div>

            {importCsv.isError ? (
              <p className="mt-3 text-sm text-danger">Import failed. Check the catalog id and CSV file.</p>
            ) : null}
            {importCsv.data != null ? (
              <pre className="mt-3 max-h-64 overflow-auto rounded-lg border border-border bg-background p-3 text-[11px] text-foreground">
                {stringifyJson(importCsv.data)}
              </pre>
            ) : null}
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Pending mappings</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET/POST <code className="text-[11px]">/internal/v1/msika/mappings/pending</code>, approve, and reject.
            </p>

            {mappingsQ.isLoading ? (
              <p className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading pending mappings...
              </p>
            ) : mappingsQ.isError ? (
              <p className="mt-4 text-sm text-danger">Could not load pending mappings.</p>
            ) : mappings.length > 0 ? (
              <div className="mt-4 overflow-x-auto rounded-xl border border-border">
                <table className="w-full text-sm">
                  <thead className="bg-background text-left">
                    <tr>
                      <th className="px-3 py-2 font-medium text-foreground">External code</th>
                      <th className="px-3 py-2 font-medium text-foreground">External name</th>
                      <th className="px-3 py-2 font-medium text-foreground">Mapped item</th>
                      <th className="px-3 py-2 font-medium text-foreground">Type</th>
                      <th className="px-3 py-2 font-medium text-foreground">Confidence</th>
                      <th className="px-3 py-2 font-medium text-foreground">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {mappings.map((mapping) => (
                      <tr key={mapping.id}>
                        <td className="px-3 py-3 font-mono text-xs text-foreground">{mapping.externalCode}</td>
                        <td className="px-3 py-3 text-foreground">{mapping.externalName}</td>
                        <td className="px-3 py-3 font-mono text-xs text-foreground">{mapping.internalItemId ?? "—"}</td>
                        <td className="px-3 py-3 text-foreground">{mapping.mapType ?? "—"}</td>
                        <td className="px-3 py-3 text-foreground">
                          {typeof mapping.confidence === "number" ? `${(mapping.confidence * 100).toFixed(1)}%` : "—"}
                        </td>
                        <td className="px-3 py-3">
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              disabled={approveMapping.isPending}
                              className="rounded-lg border border-success/25 px-3 py-1.5 text-xs text-primary-hover hover:bg-success-soft disabled:opacity-50"
                              onClick={() => approveMapping.mutate(mapping.id)}
                            >
                              Approve
                            </button>
                            <button
                              type="button"
                              disabled={rejectMapping.isPending}
                              className="rounded-lg border border-danger/28 px-3 py-1.5 text-xs text-red-800 hover:bg-danger-soft disabled:opacity-50"
                              onClick={() => rejectMapping.mutate(mapping.id)}
                            >
                              Reject
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="mt-4 text-sm text-muted-foreground">No pending mappings are waiting for review.</p>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
