"use client";

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

  function handleSearch() {
    const trimmed = canonicalUrl.trim();
    if (trimmed) setSubmitted({ canonicalUrl: trimmed, version: version.trim() || undefined });
  }

  return (
    <AppLayout>
      <PageShell title="Terminology Browser" subtitle="Resolve ZIBO terminology artifacts">
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href={fromRegistryAdmin ? "/registry-admin" : "/registry"}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromRegistryAdmin ? "Back to registry administration" : "Back to registry hub"}
          </Link>
        </div>
        <div className="mb-6 rounded-2xl border border-purple-100 bg-white p-5">
          <p className="mb-3 text-sm text-gray-600">
            The canonical Experience BFF exposes ZIBO artifact resolve, not a free-text terminology search.
          </p>
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Canonical URL"
              value={canonicalUrl}
              onChange={(e) => setCanonicalUrl(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
            />
          </div>
          <div className="mt-3 flex flex-wrap gap-3">
            <input
              type="text"
              placeholder="Version optional"
              value={version}
              onChange={(e) => setVersion(e.target.value)}
              className="min-w-[220px] rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
            />
            <button
              onClick={handleSearch}
              className="px-4 py-2 bg-impilo-500 text-white rounded-lg text-sm font-medium hover:bg-impilo-600"
            >
              Resolve artifact
            </button>
          </div>
        </div>

        {!submitted ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <BookOpen className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Enter a canonical URL to resolve a ZIBO artifact</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Resolving terminology artifact...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to resolve terminology artifact</p>
          </div>
        ) : (
          <div className="rounded-2xl border border-gray-200 bg-white p-5">
            <p className="text-xs uppercase tracking-[0.18em] text-gray-500">Resolved artifact</p>
            <h2 className="mt-1 break-all font-mono text-sm font-semibold text-gray-900">{submitted.canonicalUrl}</h2>
            <pre className="mt-3 max-h-[520px] overflow-auto rounded-lg bg-slate-950 p-4 text-xs text-slate-100">
              {JSON.stringify(data?.data ?? {}, null, 2)}
            </pre>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
