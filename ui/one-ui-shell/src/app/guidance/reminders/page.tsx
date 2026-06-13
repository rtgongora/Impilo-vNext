"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { Bell } from "lucide-react";

export default function GuidanceRemindersPage() {
  return (
    <AppLayout>
      <PageShell title="Health Reminders">
        <div className="max-w-2xl mx-auto text-center py-16">
          <Bell className="w-12 h-12 text-impilo-400 mx-auto mb-4" />
          <h2 className="text-xl font-semibold text-foreground mb-2">Health Reminders</h2>
          <p className="text-sm text-muted-foreground">
            Medication reminders, appointment follow-ups, screening schedules, and
            preventive care nudges based on your health profile.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
