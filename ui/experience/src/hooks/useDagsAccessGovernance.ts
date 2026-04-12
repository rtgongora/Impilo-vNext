/** DAGS — data access governance shell references. */
export const DAGS_SERVICE_MODULE = 'data-access-governance-service';

export function useDagsAccessGovernance() {
  return { module: DAGS_SERVICE_MODULE, labels: ['dags', 'data access governance', 'policy'] };
}
