"use client";

import Link from "next/link";
import type { ReactNode } from "react";

export function WorkspaceEmptyState(props: {
  title: string;
  description: string;
  actionLabel?: string;
  actionHref?: string;
  icon?: ReactNode;
}) {
  return (
    <div className="relative flex flex-col items-center justify-center overflow-hidden rounded-2xl border border-dashed border-border bg-primary-soft px-6 py-12 text-center">
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.05]"
        style={{
          backgroundImage: "url('/brand/mark-rgb.svg')",
          backgroundRepeat: "no-repeat",
          backgroundPosition: "center",
          backgroundSize: "180px",
        }}
        aria-hidden
      />
      {props.icon ? <div className="relative mb-3 text-muted-foreground">{props.icon}</div> : null}
      <h3 className="relative text-sm font-semibold text-foreground">{props.title}</h3>
      <p className="relative mt-1 max-w-md text-sm text-muted-foreground">{props.description}</p>
      {props.actionLabel && props.actionHref ? (
        <Link
          href={props.actionHref}
          className="relative mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary-hover"
        >
          {props.actionLabel}
        </Link>
      ) : null}
    </div>
  );
}
