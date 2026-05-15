$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$ServicesDir = Join-Path $RootDir "services"

Write-Host "[clinical-shr-fhir-runtime] Validating SHR/FHIR boundary and clinical guardrails..."

Set-Location $ServicesDir

Write-Host "[1/4] Running BUTANO/BUTANO-FHIR/FHIR-Gateway suite"
mvn -pl butano-service,butano-fhir,fhir-gateway-service -am test

Write-Host "[2/4] Running clinical authz/audit evidence guard test"
mvn -pl pharmacy-service -am test -Dtest=ClinicalPlaneEvidenceGuardTest -Dsurefire.failIfNoSpecifiedTests=false

Write-Host "[3/4] Running core clinical mutation services suite"
mvn -pl pharmacy-service,pacs-adapter-service,oros-service,pct-service,inpatient-service,document-service,forms-service,guidance-service,rules-service,clinical-knowledge-platform-service -am test

Write-Host "[4/4] Running Experience telemedicine wiring regression"
mvn -pl experience-bff -am test -Dtest=MobileTelemedicineControllerTest -Dsurefire.failIfNoSpecifiedTests=false

Write-Host "[clinical-shr-fhir-runtime] PASS"
