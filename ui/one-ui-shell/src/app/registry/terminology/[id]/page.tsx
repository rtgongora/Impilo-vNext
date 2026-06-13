"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { REGISTRY_ENTITY_COLUMNS } from "@/lib/json-api/generic-table-columns";
/**
 * Term Detail — Resolve a ZIBO terminology artifact by canonical URL.
 * Route: /registry/terminology/[id]
 */

import { useParams } from "next/navigation";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft, BookOpen } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function TermDetailPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const id = params.id as string;
  const canonicalUrl = decodeURIComponent(id);
  const version = searchParams.get("version") ?? undefined;

  const { data, isLoading, error } = useQuery<ApiResponse<unknown>>({
    queryKey: ["terminology", "zibo-resolve", canonicalUrl, version],
    queryFn: () => {
      const query = new URLSearchParams({ canonicalUrl });
      if (version) query.set("version", version);
      return apiClient.get<ApiResponse<unknown>>(`/internal/v1/registry/zibo/artifacts/resolve?${query.toString()}`);
    },
    enabled: Boolean(canonicalUrl),
  });

  return (
    <AppLayout>
      <PageShell title="Terminology Artifact" subtitle="ZIBO canonical artifact resolution">
        <div className="mb-4">
          <Link
            href="/registry/terminology"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Terminology
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Resolving artifact...</span>
          </div>
        ) : error ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to resolve terminology artifact</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            <div className="bg-card rounded-lg border border-border p-5">
              <div className="flex items-start gap-3 mb-4">
                <div className="w-10 h-10 rounded-lg bg-purple-100 flex items-center justify-center shrink-0">
                  <BookOpen className="w-5 h-5 text-purple-600" />
                </div>
                <div>
                  <h3 className="font-medium text-foreground">Resolved ZIBO artifact</h3>
                  <p className="break-all font-mono text-sm text-muted-foreground">{canonicalUrl}</p>
                  {version ? <p className="text-xs text-muted-foreground">Version {version}</p> : null}
                </div>
              </div>
              <p className="text-xs text-muted-foreground">
                Loaded from <code>/internal/v1/registry/zibo/artifacts/resolve</code>. Free-text terminology search is
                intentionally not shown until the Experience BFF exposes a canonical search contract.
              </p>
            </div>
            <div className="rounded-lg border border-border bg-card p-5">
              <JsonApiDataTable data={data?.data ?? {}} columns={REGISTRY_ENTITY_COLUMNS} emptyTitle="No artifact fields" />
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
