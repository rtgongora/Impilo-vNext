"use client";

/**
 * Terminology Browser — Search ICD, SNOMED, and drug codes.
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

interface TermResource {
  id: string;
  type: "terminology";
  attributes: {
    code: string;
    display: string;
    system: string;
    version: string;
    [key: string]: unknown;
  };
}

const SYSTEMS = [
  { value: "", label: "All Systems" },
  { value: "ICD-10", label: "ICD-10" },
  { value: "SNOMED-CT", label: "SNOMED CT" },
  { value: "LOINC", label: "LOINC" },
  { value: "RxNorm", label: "RxNorm" },
];

export default function TerminologyBrowserPage() {
  const searchParams = useSearchParams();
  const fromRegistryAdmin = searchParams.get("from") === "registry-admin";
  const [searchTerm, setSearchTerm] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [system, setSystem] = useState("");

  const { data, isLoading, error } = useQuery<ApiResponse<TermResource[]>>({
    queryKey: ["terminology", { search: searchQuery, system }],
    queryFn: () => {
      const params = new URLSearchParams();
      if (searchQuery) params.set("search", searchQuery);
      if (system) params.set("system", system);
      const qs = params.toString();
      return apiClient.get<ApiResponse<TermResource[]>>(
        `/internal/v1/registry/terminology${qs ? `?${qs}` : ""}`,
      );
    },
    enabled: !!searchQuery,
  });

  const terms = data?.data ?? [];

  function handleSearch() {
    setSearchQuery(searchTerm);
  }

  return (
    <AppLayout>
      <PageShell title="Terminology Browser" subtitle="Search ICD codes, SNOMED concepts, and drug codes">
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
        {/* Search */}
        <div className="mb-6 flex gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search codes or terms..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <select
            value={system}
            onChange={(e) => setSystem(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {SYSTEMS.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
          <button
            onClick={handleSearch}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
          >
            Search
          </button>
        </div>

        {!searchQuery ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <BookOpen className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Enter a search term to browse terminology</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Searching terminology...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to search terminology</p>
          </div>
        ) : terms.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <BookOpen className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No results found for &quot;{searchQuery}&quot;</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Code</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Display</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">System</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Version</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Action</th>
                </tr>
              </thead>
              <tbody>
                {terms.map((term) => (
                  <tr key={term.id} className="border-b last:border-b-0 hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs text-gray-700">
                      {term.attributes.code}
                    </td>
                    <td className="px-4 py-3 font-medium text-gray-900">{term.attributes.display}</td>
                    <td className="px-4 py-3 text-gray-600">
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-purple-100 text-purple-700 font-medium">
                        {term.attributes.system}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-600">{term.attributes.version}</td>
                    <td className="px-4 py-3 text-right">
                      <Link
                        href={`/registry/terminology/${term.id}`}
                        className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                      >
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
