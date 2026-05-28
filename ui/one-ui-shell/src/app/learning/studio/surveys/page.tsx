"use client";

import { useState } from "react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useCreateInteractiveActivity, useFundoInteractiveActivities } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioSurveysPage() {
  const createActivity = useCreateInteractiveActivity();
  const { data, refetch } = useFundoInteractiveActivities(undefined, undefined, 100);
  const [title, setTitle] = useState("Course satisfaction pulse");
  const [formType, setFormType] = useState<"SURVEY" | "FEEDBACK" | "REFLECTION">("SURVEY");

  async function onCreateSurveyBlock() {
    await createActivity.mutateAsync({
      activityType: formType,
      title,
      config: {
        questions:
          formType === "SURVEY"
            ? [
                { type: "RATING", prompt: "How useful was this lesson?" },
                { type: "TEXT", prompt: "What should improve?" },
              ]
            : formType === "FEEDBACK"
              ? [
                  { type: "TEXT", prompt: "Describe practical blockers." },
                  { type: "RATING", prompt: "Trainer effectiveness (1-5)." },
                ]
              : [
                  { type: "TEXT", prompt: "What did you learn today?" },
                  { type: "TEXT", prompt: "How will you apply this at work?" },
                ],
      },
    });
    void refetch();
  }

  const items = (((data?.data ?? {}) as { items?: Array<{ id: string; title: string; activityType?: string; status?: string }> }).items ?? []).filter(
    (item) => ["SURVEY", "FEEDBACK", "REFLECTION"].includes(item.activityType ?? ""),
  );

  return (
    <FundoStudioWorkspace title="Studio Surveys" subtitle="Author survey/feedback/reflection instruments and bind to course delivery.">
      <div className="rounded border border-gray-200 bg-white p-4">
        <select value={formType} onChange={(e) => setFormType(e.target.value as "SURVEY" | "FEEDBACK" | "REFLECTION")} className="mb-2 w-full rounded border border-gray-300 px-3 py-2 text-sm">
          <option value="SURVEY">Survey</option>
          <option value="FEEDBACK">Feedback form</option>
          <option value="REFLECTION">Reflection form</option>
        </select>
        <input value={title} onChange={(e) => setTitle(e.target.value)} className="w-full rounded border border-gray-300 px-3 py-2 text-sm" />
        <button onClick={onCreateSurveyBlock} className="mt-2 rounded bg-teal-600 px-3 py-2 text-sm text-white">
          Create {formType.toLowerCase()} block draft
        </button>
      </div>
      <div className="mt-3 rounded border border-gray-200 bg-white p-4">
        <p className="text-sm font-semibold text-gray-900">Existing forms</p>
        <ul className="mt-2 space-y-2 text-sm">
          {items.map((item) => (
            <li key={item.id} className="rounded border border-gray-100 px-3 py-2">
              {item.title} · {item.activityType ?? "SURVEY"} · {item.status ?? "PUBLISHED"}
            </li>
          ))}
        </ul>
      </div>
    </FundoStudioWorkspace>
  );
}
