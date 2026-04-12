/** Landela adapter — integration shell references. */
export const LANDELA_ADAPTER_MODULE = 'landela-adapter-service';

export function useLandelaAdapter() {
  return { module: LANDELA_ADAPTER_MODULE, labels: ['landela', 'adapter', 'integration'] };
}
