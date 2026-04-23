"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Download, ExternalLink, FileText, Loader2, Search } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import {
  usePersonalDocumentDownloadUrl,
  usePersonalDocuments,
  type PersonalDocumentResource,
} from "@/hooks/queries/usePersonalDocuments";
import { matchesRequiredRole } from "@/lib/auth/role-groups";

export function ShellFileExplorer() {
  const hasRole = useAuthStore((s) => s.hasRole);
  const recentItems = useShellStore((s) => s.recentItems);
  const recordRecent = useShellStore((s) => s.recordRecent);
  const [q, setQ] = useState("");
  const [source, setSource] = useState<"all" | "personal" | "recent">("all");

  const { data, isLoading, isError } = usePersonalDocuments();
  const downloadMutation = usePersonalDocumentDownloadUrl();

  const personal = data?.data ?? [];
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
        <div className="inline-flex rounded-lg border border-slate-200 bg-white p-0.5 dark:border-slate-700 dark:bg-slate-900">
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
                  ? "bg-impilo-600 text-white"
                  : "text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <label className="relative block md:w-80">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Filter by title, type, mime…"
            className="w-full rounded-lg border border-slate-200 py-2 pl-9 pr-3 text-sm dark:border-slate-700 dark:bg-slate-950"
          />
        </label>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Link
          href="/home/documents"
          className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-impilo-300 dark:border-slate-700 dark:bg-slate-900"
        >
          <FileText className="mb-2 h-6 w-6 text-impilo-500" />
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">My documents vault</h3>
          <p className="mt-1 text-xs text-slate-500">Canonical citizen/personal vault via Experience bridge.</p>
        </Link>
        <Link
          href="/search"
          className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-impilo-300 dark:border-slate-700 dark:bg-slate-900"
        >
          <Search className="mb-2 h-6 w-6 text-violet-500" />
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">Knowledge & index</h3>
          <p className="mt-1 text-xs text-slate-500">Search governed platform index (BFF → search-service).</p>
        </Link>
        {canClinical ? (
          <Link
            href="/queue/search"
            className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-impilo-300 dark:border-slate-700 dark:bg-slate-900"
          >
            <ExternalLink className="mb-2 h-6 w-6 text-teal-600" />
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-50">Patient chart documents</h3>
            <p className="mt-1 text-xs text-slate-500">Open a patient, then use chart → Documents (BUTANO / PCT).</p>
          </Link>
        ) : (
          <div className="rounded-xl border border-dashed border-slate-200 p-4 text-xs text-slate-500 dark:border-slate-700">
            Patient-chart documents require clinical roles and facility context.
          </div>
        )}
      </div>

      {source === "recent" || source === "all" ? (
        <section>
          <h2 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-100">Shell recent (document-like)</h2>
          {recentDocs.length === 0 ? (
            <p className="text-xs text-slate-500">No recent document activity recorded in this session.</p>
          ) : (
            <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white dark:divide-slate-800 dark:border-slate-800 dark:bg-slate-900">
              {recentDocs.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-2 px-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-800 dark:text-slate-100">{r.title}</p>
                    <p className="truncate text-xs text-slate-500">{r.subtitle}</p>
                  </div>
                  <Link href={r.href} className="shrink-0 text-xs font-medium text-impilo-600 hover:underline">
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
          <h2 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-100">Personal vault (bridge)</h2>
          {isLoading ? (
            <div className="flex items-center gap-2 py-8 text-slate-500">
              <Loader2 className="h-5 w-5 animate-spin" /> Loading documents…
            </div>
          ) : isError ? (
            <p className="text-sm text-red-600">Could not load documents. Confirm BFF and clinical-tools bridge.</p>
          ) : filteredPersonal.length === 0 ? (
            <p className="text-sm text-slate-500">No documents match the current filter.</p>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
              <table className="min-w-full text-left text-sm">
                <thead className="border-b border-slate-100 bg-slate-50 text-xs uppercase text-slate-500 dark:border-slate-800 dark:bg-slate-950">
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
                        <td className="px-3 py-2 font-medium text-slate-900 dark:text-slate-50">{doc.title}</td>
                        <td className="px-3 py-2 text-slate-600">{doc.documentTypeCode}</td>
                        <td className="px-3 py-2 text-slate-600">{doc.lifecycleState}</td>
                        <td className="px-3 py-2 text-right">
                          <button
                            type="button"
                            disabled={!doc.id || downloadMutation.isPending}
                            onClick={() => handleDownload(doc)}
                            className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs font-medium hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:hover:bg-slate-800"
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
