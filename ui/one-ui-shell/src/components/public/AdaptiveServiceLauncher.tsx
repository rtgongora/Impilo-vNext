import Link from "next/link";
import type { LucideIcon } from "lucide-react";
import { ArrowRight, LockKeyhole, ShieldCheck } from "lucide-react";

export type PublicServiceAction = {
  title: string;
  description: string;
  href: string;
  icon: LucideIcon;
  access: "public" | "sign-in" | "information";
  emphasis?: "primary" | "emergency" | "standard";
};

/**
 * Container-adaptive service launcher used by the living canvas and public hubs.
 * The component keeps one clear title/action per card and states the trust boundary
 * before the person follows it.
 */
export function AdaptiveServiceLauncher({
  actions,
  compact = false,
}: {
  actions: PublicServiceAction[];
  compact?: boolean;
}) {
  return (
    <div
      className="public-service-launcher grid gap-3"
      data-testid="adaptive-service-launcher"
    >
      {actions.map((action) => {
        const Icon = action.icon;
        const emergency = action.emphasis === "emergency";
        const primary = action.emphasis === "primary";
        return (
          <Link
            key={action.title}
            href={action.href}
            className={[
              "group flex min-w-0 items-start gap-3 rounded-2xl border transition",
              compact ? "p-4" : "p-5",
              emergency
                ? "border-red-200 bg-red-50/70 hover:border-red-300 hover:bg-red-50"
                : primary
                  ? "border-emerald-300 bg-emerald-50 hover:border-emerald-400"
                  : "border-slate-200 bg-white hover:border-emerald-300 hover:shadow-sm",
              "focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600 focus-visible:ring-offset-2",
            ].join(" ")}
          >
            <span
              className={[
                "grid h-11 w-11 shrink-0 place-items-center rounded-xl",
                emergency
                  ? "bg-red-100 text-red-700"
                  : primary
                    ? "bg-emerald-600 text-white"
                    : "bg-emerald-50 text-emerald-700",
              ].join(" ")}
            >
              <Icon className="h-5 w-5" aria-hidden />
            </span>
            <span className="min-w-0 flex-1">
              <span
                className={`block font-semibold ${
                  emergency ? "text-red-800" : "text-slate-950"
                }`}
              >
                {action.title}
              </span>
              <span className="mt-1 block text-sm leading-5 text-slate-600">
                {action.description}
              </span>
              <span
                className={`mt-2 inline-flex items-center gap-1.5 text-xs font-semibold ${
                  emergency ? "text-red-700" : "text-emerald-700"
                }`}
              >
                {action.access === "public" ? (
                  <ShieldCheck className="h-3.5 w-3.5" aria-hidden />
                ) : action.access === "sign-in" ? (
                  <LockKeyhole className="h-3.5 w-3.5" aria-hidden />
                ) : null}
                {action.access === "public"
                  ? "No sign-in needed"
                  : action.access === "sign-in"
                    ? "Sign in at the protected step"
                    : "Public information"}
                <ArrowRight className="h-3.5 w-3.5" aria-hidden />
              </span>
            </span>
          </Link>
        );
      })}
    </div>
  );
}
