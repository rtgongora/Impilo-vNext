"use client";

/**
 * Term Detail — View terminology concept with relationships and mappings.
 * Route: /registry/terminology/[id]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft, BookOpen, Link2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface TermDetail {
  id: string;
  type: "terminology";
  attributes: {
    code: string;
    display: string;
    system: string;
    version: string;
    definition: string;
    relationships: Array<{
      type: string;
      targetCode: string;
      targetDisplay: string;
      targetSystem: string;
    }>;
    mappings: Array<{
      targetSystem: string;
      targetCode: string;
      targetDisplay: string;
      equivalence: string;
    }>;
    [key: string]: unknown;
  };
}

export default function TermDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useQuery<ApiResponse<TermDetail>>({
    queryKey: ["terminology", id],
    queryFn: () => apiClient.get<ApiResponse<TermDetail>>(`/internal/v1/registry/terminology/${id}`),
    enabled: !!id,
  });

  const term = data?.data;

  return (
    <AppLayout>
      <PageShell title="Concept Details" subtitle="Terminology concept with relationships">
        <div className="mb-4">
          <Link
            href="/registry/terminology"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Terminology
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading concept...</span>
          </div>
        ) : error || !term ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load concept</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            {/* Concept Info */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start gap-3 mb-4">
                <div className="w-10 h-10 rounded-lg bg-purple-100 flex items-center justify-center shrink-0">
                  <BookOpen className="w-5 h-5 text-purple-600" />
                </div>
                <div>
                  <h3 className="font-medium text-gray-900">{term.attributes.display}</h3>
                  <p className="text-sm text-gray-500">
                    {term.attributes.system} &middot; {term.attributes.code}
                  </p>
                </div>
              </div>
              <dl className="space-y-3 text-sm">
                <div>
                  <dt className="text-gray-500">Definition</dt>
                  <dd className="text-gray-900 mt-0.5">
                    {term.attributes.definition || "No definition available"}
                  </dd>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <dt className="text-gray-500">System</dt>
                    <dd className="font-medium text-gray-900 mt-0.5">{term.attributes.system}</dd>
                  </div>
                  <div>
                    <dt className="text-gray-500">Version</dt>
                    <dd className="font-medium text-gray-900 mt-0.5">{term.attributes.version}</dd>
                  </div>
                </div>
              </dl>
            </div>

            {/* Relationships */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
                <Link2 className="w-4 h-4" /> Relationships
              </h3>
              {(term.attributes.relationships?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {term.attributes.relationships.map((rel, i) => (
                    <div key={i} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                      <div>
                        <p className="text-sm font-medium text-gray-900">{rel.targetDisplay}</p>
                        <p className="text-xs text-gray-500">
                          {rel.targetSystem} &middot; {rel.targetCode}
                        </p>
                      </div>
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-impilo-100 text-impilo-600 font-medium">
                        {rel.type}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400">No relationships found</p>
              )}
            </div>

            {/* Mappings */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3">Cross-System Mappings</h3>
              {(term.attributes.mappings?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {term.attributes.mappings.map((mapping, i) => (
                    <div key={i} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                      <div>
                        <p className="text-sm font-medium text-gray-900">{mapping.targetDisplay}</p>
                        <p className="text-xs text-gray-500">
                          {mapping.targetSystem} &middot; {mapping.targetCode}
                        </p>
                      </div>
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-green-100 text-green-700 font-medium">
                        {mapping.equivalence}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400">No mappings found</p>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
