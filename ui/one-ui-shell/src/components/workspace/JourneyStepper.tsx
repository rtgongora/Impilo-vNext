"use client";

import Link from "next/link";
import { CheckCircle2, Circle, ChevronRight } from "lucide-react";

export interface JourneyStep {
  id: string;
  label: string;
  description: string;
  href: string;
  status?: "complete" | "current" | "upcoming" | "blocked";
  statusNote?: string;
}

export function JourneyStepper(props: {
  title: string;
  subtitle?: string;
  steps: JourneyStep[];
}) {
  return (
    <div className="impilo-surface-card p-4">
      <div className="mb-4">
        <h3 className="text-sm font-semibold text-[var(--text-primary)]">{props.title}</h3>
        {props.subtitle ? (
          <p className="mt-1 text-xs text-[var(--text-muted)]">{props.subtitle}</p>
        ) : null}
      </div>
      <ol className="space-y-3">
        {props.steps.map((step, index) => {
          const isBlocked = step.status === "blocked";
          const Icon = step.status === "complete" ? CheckCircle2 : Circle;
          const iconClass =
            step.status === "complete"
              ? "text-success"
              : step.status === "current"
                ? "text-primary"
                : step.status === "blocked"
                  ? "text-[var(--text-muted)]"
                  : "text-[var(--border-strong)]";

          return (
            <li key={step.id} className="flex gap-3">
              <div className="flex flex-col items-center">
                <Icon className={`h-5 w-5 shrink-0 ${iconClass}`} />
                {index < props.steps.length - 1 ? (
                  <div className="mt-1 h-full min-h-[1.5rem] w-px bg-[var(--border-soft)]" />
                ) : null}
              </div>
              <div className="min-w-0 flex-1 pb-2">
                {isBlocked ? (
                  <div>
                    <p className="text-sm font-medium text-[var(--text-muted)]">{step.label}</p>
                    <p className="text-xs text-[var(--text-muted)]">{step.description}</p>
                    {step.statusNote ? (
                      <p className="mt-1 text-xs text-warning">{step.statusNote}</p>
                    ) : null}
                  </div>
                ) : (
                  <Link
                    href={step.href}
                    className="group -m-1 block rounded-xl p-1 transition hover:bg-[var(--primary-soft)]"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <p className="text-sm font-medium text-[var(--text-primary)] group-hover:text-primary-hover">
                          {step.label}
                        </p>
                        <p className="text-xs text-[var(--text-secondary)]">{step.description}</p>
                        {step.statusNote ? (
                          <p className="mt-1 text-xs text-[var(--text-muted)]">{step.statusNote}</p>
                        ) : null}
                      </div>
                      <ChevronRight className="mt-0.5 h-4 w-4 shrink-0 text-[var(--border-strong)] group-hover:text-primary" />
                    </div>
                  </Link>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
