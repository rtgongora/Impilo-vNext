import { AppLayout } from "@/components/AppLayout";

export default function LearningLayout({ children }: { children: React.ReactNode }) {
  return (
    <AppLayout>
      <div className="learning-module-shell min-h-full">{children}</div>
    </AppLayout>
  );
}
