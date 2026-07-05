"use client";

import Link from "next/link";
import { HelpCircle } from "lucide-react";
import { useCourseCompletionRules } from "@/hooks/queries/useFundoPlayer";

export interface QuizInterruptProps {
  courseId?: string;
  enrolmentId?: string;
  /** Show once the watch milestone is reached (the natural quiz moment). */
  visible: boolean;
}

/**
 * Quiz prompt shown when the course's completion rules include QUIZ_REQUIRED
 * (reuses lrn_completion_rule truth — no invented quiz engine). Deliberately a
 * prompt into the existing Fundo assessment flow rather than an in-player quiz
 * renderer: assessments, attempts and marking already live on the enrolment
 * surface, and duplicating them here would fork assessment truth.
 */
export function QuizInterrupt({ courseId, enrolmentId, visible }: QuizInterruptProps) {
  const { data } = useCourseCompletionRules(courseId);
  const items = (data?.data?.items ?? []) as Array<Record<string, unknown>>;
  const quizRule = items.find((r) => String(r.ruleType ?? "") === "QUIZ_REQUIRED");
  if (!visible || !quizRule || !enrolmentId) return null;

  return (
    <div
      className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900"
      data-testid="player-quiz-interrupt"
    >
      <HelpCircle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" />
      <div>
        <p className="font-medium">This course requires a quiz to complete.</p>
        <p className="mt-0.5 text-xs">
          You have met the watch requirement — take the quiz from your course page to finish.
        </p>
        <Link
          href={`/learning/enrolments/${enrolmentId}`}
          className="mt-1 inline-block text-xs font-medium text-amber-800 underline hover:text-amber-900"
          data-testid="player-quiz-link"
        >
          Go to course assessments
        </Link>
      </div>
    </div>
  );
}
