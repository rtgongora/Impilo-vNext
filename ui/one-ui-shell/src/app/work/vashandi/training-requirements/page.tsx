"use client";

/**
 * Governed role → required-Fundo-course mapping (PO-20260629-01, G-FU-02).
 * The mapping drives the graduated training-gate at assignment precheck/activation:
 * ADVISORY warns, SOFT withholds pending governance override, HARD denies.
 */

import { useState } from "react";
import { GraduationCap, ShieldAlert } from "lucide-react";
import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import {
  isVashandiDegraded,
  useCreateTrainingRequirement,
  useDeactivateTrainingRequirement,
  useTrainingRequirements,
} from "@/hooks/useVashandi";

const LEVELS = ["ADVISORY", "SOFT", "HARD"] as const;

function levelBadge(level: string) {
  if (level === "HARD") return "bg-red-100 text-red-800 border-red-200";
  if (level === "SOFT") return "bg-amber-100 text-amber-800 border-amber-200";
  return "bg-sky-100 text-sky-800 border-sky-200";
}

export default function Page() {
  const { data, isLoading } = useTrainingRequirements();
  const create = useCreateTrainingRequirement();
  const deactivate = useDeactivateTrainingRequirement();
  const [roleTemplateId, setRoleTemplateId] = useState("");
  const [courseCode, setCourseCode] = useState("");
  const [level, setLevel] = useState<string>("ADVISORY");
  const [note, setNote] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const items = data?.items ?? [];

  const submit = () => {
    setFormError(null);
    if (!roleTemplateId.trim() || !courseCode.trim()) {
      setFormError("Role template ID and Fundo course code are both required.");
      return;
    }
    create.mutate(
      {
        roleTemplateId: roleTemplateId.trim(),
        courseCode: courseCode.trim(),
        enforcementLevel: level,
        note: note.trim() || undefined,
      },
      {
        onSuccess: () => {
          setRoleTemplateId("");
          setCourseCode("");
          setLevel("ADVISORY");
          setNote("");
        },
        onError: (e) => setFormError(e instanceof Error ? e.message : "Failed to save requirement"),
      },
    );
  };

  return (
    <VashandiShell
      title="Training requirements"
      subtitle="Role → required Fundo course mapping with graduated enforcement (PO-20260629-01)"
    >
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage}
        />
      ) : null}

      <div className="space-y-6">
        <div className="rounded-xl border border-indigo-100 bg-info-soft/60 px-4 py-3 text-sm text-primary-hover">
          <p className="flex items-center gap-2 font-medium">
            <ShieldAlert className="h-4 w-4" />
            Graduated enforcement at assignment precheck/activation
          </p>
          <p className="mt-1 text-xs">
            ADVISORY = warn and allow · SOFT = withhold activation pending a governance override
            (re-level the requirement) · HARD = deny. Satisfaction is evaluated live by the Fundo
            training-gate (completed enrolment + valid certificate). New requirements default to
            ADVISORY — escalation is a deliberate governance action.
          </p>
        </div>

        <section className="rounded-2xl border border-border bg-card p-5" data-testid="training-requirement-form">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <GraduationCap className="h-4 w-4" />
            Add or update a requirement
          </h2>
          {formError ? (
            <p className="mt-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger">
              {formError}
            </p>
          ) : null}
          <div className="mt-3 grid gap-3 md:grid-cols-4">
            <label className="text-xs text-muted-foreground">
              Role template ID
              <input
                value={roleTemplateId}
                onChange={(e) => setRoleTemplateId(e.target.value)}
                data-testid="training-req-role"
                placeholder="e.g. role-nurse"
                className="mt-1 w-full rounded border border-border px-2 py-1.5 text-sm text-foreground"
              />
            </label>
            <label className="text-xs text-muted-foreground">
              Fundo course code
              <input
                value={courseCode}
                onChange={(e) => setCourseCode(e.target.value)}
                data-testid="training-req-course"
                placeholder="e.g. INFECT-CTL"
                className="mt-1 w-full rounded border border-border px-2 py-1.5 text-sm text-foreground"
              />
            </label>
            <label className="text-xs text-muted-foreground">
              Enforcement level
              <select
                value={level}
                onChange={(e) => setLevel(e.target.value)}
                data-testid="training-req-level"
                className="mt-1 w-full rounded border border-border px-2 py-1.5 text-sm text-foreground"
              >
                {LEVELS.map((l) => (
                  <option key={l} value={l}>
                    {l}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs text-muted-foreground">
              Note (audited)
              <input
                value={note}
                onChange={(e) => setNote(e.target.value)}
                data-testid="training-req-note"
                placeholder="Why this requirement exists"
                className="mt-1 w-full rounded border border-border px-2 py-1.5 text-sm text-foreground"
              />
            </label>
          </div>
          <button
            type="button"
            disabled={create.isPending}
            onClick={submit}
            data-testid="training-req-submit"
            className="mt-3 rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {create.isPending ? "Saving…" : "Save requirement"}
          </button>
        </section>

        <section className="rounded-2xl border border-border bg-card p-5">
          <h2 className="text-sm font-semibold text-foreground">Current mapping</h2>
          {isLoading ? (
            <p className="mt-2 text-sm text-muted-foreground">Loading…</p>
          ) : items.length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">
              No training requirements configured. Without a mapping the training-gate never runs —
              no provider is blocked by an unconfigured gate.
            </p>
          ) : (
            <ul className="mt-2 divide-y divide-gray-100">
              {items.map((req) => (
                <li
                  key={String(req.id)}
                  className="flex flex-wrap items-center justify-between gap-3 py-2.5"
                  data-testid={`training-req-row-${req.id}`}
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-foreground">
                      {String(req.roleTemplateId)} → {String(req.courseCode)}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {req.note ? `${String(req.note)} · ` : ""}
                      {req.active === false ? "Inactive" : "Active"}
                      {req.createdBy ? ` · by ${String(req.createdBy)}` : ""}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span
                      className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${levelBadge(String(req.enforcementLevel ?? "ADVISORY"))}`}
                    >
                      {String(req.enforcementLevel ?? "ADVISORY")}
                    </span>
                    {req.active !== false && req.id ? (
                      <button
                        type="button"
                        disabled={deactivate.isPending}
                        onClick={() => deactivate.mutate(String(req.id))}
                        data-testid={`training-req-deactivate-${req.id}`}
                        className="rounded border border-danger/28 px-2 py-1 text-xs font-medium text-danger hover:bg-danger-soft disabled:opacity-50"
                      >
                        Deactivate
                      </button>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </VashandiShell>
  );
}
