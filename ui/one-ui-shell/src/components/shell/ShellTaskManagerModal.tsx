"use client";

import { useRouter } from "next/navigation";
import { X } from "lucide-react";
import { useShellStore } from "@/hooks/useShellStore";

export function ShellTaskManagerModal() {
  const router = useRouter();
  const open = useShellStore((s) => s.taskManagerOpen);
  const setTaskManagerOpen = useShellStore((s) => s.setTaskManagerOpen);
  const openTasks = useShellStore((s) => s.openTasks);
  const activeTaskId = useShellStore((s) => s.activeTaskId);
  const setActiveTask = useShellStore((s) => s.setActiveTask);
  const closeTask = useShellStore((s) => s.closeTask);
  const restoreTask = useShellStore((s) => s.restoreTask);
  const minimizeTask = useShellStore((s) => s.minimizeTask);

  if (!open) return null;

  const ordered = [...openTasks].sort(
    (a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime(),
  );

  return (
    <div className="fixed inset-0 z-[10001] flex items-end justify-center pb-[64px]">
      <button
        type="button"
        className="absolute inset-0 bg-black/40 backdrop-blur-sm"
        aria-label="Close task manager"
        onClick={() => setTaskManagerOpen(false)}
      />
      <div className="relative flex max-h-[70vh] w-[min(720px,100vw-1rem)] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-950">
        <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-slate-800">
          <div>
            <p className="text-sm font-semibold text-slate-900 dark:text-slate-50">Task manager</p>
            <p className="text-xs text-slate-500">Switch, minimize, or close open workspaces</p>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900"
            onClick={() => setTaskManagerOpen(false)}
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <ul className="min-h-0 flex-1 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800">
          {ordered.length === 0 ? (
            <li className="px-4 py-10 text-center text-sm text-slate-500">No open tasks yet — navigate to a module.</li>
          ) : (
            ordered.map((task) => (
              <li key={task.id} className="flex flex-wrap items-center gap-2 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-900 dark:text-slate-50">{task.title}</p>
                  <p className="truncate text-xs text-slate-500">{task.route}</p>
                  <p className="text-[10px] uppercase tracking-wide text-slate-400">
                    {task.taskType} · {task.status}
                    {task.id === activeTaskId ? " · active" : ""}
                  </p>
                </div>
                <div className="flex shrink-0 gap-1">
                  <button
                    type="button"
                    className="rounded-md border border-slate-200 px-2 py-1 text-xs font-medium hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-900"
                    onClick={() => {
                      restoreTask(task.id);
                      setActiveTask(task.id);
                      router.push(task.route);
                      setTaskManagerOpen(false);
                    }}
                  >
                    Switch
                  </button>
                  <button
                    type="button"
                    className="rounded-md border border-slate-200 px-2 py-1 text-xs font-medium hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-900"
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
        <div className="border-t border-slate-100 px-4 py-2 text-right dark:border-slate-800">
          <button
            type="button"
            className="text-xs font-medium text-impilo-600 hover:underline"
            onClick={() => {
              router.push("/shell/task-manager");
              setTaskManagerOpen(false);
            }}
          >
            Open full task manager
          </button>
        </div>
      </div>
    </div>
  );
}
