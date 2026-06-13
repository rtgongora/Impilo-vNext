"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMemo } from "react";
import { Bell, HelpCircle, LifeBuoy, Shield } from "lucide-react";
import { NotificationsCommsHub } from "@/components/notifications/NotificationsCommsHub";
import { useAssistantNotifications } from "@/hooks/queries/useAssistantNotifications";

/**
 * Operational Comms / Help / System Support strip for clinical and EHR shells.
 * Notification counts come from NotificationsCommsHub (API-backed); no mock badges here.
 */
export function ClinicalSupportStrip() {
  const pathname = usePathname() ?? "";
  const isPatientChart = pathname.startsWith("/ehr/");
  const chartPatientCpid = useMemo(() => {
    const m = pathname.match(/^\/ehr\/([^/]+)/);
    return m ? m[1] : undefined;
  }, [pathname]);
  const { data: assistant = [], isLoading: assistantLoading } = useAssistantNotifications(isPatientChart);

  return (
    <div className="h-9 border-b border-border bg-background/90 px-3 flex items-center justify-between gap-2 text-xs shrink-0">
      <div className="flex min-w-0 items-center gap-1 sm:gap-2">
        {isPatientChart ? (
          <>
            <Link
              href="/home/notifications"
              className="inline-flex max-w-[50vw] items-center gap-1 truncate rounded-md px-2 py-1 text-muted-foreground hover:bg-card hover:text-primary-hover sm:max-w-none"
              title="BFF assistant signals and system notification feed"
            >
              <Bell className="h-3.5 w-3.5 shrink-0" />
              <span className="hidden font-medium sm:inline">Assistant</span>
              <span className="rounded bg-card/80 px-1 font-mono text-[10px] text-foreground">
                {assistantLoading ? "…" : assistant.length}
              </span>
            </Link>
            <Link
              href="/admin/policies"
              className="hidden items-center gap-1 rounded-md px-2 py-1 text-muted-foreground hover:bg-card hover:text-foreground md:inline-flex"
              title="ABAC policies (Tshepo) — admin or delegated access"
            >
              <Shield className="h-3.5 w-3.5 shrink-0" />
              <span className="font-medium">Policies</span>
            </Link>
          </>
        ) : (
          <span className="text-[10px] uppercase tracking-wide text-muted-foreground">Support</span>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-1 sm:gap-3">
      <NotificationsCommsHub triggerLabel="Comms Hub" chartPatientCpid={chartPatientCpid} />
      <Link
        href="/support/knowledge-base"
        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-muted-foreground hover:bg-card hover:text-primary-hover"
      >
        <HelpCircle className="h-3.5 w-3.5" />
        <span className="hidden sm:inline font-medium">Help</span>
      </Link>
      <Link
        href="/support/tickets"
        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-muted-foreground hover:bg-card hover:text-primary-hover"
      >
        <LifeBuoy className="h-3.5 w-3.5" />
        <span className="hidden sm:inline font-medium">System Support</span>
      </Link>
      </div>
    </div>
  );
}
