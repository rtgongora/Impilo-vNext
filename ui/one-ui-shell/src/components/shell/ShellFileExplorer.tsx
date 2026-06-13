"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Download, ExternalLink, FileText, Loader2, Search } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShellFileCatalog } from "@/hooks/queries/useShellFileCatalog";
import { useShellStore } from "@/hooks/useShellStore";
import {
  usePersonalDocumentDownloadUrl,
  usePersonalDocuments,
  type PersonalDocumentResource,
} from "@/hooks/queries/usePersonalDocuments";
import { matchesRequiredRole } from "@/lib/auth/role-groups";
import {
  mergeFileResources,
  personalDocumentsToFileResources,
  recentItemsToFileResources,
} from "@/lib/shell/file-resources";
import type { FileResource } from "@/lib/shell/types";

export function ShellFileExplorer() {
  const hasRole = useAuthStore((s) => s.hasRole);
  const recentItems = useShellStore((s) => s.recentItems);
  const recordRecent = useShellStore((s) => s.recordRecent);
  const [q, setQ] = useState("");
  const [source, setSource] = useState<"all" | "personal" | "recent">("all");

  const facilityId = useFacilityStore((s) => s.facility?.id);
  const catalogQ = useShellFileCatalog(facilityId, true);

  const { data, isLoading, isError } = usePersonalDocuments();
  const downloadMutation = usePersonalDocumentDownloadUrl();

  const personal = useMemo(() => data?.data ?? [], [data?.data]);
  const filteredPersonal = useMemo(() => {
    if (!q.trim()) return personal;
    const n = q.toLowerCase();
    return personal.filter(
      (d) =>
        d.title.toLowerCase().includes(n) ||
        d.documentTypeCode.toLowerCase().includes(n) ||
        d.mimeType.toLowerCase().includes(n),
    );
  }, [personal, q]);

  const recentDocs = useMemo(() => {
    return recentItems.filter(
      (r) =>
        r.refKey.includes("document") ||
        r.href.includes("document") ||
        r.title.toLowerCase().includes("document"),
    );
  }, [recentItems]);

  const unifiedCatalog: FileResource[] = useMemo(() => {
    const fromPersonal = personalDocumentsToFileResources(personal);
    const fromRecent = recentItemsToFileResources(recentItems);
    const fromApis = [
      ...(catalogQ.data?.reportRuns ?? []),
      ...(catalogQ.data?.facilityCertificates ?? []),
    ];
    return mergeFileResources(mergeFileResources(fromPersonal, fromRecent), fromApis);
  }, [personal, recentItems, catalogQ.data]);

  const filteredUnified = useMemo(() => {
    if (!q.trim()) return unifiedCatalog;
    const n = q.toLowerCase();
    return unifiedCatalog.filter(
      (r) =>
        r.title.toLowerCase().includes(n) ||
        (r.category ?? "").toLowerCase().includes(n) ||
        (r.associatedApp ?? "").toLowerCase().includes(n) ||
        (r.resourceType ?? "").toLowerCase().includes(n),
    );
  }, [unifiedCatalog, q]);

  const canClinical = matchesRequiredRole(hasRole, "CLINICAL");

  async function handleDownload(doc: PersonalDocumentResource) {
    if (!doc.id) return;
    try {
      const res = await downloadMutation.mutateAsync(doc.id);
      const url = res.data.url;
      if (url) window.open(url, "_blank", "noopener,noreferrer");
      recordRecent({
        kind: "resource",
        title: `Download · ${doc.title}`,
        subtitle: doc.documentTypeCode,
        href: url ?? "/home/documents",
        refKey: `download:${doc.id}`,
        sensitivity: "normal",
      });
    } catch {
      /* handled by UI */
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="inline-flex rounded-lg border border-border bg-card p-0.5 dark:border-border dark:bg-neutral-900">
          {(
            [
              ["all", "All sources"],
              ["personal", "Personal vault"],
              ["recent", "Recent"],
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              type="button"
              onClick={() => setSource(key)}
              className={`rounded-md px-3 py-1.5 text-xs font-medium ${
                source === key
                  ? "bg-primary-hover text-white"
                  : "text-muted-foreground hover:bg-background dark:text-muted-foreground dark:hover:bg-neutral-900"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <label className="relative block md:w-80">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Filter by title, type, mime…"
            className="w-full rounded-lg border border-border py-2 pl-9 pr-3 text-sm dark:border-border dark:bg-card"
          />
        </label>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Link
          href="/home/documents"
          className="rounded-xl border border-border bg-card p-4 shadow-sm transition hover:border-impilo-300 dark:border-border dark:bg-neutral-900"
        >
          <FileText className="mb-2 h-6 w-6 text-primary" />
          <h3 className="text-sm font-semibold text-foreground dark:text-foreground">My documents vault</h3>
          <p className="mt-1 text-xs text-muted-foreground">Canonical citizen/personal vault via Experience bridge.</p>
        </Link>
        <Link
          href="/search"
          className="rounded-xl border border-border bg-card p-4 shadow-sm transition hover:border-impilo-300 dark:border-border dark:bg-neutral-900"
        >
          <Search className="mb-2 h-6 w-6 text-violet-500" />
          <h3 className="text-sm font-semibold text-foreground dark:text-foreground">Knowledge & index</h3>
          <p className="mt-1 text-xs text-muted-foreground">Search governed platform index (BFF → search-service).</p>
        </Link>
        {canClinical ? (
          <Link
            href="/queue/search"
            className="rounded-xl border border-border bg-card p-4 shadow-sm transition hover:border-impilo-300 dark:border-border dark:bg-neutral-900"
          >
            <ExternalLink className="mb-2 h-6 w-6 text-teal-600" />
            <h3 className="text-sm font-semibold text-foreground dark:text-foreground">Patient chart documents</h3>
            <p className="mt-1 text-xs text-muted-foreground">Open a patient, then use chart → Documents (BUTANO / PCT).</p>
          </Link>
        ) : (
          <div className="rounded-xl border border-dashed border-border p-4 text-xs text-muted-foreground dark:border-border">
            Patient-chart documents require clinical roles and facility context.
          </div>
        )}
      </div>

      {source === "all" ? (
        <section>
          <h2 className="mb-2 text-sm font-semibold text-foreground dark:text-foreground">
            Unified catalog (personal + shell recents + BFF catalog)
          </h2>
          <p className="mb-3 text-xs text-muted-foreground">
            Includes report runs and facility certificates from the Experience BFF file catalog when authorized; vault
            downloads still use Prepare below.
          </p>
          {catalogQ.isLoading ? (
            <p className="mb-2 flex items-center gap-2 text-xs text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading BFF file catalog…
            </p>
          ) : null}
          {catalogQ.isError ? (
            <p className="mb-2 text-xs text-warning-foreground dark:text-amber-400">
              File catalog bridge unavailable (reports or facility registry may be down).
            </p>
          ) : null}
          {filteredUnified.length === 0 ? (
            <p className="text-xs text-muted-foreground">No catalog rows yet — open documents or download from your vault.</p>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border bg-card dark:border-border dark:bg-neutral-900">
              <table className="min-w-full text-left text-sm">
                <thead className="border-b border-border bg-background text-xs uppercase text-muted-foreground dark:border-border dark:bg-card">
                  <tr>
                    <th className="px-3 py-2">Title</th>
                    <th className="px-3 py-2">Type</th>
                    <th className="px-3 py-2">Source</th>
                    <th className="px-3 py-2 text-right">Open</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {filteredUnified.map((r) => (
                    <tr key={r.id}>
                      <td className="px-3 py-2 font-medium text-foreground dark:text-foreground">{r.title}</td>
                      <td className="px-3 py-2 text-muted-foreground">{r.resourceType}</td>
                      <td className="px-3 py-2 text-muted-foreground">{r.associatedApp ?? "—"}</td>
                      <td className="px-3 py-2 text-right">
                        {r.href ? (
                          <Link href={r.href} className="text-xs font-medium text-primary hover:underline">
                            Open
                          </Link>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}

      {source === "recent" || source === "all" ? (
        <section>
          <h2 className="mb-2 text-sm font-semibold text-foreground dark:text-foreground">Shell recent (document-like)</h2>
          {recentDocs.length === 0 ? (
            <p className="text-xs text-muted-foreground">No recent document activity recorded in this session.</p>
          ) : (
            <ul className="divide-y divide-slate-100 rounded-lg border border-border bg-card dark:divide-slate-800 dark:border-border dark:bg-neutral-900">
              {recentDocs.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-2 px-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground dark:text-foreground">{r.title}</p>
                    <p className="truncate text-xs text-muted-foreground">{r.subtitle}</p>
                  </div>
                  <Link href={r.href} className="shrink-0 text-xs font-medium text-primary hover:underline">
                    Open
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}

      {source === "personal" || source === "all" ? (
        <section>
          <h2 className="mb-2 text-sm font-semibold text-foreground dark:text-foreground">Personal vault (bridge)</h2>
          {isLoading ? (
            <div className="flex items-center gap-2 py-8 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" /> Loading documents…
            </div>
          ) : isError ? (
            <p className="text-sm text-red-600">Could not load documents. Confirm BFF and clinical-tools bridge.</p>
          ) : filteredPersonal.length === 0 ? (
            <p className="text-sm text-muted-foreground">No documents match the current filter.</p>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border bg-card dark:border-border dark:bg-neutral-900">
              <table className="min-w-full text-left text-sm">
                <thead className="border-b border-border bg-background text-xs uppercase text-muted-foreground dark:border-border dark:bg-card">
                  <tr>
                    <th className="px-3 py-2">Title</th>
                    <th className="px-3 py-2">Type</th>
                    <th className="px-3 py-2">State</th>
                    <th className="px-3 py-2 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {filteredPersonal.map((doc) => (
                      <tr key={doc.id || doc.title}>
                        <td className="px-3 py-2 font-medium text-foreground dark:text-foreground">{doc.title}</td>
                        <td className="px-3 py-2 text-muted-foreground">{doc.documentTypeCode}</td>
                        <td className="px-3 py-2 text-muted-foreground">{doc.lifecycleState}</td>
                        <td className="px-3 py-2 text-right">
                          <button
                            type="button"
                            disabled={!doc.id || downloadMutation.isPending}
                            onClick={() => handleDownload(doc)}
                            className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs font-medium hover:bg-background disabled:opacity-50 dark:border-border dark:hover:bg-neutral-900"
                          >
                            <Download className="h-3.5 w-3.5" />
                            Prepare
                          </button>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}
    </div>
  );
}
