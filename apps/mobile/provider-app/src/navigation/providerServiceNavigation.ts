import * as Linking from "expo-linking";
import {
  MOBILE_SERVICE_WIRING,
  MOBILE_SOVEREIGN_SERVICES,
  type CanonicalServiceSlug,
} from "@impilo/mobile-registry";
import type { ServiceWiringBadgeStatus } from "@impilo/mobile-design-system";
import type { AppMode, ProviderTabKey } from "../types";

const PREVIEW_WEB = process.env.EXPO_PUBLIC_WEB_BASE_URL ?? "https://impilo.mohcc.gov.zw";

export interface ProviderServiceNavAction {
  slug: CanonicalServiceSlug;
  name: string;
  description: string;
  icon?: string;
  wiringStatus: ServiceWiringBadgeStatus;
  onPress?: () => void;
  disabled?: boolean;
}

type NavHandlers = {
  setProviderTab: (tab: ProviderTabKey) => void;
  /**
   * Enters a workspace. Callers MUST supply `useSwitchAppMode`'s switcher, not
   * the store's raw setter — `simba` below enters supervisor, a governed mode
   * that has to mint a supervisory duty token first. Named `enterMode` rather
   * than `setMode` precisely so it cannot be satisfied by the store setter,
   * which no longer accepts governed modes at all.
   */
  enterMode?: (mode: AppMode) => void;
  openClinicalTool?: (toolId: string) => void;
  openNompilo?: () => void;
};

const PROVIDER_PRIORITY: CanonicalServiceSlug[] = [
  "pct",
  "oros",
  "pacs",
  "madi",
  "simba",
  "fundo",
  "live",
  "varapi",
  "tuso",
  "nhume",
];

function mapWiringStatus(status: string): ServiceWiringBadgeStatus {
  if (status === "fullyWired") return "fullyWired";
  if (status === "partiallyWired") return "partiallyWired";
  if (status === "deepLinked") return "deepLinked";
  if (status === "backendMissing") return "backendMissing";
  if (status === "blocked") return "blocked";
  return "planned";
}

function resolveProviderAction(slug: CanonicalServiceSlug, handlers: NavHandlers): (() => void) | undefined {
  switch (slug) {
    case "pct":
      return () => handlers.setProviderTab("queue");
    case "oros":
      return () => {
        handlers.setProviderTab("tools");
        handlers.openClinicalTool?.("lab");
      };
    case "pacs":
      return () => {
        handlers.setProviderTab("tools");
        handlers.openClinicalTool?.("pacs");
      };
    case "madi":
      return () => {
        handlers.setProviderTab("tools");
        handlers.openClinicalTool?.("madi_orders");
      };
    case "simba":
      return () => handlers.enterMode?.("supervisor");
    case "fundo":
      return () => handlers.setProviderTab("apps");
    case "live":
      return () => {
        handlers.setProviderTab("tools");
        handlers.openClinicalTool?.("impilo_live");
      };
    case "varapi":
      return () => handlers.setProviderTab("professional");
    case "tuso":
      return () => handlers.setProviderTab("professional");
    case "nhume":
      return () => handlers.enterMode?.("courier");
    case "nompilo":
      return handlers.openNompilo;
    default:
      return undefined;
  }
}

export function buildProviderServiceCards(handlers: NavHandlers): ProviderServiceNavAction[] {
  return PROVIDER_PRIORITY.map((slug) => {
    const meta = MOBILE_SOVEREIGN_SERVICES[slug];
    const wiring = MOBILE_SERVICE_WIRING[slug];
    const wiringStatus = mapWiringStatus(wiring.wiringStatus);
    const isBlocked = wiring.mobileStatus === "blocked" || wiring.wiringStatus === "backendMissing";
    const deepLink = wiring.deepLinks?.[0];

    let onPress = resolveProviderAction(slug, handlers);
    if (wiring.wiringStatus === "deepLinked" && deepLink) {
      onPress = () => {
        void Linking.openURL(deepLink);
      };
    }

    return {
      slug,
      name: meta.name,
      description: meta.description,
      icon: meta.icon,
      wiringStatus,
      onPress: isBlocked ? undefined : onPress,
      disabled: isBlocked,
    };
  });
}
