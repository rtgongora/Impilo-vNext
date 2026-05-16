"use client";

import { useMemo, useState, type FormEvent } from "react";
import { AlertTriangle, Loader2, Plus } from "lucide-react";
import {
  CTG_CHUNKS_POLL_MS,
  CTG_SESSION_POLL_MS,
  useActiveCtgSession,
  useAddCtgAnnotation,
  useAddCtgTraceChunk,
  useCreateCtgSession,
  useCtgTraceChunks,
  type CtgAnnotation,
} from "@/hooks/queries/useFetalMonitoring";
import { CtgTracePlot } from "./CtgTracePlot";
import { ctgTraceOriginMs, flattenCtgChannelSamples } from "./ctgTraceUtils";

function parseSamplesInput(raw: string): number[] {
  return raw
    .split(/[\s,;]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => Number(s))
    .filter((n) => Number.isFinite(n));
}

export interface VitalsCtgSectionProps {
  patientId: string;
  encounterId: string;
  hasActiveEncounter: boolean;
  isClinical: boolean;
  recordedBy: string;
}

export function VitalsCtgSection({
  patientId,
  encounterId,
  hasActiveEncounter,
  isClinical,
  recordedBy,
}: VitalsCtgSectionProps) {
  const activeQuery = useActiveCtgSession(
    patientId,
    hasActiveEncounter && encounterId ? encounterId : null,
  );
  const createSession = useCreateCtgSession();
  const addChunk = useAddCtgTraceChunk();
  const addAnnotation = useAddCtgAnnotation();

  const session = activeQuery.data?.data ?? null;
  const sessionId = session?.id ? String(session.id) : undefined;
  const isActive = session?.status === "ACTIVE";

  const chunksQuery = useCtgTraceChunks(sessionId, { enabled: Boolean(sessionId) && isActive });
  const chunks = useMemo(() => chunksQuery.data?.data ?? [], [chunksQuery.data?.data]);

  const originMs = useMemo(() => ctgTraceOriginMs(chunks), [chunks]);
  const fhr = useMemo(() => flattenCtgChannelSamples(chunks, "FHR", originMs), [chunks, originMs]);
  const toco = useMemo(() => flattenCtgChannelSamples(chunks, "TOCO", originMs), [chunks, originMs]);
  const matHr = useMemo(() => flattenCtgChannelSamples(chunks, "MATERNAL_HR", originMs), [chunks, originMs]);

  const annotationMarks = useMemo(() => {
    const list = session?.annotations ?? [];
    return list
      .map((a: CtgAnnotation) => {
        const off = a.sample_offset_sec;
        if (off == null || off === "") return null;
        const tSec = Number(off);
        if (!Number.isFinite(tSec)) return null;
        return { tSec, category: a.category, severity: a.severity ?? undefined };
      })
      .filter(Boolean) as Array<{ tSec: number; category?: string; severity?: string }>;
  }, [session?.annotations]);

  const [showChunkForm, setShowChunkForm] = useState(false);
  const [showAnnotForm, setShowAnnotForm] = useState(false);
  const [chunkChannel, setChunkChannel] = useState("FHR");
  const [chunkHz, setChunkHz] = useState("4");
  const [chunkSamples, setChunkSamples] = useState("");
  const [chunkNotes, setChunkNotes] = useState("");
  const [annotCategory, setAnnotCategory] = useState("CLINICAL_NOTE");
  const [annotOffset, setAnnotOffset] = useState("");
  const [annotNotes, setAnnotNotes] = useState("");
  const [deviceId, setDeviceId] = useState("");

  function handleCreateSession() {
    createSession.mutate(
      {
        patientId,
        encounterId: hasActiveEncounter && encounterId ? encounterId : null,
        startedBy: recordedBy,
        deviceId: deviceId.trim() || null,
        monitoringMode: "EXTERNAL_CTG",
      },
      { onSuccess: () => void activeQuery.refetch() },
    );
  }

  function handleAddChunk(e: FormEvent) {
    e.preventDefault();
    if (!sessionId) return;
    const hz = Number(chunkHz);
    const samples = parseSamplesInput(chunkSamples);
    if (!Number.isFinite(hz) || hz <= 0 || samples.length === 0) return;
    addChunk.mutate(
      {
        patientId,
        sessionId,
        channel: chunkChannel,
        sampleRateHz: hz,
        samples,
        capturedBy: recordedBy,
        notes: chunkNotes.trim() || null,
      },
      {
        onSuccess: () => {
          setChunkSamples("");
          setChunkNotes("");
          setShowChunkForm(false);
          void chunksQuery.refetch();
          void activeQuery.refetch();
        },
      },
    );
  }

  function handleAddAnnotation(e: FormEvent) {
    e.preventDefault();
    if (!sessionId) return;
    const off = annotOffset.trim() === "" ? null : Number(annotOffset.trim());
    addAnnotation.mutate(
      {
        patientId,
        sessionId,
        category: annotCategory,
        sampleOffsetSec: off != null && Number.isFinite(off) ? off : null,
        notes: annotNotes.trim() || null,
        recordedBy: recordedBy,
      },
      {
        onSuccess: () => {
          setAnnotNotes("");
          setAnnotOffset("");
          setShowAnnotForm(false);
          void activeQuery.refetch();
        },
      },
    );
  }

  return (
    <div className="mt-5 rounded-xl border border-violet-200/90 bg-gradient-to-b from-violet-50/50 to-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-violet-950">Fetal monitoring (CTG)</h3>
        <div className="flex flex-wrap gap-2">
          {isClinical && !session && (
            <button
              type="button"
              onClick={() => handleCreateSession()}
              disabled={createSession.isPending}
              className="inline-flex items-center gap-1.5 rounded-lg bg-violet-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-800 disabled:opacity-50"
            >
              {createSession.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
              <Plus className="h-3.5 w-3.5" />
              Start CTG session
            </button>
          )}
          {isClinical && isActive && sessionId && (
            <>
              <button
                type="button"
                onClick={() => setShowChunkForm((v) => !v)}
                className="rounded-lg border border-violet-300 bg-white px-3 py-1.5 text-xs font-medium text-violet-900 hover:bg-violet-50"
              >
                {showChunkForm ? "Hide chunk ingest" : "Ingest trace chunk"}
              </button>
              <button
                type="button"
                onClick={() => setShowAnnotForm((v) => !v)}
                className="rounded-lg border border-violet-300 bg-white px-3 py-1.5 text-xs font-medium text-violet-900 hover:bg-violet-50"
              >
                {showAnnotForm ? "Hide annotation" : "Add annotation"}
              </button>
            </>
          )}
        </div>
      </div>
      <p className="mt-1 text-xs text-gray-600">
        <span className="font-medium text-gray-800">HTTP polling only</span> — session every {CTG_SESSION_POLL_MS / 1000}s,
        trace chunks every {CTG_CHUNKS_POLL_MS / 1000}s (<code className="rounded bg-gray-100 px-1">GET …/sessions/active</code>{" "}
        and <code className="rounded bg-gray-100 px-1">GET …/chunks</code>). This is not a websocket or device live stream.
      </p>
      <p className="mt-1 text-xs text-gray-500">
        APIs: <code className="rounded bg-gray-100 px-1">/internal/v1/maternity/ctg/sessions</code> (create),{" "}
        <code className="rounded bg-gray-100 px-1">…/chunks</code>, <code className="rounded bg-gray-100 px-1">…/annotations</code>.
      </p>

      {isClinical && !session && (
        <label className="mt-2 block text-xs text-gray-600">
          Device ID (optional)
          <input
            value={deviceId}
            onChange={(e) => setDeviceId(e.target.value)}
            className="mt-1 block w-full max-w-md rounded-lg border border-gray-300 px-3 py-1.5 text-sm"
            placeholder="Monitor / bed identifier"
          />
        </label>
      )}

      {activeQuery.isError && (
        <div className="mt-2 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <div>
            <p className="font-medium">Could not load CTG session</p>
            <button type="button" className="mt-1 text-xs underline" onClick={() => void activeQuery.refetch()}>
              Retry
            </button>
          </div>
        </div>
      )}

      {activeQuery.isLoading && (
        <div className="mt-3 flex items-center gap-2 text-sm text-gray-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading CTG session…
        </div>
      )}

      {!activeQuery.isLoading && !session && (
        <p className="mt-3 text-sm text-gray-600">
          No active CTG session
          {hasActiveEncounter ? " for this encounter" : ""}. {isClinical ? "Start a session to capture trace chunks." : null}
        </p>
      )}

      {session && (
        <div className="mt-3 space-y-2">
          <p className="text-xs text-gray-700">
            Session <code className="rounded bg-gray-100 px-1">{sessionId}</code>
            {session.started_at ? <> · started {new Date(String(session.started_at)).toLocaleString()}</> : null}
            {session.device_id ? (
              <>
                {" "}
                · device <code className="rounded bg-gray-100 px-1">{String(session.device_id)}</code>
              </>
            ) : null}
            {session.summary?.chunk_count != null ? ` · ${session.summary.chunk_count} chunk(s)` : null}
          </p>
          {Array.isArray(session.summary?.active_channels) && session.summary!.active_channels!.length > 0 ? (
            <p className="text-xs text-gray-600">
              Active channels: {session.summary!.active_channels!.join(", ")}
            </p>
          ) : null}

          {chunksQuery.isError && (
            <p className="text-xs text-amber-800">Could not load trace chunks — strips may be stale.</p>
          )}

          <CtgTracePlot fhr={fhr} toco={toco} maternalHr={matHr} annotationMarks={annotationMarks} className="mt-2" />

          {session.annotations && session.annotations.length > 0 ? (
            <div className="mt-2 rounded-lg border border-gray-100 bg-gray-50/80 p-2 text-xs text-gray-700">
              <p className="font-semibold text-gray-800">Annotations</p>
              <ul className="mt-1 list-inside list-disc space-y-0.5">
                {session.annotations.map((a) => (
                  <li key={String(a.id ?? `${a.recorded_at}-${a.category}`)}>
                    {a.category}
                    {a.sample_offset_sec != null && a.sample_offset_sec !== "" ? ` @ ${a.sample_offset_sec}s` : ""}
                    {a.notes ? ` — ${a.notes}` : ""}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          {showChunkForm && isActive && isClinical && (
            <form onSubmit={handleAddChunk} className="mt-3 space-y-2 rounded-lg border border-gray-100 bg-white p-3">
              <p className="text-xs font-medium text-gray-800">Ingest chunk (POST …/chunks)</p>
              <div className="flex flex-wrap gap-3">
                <label className="text-xs text-gray-600">
                  Channel
                  <select
                    value={chunkChannel}
                    onChange={(e) => setChunkChannel(e.target.value)}
                    className="mt-1 block rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                  >
                    <option value="FHR">FHR</option>
                    <option value="TOCO">TOCO</option>
                    <option value="MATERNAL_HR">MATERNAL_HR</option>
                  </select>
                </label>
                <label className="text-xs text-gray-600">
                  Sample rate (Hz)
                  <input
                    value={chunkHz}
                    onChange={(e) => setChunkHz(e.target.value)}
                    className="mt-1 block w-24 rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                  />
                </label>
              </div>
              <label className="block text-xs text-gray-600">
                Samples (whitespace-separated decimals, one per sample)
                <textarea
                  value={chunkSamples}
                  onChange={(e) => setChunkSamples(e.target.value)}
                  rows={3}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-2 py-1.5 font-mono text-xs"
                  placeholder="e.g. 132 134 138 142 …"
                />
              </label>
              <label className="block text-xs text-gray-600">
                Notes (optional)
                <input
                  value={chunkNotes}
                  onChange={(e) => setChunkNotes(e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                />
              </label>
              <button
                type="submit"
                disabled={addChunk.isPending}
                className="rounded-lg bg-violet-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-800 disabled:opacity-50"
              >
                {addChunk.isPending ? "Saving…" : "Post chunk"}
              </button>
              {addChunk.isError && <p className="text-xs text-red-600">Chunk rejected — check channel, Hz, and samples.</p>}
            </form>
          )}

          {showAnnotForm && isActive && isClinical && (
            <form onSubmit={handleAddAnnotation} className="mt-2 space-y-2 rounded-lg border border-gray-100 bg-white p-3">
              <p className="text-xs font-medium text-gray-800">Annotation (POST …/annotations)</p>
              <label className="block text-xs text-gray-600">
                Category
                <input
                  value={annotCategory}
                  onChange={(e) => setAnnotCategory(e.target.value)}
                  className="mt-1 block w-full max-w-md rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                  required
                />
              </label>
              <label className="block text-xs text-gray-600">
                Sample offset (seconds on trace, optional)
                <input
                  value={annotOffset}
                  onChange={(e) => setAnnotOffset(e.target.value)}
                  className="mt-1 block w-full max-w-md rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                  placeholder="e.g. 120.5"
                />
              </label>
              <label className="block text-xs text-gray-600">
                Notes
                <textarea
                  value={annotNotes}
                  onChange={(e) => setAnnotNotes(e.target.value)}
                  rows={2}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                />
              </label>
              <button
                type="submit"
                disabled={addAnnotation.isPending}
                className="rounded-lg bg-violet-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-800 disabled:opacity-50"
              >
                {addAnnotation.isPending ? "Saving…" : "Save annotation"}
              </button>
            </form>
          )}
        </div>
      )}

      {createSession.isError && <p className="mt-2 text-xs text-red-600">Could not start CTG session.</p>}
    </div>
  );
}
