"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  useAdvanceClinicalPathwaySession,
  useClinicalPathways,
  useStartClinicalPathwaySession,
} from "@/hooks/queries/useGuidance";
import { Loader2, Route } from "lucide-react";

type SchemaField = { name: string; type: string; values?: string[] };

function readTenantId(): string {
  if (typeof window === "undefined") return "tenant-moh-zw";
  return sessionStorage.getItem("exp:tenant_id") || "tenant-moh-zw";
}

function parseSchema(raw: unknown): SchemaField[] {
  if (!raw || typeof raw !== "object") return [];
  const fields = (raw as { fields?: unknown }).fields;
  if (!Array.isArray(fields)) return [];
  return fields
    .flatMap((f): SchemaField[] => {
      if (!f || typeof f !== "object") return [];
      const o = f as Record<string, unknown>;
      const name = typeof o.name === "string" ? o.name : "";
      const type = typeof o.type === "string" ? o.type : "text";
      const values = Array.isArray(o.values) ? o.values.filter((v): v is string => typeof v === "string") : undefined;
      if (!name) return [];
      return [{ name, type, values }];
    });
}

function applyStepPayload(
  data: Record<string, unknown>,
  setters: {
    setPrompt: (s: string) => void;
    setStepType: (s: string) => void;
    setSchema: (s: unknown) => void;
    setStatus: (s: string) => void;
    setSummary: (s: string | null) => void;
  },
) {
  setters.setPrompt(typeof data.current_prompt === "string" ? data.current_prompt : "");
  setters.setStepType(typeof data.current_step_type === "string" ? data.current_step_type : "");
  setters.setSchema(data.data_capture_schema ?? null);
  setters.setStatus(typeof data.status === "string" ? data.status : "ACTIVE");
  setters.setSummary(typeof data.summary === "string" ? data.summary : null);
}

type Props = {
  patientId?: string;
  encounterId?: string;
  compact?: boolean;
};

