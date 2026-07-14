"use client";

/**
 * Triage branches for the public "Report a health incident" page. Each branch routes to
 * an existing, real destination — the emergency surface is always open; the safety/
 * complaint branch carries a gateway intent through the sign-in gate until the anonymous
 * public intake lane exists (gateway-public-lane ADR W4). No dead surfaces.
 */

import Link from "next/link";
import { IntentLink } from "@/components/public/IntentLink";

const FEEDBACK_DEST = "/my-life/feedback/new?type=SAFETY_CONCERN";

export function ReportIncidentTriage() {
  return (
    <div className="mt-6 grid gap-4 sm:grid-cols-2" data-testid="report-incident-triage">
      <Link
        href="/welcome/emergency"
        data-testid="report-branch-emergency"
        className="group rounded-xl border border-red-200 bg-white p-6 transition hover:border-red-300 hover:bg-red-50/40"
      >
        <h3 className="text-lg font-semibold text-red-700">
          Someone needs help right now
        </h3>
        <p className="mt-1 text-sm text-slate-600">
          A medical emergency, accident or urgent danger. See emergency numbers and request
          assistance immediately — no account needed.
        </p>
        <span className="mt-3 inline-block rounded-full bg-red-50 px-2 py-0.5 text-[11px] font-medium text-red-700">
          Never blocked by sign-in
        </span>
      </Link>

      <Link
        href="/welcome/report/anonymous"
        data-testid="report-branch-anonymous"
        className="group rounded-xl border border-slate-200 bg-white p-6 transition hover:border-emerald-300 hover:bg-emerald-50/40"
      >
        <h3 className="text-lg font-semibold text-slate-900 group-hover:text-emerald-800">
          Unsafe care, a safety concern, or a complaint
        </h3>
        <p className="mt-1 text-sm text-slate-600">
          Report without an account. You get a claim code to check the outcome — your
          identity is never recorded.
        </p>
        <span className="mt-3 inline-block rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700">
          No sign-in needed
        </span>
      </Link>

      <IntentLink
        pillar="feedback-complaints"
        goal="report-incident"
        dest={FEEDBACK_DEST}
        from="/welcome/report"
        href={`/auth/login?returnTo=${encodeURIComponent(FEEDBACK_DEST)}`}
        className="group rounded-xl border border-slate-200 bg-white p-6 transition hover:border-emerald-300 hover:bg-emerald-50/40"
      >
        <h3 className="text-lg font-semibold text-slate-900 group-hover:text-emerald-800">
          Report from your account
        </h3>
        <p className="mt-1 text-sm text-slate-600">
          Sign in to open a tracked case in your name (or anonymously) and follow it from
          your feedback list.
        </p>
        <span className="mt-3 inline-block rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600">
          Sign in to continue
        </span>
      </IntentLink>

      <Link
        href="/welcome/report/status"
        data-testid="report-branch-status"
        className="group rounded-xl border border-slate-200 bg-white p-6 transition hover:border-emerald-300 hover:bg-emerald-50/40 sm:col-span-2"
      >
        <h3 className="font-semibold text-slate-900 group-hover:text-emerald-800">
          Already reported? Check the outcome
        </h3>
        <p className="mt-1 text-sm text-slate-600">
          Use the claim code you were given to see the status of an anonymous report.
        </p>
      </Link>
    </div>
  );
}
