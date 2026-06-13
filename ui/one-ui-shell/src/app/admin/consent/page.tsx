"use client";

/**
 * Consent Management — Patient consent directives table.
 * Route: /admin/consent | pageTitle: "Consent Management"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, HeartHandshake, AlertCircle } from "lucide-react";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface ConsentResource {
  id: string;
  type: "consent";
  attributes: {
    patientName: string;
    consentType: string;
    status: string;
    validFrom: string;
    validTo: string;
    lastUpdated: string;
    [key: string]: unknown;
  };
}

type ConsentResponse = ApiResponse<ConsentResource[]>;

function useSubjectConsents(subjectId: string) {
  return useQuery<ConsentResponse>({
    queryKey: ["admin-consent", subjectId],
    queryFn: () =>
      apiClient.get<ConsentResponse>(
        `/internal/v1/admin/trust/consents?subjectId=${encodeURIComponent(subjectId)}&size=20`,
      ),
    enabled: subjectId.trim().length > 0,
  });
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  REVOKED: "bg-red-100 text-danger",
  EXPIRED: "bg-neutral-100 text-muted-foreground",
  PENDING: "bg-yellow-100 text-yellow-700",
};

export default function ConsentPage() {
  const searchParams = useSearchParams();
  const fromRegistryAdmin = searchParams.get("from") === "registry-admin";
  const [subjectInput, setSubjectInput] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const { data, isLoading, error } = useSubjectConsents(subjectId);

  const consents = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Consent Management"
        subtitle="Manage patient consent directives"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href={fromRegistryAdmin ? "/registry/trust" : "/admin"}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromRegistryAdmin ? "Back to trust hub" : "Back to administration"}
          </Link>
        </div>

        <div className="mb-5 rounded-2xl border border-pink-100 bg-card p-4">
          <p className="mb-3 text-sm text-muted-foreground">
            Consent governance is subject-scoped through <code>/internal/v1/admin/trust/consents</code>. Enter a CPID,
            Health ID, or subject reference to load directives.
          </p>
          <div className="flex flex-wrap gap-3">
            <input
              value={subjectInput}
              onChange={(event) => setSubjectInput(event.target.value)}
              onKeyDown={(event) => event.key === "Enter" && setSubjectId(subjectInput.trim())}
              placeholder="Subject ID"
              className="min-w-[260px] rounded-lg border border-border px-3 py-2 text-sm"
            />
            <button
              type="button"
              disabled={!subjectInput.trim()}
              onClick={() => setSubjectId(subjectInput.trim())}
              className="rounded-lg bg-pink-600 px-4 py-2 text-sm font-medium text-white hover:bg-pink-700 disabled:opacity-50"
            >
              Load consents
            </button>
          </div>
        </div>

        {!subjectId ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <HeartHandshake className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">Enter a subject ID to load consent records</p>
          </div>
        ) : error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load consent records</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading consent records...</span>
          </div>
        ) : consents.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <HeartHandshake className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No consent records</p>
          </div>
        ) : (
          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Patient Name</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Consent Type</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Valid From</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Valid To</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Last Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {consents.map((consent) => {
                  const statusStyle =
                    STATUS_STYLES[consent.attributes.status] ?? "bg-neutral-100 text-muted-foreground";
                  return (
                    <tr key={consent.id} className="hover:bg-background transition-colors">
                      <td className="px-4 py-3 font-medium text-foreground">
                        {consent.attributes.patientName}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-pink-100 text-pink-700">
                          {consent.attributes.consentType}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {consent.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                        {new Date(consent.attributes.validFrom).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                        {new Date(consent.attributes.validTo).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                        {new Date(consent.attributes.lastUpdated).toLocaleString()}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
