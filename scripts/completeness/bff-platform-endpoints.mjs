/**
 * Maps maven modules to ServiceEndpoints field names in ServiceClientConfig.java.
 * Used for BFF dimension: a declared URL slot counts as at least stub integration.
 */
export const BFF_SERVICE_ENDPOINT_ACCESSOR_BY_MODULE = {
  'pct-service': 'pctBaseUrl',
  'oros-service': 'orosBaseUrl',
  'pharmacy-service': 'pharmacyBaseUrl',
  'butano-service': 'butanoBaseUrl',
  'butano-fhir': 'fhirBaseUrl',
  'msika-service': 'msikaBaseUrl',
  'msika-flow-service': 'msikaFlowBaseUrl',
  'mushex-service': 'mushexBaseUrl',
  'vito-service': 'vitoBaseUrl',
  'tuso-service': 'tusoBaseUrl',
  'varapi-service': 'varapiBaseUrl',
  'document-service': 'documentStoreBaseUrl',
  'costa-service': 'costaBaseUrl',
  'costing-engine-service': 'costaBaseUrl',
  'coverage-service': 'coverageBaseUrl',
  'surveillance-service': 'surveillanceBaseUrl',
  'campaigns-service': 'campaignsBaseUrl',
  'indawo-service': 'indawoBaseUrl',
  'data-governance-service': 'dataGovernanceBaseUrl',
  'landela-adapter-service': 'landelaBaseUrl',
  'notification-service': 'notificationBaseUrl',
  'credential-verification-service': 'credentialBaseUrl',
  'fhir-gateway-service': 'fhirGatewayBaseUrl',
  'search-service': 'searchBaseUrl',
  'forms-service': 'formsBaseUrl',
  'rules-service': 'rulesBaseUrl',
  'workflow-service': 'workflowBaseUrl',
  'guidance-service': 'guidanceBaseUrl',
  'integration-hub': 'integrationHubBaseUrl',
};

/** impilo.services / related keys in experience-bff application.yml (not always mirrored in Java record). */
export const BFF_APP_YML_MARKERS_BY_MODULE = {
  'pacs-adapter-service': 'orthanc-base-url:',
};
