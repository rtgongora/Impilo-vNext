"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Link2, Loader2 } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient } from "@/lib/api-client";

type AuthUser = NonNullable<ReturnType<typeof useAuthStore.getState>["user"]>;

export function deriveLearningSubject(user: AuthUser) {
  const subjectType = user.providerId ? "PROVIDER_PUBLIC_ID" : "USER_HEALTH_ID";
  const subjectId = user.providerId ?? user.id;
  return { subjectType, subjectId };
}

function formatSubjectProfileError(err: unknown): string {
  if (err instanceof Error) {
    return err.message;
  }
  if (err && typeof err === "object") {
    const r = err as Record<string, unknown>;
    const nested = r.error as Record<string, unknown> | undefined;
    if (nested?.message && typeof nested.message === "string") {
      return nested.message;
    }
    if (typeof r.message === "string") {
      return r.message;
    }
  }
  return "Could not save profile.";
}

export function LearnerExternalIdentityLink({ variant = "default" }: { variant?: "default" | "compact" }) {
  const user = useAuthStore((s) => s.user);
  const [fundo, setFundo] = useState("");
  const [moodle, setMoodle] = useState("");
  const [feedback, setFeedback] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  const mutation = useMutation({
    mutationFn: async () => {
      if (!user?.id) {
        throw new Error("Sign in to link your learning identities.");
      }
      const { subjectType, subjectId } = deriveLearningSubject(user);
      const fundoTrim = fundo.trim();
      const moodleTrim = moodle.trim();
      if (!fundoTrim && !moodleTrim) {
        throw new Error("Enter your Fundo user reference and/or Moodle user id.");
      }
      const body: Record<string, unknown> = { subjectType, subjectId };
      if (fundoTrim) {
        body.fundoUserRef = fundoTrim;
      }
      if (moodleTrim) {
        body.metadata = { moodleUserId: moodleTrim };
      }
      return apiClient.post<{ data?: { status?: string; profileId?: string } }>(
        "/internal/v1/learning/subject-profile",
        body,
      );
    },
    onSuccess: () => {
      setFeedback({ kind: "ok", text: "Saved. Moodle sync and Fundo completions can use this profile." });
    },
    onError: (err: unknown) => {
      setFeedback({ kind: "err", text: formatSubjectProfileError(err) });
    },
  });

  if (!user?.id) {
    return null;
  }

  const subject = deriveLearningSubject(user);
  const compact = variant === "compact";
  const labelClass = compact ? "text-[11px] font-medium text-slate-600 dark:text-slate-400" : "text-xs font-medium text-slate-700 dark:text-slate-300";
  const inputClass =
    "mt-0.5 w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-xs text-slate-900 shadow-sm focus:border-teal-600 focus:outline-none focus:ring-1 focus:ring-teal-600 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100";

  return (
    <div
      className={
        compact
          ? "rounded-md border border-slate-200 bg-white/80 p-3 dark:border-slate-700 dark:bg-slate-950/50"
          : "rounded-lg border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/40"
      }
    >
      <div className="mb-2 flex items-center gap-2">
        <Link2 className={`${compact ? "h-3.5 w-3.5" : "h-4 w-4"} text-teal-700 dark:text-teal-400`} />
        <h4 className={`font-semibold text-slate-900 dark:text-slate-100 ${compact ? "text-xs" : "text-sm"}`}>
          Link Fundo &amp; Moodle
        </h4>
      </div>
      {!compact ? (
        <p className="mb-3 text-xs text-slate-600 dark:text-slate-400">
          Store your Impilo Fundo account reference and Moodle user id against your profile so orchestration and
          completion sync can match you. Use the numeric Moodle user id from your Moodle profile URL.
        </p>
      ) : (
        <p className="mb-2 text-[11px] text-slate-500 dark:text-slate-400">
          Optional: save Fundo / Moodle ids for automated learning sync.
        </p>
      )}
      <div className={compact ? "space-y-2" : "grid gap-3 sm:grid-cols-2"}>
        <label className="block">
          <span className={labelClass}>Fundo user reference</span>
          <input
            className={inputClass}
            value={fundo}
            onChange={(e) => setFundo(e.target.value)}
            placeholder="From Fundo / LMS profile"
            autoComplete="off"
          />
        </label>
        <label className="block">
          <span className={labelClass}>Moodle user id</span>
          <input
            className={inputClass}
            value={moodle}
            onChange={(e) => setMoodle(e.target.value)}
            placeholder="e.g. 7"
            inputMode="numeric"
            autoComplete="off"
          />
        </label>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <button
          type="button"
          disabled={mutation.isPending}
          onClick={() => {
            setFeedback(null);
            mutation.mutate();
          }}
          className="inline-flex items-center gap-1.5 rounded-md bg-teal-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-800 disabled:opacity-60 dark:bg-teal-600 dark:hover:bg-teal-500"
        >
          {mutation.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
          Save to learning profile
        </button>
        <span className="text-[11px] text-slate-500">
          Subject: {subject.subjectType} / {subject.subjectId}
        </span>
      </div>
      {feedback ? (
        <p
          className={`mt-2 text-xs ${feedback.kind === "ok" ? "text-emerald-800 dark:text-emerald-300" : "text-red-700 dark:text-red-400"}`}
          role="status"
        >
          {feedback.text}
        </p>
      ) : null}
    </div>
  );
}
