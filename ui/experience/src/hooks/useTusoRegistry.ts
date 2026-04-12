/** TUSO — trust / token surface shell references (data-classification registry). */
export const TUSO_SERVICE_MODULE = 'tuso-service';

export function useTusoRegistry() {
  return { module: TUSO_SERVICE_MODULE, labels: ['tuso', 'classification', 'registry'] };
}
