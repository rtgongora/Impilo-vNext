"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { BookOpen } from "lucide-react";

export default function GuidanceEducationPage() {
  return (
    <AppLayout>
      <PageShell title="Health Education">
        <div className="max-w-2xl mx-auto text-center py-16">
          <BookOpen className="w-12 h-12 text-impilo-400 mx-auto mb-4" />
          <h2 className="text-xl font-semibold text-foreground mb-2">Health Education</h2>
          <p className="text-sm text-muted-foreground">
            Condition-specific education materials, self-care guides, and treatment information
            personalised to your health profile.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
