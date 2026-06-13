"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { WELLNESS_CONNECT_COLUMNS } from "@/lib/json-api/generic-table-columns";
/**
 * Health Connect–equivalent ingest demo — posts typed changesets to the Experience BFF.
 */

import { useCallback, useEffect, useState } from "react";
import { Link2, Loader2, CheckCircle2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  connectPersonalHealthSource,
  getHealthConnectManifest,
  getPersonalWellnessSummary,
  listPersonalHealthSources,
  postHealthConnectChangeSet,
  updatePersonalHealthSourcePermissions,
  type WellnessConnectedSource,
} from "@/lib/health-connect-ingest";

const SAMPLE_JSON = `{
  "patientId": "REPLACE_ME",
  "dataOrigin": { "platform": "web_simulator", "appPackage": "zw.gov.mohcc.impilo.experience", "appVersion": "1.0" },
  "grantedScopes": ["write.steps", "write.distance", "write.hydration", "write.sleep", "write.heart_rate"],
  "records": [
    { "id": "hc-parity-steps-1", "type": "StepsRecord", "startTime": "2026-04-12T06:00:00Z", "endTime": "2026-04-12T22:00:00Z", "count": 3200 },
    { "id": "hc-parity-distance-1", "type": "DistanceRecord", "startTime": "2026-04-12T07:00:00Z", "endTime": "2026-04-12T08:00:00Z", "distanceMeters": 2100 },
    { "id": "hc-parity-water-1", "type": "HydrationRecord", "startTime": "2026-04-12T12:00:00Z", "volume": 0.35 },
    { "id": "hc-parity-sleep-1", "type": "SleepSessionRecord", "startTime": "2026-04-11T22:30:00Z", "endTime": "2026-04-12T06:15:00Z", "sleepQuality": 4,
      "sleepStages": [
        { "stage": "DEEP", "startTime": "2026-04-11T23:00:00Z", "endTime": "2026-04-12T01:00:00Z" },
        { "stage": "REM", "startTime": "2026-04-12T05:00:00Z", "endTime": "2026-04-12T06:00:00Z" }
      ]
    },
    { "id": "hc-parity-hr-1", "type": "HeartRateRecord", "startTime": "2026-04-12T08:00:00Z", "endTime": "2026-04-12T08:05:00Z",
      "samples": [
        { "time": "2026-04-12T08:01:00Z", "beatsPerMinute": 72 },
        { "time": "2026-04-12T08:03:00Z", "beatsPerMinute": 78 }
      ]
    }
  ]
}`;

