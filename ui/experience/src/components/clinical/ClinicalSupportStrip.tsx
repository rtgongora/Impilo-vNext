"use client";

import Link from "next/link";
import { HelpCircle, LifeBuoy } from "lucide-react";
import { NotificationsCommsHub } from "@/components/notifications/NotificationsCommsHub";

/**
 * Operational Comms / Help / System Support strip for clinical and EHR shells.
 * Notification counts come from NotificationsCommsHub (API-backed); no mock badges here.
 */
export function ClinicalSupportStrip() {
  return (
    <div className="h-9 border-b border-gray-200 bg-slate-50/90 px-3 flex items-center justify-end gap-1 sm:gap-3 text-xs shrink-0">
      <NotificationsCommsHub triggerLabel="Comms Hub" />
      <Link
        href="/support/knowledge-base"
        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-gray-600 hover:bg-white hover:text-impilo-700"
      >
        <HelpCircle className="h-3.5 w-3.5" />
        <span className="hidden sm:inline font-medium">Help</span>
      </Link>
      <Link
        href="/support/tickets"
        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-gray-600 hover:bg-white hover:text-impilo-700"
      >
        <LifeBuoy className="h-3.5 w-3.5" />
        <span className="hidden sm:inline font-medium">System Support</span>
      </Link>
    </div>
  );
}
