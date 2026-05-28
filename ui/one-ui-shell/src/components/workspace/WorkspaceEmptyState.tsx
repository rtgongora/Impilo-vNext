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
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50 px-6 py-12 text-center dark:border-slate-700 dark:bg-slate-900/40">
      {props.icon ? <div className="mb-3 text-slate-400">{props.icon}</div> : null}
      <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{props.title}</h3>
      <p className="mt-1 max-w-md text-sm text-slate-500 dark:text-slate-400">{props.description}</p>
      {props.actionLabel && props.actionHref ? (
        <Link
          href={props.actionHref}
          className="mt-4 inline-flex rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-600"
        >
          {props.actionLabel}
        </Link>
      ) : null}
    </div>
  );
}
