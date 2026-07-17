import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { IdeaBoard } from "@/components/get-involved/IdeaBoard";

export const metadata = {
  title: "Ideas Zimbabwe is exploring — Impilo",
  description:
    "Browse the moderated idea board and back the ideas that matter most to you. No account needed.",
};

/** Public idea board (gateway ADR W4). Reachable WITHOUT login. */
export default function IdeaBoardPage() {
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
        / Ideas board
      </nav>

      <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Ideas Zimbabwe is exploring</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          These are ideas people have shared and our team has published. Back the ones that matter
          to you — the more support an idea has, the more we hear it.
        </p>
        <Link
          href="/get-involved/idea"
          className="mt-4 inline-block rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
        >
          Share your own idea
        </Link>
      </section>

      <section className="mt-6">
        <IdeaBoard />
      </section>
    </PublicShell>
  );
}
