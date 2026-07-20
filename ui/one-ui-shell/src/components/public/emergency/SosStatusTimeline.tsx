/**
 * Honest status timeline for an emergency assistance request.
 *
 * Renders ONLY what the daidzai backend actually reports — the interface never
 * claims an ambulance/responder was dispatched. Stage vocabulary (real):
 * AWAITING_CALLBACK → RECEIVED → TRIAGED → LINKED → CLOSED/RESOLVED.
 */

const STEPS: { key: string; matches: string[]; label: string; detail: string }[] = [
  {
    key: "received",
    matches: ["AWAITING_CALLBACK", "RECEIVED"],
    label: "Request received",
    detail: "Your request is registered with the emergency assistance service.",
  },
  {
    key: "callback",
    matches: ["AWAITING_CALLBACK"],
    label: "Confirmation pending",
    detail: "A responder will call your number to confirm before anything is arranged.",
  },
  {
    key: "triaged",
    matches: ["TRIAGED"],
    label: "Assessed by a call-taker",
    detail: "A trained call-taker has reviewed your request.",
  },
  {
    key: "linked",
    matches: ["LINKED"],
    label: "Linked to a response workflow",
    detail: "Your request is connected to an incident a response team is working.",
  },
  {
    key: "closed",
    matches: ["CLOSED", "RESOLVED", "CANCELLED"],
    label: "Closed",
    detail: "This request has been closed.",
  },
];

const ORDER = ["AWAITING_CALLBACK", "RECEIVED", "TRIAGED", "LINKED", "CLOSED", "RESOLVED", "CANCELLED"];

export function SosStatusTimeline({
  status,
  callbackPending,
}: {
  status?: string;
  callbackPending?: boolean;
}) {
  const normalized = (status ?? "").toUpperCase();
  const rank = ORDER.indexOf(normalized);

  function stepState(step: (typeof STEPS)[number]): "done" | "current" | "pending" {
    if (step.key === "callback") {
      if (callbackPending) return "current";
      return rank > ORDER.indexOf("AWAITING_CALLBACK") ? "done" : "pending";
    }
    const stepRanks = step.matches.map((m) => ORDER.indexOf(m)).filter((r) => r >= 0);
    const minRank = Math.min(...stepRanks);
    if (rank < 0) return "pending";
    if (step.matches.includes(normalized)) return "current";
    return rank > minRank ? "done" : "pending";
  }

  return (
    <ol data-testid="sos-status-timeline" className="mt-4 space-y-0">
      {STEPS.map((step, i) => {
        const state = stepState(step);
        // Hide the callback step entirely once it is no longer relevant and was never pending.
        if (step.key === "callback" && state === "pending" && rank > ORDER.indexOf("RECEIVED")) {
          return null;
        }
        return (
          <li key={step.key} className="relative flex gap-3 pb-4 last:pb-0">
            {i < STEPS.length - 1 && (
              <span aria-hidden className="absolute left-[7px] top-5 h-full w-px bg-slate-200" />
            )}
            <span
              aria-hidden
              className={`relative mt-1 h-[15px] w-[15px] shrink-0 rounded-full border-2 ${
                state === "done"
                  ? "border-emerald-600 bg-emerald-600"
                  : state === "current"
                    ? "border-emerald-600 bg-white"
                    : "border-slate-300 bg-white"
              }`}
            />
            <div>
              <p
                className={`text-sm font-semibold ${
                  state === "pending" ? "text-slate-400" : "text-slate-900"
                }`}
              >
                {step.label}
                {state === "current" && (
                  <span className="ml-2 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700">
                    current
                  </span>
                )}
              </p>
              {state !== "pending" && <p className="text-xs text-slate-500">{step.detail}</p>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
