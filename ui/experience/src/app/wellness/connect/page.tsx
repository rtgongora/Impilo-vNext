"use client";

/**
 * Health Connect–equivalent ingest demo — posts typed changesets to the Experience BFF.
 */

import { useState } from "react";
import { Link2, Loader2, CheckCircle2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { getHealthConnectManifest, postHealthConnectChangeSet } from "@/lib/health-connect-ingest";

const SAMPLE_JSON = `{
  "patientId": "REPLACE_ME",
  "dataOrigin": { "platform": "web_simulator", "appPackage": "zw.gov.mohcc.impilo.experience", "appVersion": "1.0" },
  "grantedScopes": ["write.steps", "write.hydration"],
  "records": [
    { "id": "hc-demo-steps-1", "type": "Steps", "startTime": "2026-04-12T06:00:00Z", "endTime": "2026-04-12T22:00:00Z", "count": 3200 },
    { "id": "hc-demo-water-1", "type": "Hydration", "startTime": "2026-04-12T12:00:00Z", "volumeLiters": 0.35 }
  ]
}`;

export default function WellnessHealthConnectPage() {
  const patientId = useAuthStore((s) => s.user?.id);
  const [manifest, setManifest] = useState<string>("");
  const [jsonText, setJsonText] = useState(SAMPLE_JSON);
  const [loadingManifest, setLoadingManifest] = useState(false);
  const [posting, setPosting] = useState(false);
  const [result, setResult] = useState<string>("");

  const loadManifest = async () => {
    setLoadingManifest(true);
    try {
      const m = await getHealthConnectManifest();
      setManifest(JSON.stringify(m, null, 2));
    } catch (e) {
      setManifest(e instanceof Error ? e.message : "Failed to load manifest");
    } finally {
      setLoadingManifest(false);
    }
  };

  const submit = async () => {
    setPosting(true);
    setResult("");
    try {
      const parsed = JSON.parse(jsonText.replace("REPLACE_ME", patientId || "REPLACE_ME")) as Parameters<
        typeof postHealthConnectChangeSet
      >[0];
      if (!patientId) {
        setResult("Sign in first so patientId can be set.");
        return;
      }
      parsed.patientId = patientId;
      const r = await postHealthConnectChangeSet(parsed);
      setResult(`applied=${r.applied} skipped=${r.skipped} errors=${JSON.stringify(r.errors)}`);
    } catch (e) {
      setResult(e instanceof Error ? e.message : "Request failed");
    } finally {
      setPosting(false);
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="Health Connect ingest"
        subtitle="Typed changesets → wellness_activities / vitals (idempotent by record id)"
        icon={<Link2 className="h-6 w-6" />}
      >
        <p className="text-sm text-gray-600 mb-4">
          This surface aligns with Android Health Connect–style batches: stable <code className="text-xs bg-gray-100 px-1 rounded">records[].id</code>,
          ISO times, and types <strong>Steps</strong>, <strong>Hydration</strong>, <strong>SleepSession</strong>, <strong>HeartRate</strong>. See BFF{" "}
          <code className="text-xs bg-gray-100 px-1 rounded">GET /internal/v1/wellness/connect/v1/manifest</code>.
        </p>

        <div className="flex flex-wrap gap-2 mb-6">
          <button
            type="button"
            onClick={loadManifest}
            disabled={loadingManifest}
            className="inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium hover:bg-gray-50"
          >
            {loadingManifest ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Load manifest
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={posting || !patientId}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 text-white px-4 py-2 text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {posting ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
            POST changeset
          </button>
        </div>

        {!patientId && (
          <p className="flex items-center gap-2 text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 text-sm mb-4">
            <AlertCircle className="h-4 w-4 shrink-0" /> Sign in to set patientId automatically.
          </p>
        )}

        {manifest && (
          <div className="mb-6">
            <h3 className="text-sm font-semibold text-gray-800 mb-2">Manifest</h3>
            <pre className="text-xs bg-gray-900 text-gray-100 p-4 rounded-lg overflow-x-auto max-h-48">{manifest}</pre>
          </div>
        )}

        <div>
          <h3 className="text-sm font-semibold text-gray-800 mb-2">Changeset JSON</h3>
          <textarea
            value={jsonText}
            onChange={(e) => setJsonText(e.target.value)}
            rows={16}
            className="w-full font-mono text-xs border border-gray-300 rounded-lg p-3 bg-white"
            spellCheck={false}
          />
        </div>

        {result && (
          <p className="mt-4 text-sm text-gray-800 bg-gray-50 border border-gray-200 rounded-lg px-3 py-2 font-mono">{result}</p>
        )}
      </PageShell>
    </AppLayout>
  );
}
