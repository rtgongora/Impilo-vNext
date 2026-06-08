/**
 * Shared OpenAPI contract filename rules for completeness reporting and stub emission.
 */
export const OPENAPI_BY_MODULE = {
  'butano-fhir': 'butano.custom.openapi.yaml',
  'butano-service': 'butano.custom.openapi.yaml',
  'card-print-agent': 'card-print.openapi.yaml',
  'campaigns-service': 'campaigns.openapi.yaml',
  'clinical-knowledge-platform-service': 'clinical-knowledge-platform.openapi.yaml',
  'costa-service': 'costa.openapi.yaml',
  'costing-engine-service': 'costa.openapi.yaml',
  'coverage-service': 'coverage.openapi.yaml',
  'credential-verification-service': 'credential-verification.openapi.yaml',
  'data-governance-service': 'data-governance.openapi.yaml',
  'document-service': 'document-store.openapi.yaml',
  'forms-service': 'forms.openapi.yaml',
  'fhir-gateway-service': 'fhir-gateway.openapi.yaml',
  'guidance-service': 'guidance.openapi.yaml',
  'indawo-service': 'indawo.openapi.yaml',
  'integration-hub': 'integration-hub.openapi.yaml',
  'msika-apps-service': 'msika-apps.openapi.yaml',
  'inventory-service': 'inventory.openapi.yaml',
  'landela-adapter-service': 'landela-adapter.openapi.yaml',
  'msika-flow-service': 'msika-flow.openapi.yaml',
  'msika-service': 'msika-core.openapi.yaml',
  'mushex-service': 'mushex.openapi.yaml',
  'notification-service': 'notification.openapi.yaml',
  'oros-service': 'oros.openapi.yaml',
  'pct-service': 'pct.openapi.yaml',
  'rules-service': 'rules.openapi.yaml',
  'search-service': 'search.openapi.yaml',
  'product-registry-service': 'product-registry.openapi.yaml',
  'pharmacy-service': 'pharmacy.openapi.yaml',
  'share-slip-service': 'share-slip.openapi.yaml',
  'surveillance-service': 'surveillance.openapi.yaml',
  'vito-service': 'vito.openapi.yaml',
  'varapi-service': 'varapi.openapi.yaml',
  'tuso-service': 'tuso.openapi.yaml',
  'ubomi-service': 'ubomi.openapi.yaml',
  'workflow-service': 'workflow.openapi.yaml',
  'wellness-service': 'wellness.openapi.yaml',
  'zibo-service': 'zibo.openapi.yaml',
  'booking-service': 'booking.openapi.yaml',
  'madi-service': 'madi.openapi.yaml',
  'live-service': 'impilo-live.openapi.yaml',
  'mvumo-service': 'mvumo.openapi.yaml',
  'community-service': 'social.openapi.yaml',
  'learning-service': 'learning.openapi.yaml',
  'simba-service': 'simba.openapi.yaml',
  'rtc-gateway-service': 'rtc-gateway.openapi.yaml',
  'nhume-service': 'nhume.openapi.yaml',
  'ndila-service': 'ndila.openapi.yaml',
  'referral-service': 'referral.openapi.yaml',
  'scheduling-service': 'scheduling.openapi.yaml',
  'inpatient-service': 'inpatient.openapi.yaml',
  'dispatch-service': 'dispatch.openapi.yaml',
  'offline-sync-service': 'offline-sync.openapi.yaml',
  'offline-edge-service': 'offline-edge.openapi.yaml',
  'pharmacy-elmis-adapter': 'pharmacy-elmis.openapi.yaml',
  'inventory-elmis-adapter': 'inventory-elmis.openapi.yaml',
  'connector-fhir-adapter': 'connector-fhir.openapi.yaml',
  'pacs-adapter-service': 'pacs-adapter.openapi.yaml',
  'card-print-agent': 'card-print.openapi.yaml',
};

/** One suffix only — avoids `landela-adapter-service` → `landela` (wrong). */
export function stemMavenModule(module) {
  if (module.endsWith('-service')) return module.slice(0, -'-service'.length);
  if (module.endsWith('-adapter')) return module.slice(0, -'-adapter'.length);
  if (module.endsWith('-agent')) return module.slice(0, -'-agent'.length);
  return module;
}

/** Canonical primary contract file name for a maven module (may be shared across modules). */
export function defaultOpenApiContractFilename(module) {
  if (OPENAPI_BY_MODULE[module]) return OPENAPI_BY_MODULE[module];
  return `${stemMavenModule(module)}.openapi.yaml`;
}
