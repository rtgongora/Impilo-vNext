"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { ShellTaskManagerView } from "@/components/shell/ShellTaskManagerView";
import { Layers } from "lucide-react";

export default function ShellTaskManagerPage() {
  return (
    <AppLayout>
      <PageShell
        title="Task manager"
        subtitle="Open modules, patient charts, and shell utilities — session-scoped like an OS task list"
        icon={<Layers className="h-6 w-6" />}
      >
        <ShellTaskManagerView />
      </PageShell>
    </AppLayout>
  );
}
