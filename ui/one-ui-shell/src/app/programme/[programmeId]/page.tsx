"use client";

/**
 * Programme profile — destination for Work Home PROGRAMME_MANAGEMENT section items.
 * Route: /programme/[programmeId]
 *
 * Reads the WGV programme registry record via the BFF workforce-governance proxy.
 * Not a clinical programme enrolment surface (those live under /internal/v1/programmes).
 */

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface ProgrammeAttrs {
  id?: string;
  programmeId?: string;
  name?: string;
  code?: string;
  programmeType?: string;
  status?: string;
  description?: string;
}

function unwrapProgramme(payload: unknown): ProgrammeAttrs | null {
  const data = (payload as { data?: ProgrammeAttrs | { attributes?: ProgrammeAttrs } })?.data;
  if (!data || typeof data !== "object") return null;
  if ("attributes" in data && data.attributes) return data.attributes;
  return data as ProgrammeAttrs;
}

export default function ProgrammeProfilePage() {
  const params = useParams<{ programmeId: string }>();
  const programmeId = decodeURIComponent(String(params?.programmeId ?? ""));

  const { data, isLoading, isError } = useQuery({
    queryKey: ["wgv-programme", programmeId],
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/workforce-governance/programmes/${encodeURIComponent(programmeId)}`,
      ),
    enabled: !!programmeId,
  });

  const programme = unwrapProgramme(data);
  const title =
    programme?.name ?? programme?.code ?? (programmeId ? `Programme ${programmeId}` : "Programme");

  return (
    <AppLayout>
      <PageShell title={title} subtitle="Programme registry profile" density="compact">
        <div className="mb-4">
          <Link
            href="/work"
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Back to Work Home
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading programme…
          </div>
        ) : isError || !programme ? (
          <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
            Programme not found in the governance registry, or the registry is temporarily
            unavailable.
          </div>
        ) : (
          <div className="rounded-lg border border-border bg-card p-5 space-y-3">
            <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 text-sm">
              <div>
                <dt className="text-xs text-muted-foreground">Code</dt>
                <dd className="font-medium text-foreground">{programme.code ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Type</dt>
                <dd className="font-medium text-foreground">{programme.programmeType ?? "UNKNOWN"}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Status</dt>
                <dd className="font-medium text-foreground">{programme.status ?? "UNKNOWN"}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Id</dt>
                <dd className="font-mono text-xs text-foreground">
                  {programme.id ?? programme.programmeId ?? programmeId}
                </dd>
              </div>
            </dl>
            {programme.description ? (
              <p className="text-sm text-muted-foreground">{programme.description}</p>
            ) : null}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
