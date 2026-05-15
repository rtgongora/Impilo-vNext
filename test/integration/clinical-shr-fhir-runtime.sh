#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVICES_DIR="$ROOT_DIR/services"

echo "[clinical-shr-fhir-runtime] Validating SHR/FHIR boundary and clinical guardrails..."

cd "$SERVICES_DIR"

echo "[1/4] Running BUTANO/BUTANO-FHIR/FHIR-Gateway suite"
mvn -pl butano-service,butano-fhir,fhir-gateway-service -am test

echo "[2/4] Running clinical authz/audit evidence guard test"
mvn -pl pharmacy-service -am test -Dtest=ClinicalPlaneEvidenceGuardTest -Dsurefire.failIfNoSpecifiedTests=false

echo "[3/4] Running core clinical mutation services suite"
mvn -pl pharmacy-service,pacs-adapter-service,oros-service,pct-service,inpatient-service,document-service,forms-service,guidance-service,rules-service,clinical-knowledge-platform-service -am test

echo "[4/4] Running Experience telemedicine wiring regression"
mvn -pl experience-bff -am test -Dtest=MobileTelemedicineControllerTest -Dsurefire.failIfNoSpecifiedTests=false

echo "[clinical-shr-fhir-runtime] PASS"
