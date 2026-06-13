"use client";

import { useState } from "react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useGenerateFundoAiDraft } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioMediaScriptsPage() {
  const generate = useGenerateFundoAiDraft();
  const [topic, setTopic] = useState("Patient registration SOP");
  const [output, setOutput] = useState<string>("");

  async function onGenerate() {
    const res = (await generate.mutateAsync({
      generationType: "voiceover-script",
      prompt: `Create voiceover script for ${topic}`,
      topic,
      outputType: "VOICEOVER_SCRIPT",
    })) as { data?: { output?: { script?: string } } };
    setOutput(res?.data?.output?.script ?? "");
  }

  return (
    <FundoStudioWorkspace title="Media Scripts" subtitle="AI-assisted voiceover/facilitator script drafts with human review workflow.">
      <div className="rounded border border-border bg-card p-4">
        <input value={topic} onChange={(e) => setTopic(e.target.value)} className="w-full rounded border border-border px-3 py-2 text-sm" />
        <button onClick={onGenerate} className="mt-2 rounded bg-teal-600 px-3 py-2 text-sm text-white">Generate draft script</button>
        {output ? <pre className="mt-3 whitespace-pre-wrap rounded bg-background p-3 text-xs text-foreground">{output}</pre> : null}
      </div>
    </FundoStudioWorkspace>
  );
}
