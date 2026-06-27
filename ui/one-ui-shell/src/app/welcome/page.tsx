import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";

export const metadata = {
  title: "Welcome to Impilo — Zimbabwe's Health Operating System",
  description:
    "Discover Impilo, find care, get emergency information, and create an account or request a Health ID. No Health ID is needed to get started.",
};

/**
 * Public L0 landing (G-CZO-02). Reachable WITHOUT login. Explains what Impilo is and
 * routes a newcomer to get started. Static, server-rendered, no authenticated calls,
 * no personal/health data.
 */
export default function WelcomePage() {
  return (
    <PublicShell>
      <section className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <p className="text-sm font-medium uppercase tracking-wide text-emerald-700">
          Ministry of Health &amp; Child Care
        </p>
        <h1 className="mt-2 text-3xl font-bold text-slate-900 sm:text-4xl">
          Your health, connected and protected.
        </h1>
        <p className="mt-4 max-w-2xl text-lg text-slate-600">
          Impilo is Zimbabwe&apos;s national Health Operating System. Create an account to request a
          Health ID, receive care, view your records, and manage who can see them — with strong
          privacy and your consent at every step.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link
            href="/auth/register"
            className="rounded-lg bg-emerald-600 px-5 py-2.5 font-semibold text-white hover:bg-emerald-700"
          >
            Create an account
          </Link>
          <Link
            href="/auth/login"
            className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 font-semibold text-slate-800 hover:bg-slate-50"
          >
            Sign in
          </Link>
          <Link
            href="/auth/register?intent=health-id"
            className="rounded-lg border border-emerald-300 bg-emerald-50 px-5 py-2.5 font-semibold text-emerald-800 hover:bg-emerald-100"
          >
            Request a Health ID
          </Link>
        </div>
        <p className="mt-3 text-sm text-slate-500">
          You do <strong>not</strong> need a Health ID to get started — create an account first, then
          request one.
        </p>
      </section>

      <section className="mt-8 grid gap-4 sm:grid-cols-3">
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">1 · Account</h2>
          <p className="mt-1 text-sm text-slate-600">
            Start an account in minutes. Begin or continue a Health ID request and get help — no
            private records yet.
          </p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">2 · Temporary Health ID</h2>
          <p className="mt-1 text-sm text-slate-600">
            Receive care, show your ID at a facility, and book selected services while your identity
            is being verified. Sensitive records stay protected.
          </p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">3 · Verified Health ID</h2>
          <p className="mt-1 text-sm text-slate-600">
            Once verified, see your health summary, appointments, results, prescriptions and care
            plans — and control who can access them.
          </p>
        </div>
      </section>

      <section className="mt-8 grid gap-4 sm:grid-cols-2">
        <Link
          href="/welcome/find-care"
          className="group rounded-xl border border-slate-200 bg-white p-6 hover:border-emerald-300 hover:bg-emerald-50/40"
        >
          <h2 className="text-lg font-semibold text-slate-900 group-hover:text-emerald-800">
            Find care near you
          </h2>
          <p className="mt-1 text-sm text-slate-600">
            Major public hospitals, how to visit a facility, and how to get the full facility finder.
          </p>
        </Link>
        <Link
          href="/welcome/emergency"
          className="group rounded-xl border border-red-200 bg-white p-6 hover:border-red-300 hover:bg-red-50/40"
        >
          <h2 className="text-lg font-semibold text-red-700">Emergency &amp; public health</h2>
          <p className="mt-1 text-sm text-slate-600">
            Emergency numbers and current public-health guidance. If life is at risk, call
            emergency services now.
          </p>
        </Link>
      </section>

      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">Built for everyone</h2>
        <p className="mt-1 text-sm text-slate-600">
          Impilo is designed to be usable on low-cost phones and shared devices, with accessibility
          and language options. A health worker can also help you register at a facility if you have
          no smartphone.
        </p>
        <Link
          href="/welcome/accessibility"
          className="mt-3 inline-block text-sm font-medium text-emerald-700 hover:text-emerald-800"
        >
          Accessibility &amp; language options →
        </Link>
      </section>
    </PublicShell>
  );
}
