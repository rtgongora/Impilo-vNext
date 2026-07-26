import type { ReactNode } from "react";
import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { GlassSurface } from "shared-ui";

/**
 * Shared surface primitives for the public lane.
 *
 * Every `app/welcome/**` page hand-wrote the same breadcrumb + h1 + lede preamble and
 * the same four card shapes — `rounded-xl border border-slate-200 bg-white` and its
 * variants, ~65 times across the lane — while importing nothing from `shared-ui`, even
 * though `GlassSurface` was already adopted in 68 files elsewhere in the shell. These
 * primitives close that gap so the refreshed landing look reaches the deeper journeys
 * without each page re-deciding what a card is.
 *
 * They sit on `GlassSurface`, whose tokens are theme-aware: in the public lane's light
 * theme `--glass-fill` resolves to `rgba(255,255,255,.62)` over the shell's teal wash,
 * and it carries its own AA-safe opaque fallback for browsers without `backdrop-filter`
 * and for the `.low-blur` tier. So this is softer and more layered than the flat white
 * it replaces, without becoming unreadable on a low-end device.
 */

export interface Crumb {
  label: string;
  href?: string;
}

/**
 * Page preamble: breadcrumb, title, lede and an optional action row. Replaces the
 * hand-written `<nav>` + `text-3xl font-bold` + `mt-3 max-w-2xl` block on every
 * deeper public page.
 */
export function PublicPageHeader({
  crumbs = [],
  title,
  lede,
  actions,
  id,
}: {
  crumbs?: Crumb[];
  title: string;
  lede?: ReactNode;
  actions?: ReactNode;
  id?: string;
}) {
  return (
    <header className="max-w-3xl">
      {crumbs.length > 0 && (
        <nav aria-label="Breadcrumb" className="mb-3">
          <ol className="flex flex-wrap items-center gap-1 text-sm text-slate-600">
            {crumbs.map((c, i) => (
              <li key={`${c.label}-${i}`} className="inline-flex items-center gap-1">
                {i > 0 && <ChevronRight className="h-3.5 w-3.5 text-slate-400" aria-hidden />}
                {c.href ? (
                  <Link
                    href={c.href}
                    className="rounded font-medium hover:text-emerald-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600"
                  >
                    {c.label}
                  </Link>
                ) : (
                  <span aria-current="page" className="text-slate-500">
                    {c.label}
                  </span>
                )}
              </li>
            ))}
          </ol>
        </nav>
      )}

      <h1
        id={id}
        className="text-[clamp(1.6rem,3vw,2.25rem)] font-extrabold leading-tight tracking-[-0.02em] text-slate-950"
      >
        {title}
      </h1>
      {lede && <p className="mt-3 text-base leading-7 text-slate-600">{lede}</p>}
      {actions && <div className="mt-5 flex flex-wrap items-center gap-2.5">{actions}</div>}
    </header>
  );
}

/**
 * A titled band of content. `eyebrow` carries the small uppercase kicker the landing
 * uses, so below-fold sections and deeper pages share one rhythm.
 */
export function PublicSection({
  eyebrow,
  title,
  lede,
  id,
  children,
  className = "",
}: {
  eyebrow?: string;
  title?: string;
  lede?: ReactNode;
  id?: string;
  children: ReactNode;
  className?: string;
}) {
  const headingId = id ? `${id}-title` : undefined;
  return (
    <section
      id={id}
      aria-labelledby={title ? headingId : undefined}
      className={`mt-14 scroll-mt-28 ${className}`}
    >
      {(eyebrow || title || lede) && (
        <div className="max-w-3xl">
          {eyebrow && (
            <p className="text-sm font-semibold uppercase tracking-[0.08em] text-emerald-700">
              {eyebrow}
            </p>
          )}
          {title && (
            <h2
              id={headingId}
              className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl"
            >
              {title}
            </h2>
          )}
          {lede && <p className="mt-3 text-base leading-7 text-slate-600">{lede}</p>}
        </div>
      )}
      <div className={eyebrow || title || lede ? "mt-6" : ""}>{children}</div>
    </section>
  );
}

type CardVariant = "panel" | "item" | "accent" | "alarm";

/**
 * The four shapes the public lane actually uses.
 *
 * `alarm` deliberately does NOT use glass: emergency surfaces already carry a hard
 * red-bordered treatment, and softening an alarm to match the rest of the page would
 * trade a safety signal for visual consistency. It keeps the solid card and only
 * inherits the shared radius.
 */
export function PublicCard({
  variant = "panel",
  as: Tag = "div",
  className = "",
  children,
  ...rest
}: {
  variant?: CardVariant;
  /** `li` for cards inside a list; the props type stays element-agnostic. */
  as?: "div" | "li" | "article";
  className?: string;
  children: ReactNode;
} & React.HTMLAttributes<HTMLElement>) {
  const padding = variant === "item" ? "p-4" : "p-6";

  if (variant === "alarm") {
    return (
      <Tag
        className={`rounded-2xl border-2 border-red-300 bg-red-50 ${padding} ${className}`}
        {...rest}
      >
        {children}
      </Tag>
    );
  }

  if (variant === "accent") {
    return (
      <Tag
        className={`rounded-2xl border border-emerald-200/80 bg-emerald-50/70 ${padding} ${className}`}
        {...rest}
      >
        {children}
      </Tag>
    );
  }

  return (
    <GlassSurface
      blur="soft"
      className={`rounded-2xl ${padding} ${className}`}
      {...(rest as React.HTMLAttributes<HTMLDivElement>)}
    >
      {children}
    </GlassSurface>
  );
}
