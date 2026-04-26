"use client";

import type { ReactNode } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { EnterpriseSubnav } from "@/components/enterprise/EnterpriseSubnav";

export function EnterpriseWorkspaceShell(props: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <AppLayout>
      <PageShell title={props.title} subtitle={props.subtitle}>
        <EnterpriseSubnav />
        {props.children}
      </PageShell>
    </AppLayout>
  );
}
