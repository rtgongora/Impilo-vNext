"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useShellStore } from "@/hooks/useShellStore";

export function ShellTaskManagerView() {
  const router = useRouter();
  const openTasks = useShellStore((s) => s.openTasks);
  const activeTaskId = useShellStore((s) => s.activeTaskId);
  const setActiveTask = useShellStore((s) => s.setActiveTask);
  const closeTask = useShellStore((s) => s.closeTask);
  const restoreTask = useShellStore((s) => s.restoreTask);
  const minimizeTask = useShellStore((s) => s.minimizeTask);
  const recentItems = useShellStore((s) => s.recentItems);

  const ordered = [...openTasks].sort(
    (a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime(),
  );

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
      <section className="rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
        <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-50">Open workspaces</h2>
        <p className="mt-1 text-xs text-slate-500">
          Tasks are synced from navigation. Close removes them from this session; reopen modules from Start or the
          sidebar.
        </p>
        <ul className="mt-4 divide-y divide-slate-100 dark:divide-slate-800">
          {ordered.length === 0 ? (
            <li className="py-8 text-center text-sm text-slate-500">No open tasks.</li>
          ) : (
            ordered.map((task) => (
              <li key={task.id} className="flex flex-wrap items-center gap-2 py-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-slate-900 dark:text-slate-50">{task.title}</p>
                  <p className="text-xs text-slate-500">{task.route}</p>
                  <p className="text-[10px] uppercase tracking-wide text-slate-400">
                    {task.taskType} · {task.status}
                    {task.id === activeTaskId ? " · active" : ""}
                  </p>
                </div>
                <div className="flex flex-wrap gap-1">
                  <button
                    type="button"
                    className="rounded-md border border-slate-200 px-2 py-1 text-xs font-medium hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
                    onClick={() => {
                      restoreTask(task.id);
                      setActiveTask(task.id);
                      router.push(task.route);
                    }}
                  >
                    Switch
                  </button>
                  <button
                    type="button"
                    className="rounded-md border border-slate-200 px-2 py-1 text-xs font-medium hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
                    onClick={() => minimizeTask(task.id)}
                  >
                    Minimize
                  </button>
                  <button
                    type="button"
                    className="rounded-md border border-red-200 px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-50 dark:border-red-900 dark:text-red-300 dark:hover:bg-red-950/40"
                    onClick={() => closeTask(task.id)}
                  >
                    Close
                  </button>
                </div>
              </li>
            ))
          )}
        </ul>
      </section>

      <aside className="space-y-4">
        <div className="rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Quick links</h3>
          <ul className="mt-2 space-y-2 text-sm">
            <li>
              <Link href="/home" className="text-impilo-600 hover:underline">
                Home dashboard
              </Link>
            </li>
            <li>
              <Link href="/shell/file-manager" className="text-impilo-600 hover:underline">
                File manager
              </Link>
            </li>
            <li>
              <button
                type="button"
                className="text-left text-impilo-600 hover:underline"
                onClick={() => useShellStore.getState().toggleSearch()}
              >
                Global search
              </button>
            </li>
          </ul>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Recent shell activity</h3>
          <ul className="mt-2 max-h-64 space-y-2 overflow-y-auto text-xs text-slate-600 dark:text-slate-300">
            {recentItems.slice(0, 12).map((r) => (
              <li key={r.id}>
                <Link href={r.href} className="line-clamp-2 hover:text-impilo-600 hover:underline">
                  {r.title}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </aside>
    </div>
  );
}
