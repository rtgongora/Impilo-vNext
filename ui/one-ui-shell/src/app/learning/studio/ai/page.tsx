"use client";

import { useState } from "react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useGenerateFundoAiDraft, useNompiloAssist } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioAiPage() {
  const generate = useGenerateFundoAiDraft();
  const nompilo = useNompiloAssist();
  const [prompt, setPrompt] = useState("Create a 2-hour course for nurses on patient registration.");
  const [generationType, setGenerationType] = useState("course-outline");
  const [audience, setAudience] = useState("NURSE");
  const [result, setResult] = useState("");
  const [assistantReply, setAssistantReply] = useState("");

  async function onGenerateOutline() {
    const response = (await generate.mutateAsync({
      generationType,
      prompt,
      targetAudience: audience,
      durationHours: 2,
      difficulty: "FOUNDATION",
      language: "en",
      sourceDocuments: [],
      outputType: generationType.toUpperCase().replace("-", "_"),
    })) as { data?: Record<string, unknown> };
    setResult(JSON.stringify(response?.data ?? {}, null, 2));
  }

  async function onNompiloHelp() {
    const response = (await nompilo.mutateAsync({
      context: "creator-admin",
      message: prompt,
    })) as { data?: { response?: string } };
    setAssistantReply(response?.data?.response ?? "");
  }

  return (
    <FundoStudioWorkspace title="Studio AI" subtitle="Provider-agnostic AI authoring with safety metadata and draft-only persistence.">
      <div className="rounded-lg border border-gray-200 bg-white p-4">
        <div className="mb-2 grid gap-2 md:grid-cols-2">
          <select value={generationType} onChange={(e) => setGenerationType(e.target.value)} className="rounded border border-gray-300 px-3 py-2 text-sm">
            {["course-outline", "lesson-content", "quiz", "survey", "summary", "facilitator-guide", "voiceover-script"].map((kind) => (
              <option key={kind} value={kind}>{kind}</option>
            ))}
          </select>
          <select value={audience} onChange={(e) => setAudience(e.target.value)} className="rounded border border-gray-300 px-3 py-2 text-sm">
            {["NURSE", "DOCTOR", "COMMUNITY_HEALTH_WORKER", "REGISTRY_OFFICER", "SUPERVISOR"].map((role) => (
              <option key={role} value={role}>{role}</option>
            ))}
          </select>
        </div>
        <textarea value={prompt} onChange={(e) => setPrompt(e.target.value)} className="h-28 w-full rounded border border-gray-300 px-3 py-2 text-sm" />
        <div className="mt-2 flex flex-wrap gap-2">
          <button onClick={onGenerateOutline} className="rounded bg-teal-600 px-3 py-2 text-sm text-white">Generate draft</button>
          <button onClick={onNompiloHelp} className="rounded border border-gray-300 px-3 py-2 text-sm text-gray-700">Ask Nompilo</button>
        </div>
        <div className="mt-3 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
          AI governance: generated content is saved as draft with review metadata (`createdByAi`, `modelProvider`, `prompt`, `sourceDocuments`, `reviewStatus`).
        </div>
        {assistantReply ? <p className="mt-3 rounded bg-teal-50 p-2 text-sm text-teal-900">{assistantReply}</p> : null}
        {result ? <pre className="mt-3 max-h-72 overflow-auto rounded bg-gray-50 p-3 text-xs text-gray-700">{result}</pre> : null}
      </div>
    </FundoStudioWorkspace>
  );
}
