import { classifyRouteJourney } from "@/lib/ui-route-journey-map";

export interface NompiloRouteContext {
  routePath: string;
  journey: string;
  surface: string;
}

/** Canonical route context forwarded to guidance BFF on every Nompilo ask. */
export function buildNompiloRouteContext(routePath: string): NompiloRouteContext {
  const path = routePath.trim() || "/";
  const journey = classifyRouteJourney(path);
  return {
    routePath: path,
    journey,
    surface: journey.replaceAll("_", " ").toLowerCase(),
  };
}
