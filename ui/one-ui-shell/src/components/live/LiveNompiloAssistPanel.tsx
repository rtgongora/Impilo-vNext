"use client";

import { useState } from "react";
import { Lightbulb, Loader2 } from "lucide-react";
import { useLiveComposerAssist } from "@/hooks/queries/useLive";
import type { LiveComposerOp } from "@/lib/live";

interface LiveNompiloAssistPanelProps {
  eventTitle?: string;
  eventType?: string;
  defaultOp?: LiveComposerOp;
}

const OPS: Array<{ value: LiveComposerOp; label: string }> = [
  { value: "suggest_title", label: "Suggest title" },
  { value: "suggest_agenda", label: "Suggest agenda" },
  { value: "suggest_objectives", label: "Suggest objectives" },
  { value: "moderation_hint", label: "Moderation hint" },
  { value: "cpd_summary", label: "CPD summary" },
];

export function LiveNompiloAssistPanel({
  eventTitle,
  eventType,
  defaultOp = "suggest_agenda",
}: LiveNompiloAssistPanelProps) {
  const assist = useLiveComposerAssist();
  const [op, setOp] = useState<LiveComposerOp>(defaultOp);
  const [context, setContext] = useState(eventTitle ?? "");
  const [result, setResult] = useState<string>("");

  async function onAssist() {
    const response = await assist.mutateAsync({
      op,
      eventTitle: eventTitle ?? context,
      eventType,
      context,
      language: "en",
    });
    const payload = response.result;
    if (Array.isArray(payload)) {
      setResult(payload.map((item) => `• ${String(item)}`).join("\n"));
    } else if (payload && typeof payload === "object") {
      setResult(JSON.stringify(payload, null, 2));
    } else {
      setResult(String(payload ?? ""));
    }
    if (response.fallbackUsed) {
      setResult((prev) => `${prev}\n\n(Offline deterministic assist — LLM unavailable)`);
    }
  }

  return (
    <section className="rounded-2xl border border-teal-200 bg-teal-50/50 p-4">
      <div className="flex items-center gap-2 mb-3">
        <Lightbulb className="h-5 w-5 text-teal-700" />
        <h3 className="font-semibold text-teal-900">Nompilo Live Assist</h3>
      </div>
      <p className="text-xs text-teal-800 mb-3">
        Governed composer assist for titles, agendas, and moderation guidance. Suggestions require human review.
      </p>
      <div className="space-y-2">
        <select
          value={op}
          onChange={(e) => setOp(e.target.value as LiveComposerOp)}
          className="w-full rounded-lg border border-teal-200 bg-white px-3 py-2 text-sm"
        >
          {OPS.map((item) => (
            <option key={item.value} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
        <textarea
          value={context}
          onChange={(e) => setContext(e.target.value)}
          placeholder="Describe the session topic or paste draft content…"
          className="w-full rounded-lg border border-teal-200 bg-white px-3 py-2 text-sm min-h-[80px]"
        />
        <button
          type="button"
          onClick={onAssist}
          disabled={assist.isPending}
          className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-3 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60"
        >
          {assist.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          Ask Nompilo
        </button>
      </div>
      {result ? (
        <pre className="mt-3 max-h-48 overflow-auto rounded-lg border border-teal-100 bg-white p-3 text-xs text-gray-700 whitespace-pre-wrap">
          {result}
        </pre>
      ) : null}
    </section>
  );
}
