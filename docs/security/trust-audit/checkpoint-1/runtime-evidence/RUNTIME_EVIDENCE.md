# Runtime evidence capture — 2026-08-01T08:38:32Z
namespace: impilo-full-preview ; cluster: k3s on 41.57.127.235

## Deployed digests (MFA cohort) vs source commits — from mfa-preview-release-evidence-20260801.md
keycloak sha256:70f0af3d... (304152be6); experience-bff sha256:1948d8d3... (486b3a4ff); one-ui-shell sha256:d264a0c1... (304152be6); tshepo-authz sha256:4da33b6f... (07b8674a8); tshepo-audit sha256:bba09c39... (3f42627f3)

## Service exposure
114 Services, ALL ClusterIP. 0 NodePort/LoadBalancer among app services (traefik LB in kube-system is the only external LB).
0 NetworkPolicies in namespace -> no east-west containment.
114/116 workloads use 'default' ServiceAccount (only estate-health-watch has a dedicated SA) -> no workload identity.

## Deployed edge routing (traefik IngressRoutes) — Envoy is NOT in the path
Host(impilo.mohcc.gov.zw):
  /internal,/actuator,/health -> experience-bff:8160
  / (UI)                      -> one-ui-shell:3000
  /realms,/resources          -> keycloak:8080
  /.well-known                -> public-website:8080
envoy service (10000) exists with 1 endpoint but no IngressRoute targets it.
Deployed envoy configmap = bare passthrough to experience_bff:8160, ext_authz count = 0.
Repo canonical infra/envoy/envoy.yaml HAS ext_authz (10 refs) -> DEPLOYED != SOURCE.

## Kafka
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT -> no broker auth/TLS.
