import type { MaternitySummaryProgress } from "@/hooks/queries/useMaternitySummary";

/**
 * How the partograph progress verdict is presented.
 *
 * The alert and action lines are the whole point of a partograph. Crossing the alert line means
 * reassess and, at a peripheral site, plan transfer; crossing the action line means intervene now.
 * The engine computes both and the summary carried only the raw status name, rendered as a muted
 * lowercase parenthetical — so "at or right of action", the sentence that means *intervene now*,
 * appeared in the same grey as everything else.
 *
 * Two rules follow, and the second is the one the engine's own author flagged:
 *
 * 1. **The recommended action is shown, not just the status.** A status a midwife has to translate
 *    into an action is a status that will be skimmed.
 * 2. **`INSUFFICIENT_DATA` is never the quiet case.** The engine's comment on that enum value says
 *    it is "never to be rendered as the reassuring case", and a grey parenthetical beside a green
 *    "Partograph: Active" is exactly that. Not enough recorded to say whether a labour is
 *    obstructed is an outstanding job, and it is displayed as one, with the observations it is
 *    waiting for.
 */

export type ProgressTone = "normal" | "alert" | "action" | "second-stage" | "insufficient";

export interface ProgressPresentation {
  tone: ProgressTone;
  /** The headline a midwife reads. Never a raw enum name. */
  label: string;
  /** What to do about it. */
  action: string | null;
  containerClass: string;
  chipClass: string;
  /** True when this must not be skimmed past — drives emphasis, never hidden. */
  urgent: boolean;
}

const STYLES: Record<ProgressTone, { container: string; chip: string }> = {
  normal: {
    container: "border-emerald-400 bg-emerald-50 dark:border-emerald-600 dark:bg-emerald-950/40",
    chip: "bg-emerald-600 text-white",
  },
  alert: {
    container: "border-amber-400 bg-amber-50 dark:border-amber-600 dark:bg-amber-950/40",
    chip: "bg-amber-500 text-amber-950",
  },
  action: {
    container: "border-rose-400 bg-rose-50 dark:border-rose-600 dark:bg-rose-950/40",
    chip: "bg-rose-600 text-white",
  },
  "second-stage": {
    container: "border-sky-400 bg-sky-50 dark:border-sky-600 dark:bg-sky-950/40",
    chip: "bg-sky-600 text-white",
  },
  // Dashed and uncoloured, like every other "not established" state in this codebase. Deliberately
  // not green and deliberately not quiet.
  insufficient: {
    container: "border-slate-400 border-dashed bg-slate-50 dark:border-slate-500 dark:bg-slate-900/60",
    chip: "bg-slate-700 text-white",
  },
};

export function presentProgress(progress: MaternitySummaryProgress): ProgressPresentation {
  const tone: ProgressTone =
    progress.status === "AT_OR_RIGHT_OF_ACTION"
      ? "action"
      : progress.status === "BETWEEN_ALERT_AND_ACTION"
        ? "alert"
        : progress.status === "SECOND_STAGE"
          ? "second-stage"
          : progress.status === "LEFT_OF_ALERT"
            ? "normal"
            : // Anything unrecognised is treated as not-established rather than as normal. A status
              // this screen does not understand is not a labour progressing well.
              "insufficient";

  const label =
    tone === "action"
      ? "Action line crossed"
      : tone === "alert"
        ? "Alert line crossed"
        : tone === "second-stage"
          ? "Second stage — progress question no longer applies"
          : tone === "normal"
            ? "Progressing at or ahead of the alert line"
            : "Progress not established";

  return {
    tone,
    label,
    action: progress.recommended_action ?? null,
    containerClass: STYLES[tone].container,
    chipClass: STYLES[tone].chip,
    // Insufficient data counts as urgent: an unmonitored labour is not a reassuring one.
    urgent: tone === "action" || tone === "alert" || tone === "insufficient",
  };
}

export function formatHoursBehind(hours: number | null | undefined): string | null {
  if (hours === null || hours === undefined) return null;
  const n = Number(hours);
  if (Number.isNaN(n) || n <= 0) return null;
  return `${n.toFixed(1)} hour${n === 1 ? "" : "s"} behind the alert line`;
}
