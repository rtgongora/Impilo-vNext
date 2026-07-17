import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import {
  SERVICE_STATUS_BOARD,
  STATE_META,
  STATUS_BOARD_UPDATED,
} from "@/lib/service-status-board";

export const metadata = {
  title: "Service status — Impilo",
  description:
    "An indicative overview of Impilo service groups and their general availability. Not a live status feed.",
};

/**
 * Public service-status board (gateway ADR W4). Reachable WITHOUT login. Sourced from a local,
 * clearly labelled INDICATIVE config — a live status backend is a later wave. We never fabricate
 * a live state.
 */
export default function ServiceStatusPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        / Service status
      </nav>

      <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Service status</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          A general overview of Impilo&apos;s main service groups and how available they are.
        </p>
        <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          <strong>Indicative only.</strong> This is not a live status feed. A real-time status
          service is on the way; until then, this page shows an authored overview last reviewed on{" "}
          {new Date(STATUS_BOARD_UPDATED).toLocaleDateString()}. For an emergency, always use{" "}
          <Link href="/welcome/emergency" className="font-semibold underline">
            emergency help
          </Link>
          .
        </div>
      </section>

      <section className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <ul className="divide-y divide-slate-100">
          {SERVICE_STATUS_BOARD.map((entry) => {
            const meta = STATE_META[entry.state];
            return (
              <li key={entry.group} className="flex items-start justify-between gap-4 p-5">
                <div>
                  <p className="font-semibold text-slate-900">{entry.group}</p>
                  <p className="mt-1 text-sm text-slate-600">{entry.description}</p>
                </div>
                <span
                  className={`inline-flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${meta.badge}`}
                >
                  <span className={`h-2 w-2 rounded-full ${meta.dot}`} aria-hidden="true" />
                  {meta.label}
                </span>
              </li>
            );
          })}
        </ul>
      </section>

      <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">Seeing a problem we&apos;re not showing?</h2>
        <p className="mt-1 text-sm text-slate-600">
          Tell us what&apos;s not working — it helps us fix it and keep this page honest.
        </p>
        <div className="mt-3 flex flex-wrap gap-3">
          <Link
            href="/welcome/report"
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
          >
            Report a problem
          </Link>
          <Link
            href="/get-involved"
            className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            Get involved
          </Link>
        </div>
      </section>
    </PublicShell>
  );
}
