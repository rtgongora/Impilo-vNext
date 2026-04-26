"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { ShellFileExplorer } from "@/components/shell/ShellFileExplorer";
import { FolderOpen } from "lucide-react";

export default function ShellFileManagerPage() {
  return (
    <AppLayout>
      <PageShell
        title="File manager"
        subtitle="vNext-aware document and resource explorer — personal vault, indexed knowledge, and pathways into chart documents"
        icon={<FolderOpen className="h-6 w-6" />}
      >
        <ShellFileExplorer />
      </PageShell>
    </AppLayout>
  );
}
