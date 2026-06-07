"use client";

import { useState } from "react";
import { Loader2, Sparkles } from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type DonorAssistOp =
  | "explain_donation_steps"
  | "explain_eligibility"
  | "prescreening_guide"
  | "explain_deferral_safely"
  | "find_drives_help"
  | "interpret_prescreening_outcome";

interface AssistResponse {
  content?: string | string[];
  steps?: string[];
  questions?: Array<{ id: string; label: string; help?: string }>;
  disclaimer?: string;
  fallbackUsed?: boolean;
}

export function MadiDonorAssistPanel({
  op,
  context,
  label = "Ask Nompilo",
}: {
  op: DonorAssistOp;
  context?: Record<string, unknown>;
  label?: string;
}) {
  const [result, setResult] = useState<AssistResponse | null>(null);
  const assist = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<{ data?: AssistResponse }>("/internal/v1/madi/donor/assist", {
        op,
        language: "en",
        context: context ?? {},
      });
      return (res as { data?: AssistResponse }).data ?? (res as AssistResponse);
    },
    onSuccess: (data) => setResult(data),
  });

  return (
    <div className="rounded-2xl border border-violet-200 bg-violet-50/60 p-4">
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 text-sm font-semibold text-violet-900">
          <Sparkles className="h-4 w-4" />
          Nompilo donor guide
        </div>
        <button
          type="button"
          onClick={() => assist.mutate()}
          disabled={assist.isPending}
          className="rounded-lg bg-violet-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50"
        >
          {assist.isPending ? <Loader2 className="h-3 w-3 animate-spin inline" /> : label}
        </button>
      </div>
      {result?.disclaimer && (
        <p className="text-xs text-violet-800/80 mb-2">{result.disclaimer}</p>
      )}
      {result?.fallbackUsed && (
        <p className="text-xs text-amber-700 mb-2">Using offline-safe guidance until the assistant is back.</p>
      )}
      {Array.isArray(result?.steps) && (
        <ol className="list-decimal list-inside text-sm text-gray-700 space-y-1">
          {result.steps.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ol>
      )}
      {Array.isArray(result?.questions) && (
        <ul className="space-y-2 text-sm text-gray-700">
          {result.questions.map((q) => (
            <li key={q.id}>
              <span className="font-medium">{q.label}</span>
              {q.help ? <span className="block text-xs text-gray-500">{q.help}</span> : null}
            </li>
          ))}
        </ul>
      )}
      {typeof result?.content === "string" && (
        <p className="text-sm text-gray-700">{result.content}</p>
      )}
      {Array.isArray(result?.content) && (
        <ul className="list-disc list-inside text-sm text-gray-700 space-y-1">
          {result.content.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
