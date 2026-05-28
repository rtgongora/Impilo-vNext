"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";

export interface RelatedServiceLink {
  label: string;
  href: string;
  description?: string;
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
        {props.links.map((link) => (
          <li key={link.href}>
            <Link
              href={link.href}
              className="group flex items-start gap-2 rounded-xl px-2 py-2 transition hover:bg-[var(--primary-soft)]"
            >
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium text-[var(--text-primary)] group-hover:text-impilo-700">
                  {link.label}
                </span>
                {link.description ? (
                  <span className="block text-xs text-[var(--text-secondary)]">{link.description}</span>
                ) : null}
              </span>
              <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-[var(--border-strong)] group-hover:text-impilo-600" />
            </Link>
          </li>
        ))}
      </ul>
    </aside>
  );
}
