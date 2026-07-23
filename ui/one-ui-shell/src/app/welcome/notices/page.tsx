import { PublicShell } from "@/components/public/PublicShell";
import { PublicNoticesBoard } from "@/components/public/PublicNoticesBoard";

export const metadata = {
  title: "Notices & bulletins — Impilo",
  description:
    "Official public notices: health campaigns, product and safety recalls, disaster alerts, regulatory notices and procurement opportunities. Read freely — no sign-in needed.",
};

/**
 * Public Notices & Bulletins archive (PF-W2 / gateway doctrine §13.6). A browsable board
 * of currently-published public notices across categories, reachable WITHOUT sign-in via
 * the anonymous gateway lane. No personal or health data appears here; regulatory and
 * recall notices are shown only when a named authority has approved them for publication.
 */
export default function NoticesPage() {
  return (
    <PublicShell>
      <h1 className="text-3xl font-bold text-slate-900">Notices &amp; bulletins</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        Official public notices from the Ministry of Health &amp; Child Care and the national
        health platform — campaigns, safety and product recalls, disaster alerts, regulatory
        notices and procurement opportunities. Read freely; no account is needed.
      </p>
      <div className="mt-6">
        <PublicNoticesBoard />
      </div>
    </PublicShell>
  );
}
