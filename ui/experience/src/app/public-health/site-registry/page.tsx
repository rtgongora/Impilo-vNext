"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { ExternalLink, Loader2, Search } from "lucide-react";
import { useSiteRegistrySites } from "@/hooks/queries/useSiteRegistry";

export default function SiteRegistryListPage() {
  const [search, setSearch] = useState("");
  const q = useSiteRegistrySites({ search, page: 0, size: 50 });

  const rows = useMemo(() => q.data ?? [], [q.data]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Public Health Site Registry</h1>
          <p className="text-sm text-gray-500">
            Non-facility sites (premises) lifecycle: applications, inspections, compliance, licensing.
          </p>
        </div>
        <Link href="/public-health" className="text-sm text-impilo-600 hover:underline">
          Back to Public Health
        </Link>
      </div>

      <div className="rounded-lg border bg-white p-3">
        <div className="flex items-center gap-2">
          <Search className="h-4 w-4 text-gray-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name or site code…"
            className="w-full rounded-md border px-3 py-2 text-sm"
          />
        </div>
      </div>

      <div className="rounded-lg border bg-white">
        <div className="border-b px-4 py-3">
          <div className="flex items-center justify-between">
            <div className="text-sm font-medium text-gray-900">Sites</div>
            {q.isLoading && (
              <div className="flex items-center gap-2 text-xs text-gray-500">
                <Loader2 className="h-4 w-4 animate-spin" />
                Loading…
              </div>
            )}
          </div>
        </div>

        {q.isError ? (
          <div className="p-4 text-sm text-red-600">Failed to load site registry.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                <tr>
                  <th className="px-4 py-3 text-left">Site</th>
                  <th className="px-4 py-3 text-left">Category</th>
                  <th className="px-4 py-3 text-left">Jurisdiction</th>
                  <th className="px-4 py-3 text-left">Regulatory</th>
                  <th className="px-4 py-3 text-left">Licence</th>
                  <th className="px-4 py-3 text-right">Open</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {rows.map((r) => (
                  <tr key={r.siteId} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <div className="font-medium text-gray-900">{r.name}</div>
                      <div className="text-xs text-gray-500">{r.siteCode || r.siteId}</div>
                    </td>
                    <td className="px-4 py-3">{r.siteCategory || "—"}</td>
                    <td className="px-4 py-3">
                      <div>{r.province || "—"}</div>
                      <div className="text-xs text-gray-500">{r.district || ""}</div>
                    </td>
                    <td className="px-4 py-3">{r.regulatoryStatus || "—"}</td>
                    <td className="px-4 py-3">
                      <div>{r.licenceStatus || "—"}</div>
                      <div className="text-xs text-gray-500">{r.licenceExpiryDate ? `Expiry: ${r.licenceExpiryDate}` : ""}</div>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Link
                        href={`/public-health/site-registry/${encodeURIComponent(r.siteId)}`}
                        className="inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs font-medium text-gray-700 hover:bg-white"
                      >
                        <ExternalLink className="h-3.5 w-3.5" />
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
                {rows.length === 0 && !q.isLoading ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-500">
                      No sites yet. Create an application from the site profile workflow once you have Indawo sites registered.
                      <div className="mt-2 text-xs">
                        Tip: existing Indawo sites list is at{" "}
                        <Link href="/public-health?tab=sites" className="text-impilo-600 hover:underline">
                          Public Health → Sites
                        </Link>
                        .
                      </div>
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

