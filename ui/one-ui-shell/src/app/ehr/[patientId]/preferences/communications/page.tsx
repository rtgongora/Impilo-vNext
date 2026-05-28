"use client";

/**
 * Communication preferences (Mvumo) — EHR view of the versioned preference bundle.
 * Citizen portal / mobile may offer richer self-service; this route proves BFF access to the internal API.
 */

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Loader2, MessageCircle } from "lucide-react";
import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type PrefResponse = {
  exists?: boolean;
  patientRef?: string;
  revisionId?: string;
  bundleVersion?: number;
  lifecycleState?: string;
  payload?: Record<string, unknown>;
};

function patientKey(cpid: string) {
  return `Patient/${cpid}`;
}

export default function CommunicationPreferencesPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const pkey = patientKey(patientId);
  const pathSeg = encodeURIComponent(pkey);

  const q = useQuery({
    queryKey: ["mvumo", "communication-preferences", pkey],
    queryFn: () =>
      apiClient.get<ApiResponse<PrefResponse>>(
        `/internal/v1/mvumo/patients/${pathSeg}/communication-preferences`,
      ),
    enabled: !!patientId,
  });

  const data = q.data?.data;

  return (
    <PageShell
      title="Communication preferences"
      subtitle="Versioned communication, reminder, and follow-up preferences from Mvumo (read via Experience BFF)."
    >
      <div className="space-y-4 max-w-3xl">
        <Link
          href={`/ehr/${patientId}/summary#consent-preferences`}
          className="text-sm text-impilo-600 hover:underline"
        >
          Back to Consent, Preferences &amp; Directives
        </Link>

        {q.isLoading && (
          <div className="flex items-center gap-2 text-slate-600">
            <Loader2 className="h-4 w-4 animate-spin" />
            Loading preferences…
          </div>
        )}

        {q.isError && (
          <p className="text-sm text-red-700">
            Could not load preferences. If Mvumo is down, the summary will still list consent context only.
          </p>
        )}

        {data && (
          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="mb-2 flex items-center gap-2">
              <MessageCircle className="h-4 w-4 text-slate-500" />
              <span className="text-sm font-medium text-slate-900">Current bundle</span>
            </div>
            {data.exists === false ? (
              <p className="text-sm text-slate-600">No versioned profile stored yet. Saving from intake, portal, or an assisted update will create revision 1.</p>
            ) : (
              <ul className="space-y-1 text-sm text-slate-800">
                <li>
                  <span className="text-slate-500">Lifecycle: </span>
                  {data.lifecycleState ?? "—"}
                </li>
                <li>
                  <span className="text-slate-500">Bundle version: </span>
                  {data.bundleVersion ?? "—"}
                </li>
                <li>
                  <span className="text-slate-500">Revision: </span>
                  <code className="text-xs">{data.revisionId ?? "—"}</code>
                </li>
                <li className="pt-2">
                  <span className="text-slate-500">Payload (structured JSON):</span>
                  <pre className="mt-1 max-h-80 overflow-auto rounded border border-slate-100 bg-slate-50 p-2 text-xs">
                    {JSON.stringify(data.payload ?? {}, null, 2)}
                  </pre>
                </li>
              </ul>
            )}
          </div>
        )}
      </div>
    </PageShell>
  );
}
