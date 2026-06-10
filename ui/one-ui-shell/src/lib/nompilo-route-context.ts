import { classifyRouteJourney } from "@/lib/ui-route-journey-map";
import type { IdentityContext } from "@/lib/identity-context";
import { buildNompiloIdentityPacket } from "@/lib/identity-context";

export interface NompiloRouteContext {
  routePath: string;
  journey: string;
  surface: string;
  shellMode?: string;
  isCitizenOnly?: boolean;
  hasWorkAccess?: boolean;
  suggestedPrompts?: string[];
}

/** Canonical route + identity context forwarded to guidance BFF on every Nompilo ask. */
export function buildNompiloRouteContext(
  routePath: string,
  identityContext?: Pick<
    IdentityContext,
    | "shellMode"
    | "isCitizenOnly"
    | "hasWorkAccess"
    | "providerStatus"
    | "isFacilityAdmin"
  > | null,
): NompiloRouteContext {
  const path = routePath.trim() || "/";
  const journey = classifyRouteJourney(path);
  const base: NompiloRouteContext = {
    routePath: path,
    journey,
    surface: journey.replaceAll("_", " ").toLowerCase(),
  };

  if (!identityContext) return base;

  const packet = buildNompiloIdentityPacket(identityContext as IdentityContext);
  return {
    ...base,
    shellMode: identityContext.shellMode,
    isCitizenOnly: packet.isCitizenOnly,
    hasWorkAccess: packet.hasWorkAccess,
    suggestedPrompts: packet.suggestedPrompts,
  };
}
