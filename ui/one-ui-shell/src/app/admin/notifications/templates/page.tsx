"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, Mail } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateNotificationTemplate,
  useNotificationTemplates,
  usePublishNotificationTemplate,
  useRetireNotificationTemplate,
} from "@/hooks/queries/useNotificationTemplates";

export default function NotificationTemplatesAdminPage() {
  const { data: templates, isLoading } = useNotificationTemplates();
  const createTemplate = useCreateNotificationTemplate();
  const publishTemplate = usePublishNotificationTemplate();
  const retireTemplate = useRetireNotificationTemplate();
  const [form, setForm] = useState({ key: "", name: "", channel: "SMS", body: "" });

  return (
    <AppLayout>
      <PageShell title="Notification templates" subtitle="Governed template CRUD via notification-service BFF">
        <Link href="/admin" className="mb-4 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
          <ArrowLeft className="h-4 w-4" />
          Administration
        </Link>

        <form
          className="mb-6 grid gap-3 rounded-xl border border-gray-200 bg-white p-4 sm:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            createTemplate.mutate({
              key: form.key,
              name: form.name,
              channel: form.channel,
              body: form.body,
            });
          }}
        >
          <h3 className="sm:col-span-2 flex items-center gap-2 text-sm font-semibold text-gray-900">
            <Mail className="h-4 w-4 text-teal-600" />
            Create template
          </h3>
          <input
            className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="Template key"
            value={form.key}
            onChange={(e) => setForm((p) => ({ ...p, key: e.target.value }))}
            required
          />
          <input
            className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="Display name"
            value={form.name}
            onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
            required
          />
          <select
            className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
            value={form.channel}
            onChange={(e) => setForm((p) => ({ ...p, channel: e.target.value }))}
          >
            <option value="SMS">SMS</option>
            <option value="EMAIL">Email</option>
            <option value="PUSH">Push</option>
          </select>
          <textarea
            className="sm:col-span-2 rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="Message body"
            rows={3}
            value={form.body}
            onChange={(e) => setForm((p) => ({ ...p, body: e.target.value }))}
          />
          <button
            type="submit"
            disabled={createTemplate.isPending}
            className="sm:col-span-2 rounded-lg bg-teal-700 px-4 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-50"
          >
            {createTemplate.isPending ? "Creating…" : "Create template"}
          </button>
        </form>

        {isLoading ? (
          <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
        ) : (
          <ul className="divide-y divide-gray-100 rounded-xl border border-gray-200 bg-white">
            {(templates ?? []).length === 0 ? (
              <li className="p-4 text-sm text-gray-500">No templates returned from notification-service.</li>
            ) : (
              templates?.map((row) => {
                const id = String(row.id ?? row.key ?? "");
                return (
                  <li key={id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                    <div>
                      <p className="text-sm font-medium text-gray-900">{String(row.name ?? row.key ?? id)}</p>
                      <p className="text-xs text-gray-500">
                        {String(row.key ?? "—")} · {String(row.channel ?? "—")} · {String(row.status ?? "DRAFT")}
                      </p>
                    </div>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => publishTemplate.mutate(id)}
                        disabled={publishTemplate.isPending}
                        className="rounded-lg border border-teal-200 px-3 py-1 text-xs font-medium text-teal-800 hover:bg-teal-50"
                      >
                        Publish
                      </button>
                      <button
                        type="button"
                        onClick={() => retireTemplate.mutate(id)}
                        disabled={retireTemplate.isPending}
                        className="rounded-lg border border-gray-200 px-3 py-1 text-xs font-medium text-gray-600 hover:bg-gray-50"
                      >
                        Retire
                      </button>
                    </div>
                  </li>
                );
              })
            )}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