export function ClinicalPathwaySessionPanel({ patientId, encounterId, compact }: Props) {
  const { data: pathwaysRes, isLoading: loadingList } = useClinicalPathways();
  const start = useStartClinicalPathwaySession();
  const advance = useAdvanceClinicalPathwaySession();

  const pathways = (pathwaysRes?.data ?? []) as Array<{
    id?: string;
    pathway_name?: string;
    condition_code?: string;
  }>;

  const [pathwayId, setPathwayId] = useState("");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [prompt, setPrompt] = useState("");
  const [stepType, setStepType] = useState("");
  const [schemaRaw, setSchemaRaw] = useState<unknown>(null);
  const [status, setStatus] = useState<string>("");
  const [summary, setSummary] = useState<string | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});

  const fields = useMemo(() => parseSchema(schemaRaw), [schemaRaw]);
  const fieldKeys = fields.map((f) => f.name).join("|");

  useEffect(() => {
    setAnswers({});
  }, [fieldKeys, sessionId]);

  const resetAfterStart = useCallback((data: Record<string, unknown>) => {
    const sid = typeof data.session_id === "string" ? data.session_id : null;
    setSessionId(sid);
    applyStepPayload(data, {
      setPrompt,
      setStepType,
      setSchema: setSchemaRaw,
      setStatus,
      setSummary,
    });
    setAnswers({});
  }, []);

  const handleStart = () => {
    if (!pathwayId.trim()) return;
    start.mutate(
      {
        pathway_id: pathwayId.trim(),
        patient_id: patientId,
        encounter_id: encounterId,
      },
      {
        onSuccess: (res) => {
          const data = (res?.data ?? {}) as Record<string, unknown>;
          resetAfterStart(data);
        },
      },
    );
  };

  const handleAdvance = () => {
    if (!sessionId) return;
    const payload: Record<string, unknown> = {};
    for (const f of fields) {
      const raw = answers[f.name]?.trim() ?? "";
      if (f.type === "boolean") {
        payload[f.name] = raw === "true" || raw === "1" || raw.toLowerCase() === "yes";
      } else if (f.type === "number") {
        payload[f.name] = raw === "" ? null : Number.parseFloat(raw);
      } else {
        payload[f.name] = raw;
      }
    }
    advance.mutate(
      { sessionId, answers: payload },
      {
        onSuccess: (res) => {
          const data = (res?.data ?? {}) as Record<string, unknown>;
          applyStepPayload(data, {
            setPrompt,
            setStepType,
            setSchema: setSchemaRaw,
            setStatus,
            setSummary,
          });
          setAnswers({});
        },
      },
    );
  };

  const completed = status === "COMPLETED";

  return (
    <div className={compact ? "space-y-2" : "space-y-3"}>
      <div className="flex items-center gap-2 text-sm font-medium text-impilo-800">
        <Route className="h-4 w-4 shrink-0" />
        National pathways
      </div>
      {!sessionId ? (
        <div className="space-y-2">
          {loadingList ? (
            <p className="text-xs text-muted-foreground">Loading pathways…</p>
          ) : (
            <select
              className="w-full rounded border border-primary/25 bg-card px-2 py-1.5 text-sm"
              value={pathwayId}
              onChange={(e) => setPathwayId(e.target.value)}
            >
              <option value="">Select a pathway…</option>
              {pathways.map((p) => (
                <option key={p.id ?? ""} value={p.id ?? ""}>
                  {p.pathway_name ?? p.condition_code ?? p.id}
                </option>
              ))}
            </select>
          )}
          <button
            type="button"
            disabled={!pathwayId || start.isPending}
            onClick={handleStart}
            className="inline-flex items-center gap-2 rounded-md bg-primary-hover px-3 py-1.5 text-sm text-white hover:bg-impilo-700 disabled:opacity-50"
          >
            {start.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Start session
          </button>
          {start.isError && (
            <p className="text-xs text-red-600">Could not start pathway — check clinical service.</p>
          )}
        </div>
      ) : (
        <div className="space-y-2">
          <p className="text-[10px] text-muted-foreground font-mono break-all">Session {sessionId}</p>
          {completed ? (
            <div className="rounded border border-green-200 bg-green-50/80 p-3 text-sm text-green-900">
              {summary ?? "Pathway complete."}
            </div>
          ) : (
            <>
              {stepType ? (
                <span className="text-[10px] uppercase tracking-wide text-muted-foreground">{stepType}</span>
              ) : null}
              <p className="text-sm text-foreground">{prompt || "Follow the clinical step."}</p>
              {fields.length === 0 ? (
                <p className="text-xs text-muted-foreground">No fields for this step — continue to record completion.</p>
              ) : (
                <div className="space-y-2">
                  {fields.map((f) => (
                    <div key={f.name}>
                      <label className="block text-[10px] font-medium text-muted-foreground mb-0.5">{f.name}</label>
                      {f.type === "boolean" ? (
                        <select
                          className="w-full rounded border border-border bg-card px-2 py-1 text-sm"
                          value={answers[f.name] ?? ""}
                          onChange={(e) => setAnswers((a) => ({ ...a, [f.name]: e.target.value }))}
                        >
                          <option value="">—</option>
                          <option value="false">No</option>
                          <option value="true">Yes</option>
                        </select>
                      ) : f.type === "enum" && f.values && f.values.length > 0 ? (
                        <select
                          className="w-full rounded border border-border bg-card px-2 py-1 text-sm"
                          value={answers[f.name] ?? ""}
                          onChange={(e) => setAnswers((a) => ({ ...a, [f.name]: e.target.value }))}
                        >
                          <option value="">Select…</option>
                          {f.values.map((v) => (
                            <option key={v} value={v}>
                              {v}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <input
                          className="w-full rounded border border-border bg-card px-2 py-1 text-sm"
                          value={answers[f.name] ?? ""}
                          onChange={(e) => setAnswers((a) => ({ ...a, [f.name]: e.target.value }))}
                          placeholder={f.type === "number" ? "Number" : "Text"}
                        />
                      )}
                    </div>
                  ))}
                </div>
              )}
              <button
                type="button"
                disabled={advance.isPending}
                onClick={handleAdvance}
                className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-1.5 text-sm text-white hover:bg-primary-hover disabled:opacity-50"
              >
                {advance.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                Submit step
              </button>
              {advance.isError && (
                <p className="text-xs text-red-600">Advance failed — try again or restart session.</p>
              )}
            </>
          )}
          <button
            type="button"
            className="text-xs text-muted-foreground underline hover:text-foreground"
            onClick={() => {
              setSessionId(null);
              setPrompt("");
              setStepType("");
              setSchemaRaw(null);
              setStatus("");
              setSummary(null);
              setAnswers({});
            }}
          >
            New pathway
          </button>
        </div>
      )}
      <p className="text-[10px] text-muted-foreground">Tenant: {readTenantId()}</p>
    </div>
  );
}
