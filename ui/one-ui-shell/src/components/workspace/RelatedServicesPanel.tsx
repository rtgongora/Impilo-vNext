"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { ServiceLogo } from "@/components/branding/ServiceLogo";
import { resolveServiceSlug } from "@/config/serviceBranding";

export interface RelatedServiceLink {
  label: string;
  href: string;
  description?: string;
  /** Sovereign service slug for branded logo */
  serviceSlug?: string;
}

export function RelatedServicesPanel(props: {
  title?: string;
  links: RelatedServiceLink[];
}) {
  if (props.links.length === 0) return null;

  return (
    <aside data-testid="related-services-panel" className="impilo-surface-card p-4">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-[var(--text-muted)]">
        {props.title ?? "Related services"}
      </h3>
      <ul className="mt-3 space-y-2">
        {props.links.map((link) => {
          const slug = link.serviceSlug ?? resolveServiceSlug(link.label) ?? resolveServiceSlug(link.href);
          return (
          <li key={link.href}>
            <Link
              href={link.href}
              className="group flex items-start gap-2 rounded-xl px-2 py-2 transition hover:bg-[var(--primary-soft)]"
            >
              {slug ? (
                <ServiceLogo slug={slug} size="compact" className="mt-0.5" />
              ) : null}
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium text-[var(--text-primary)] group-hover:text-primary-hover">
                  {link.label}
                </span>
                {link.description ? (
                  <span className="block text-xs text-[var(--text-secondary)]">{link.description}</span>
                ) : null}
              </span>
              <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-[var(--border-strong)] group-hover:text-primary" />
            </Link>
          </li>
          );
        })}
      </ul>
    </aside>
  );
}
