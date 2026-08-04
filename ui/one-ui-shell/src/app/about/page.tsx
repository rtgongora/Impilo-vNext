import Link from "next/link";
import { Activity, CloudCog, HeartHandshake, ShieldCheck } from "lucide-react";
import { PublicShell } from "@/components/public/PublicShell";
import { PublicVisualAsset } from "@/components/public/PublicVisualAsset";

export const metadata = {
  title: "About Impilo — Zimbabwe's National Health Operating System",
  description:
    "Learn how Impilo connects public discovery, protected care, professional work and national health-system services in one governed environment.",
};

const OBJECTIVES = [
  {
    title: "Better continuity of care",
    body: "Help people and authorised care teams move safely through services without restarting the story at every facility.",
    icon: HeartHandshake,
  },
  {
    title: "Calmer health-system work",
    body: "Carry operational complexity in the platform so health workers can focus on the person and the next safe action.",
    icon: Activity,
  },
  {
    title: "Connected national infrastructure",
    body: "Link care, registries, public health, learning, diagnostics, logistics and finance through governed contracts.",
    icon: CloudCog,
  },
  {
    title: "Progressive trust",
    body: "Keep public information open while protecting personal, professional and institutional actions through TSHEPO.",
    icon: ShieldCheck,
  },
] as const;

export default function AboutPage() {
  return (
    <PublicShell>
      <section className="grid overflow-hidden rounded-[2rem] border border-emerald-100 bg-white lg:grid-cols-2">
        <div className="flex flex-col justify-center p-7 sm:p-10 lg:p-12">
          <p className="text-sm font-semibold uppercase tracking-[0.08em] text-emerald-700">
            About Impilo
          </p>
          <h1 className="mt-3 text-4xl font-bold tracking-tight text-slate-950 sm:text-5xl">
            One national health operating system
          </h1>
          <p className="mt-5 text-base leading-7 text-slate-700">
            Impilo is led by Zimbabwe&apos;s Ministry of Health and Child Care. It joins public
            service discovery, protected personal health, professional practice, facility work,
            regulation and national health-system operations in one coherent environment.
          </p>
          <p className="mt-3 text-base leading-7 text-slate-700">
            Public and authenticated experiences are not separate products. Identity is introduced
            only when personalisation, continuity, protected information, accountability or
            authority requires it.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Link
              href="/#services"
              className="inline-flex min-h-11 items-center rounded-xl bg-emerald-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-emerald-800"
            >
              Explore public services
            </Link>
            <Link
              href="/get-involved"
              className="inline-flex min-h-11 items-center rounded-xl border border-emerald-300 bg-white px-5 py-2.5 text-sm font-semibold text-emerald-900 hover:bg-emerald-50"
            >
              Help shape Impilo
            </Link>
          </div>
        </div>
        <PublicVisualAsset
          src="/experience/hero/hero-fibre-install.webp"
          compactSrc="/experience/hero/hero-fibre-install-960.webp"
          alt="Zimbabwean engineers connecting a health facility to digital infrastructure"
          objectPosition="center 42%"
          className="min-h-80 lg:min-h-[34rem]"
          fallback={
            <>
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-emerald-200">
                Health. Life. Connected.
              </p>
              <p className="mt-2 max-w-md text-2xl font-bold">
                Built for public help, protected care and accountable work.
              </p>
            </>
          }
        />
      </section>

      <section id="how-impilo-works" className="mt-12 scroll-mt-28" aria-labelledby="objectives-title">
        <h2 id="objectives-title" className="text-3xl font-bold tracking-tight text-slate-950">
          What Impilo is designed to do
        </h2>
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {OBJECTIVES.map(({ title, body, icon: Icon }) => (
            <article key={title} className="rounded-2xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-5">
              <span className="grid h-11 w-11 place-items-center rounded-xl bg-emerald-50 text-emerald-700">
                <Icon className="h-5 w-5" aria-hidden />
              </span>
              <h3 className="mt-4 font-semibold text-slate-950">{title}</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">{body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="mt-12 rounded-[2rem] border border-sky-200 bg-sky-50 p-7 sm:p-9">
        <h2 className="text-2xl font-bold text-slate-950">Trust is progressive</h2>
        <p className="mt-3 max-w-4xl text-base leading-7 text-slate-700">
          Authentication confirms a person&apos;s identity. It does not automatically prove that
          someone may practise as a professional, act for a facility, review a regulatory case or
          make a payer decision. TSHEPO resolves those relationships and authorities separately.
        </p>
        <div className="mt-5 flex flex-wrap gap-4 text-sm font-semibold">
          <Link href="/privacy" className="text-sky-800 hover:text-sky-950">
            Privacy and consent
          </Link>
          <Link href="/provider/get-access" className="text-sky-800 hover:text-sky-950">
            How professional access works
          </Link>
          <Link href="/status" className="text-sky-800 hover:text-sky-950">
            Public service status
          </Link>
        </div>
      </section>
    </PublicShell>
  );
}
