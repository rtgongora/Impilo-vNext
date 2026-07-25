import Link from "next/link";
import { FileText, Mail, MapPin, MessageSquareHeart, Phone, UsersRound } from "lucide-react";
import { PublicShell } from "@/components/public/PublicShell";

export const metadata = {
  title: "Contact and support — Impilo",
  description:
    "Official Ministry contact details, public feedback, support and participation routes for Impilo.",
};

const CHANNELS = [
  {
    title: "Give feedback or report a concern",
    body: "Start an anonymous or guest report and receive a claim code for status updates.",
    href: "/welcome/report",
    icon: MessageSquareHeart,
  },
  {
    title: "Get involved",
    body: "Share an idea, take part in consultation or help test what comes next.",
    href: "/get-involved",
    icon: UsersRound,
  },
  {
    title: "Service status",
    body: "Check the public operational status before retrying a digital service.",
    href: "/status",
    icon: FileText,
  },
] as const;

export default function ContactPage() {
  return (
    <PublicShell>
      <section className="rounded-[2rem] border border-emerald-100 bg-white p-7 sm:p-10">
        <p className="text-sm font-semibold uppercase tracking-[0.08em] text-emerald-700">
          Contact and support
        </p>
        <h1 className="mt-2 text-4xl font-bold tracking-tight text-slate-950">
          How can we help?
        </h1>
        <p className="mt-3 max-w-3xl text-base leading-7 text-slate-600">
          Choose the route that matches what you need. Emergency help stays available through the
          red Emergency control and never requires sign-in.
        </p>

        <div className="mt-8 grid gap-4 md:grid-cols-3">
          <div className="rounded-2xl bg-emerald-50 p-5">
            <MapPin className="h-5 w-5 text-emerald-700" aria-hidden />
            <h2 className="mt-3 font-semibold text-slate-950">Ministry address</h2>
            <p className="mt-1 text-sm leading-6 text-slate-700">
              Kaguvi Building, Central Avenue, Harare
            </p>
          </div>
          <div className="rounded-2xl bg-emerald-50 p-5">
            <Phone className="h-5 w-5 text-emerald-700" aria-hidden />
            <h2 className="mt-3 font-semibold text-slate-950">General phone</h2>
            <a
              href="tel:+2634798555"
              className="mt-1 inline-flex min-h-10 items-center text-sm font-semibold text-emerald-800 hover:text-emerald-950"
            >
              +263 4 798555/60
            </a>
          </div>
          <div className="rounded-2xl bg-emerald-50 p-5">
            <Mail className="h-5 w-5 text-emerald-700" aria-hidden />
            <h2 className="mt-3 font-semibold text-slate-950">Email</h2>
            <a
              href="mailto:pr@mohcc.gov.zw"
              className="mt-1 inline-flex min-h-10 items-center text-sm font-semibold text-emerald-800 hover:text-emerald-950"
            >
              pr@mohcc.gov.zw
            </a>
          </div>
        </div>
      </section>

      <section className="mt-8 grid gap-4 md:grid-cols-3" aria-label="Support routes">
        {CHANNELS.map(({ title, body, href, icon: Icon }) => (
          <Link
            key={title}
            href={href}
            className="rounded-2xl border border-slate-200 bg-white p-5 hover:border-emerald-300 hover:shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600"
          >
            <Icon className="h-5 w-5 text-emerald-700" aria-hidden />
            <h2 className="mt-3 font-semibold text-slate-950">{title}</h2>
            <p className="mt-2 text-sm leading-6 text-slate-600">{body}</p>
          </Link>
        ))}
      </section>
    </PublicShell>
  );
}
