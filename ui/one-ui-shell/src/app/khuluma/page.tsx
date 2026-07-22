import { KhulumaHome } from "@/components/khuluma/KhulumaHome";

/**
 * Khuluma — the communication front door (/khuluma). Role-aware hub: citizen ("life") and
 * provider/ops ("work") see the same first-class home adapted to their context. Messaging is
 * one workspace here, not the whole product; Impilo Live is linked as the sovereign live stage.
 */
export default function KhulumaPage() {
  return <KhulumaHome />;
}
