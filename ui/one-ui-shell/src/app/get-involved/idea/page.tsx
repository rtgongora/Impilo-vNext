import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { GetInvolvedIdeaForm } from "@/components/get-involved/GetInvolvedIdeaForm";

export const metadata = {
  title: "Share an idea — Impilo",
  description:
    "Share an idea or an experience that could make health care better. Anonymous, no account needed, with a claim code to follow what happens next.",
};

/**
 * Public idea / experience submission (gateway ADR W4). Reachable WITHOUT login. The
 * "something could be better" surface — distinct from the report lane. The type can be
 * pre-selected via ?type=EXPERIENCE from the Get Involved home.
 */
export default function ShareIdeaPage({
  searchParams,
}: {
  searchParams?: { type?: string };
}) {
  const defaultType = searchParams?.type?.toUpperCase() === "EXPERIENCE" ? "EXPERIENCE" : "IDEA";
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        /{" "}
        <Link href="/get-involved" className="hover:text-slate-900">
          Get involved
        </Link>{" "}
        / Share an idea
      </nav>

      <section className="mt-3 rounded-2xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Share an idea</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          Start with the need. Tell us what could be better and what a better experience would look
          like — a few words is enough. You do not need an account.
        </p>
      </section>

      <section className="mt-6 rounded-2xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-6 sm:p-8">
        <GetInvolvedIdeaForm defaultType={defaultType} />
      </section>
    </PublicShell>
  );
}
