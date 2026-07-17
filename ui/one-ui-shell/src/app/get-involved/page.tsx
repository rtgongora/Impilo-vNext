import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";

export const metadata = {
  title: "Get involved — help build Impilo",
  description:
    "Impilo is growing — with you. Share an idea, tell us your experience, help test Impilo, and see the ideas Zimbabwe is exploring. No account needed.",
};

/**
 * Public "Get Involved" home (gateway ADR W4). Reachable WITHOUT login. Presents the
 * participation pathways and draws the honest split between "something went wrong"
 * (the report / Rito lane) and "something could be better" (the idea lane here).
 */
const PATHWAYS: Array<{ title: string; description: string; href: string; badge: string }> = [
  {
    title: "Share an idea",
    description: "Suggest something that could make health care work better for you or others.",
    href: "/get-involved/idea",
    badge: "No sign-in needed",
  },
  {
    title: "Tell us your experience",
    description: "Describe an experience you had — good or bad — so we can learn from it.",
    href: "/get-involved/idea?type=EXPERIENCE",
    badge: "No sign-in needed",
  },
  {
    title: "See ideas Zimbabwe is exploring",
    description: "Browse the ideas board and back the ideas that matter most to you.",
    href: "/get-involved/board",
    badge: "Open board",
  },
  {
    title: "Help test Impilo",
    description: "Join a testing group and help shape features before they go live.",
    href: "/get-involved/test",
    badge: "Volunteer",
  },
  {
    title: "Track a submission",
    description: "Check what happened to an idea you shared, using your claim code.",
    href: "/get-involved/track",
    badge: "Claim code",
  },
];

export default function GetInvolvedPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        / Get involved
      </nav>

      <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <p className="text-sm font-medium uppercase tracking-wide text-emerald-700">
          Impilo is growing — with you
        </p>
        <h1 className="mt-2 text-3xl font-bold text-slate-900 sm:text-4xl">
          Your experience can improve health care
        </h1>
        <p className="mt-4 max-w-2xl text-lg text-slate-600">
          Impilo is Zimbabwe&apos;s National Health Operating System, and it is still being built.
          The best ideas come from the people who use it. Tell us what matters to you — no account
          needed, and you can follow what happens next.
        </p>
      </section>

      {/* The two clear paths — honest routing. */}
      <section className="mt-6 grid gap-4 sm:grid-cols-2">
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-6">
          <h2 className="font-semibold text-amber-900">Something went wrong</h2>
          <p className="mt-1 text-sm text-amber-800">
            Unsafe care, a safety concern or a complaint? Report it and we&apos;ll open a tracked
            case.
          </p>
          <Link
            href="/welcome/report"
            className="mt-3 inline-block rounded-lg bg-amber-600 px-4 py-2 text-sm font-semibold text-white hover:bg-amber-700"
          >
            Report a problem
          </Link>
        </div>
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-6">
          <h2 className="font-semibold text-emerald-900">Something could be better</h2>
          <p className="mt-1 text-sm text-emerald-800">
            Have an idea or an experience to share? Help us shape what Impilo becomes.
          </p>
          <Link
            href="/get-involved/idea"
            className="mt-3 inline-block rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
          >
            Share an idea
          </Link>
        </div>
      </section>

      <section className="mt-8" aria-labelledby="pathways-heading">
        <h2 id="pathways-heading" className="text-2xl font-bold text-slate-900">
          Ways to take part
        </h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {PATHWAYS.map((p) => (
            <Link
              key={p.title}
              href={p.href}
              className="group rounded-xl border border-slate-200 bg-white p-5 transition hover:border-emerald-300 hover:bg-emerald-50/40"
            >
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-semibold text-slate-900 group-hover:text-emerald-800">{p.title}</h3>
                <span className="shrink-0 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700">
                  {p.badge}
                </span>
              </div>
              <p className="mt-1 text-sm text-slate-600">{p.description}</p>
            </Link>
          ))}
        </div>
      </section>
    </PublicShell>
  );
}
