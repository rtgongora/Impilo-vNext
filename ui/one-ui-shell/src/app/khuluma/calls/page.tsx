import { KhulumaWorkspace } from "@/components/khuluma/KhulumaWorkspace";
import { KhulumaCallsPanel } from "@/components/khuluma/KhulumaCallsPanel";

export default function KhulumaCallsPage() {
  return (
    <KhulumaWorkspace title="Khuluma — Calls" subtitle="Audio and video calls">
      <KhulumaCallsPanel />
    </KhulumaWorkspace>
  );
}
