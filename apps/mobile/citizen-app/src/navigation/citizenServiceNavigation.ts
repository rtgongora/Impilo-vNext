import * as Linking from "expo-linking";
import {
  MOBILE_SERVICE_WIRING,
  MOBILE_SOVEREIGN_SERVICES,
  type CanonicalServiceSlug,
} from "@impilo/mobile-registry";
import type { ServiceWiringBadgeStatus } from "@impilo/mobile-design-system";
import type { CitizenTab } from "../types";

const PREVIEW_WEB = process.env.EXPO_PUBLIC_WEB_BASE_URL ?? "https://impilo.mohcc.gov.zw";

export interface CitizenServiceNavAction {
  slug: CanonicalServiceSlug;
  name: string;
  description: string;
  icon?: string;
  wiringStatus: ServiceWiringBadgeStatus;
  onPress?: () => void;
  disabled?: boolean;
}

type NavHandlers = {
  setActiveTab: (tab: CitizenTab) => void;
  openPersonalSection?: (sectionId: string) => void;
  openNompilo?: () => void;
};

const CITIZEN_PRIORITY: CanonicalServiceSlug[] = [
  "pct",
  "butano",
  "fundo",
  "mushex",
  "madi",
  "live",
  "nhume",
  "simba",
  "ubomi",
  "msika",
  "indawo",
];

function mapWiringStatus(status: string): ServiceWiringBadgeStatus {
  if (status === "fullyWired") return "fullyWired";
  if (status === "partiallyWired") return "partiallyWired";
  if (status === "deepLinked") return "deepLinked";
  if (status === "backendMissing") return "backendMissing";
  if (status === "blocked") return "blocked";
  return "planned";
}

function resolveCitizenAction(slug: CanonicalServiceSlug, handlers: NavHandlers): (() => void) | undefined {
  switch (slug) {
    case "pct":
      return () => handlers.setActiveTab("telehealth");
    case "butano":
      return () => handlers.setActiveTab("personal");
    case "fundo":
      return () => handlers.setActiveTab("marketplace");
    case "mushex":
    case "costa":
      return () => handlers.setActiveTab("personal");
    case "madi":
      return () => handlers.openPersonalSection?.("madi-donor") ?? handlers.setActiveTab("personal");
    case "live":
      return () => handlers.openPersonalSection?.("impilo-live") ?? handlers.setActiveTab("personal");
    case "nhume":
      return () => handlers.setActiveTab("home");
    case "simba":
      return () => handlers.openPersonalSection?.("wellness") ?? handlers.setActiveTab("personal");
    case "ubomi":
      return () => handlers.setActiveTab("marketplace");
    case "msika":
      return () => handlers.setActiveTab("marketplace");
    case "indawo":
      return () => handlers.setActiveTab("home");
    case "nompilo":
      return handlers.openNompilo;
    case "pacs":
      return () => handlers.openPersonalSection?.("results") ?? handlers.setActiveTab("personal");
    default:
      return undefined;
  }
}

export function buildCitizenServiceCards(handlers: NavHandlers): CitizenServiceNavAction[] {
  return CITIZEN_PRIORITY.map((slug) => {
    const meta = MOBILE_SOVEREIGN_SERVICES[slug];
    const wiring = MOBILE_SERVICE_WIRING[slug];
    const wiringStatus = mapWiringStatus(wiring.wiringStatus);
    const isBlocked = wiring.mobileStatus === "blocked" || wiring.wiringStatus === "backendMissing";
    const deepLink = wiring.deepLinks?.[0];

    let onPress = resolveCitizenAction(slug, handlers);
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
