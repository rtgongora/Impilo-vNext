import { AppLayout } from "@/components/AppLayout";
import { CitizenQuickAccessRail } from "@/components/home/CitizenQuickAccessRail";
import { LearningBackButton } from "@/components/learning/LearningBackButton";

export default function LearningLayout({ children }: { children: React.ReactNode }) {
  return (
    <AppLayout>
      <div className="learning-module-shell grid min-h-full grid-cols-[auto_minmax(0,1fr)] gap-4 py-3">
        <CitizenQuickAccessRail className="sticky top-3 self-start" collapsible />
        <div className="min-w-0">
          <LearningBackButton />
          {children}
        </div>
      </div>
    </AppLayout>
  );
}