export default function WellnessHealthConnectPage() {
  const patientId = useAuthStore((s) => s.user?.id);
  const [manifest, setManifest] = useState<string>("");
  const [jsonText, setJsonText] = useState(SAMPLE_JSON);
  const [loadingManifest, setLoadingManifest] = useState(false);
  const [posting, setPosting] = useState(false);
  const [loadingSources, setLoadingSources] = useState(false);
  const [sources, setSources] = useState<WellnessConnectedSource[]>([]);
  const [summary, setSummary] = useState<Record<string, unknown> | null>(null);
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
      const errText = Array.isArray(r.errors)
        ? r.errors.join("; ")
        : r.errors && typeof r.errors === "object"
          ? Object.entries(r.errors as Record<string, unknown>)
              .map(([k, v]) => `${k}: ${String(v)}`)
              .join("; ")
          : String(r.errors ?? "none");
      setResult(`applied=${r.applied} skipped=${r.skipped} errors=${errText}`);
    } catch (e) {
      setResult(e instanceof Error ? e.message : "Request failed");
    } finally {
      setPosting(false);
    }
  };

  const loadSources = useCallback(async () => {
    if (!patientId) return;
    setLoadingSources(true);
    try {
      const rows = await listPersonalHealthSources(patientId);
      setSources(rows);
      const s = await getPersonalWellnessSummary(patientId);
      setSummary(s);
    } catch (e) {
      setResult(e instanceof Error ? e.message : "Failed to load source management data");
    } finally {
      setLoadingSources(false);
    }
  }, [patientId]);

  const connectAndroidHealthConnect = async () => {
    if (!patientId) return;
    try {
      await connectPersonalHealthSource({
        person_cpid: patientId,
        source_type: "android_health_connect",
        source_name: "Android Health Connect",
        source_app_id: "com.google.android.apps.healthdata",
        provider_access_allowed: false,
        clinical_writeback_allowed: false,
        sharing_scope: "PERSONAL_ONLY",
        category_permissions: {
          activity: true,
          vitals: true,
          sleep: true,
          nutrition: true,
        },
      });
      await loadSources();
      setResult("Connected Android Health Connect source.");
    } catch (e) {
      setResult(e instanceof Error ? e.message : "Failed to connect source");
    }
  };

  const toggleProviderSharing = async (source: WellnessConnectedSource) => {
    try {
      await updatePersonalHealthSourcePermissions(source.id, {
        provider_access_allowed: !source.provider_access_allowed,
        sharing_scope: !source.provider_access_allowed ? "SHARED_WITH_PROVIDER" : "PERSONAL_ONLY",
      });
      await loadSources();
    } catch (e) {
      setResult(e instanceof Error ? e.message : "Failed to update source permissions");
    }
  };

  useEffect(() => {
    void loadSources();
  }, [loadSources]);

  return (
    <AppLayout>
      <PageShell
        title="Health Connect ingest"
        subtitle="Typed changesets → wellness_activities / vitals (idempotent by record id)"
        icon={<Link2 className="h-6 w-6" />}
      >
        <p className="text-sm text-muted-foreground mb-4">
          This surface aligns with Android Health Connect–style batches: stable <code className="text-xs bg-neutral-100 px-1 rounded">records[].id</code>,
          ISO times, and types <strong>Steps</strong>, <strong>Hydration</strong>, <strong>SleepSession</strong>, <strong>HeartRate</strong>. See BFF{" "}
          <code className="text-xs bg-neutral-100 px-1 rounded">GET /internal/v1/wellness/connect/v1/manifest</code>.
        </p>

        <div className="flex flex-wrap gap-2 mb-6">
          <button
            type="button"
            onClick={loadManifest}
            disabled={loadingManifest}
            className="inline-flex items-center gap-2 rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium hover:bg-background"
          >
            {loadingManifest ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Load manifest
          </button>
          <button
            type="button"
            onClick={connectAndroidHealthConnect}
            disabled={!patientId}
            className="inline-flex items-center gap-2 rounded-lg border border-impilo-300 bg-card px-4 py-2 text-sm font-medium hover:bg-primary-soft disabled:opacity-50"
          >
            Connect source
          </button>
          <button
            type="button"
            onClick={loadSources}
            disabled={loadingSources || !patientId}
            className="inline-flex items-center gap-2 rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium hover:bg-background disabled:opacity-50"
          >
            {loadingSources ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Refresh sources
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={posting || !patientId}
            className="inline-flex items-center gap-2 rounded-lg bg-primary text-white px-4 py-2 text-sm font-medium hover:bg-primary-hover disabled:opacity-50"
          >
            {posting ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
            POST changeset
          </button>
        </div>

        {!patientId && (
          <p className="flex items-center gap-2 text-warning-foreground bg-warning-soft border border-warning/35 rounded-lg px-3 py-2 text-sm mb-4">
            <AlertCircle className="h-4 w-4 shrink-0" /> Sign in to set patientId automatically.
          </p>
        )}

        {manifest && (
          <div className="mb-6">
            <h3 className="text-sm font-semibold text-foreground mb-2">Manifest</h3>
            <pre className="text-xs bg-neutral-900 text-gray-100 p-4 rounded-lg overflow-x-auto max-h-48">{manifest}</pre>
          </div>
        )}

        <div className="mb-6">
          <h3 className="text-sm font-semibold text-foreground mb-2">Connected sources & permissions</h3>
          <div className="rounded-lg border border-border divide-y bg-card">
            {sources.length === 0 ? (
              <p className="px-3 py-2 text-sm text-muted-foreground">No connected sources yet.</p>
            ) : (
              sources.map((source) => (
                <div key={source.id} className="px-3 py-2 flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-foreground">{source.source_name}</p>
                    <p className="text-xs text-muted-foreground">
                      {source.source_type} · {source.status} · consent {source.consent_status}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => void toggleProviderSharing(source)}
                    className="rounded-md border border-border px-2 py-1 text-xs hover:bg-background"
                  >
                    {source.provider_access_allowed ? "Revoke provider sharing" : "Allow provider sharing"}
                  </button>
                </div>
              ))
            )}
          </div>
        </div>

        {summary && (
          <div className="mb-6">
            <h3 className="text-sm font-semibold text-foreground mb-2">Wellness summary</h3>
            <JsonApiDataTable data={summary} columns={WELLNESS_CONNECT_COLUMNS} emptyTitle="No wellness summary" />
          </div>
        )}

        <div>
          <h3 className="text-sm font-semibold text-foreground mb-2">Changeset JSON</h3>
          <textarea
            value={jsonText}
            onChange={(e) => setJsonText(e.target.value)}
            rows={16}
            className="w-full font-mono text-xs border border-border rounded-lg p-3 bg-card"
            spellCheck={false}
          />
        </div>

        {result && (
          <p className="mt-4 text-sm text-foreground bg-background border border-border rounded-lg px-3 py-2 font-mono">{result}</p>
        )}
      </PageShell>
    </AppLayout>
  );
}
