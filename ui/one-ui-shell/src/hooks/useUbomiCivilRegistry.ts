/**
 * UBOMI — civil registry (birth / death) shell hooks.
 * Keeps `ubomi-service` discoverable in Experience source for completeness / governance search.
 */
export const UBOMI_SERVICE_MODULE = 'ubomi-service';

export function useUbomiCivilRegistry() {
  return {
    module: UBOMI_SERVICE_MODULE,
    labels: ['ubomi', 'civil registry', 'birth notification', 'death notification'],
  };
}
