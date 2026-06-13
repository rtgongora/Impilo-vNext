"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { REGISTRY_ENTITY_COLUMNS } from "@/lib/json-api/generic-table-columns";
/**
 * Terminology Browser — Resolve ZIBO canonical artifacts.
 * Route: /registry/terminology
 */

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, AlertTriangle, BookOpen, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function TerminologyBrowserPage() {
  const searchParams = useSearchParams();
  const fromRegistryAdmin = searchParams.get("from") === "registry-admin";
  const [canonicalUrl, setCanonicalUrl] = useState("http://impilo.health/CodeSystem/facility-type");
  const [version, setVersion] = useState("");
  const [submitted, setSubmitted] = useState<{ canonicalUrl: string; version?: string } | null>(null);

  const { data, isLoading, error } = useQuery<ApiResponse<unknown>>({
    queryKey: ["terminology", "zibo-resolve", submitted],
    queryFn: () => {
      const params = new URLSearchParams();
      params.set("canonicalUrl", submitted?.canonicalUrl ?? "");
      if (submitted?.version) params.set("version", submitted.version);
      return apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/registry/zibo/artifacts/resolve?${params.toString()}`,
      );
    },
    enabled: Boolean(submitted?.canonicalUrl),
  });

  function handleResolve() {
    const trimmed = canonicalUrl.trim();
    if (!trimmed) return;
    setSubmitted({ canonicalUrl: trimmed, version: version.trim() || undefined });
  }

  return (
    <AppLayout>
      <PageShell
        title="Terminology Browser"
        subtitle="Resolve ZIBO terminology artifacts by canonical URL and optional version"
        serviceSlug="zibo"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href={fromRegistryAdmin ? "/registry-admin" : "/registry"}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromRegistryAdmin ? "Back to registry administration" : "Back to registry hub"}
          </Link>
        </div>
        <div className="mb-6 rounded-2xl border border-purple-100 bg-card p-5">
          <p className="mb-3 text-sm text-muted-foreground">
            The canonical Experience BFF exposes ZIBO artifact resolve, not a free-text terminology search. Use a
            canonical CodeSystem or ValueSet URL below.
          </p>
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Canonical URL"
              value={canonicalUrl}
              onChange={(e) => setCanonicalUrl(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleResolve()}
              className="w-full pl-10 pr-4 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
            />
          </div>
          <div className="mt-3 flex flex-wrap gap-3">
            <input
              type="text"
              placeholder="Version optional"
              value={version}
              onChange={(e) => setVersion(e.target.value)}
              className="min-w-[220px] rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
            />
            <button
              type="button"
              onClick={handleResolve}
              disabled={!canonicalUrl.trim()}
              className="px-4 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-hover disabled:opacity-50"
            >
              Resolve artifact
            </button>
            {submitted?.canonicalUrl ? (
              <Link
                href={`/registry/terminology/${encodeURIComponent(submitted.canonicalUrl)}${
                  submitted.version ? `?version=${encodeURIComponent(submitted.version)}` : ""
                }`}
                className="rounded-lg border border-warning/35 px-4 py-2 text-sm font-medium text-warning-foreground hover:bg-warning-soft"
              >
                Open detail route
              </Link>
            ) : null}
          </div>
        </div>

        {!submitted ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <BookOpen className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">Enter a canonical URL to resolve a ZIBO artifact</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Resolving terminology artifact...</span>
          </div>
        ) : error ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to resolve terminology artifact</p>
          </div>
        ) : (
          <div className="rounded-2xl border border-border bg-card p-5">
            <div className="mb-3">
              <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Resolved artifact</p>
              <h2 className="mt-1 font-mono text-sm font-semibold text-foreground">{submitted.canonicalUrl}</h2>
              {submitted.version ? <p className="text-xs text-muted-foreground">Version {submitted.version}</p> : null}
            </div>
            <JsonApiDataTable data={data?.data ?? {}} columns={REGISTRY_ENTITY_COLUMNS} emptyTitle="No artifact fields" />
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
