import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { ServiceStatusBoard } from "@/components/public/ServiceStatusBoard";

export const metadata = {
  title: "Service status — Impilo",
  description:
    "An overview of Impilo service groups and their availability — live where monitored, indicative otherwise.",
};

/**
 * Public service-status board (gateway ADR W4). Reachable WITHOUT login. The board itself is a
 * client component (ServiceStatusBoard) that consumes the live public gateway feed and falls back
 * to the authored INDICATIVE board when a group isn't monitored or the feed is unavailable — it
 * never fabricates a live state. The authored descriptions + fallback live in
 * `lib/service-status-board.ts`.
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

      <ServiceStatusBoard />

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
