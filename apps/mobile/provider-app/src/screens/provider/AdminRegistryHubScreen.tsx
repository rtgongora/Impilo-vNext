/**
 * AdminRegistryHubScreen — Tier-3 wave 3 parity for web admin + registry professional landings.
 * Single mobile hub maps multiple canonical routes (see parity matrix tier3-wave3).
 */
import React, { useMemo, useState } from "react";
import { View, Text, TextInput, StyleSheet } from "react-native";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, CardBody } from "@impilo/mobile-design-system";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import { fetchAdminRegistryHub, type AdminRegistrySection } from "../../services/adminRegistryService";
import {
  createProviderIdentity,
  getProviderIdentity,
  registerPatientIdentity,
  resolvePatientIdentity,
  searchIdentities,
  startPatientRecovery,
  verifyPatientRecovery,
  type IdentityRegistryRecord,
} from "../../services/identityRegistryService";
import {
  approveLocalityProposal,
  createFacilityApplication,
  createRegistryImportJob,
  createRegistryIntakeSession,
  createRegistryRecoveryRequest,
  executeRegistryImportJob,
  fetchBootstrapSnapshot,
  fetchFacilityDashboardSummary,
  fetchPendingLocalityProposals,
  listTrustConsents,
  listTrustPolicies,
  markFacilityApplicationReady,
  probeRegistryServiceHealth,
  proposeLocality,
  rejectLocalityProposal,
  resolveTerminologyArtifact,
  searchFacilityRegistry,
  searchProductRegistry,
  submitFacilityApplication,
  type RegistryOperationRecord,
} from "../../services/registryOperationsService";

const FALLBACK_SECTIONS: AdminRegistrySection[] = [
  { id: "admin", title: "Administration", web_path: "/admin", hint: "Users, roles, policies, and platform configuration." },
  { id: "registry", title: "Registry Hub", web_path: "/registry", hint: "Patient and provider registry entry points." },
  { id: "registry_admin", title: "Registry Administration", web_path: "/registry-admin", hint: "Registry configuration and governance." },
  { id: "organization_admin", title: "Organization Administration", web_path: "/organization-admin", hint: "Sites, cadres, and org structure." },
  { id: "public_health", title: "Public Health", web_path: "/public-health", hint: "Programmes, surveillance, and campaigns." },
  { id: "id_services", title: "Identity Services", web_path: "/id-services", hint: "Identity proofing and credential services." },
  { id: "access", title: "Access Channels", web_path: "/access", hint: "Kiosk, landline, and alternate access paths." },
  { id: "ai_governance", title: "AI Governance", web_path: "/ai-governance", hint: "Model registry, safety, and audit controls." },
];

const REGISTRY_FAMILY_WORKFLOWS = [
  { id: "facility", title: "Facility lifecycle", status: "Application + inspection flow" },
  { id: "locality", title: "Locality governance", status: "Proposal review flow" },
  { id: "intake", title: "Intake and import", status: "Session, recovery, CSV job flow" },
  { id: "products", title: "Products and terminology", status: "MSIKA search + ZIBO resolve" },
  { id: "trust", title: "Trust and consent", status: "Subject-scoped trust reads" },
] as const;

export function AdminRegistryHubScreen() {
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["admin-registry-hub"],
    queryFn: fetchAdminRegistryHub,
    retry: 1,
  });

  const sections = useMemo(() => {
    const remote = data?.sections?.filter((s) => s?.id && s?.title);
    return remote && remote.length > 0 ? remote : FALLBACK_SECTIONS;
  }, [data]);

  return (
    <ProfessionalHubBody
      rootTestID="admin-registry-hub-screen"
      heading="Admin & Registry"
      description="Mobile entry points aligned with the web professional plane. Deep links open in the full workspace when available."
      sections={sections}
      isPending={isPending}
      isError={isError}
      refreshedAt={data?.refreshed_at}
      isRefetching={isRefetching}
      onRefresh={() => refetch()}
      getSectionTestId={(id) => `admin-registry-section-${id}`}
    >
      <IdentityOperationsPanel />
      <RegistryOperationsPanel />
    </ProfessionalHubBody>
  );
}

function asPreview(value: unknown): string {
  if (!value) return "No response yet";
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value, null, 2).slice(0, 600);
  } catch {
    return String(value);
  }
}

function parsePayload(text: string): IdentityRegistryRecord {
  const parsed = JSON.parse(text);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("Payload must be a JSON object.");
  }
  return parsed as IdentityRegistryRecord;
}

function IdentityOperationsPanel() {
  const [query, setQuery] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState("");
  const [healthId, setHealthId] = useState("");
  const [providerId, setProviderId] = useState("");
  const [submittedProviderId, setSubmittedProviderId] = useState("");
  const [patientPayload, setPatientPayload] = useState(
    JSON.stringify({ givenName: "Example", familyName: "Patient", dateOfBirth: "1990-01-01", idType: "patient" }, null, 2),
  );
  const [providerPayload, setProviderPayload] = useState(
    JSON.stringify(
      {
        title: "Dr",
        givenName: "Example",
        familyName: "Provider",
        profession: "GENERAL_PRACTITIONER",
        cadre: "CLINICIAN",
        nationality: "ZW",
        gender: "UNKNOWN",
      },
      null,
      2,
    ),
  );
  const [recoveryPayload, setRecoveryPayload] = useState(
    JSON.stringify({ method: "phone_otp", contact: "+263000000000", fullName: "Example Patient" }, null, 2),
  );
  const [verifyPayload, setVerifyPayload] = useState(JSON.stringify({ recoveryId: "RECOVERY-ID", otp: "000000" }, null, 2));
  const [feedback, setFeedback] = useState<string | null>(null);

  const searchQ = useQuery({
    queryKey: ["mobile-identity-search", submittedQuery],
    queryFn: () => searchIdentities(submittedQuery),
    enabled: submittedQuery.trim().length >= 2,
  });
  const providerQ = useQuery({
    queryKey: ["mobile-provider-identity", submittedProviderId],
    queryFn: () => getProviderIdentity(submittedProviderId),
    enabled: submittedProviderId.trim().length > 0,
  });

  const resolveM = useMutation({ mutationFn: resolvePatientIdentity, onError: () => setFeedback("Patient identity resolve failed.") });
  const registerM = useMutation({ mutationFn: registerPatientIdentity, onError: () => setFeedback("Patient registration failed.") });
  const createProviderM = useMutation({ mutationFn: createProviderIdentity, onError: () => setFeedback("Provider identity creation failed.") });
  const recoveryM = useMutation({ mutationFn: startPatientRecovery, onError: () => setFeedback("Recovery start failed.") });
  const verifyM = useMutation({ mutationFn: verifyPatientRecovery, onError: () => setFeedback("Recovery verification failed.") });

  function runJsonCommand(text: string, submit: (payload: IdentityRegistryRecord) => void) {
    setFeedback(null);
    try {
      submit(parsePayload(text));
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
    }
  }

  return (
    <Card>
      <CardBody>
        <View testID="registry-identity-operations" style={styles.panel}>
          <Text style={styles.panelTitle}>Live Identity Operations</Text>
          <Text style={styles.hint}>
            VITO patient identity and VARAPI provider identity commands through the Experience BFF. Facility identity
            creation remains explicitly unsupported until a canonical contract exists.
          </Text>

          <View style={styles.grid}>
            <TextInput style={styles.input} placeholder="Search name, PHID, phone" value={query} onChangeText={setQuery} />
            <Button title="Search" onPress={() => setSubmittedQuery(query)} disabled={query.trim().length < 2} />
            <TextInput style={styles.input} placeholder="Health ID to resolve" value={healthId} onChangeText={setHealthId} />
            <Button title="Resolve patient" onPress={() => resolveM.mutate(healthId)} disabled={!healthId.trim() || resolveM.isPending} />
            <TextInput style={styles.input} placeholder="Provider ID lookup" value={providerId} onChangeText={setProviderId} />
            <Button title="Lookup provider" onPress={() => setSubmittedProviderId(providerId)} disabled={!providerId.trim()} />
          </View>

          {searchQ.data?.slice(0, 3).map((item, index) => (
            <View key={`${String(item.id ?? item.healthId ?? index)}`} style={styles.resultRow}>
              <Text style={styles.resultTitle}>{String(item.healthId ?? item.id ?? item.cpid ?? `identity-${index + 1}`)}</Text>
              <Text style={styles.resultMeta}>{String(item.displayName ?? item.name ?? item.fullName ?? "Registry identity")}</Text>
            </View>
          ))}

          <Text style={styles.label}>Register patient payload</Text>
          <TextInput style={styles.textArea} value={patientPayload} onChangeText={setPatientPayload} multiline />
          <Button
            title={registerM.isPending ? "Registering..." : "Register patient identity"}
            onPress={() => runJsonCommand(patientPayload, (payload) => registerM.mutate(payload))}
            disabled={registerM.isPending}
          />

          <Text style={styles.label}>Create provider payload</Text>
          <TextInput style={styles.textArea} value={providerPayload} onChangeText={setProviderPayload} multiline />
          <Button
            title={createProviderM.isPending ? "Creating..." : "Create provider identity"}
            onPress={() => runJsonCommand(providerPayload, (payload) => createProviderM.mutate(payload))}
            disabled={createProviderM.isPending}
          />

          <Text style={styles.label}>Patient recovery payload</Text>
          <TextInput style={styles.textArea} value={recoveryPayload} onChangeText={setRecoveryPayload} multiline />
          <Button
            title={recoveryM.isPending ? "Starting..." : "Start patient recovery"}
            onPress={() => runJsonCommand(recoveryPayload, (payload) => recoveryM.mutate(payload))}
            disabled={recoveryM.isPending}
          />

          <Text style={styles.label}>Verify recovery payload</Text>
          <TextInput style={styles.textArea} value={verifyPayload} onChangeText={setVerifyPayload} multiline />
          <Button
            title={verifyM.isPending ? "Verifying..." : "Verify patient recovery"}
            onPress={() => runJsonCommand(verifyPayload, (payload) => verifyM.mutate(payload))}
            disabled={verifyM.isPending}
          />

          {feedback ? <Text style={styles.error}>{feedback}</Text> : null}
          <Text style={styles.preview}>
            {asPreview(
              resolveM.data ??
                registerM.data ??
                providerQ.data ??
                createProviderM.data ??
                recoveryM.data ??
                verifyM.data,
            )}
          </Text>
        </View>
      </CardBody>
    </Card>
  );
}

function RegistryOperationsPanel() {
  const [facilitySearch, setFacilitySearch] = useState("");
  const [applicationForm, setApplicationForm] = useState({
    applicantName: "Registry Operator",
    facilityName: "Sample Clinic",
    facilityType: "CLINIC",
    province: "Harare",
    district: "Harare",
  });
  const [applicationId, setApplicationId] = useState("");
  const [localityForm, setLocalityForm] = useState({
    districtCode: "HARARE",
    proposedName: "Sample Locality",
    reviewerNote: "Reviewed on mobile registry console",
  });
  const [proposalId, setProposalId] = useState("");
  const [productQuery, setProductQuery] = useState("paracetamol");
  const [canonicalUrl, setCanonicalUrl] = useState("http://impilo.health/CodeSystem/facility-type");
  const [canonicalVersion, setCanonicalVersion] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [serviceModule, setServiceModule] = useState("tuso-service");
  const [importJobId, setImportJobId] = useState("");
  const [opsFeedback, setOpsFeedback] = useState<string | null>(null);

  const bootstrapM = useMutation({ mutationFn: fetchBootstrapSnapshot });
  const facilityDashboardM = useMutation({ mutationFn: fetchFacilityDashboardSummary });
  const facilitySearchM = useMutation({ mutationFn: () => searchFacilityRegistry({ search: facilitySearch, size: 5 }) });
  const createApplicationM = useMutation({
    mutationFn: () =>
      createFacilityApplication({
        applicationType: "NEW_REGISTRATION",
        pathway: "MOBILE_REGISTRY_ADMIN",
        applicantName: applicationForm.applicantName,
        facilityName: applicationForm.facilityName,
        facilityType: applicationForm.facilityType,
        province: applicationForm.province,
        district: applicationForm.district,
        workflowNotes: "Created from provider mobile Admin & Registry.",
      }),
    onSuccess: (value) => {
      const id = (value as { applicationId?: string })?.applicationId;
      if (id) setApplicationId(id);
    },
  });
  const submitApplicationM = useMutation({ mutationFn: () => submitFacilityApplication(applicationId) });
  const readyApplicationM = useMutation({ mutationFn: () => markFacilityApplicationReady(applicationId) });
  const pendingLocalitiesM = useMutation({ mutationFn: fetchPendingLocalityProposals });
  const proposeLocalityM = useMutation({
    mutationFn: () =>
      proposeLocality({
        districtCode: localityForm.districtCode,
        proposedName: localityForm.proposedName,
        status: "PENDING",
        source: "MOBILE_REGISTRY_ADMIN",
      }),
  });
  const approveLocalityM = useMutation({ mutationFn: () => approveLocalityProposal(proposalId, localityForm.reviewerNote) });
  const rejectLocalityM = useMutation({ mutationFn: () => rejectLocalityProposal(proposalId, localityForm.reviewerNote) });
  const intakeSessionM = useMutation({
    mutationFn: () =>
      createRegistryIntakeSession({
        intakeType: "CLIENT_PROGRESSIVE",
        targetRegistry: "VITO",
        initiatedChannel: "MOBILE_PROVIDER",
        provenance: "REGISTRY_OPERATOR",
      }),
  });
  const recoveryRequestM = useMutation({
    mutationFn: () =>
      createRegistryRecoveryRequest({
        requestKind: "RESUME_OR_RECOVER",
        targetRegistry: "VITO",
        contactHint: subjectId || "mobile-registry-operator",
      }),
  });
  const importJobM = useMutation({
    mutationFn: () =>
      createRegistryImportJob({
        importType: "FACILITY_CSV",
        targetRegistry: "FACILITY",
        csv: "name,facilityCode,facilityType,province,district\nMobile Import Clinic,ZW-MOB-001,CLINIC,Harare,Harare",
      }),
    onSuccess: (value) => {
      const id = (value as { id?: string })?.id;
      if (id) setImportJobId(id);
    },
  });
  const executeImportM = useMutation({ mutationFn: () => executeRegistryImportJob(importJobId, true) });
  const productSearchM = useMutation({ mutationFn: () => searchProductRegistry(productQuery) });
  const terminologyM = useMutation({ mutationFn: () => resolveTerminologyArtifact(canonicalUrl, canonicalVersion || undefined) });
  const trustPoliciesM = useMutation({ mutationFn: listTrustPolicies });
  const trustConsentsM = useMutation({ mutationFn: () => listTrustConsents(subjectId) });
  const healthM = useMutation({ mutationFn: () => probeRegistryServiceHealth(serviceModule) });

  function guarded(run: () => void, message?: string) {
    setOpsFeedback(message ?? null);
    run();
  }

  const latestResult =
    createApplicationM.data ??
    submitApplicationM.data ??
    readyApplicationM.data ??
    proposeLocalityM.data ??
    approveLocalityM.data ??
    rejectLocalityM.data ??
    intakeSessionM.data ??
    recoveryRequestM.data ??
    importJobM.data ??
    executeImportM.data ??
    terminologyM.data ??
    healthM.data ??
    facilityDashboardM.data ??
    bootstrapM.data;

  return (
    <Card>
      <CardBody>
        <View testID="registry-nonidentity-operations" style={styles.panel}>
          <Text style={styles.panelTitle}>Registry Plane Operations</Text>
          <Text style={styles.hint}>
            Contract-backed controls for facility lifecycle, locality review, intake/import, products, terminology, and
            trust reads. High-trust registry commands are sent live only; no provisional offline queue is used.
          </Text>
          <View style={styles.familyGrid}>
            {REGISTRY_FAMILY_WORKFLOWS.map((workflow) => (
              <View key={workflow.id} style={styles.familyCard}>
                <Text style={styles.familyTitle}>{workflow.title}</Text>
                <Text style={styles.familyStatus}>{workflow.status}</Text>
              </View>
            ))}
          </View>

          <Text style={styles.label}>Read models and downstream posture</Text>
          <View style={styles.grid}>
            <Button title="Load bootstrap snapshot" onPress={() => guarded(() => bootstrapM.mutate())} />
            <Button title="Load facility lifecycle summary" onPress={() => guarded(() => facilityDashboardM.mutate())} />
            <TextInput style={styles.input} placeholder="Registry service module" value={serviceModule} onChangeText={setServiceModule} />
            <Button title="Probe service health" onPress={() => guarded(() => healthM.mutate())} disabled={!serviceModule.trim()} />
          </View>

          <Text style={styles.label}>Facility lifecycle</Text>
          <TextInput style={styles.input} placeholder="Search facilities" value={facilitySearch} onChangeText={setFacilitySearch} />
          <Button title="Search facilities" onPress={() => guarded(() => facilitySearchM.mutate())} />
          {facilitySearchM.data?.slice(0, 3).map((item, index) => (
            <View key={`${String(item.facilityId ?? item.id ?? index)}`} style={styles.resultRow}>
              <Text style={styles.resultTitle}>{String(item.name ?? item.facilityName ?? item.id ?? `facility-${index + 1}`)}</Text>
              <Text style={styles.resultMeta}>
                {String(item.regulatoryStatus ?? item.status ?? "Facility registry row")}
              </Text>
            </View>
          ))}
          <View style={styles.grid}>
            <TextInput
              style={styles.input}
              placeholder="Applicant"
              value={applicationForm.applicantName}
              onChangeText={(value) => setApplicationForm({ ...applicationForm, applicantName: value })}
            />
            <TextInput
              style={styles.input}
              placeholder="Facility name"
              value={applicationForm.facilityName}
              onChangeText={(value) => setApplicationForm({ ...applicationForm, facilityName: value })}
            />
            <TextInput
              style={styles.input}
              placeholder="Facility type"
              value={applicationForm.facilityType}
              onChangeText={(value) => setApplicationForm({ ...applicationForm, facilityType: value })}
            />
            <TextInput
              style={styles.input}
              placeholder="Province"
              value={applicationForm.province}
              onChangeText={(value) => setApplicationForm({ ...applicationForm, province: value })}
            />
            <TextInput
              style={styles.input}
              placeholder="District"
              value={applicationForm.district}
              onChangeText={(value) => setApplicationForm({ ...applicationForm, district: value })}
            />
            <Button
              title={createApplicationM.isPending ? "Creating..." : "Create facility application"}
              onPress={() =>
                guarded(
                  () => createApplicationM.mutate(),
                  "Creates a Tuso lifecycle application, not an unsupported direct facility identity mutation.",
                )
              }
              disabled={!applicationForm.applicantName.trim() || !applicationForm.facilityName.trim()}
            />
            <TextInput style={styles.input} placeholder="Application ID" value={applicationId} onChangeText={setApplicationId} />
            <Button title="Submit application" onPress={() => guarded(() => submitApplicationM.mutate())} disabled={!applicationId.trim()} />
            <Button
              title="Mark ready for inspection"
              onPress={() => guarded(() => readyApplicationM.mutate())}
              disabled={!applicationId.trim()}
            />
          </View>

          <Text style={styles.label}>Locality review</Text>
          <View style={styles.grid}>
            <Button title="Load pending proposals" onPress={() => guarded(() => pendingLocalitiesM.mutate())} />
            <TextInput
              style={styles.input}
              placeholder="District code"
              value={localityForm.districtCode}
              onChangeText={(value) => setLocalityForm({ ...localityForm, districtCode: value })}
            />
            <TextInput
              style={styles.input}
              placeholder="Proposed locality"
              value={localityForm.proposedName}
              onChangeText={(value) => setLocalityForm({ ...localityForm, proposedName: value })}
            />
            <Button
              title="Propose locality"
              onPress={() => guarded(() => proposeLocalityM.mutate())}
              disabled={!localityForm.districtCode.trim() || !localityForm.proposedName.trim()}
            />
            <TextInput style={styles.input} placeholder="Proposal ID" value={proposalId} onChangeText={setProposalId} />
            <TextInput
              style={styles.input}
              placeholder="Reviewer note"
              value={localityForm.reviewerNote}
              onChangeText={(value) => setLocalityForm({ ...localityForm, reviewerNote: value })}
            />
            <Button title="Approve proposal" onPress={() => guarded(() => approveLocalityM.mutate())} disabled={!proposalId.trim()} />
            <Button title="Reject proposal" onPress={() => guarded(() => rejectLocalityM.mutate())} disabled={!proposalId.trim()} />
          </View>
          {pendingLocalitiesM.data?.slice(0, 3).map((item, index) => (
            <View key={`${String(item.id ?? index)}`} style={styles.resultRow}>
              <Text style={styles.resultTitle}>{String(item.proposedName ?? item.name ?? `proposal-${index + 1}`)}</Text>
              <Text style={styles.resultMeta}>{String(item.districtCode ?? item.status ?? "Pending proposal")}</Text>
            </View>
          ))}

          <Text style={styles.label}>Intake, import, products, terminology, and trust</Text>
          <View style={styles.grid}>
            <Button title="Create intake session" onPress={() => guarded(() => intakeSessionM.mutate())} />
            <Button title="Create recovery request" onPress={() => guarded(() => recoveryRequestM.mutate())} />
            <Button title="Create facility CSV import job" onPress={() => guarded(() => importJobM.mutate())} />
            <TextInput style={styles.input} placeholder="Import job ID" value={importJobId} onChangeText={setImportJobId} />
            <Button title="Dry-run import job" onPress={() => guarded(() => executeImportM.mutate())} disabled={!importJobId.trim()} />
            <TextInput style={styles.input} placeholder="Product query" value={productQuery} onChangeText={setProductQuery} />
            <Button title="Search product registry" onPress={() => guarded(() => productSearchM.mutate())} disabled={!productQuery.trim()} />
            <TextInput style={styles.input} placeholder="ZIBO canonical URL" value={canonicalUrl} onChangeText={setCanonicalUrl} />
            <TextInput style={styles.input} placeholder="Version optional" value={canonicalVersion} onChangeText={setCanonicalVersion} />
            <Button
              title="Resolve terminology artifact"
              onPress={() => guarded(() => terminologyM.mutate())}
              disabled={!canonicalUrl.trim()}
            />
            <Button title="List trust policies" onPress={() => guarded(() => trustPoliciesM.mutate())} />
            <TextInput style={styles.input} placeholder="Consent subject ID" value={subjectId} onChangeText={setSubjectId} />
            <Button title="List subject consents" onPress={() => guarded(() => trustConsentsM.mutate())} disabled={!subjectId.trim()} />
          </View>
          {productSearchM.data?.slice(0, 3).map((item, index) => (
            <View key={`${String(item.id ?? item.code ?? index)}`} style={styles.resultRow}>
              <Text style={styles.resultTitle}>{String(item.name ?? item.display ?? item.code ?? `product-${index + 1}`)}</Text>
              <Text style={styles.resultMeta}>{String(item.status ?? item.category ?? "MSIKA product row")}</Text>
            </View>
          ))}
          {trustPoliciesM.data?.slice(0, 2).map((item, index) => (
            <View key={`${String(item.id ?? index)}`} style={styles.resultRow}>
              <Text style={styles.resultTitle}>{String(item.name ?? item.policyId ?? `policy-${index + 1}`)}</Text>
              <Text style={styles.resultMeta}>{String(item.status ?? item.effect ?? "Trust policy")}</Text>
            </View>
          ))}
          {trustConsentsM.data?.slice(0, 2).map((item, index) => (
            <View key={`${String(item.id ?? index)}`} style={styles.resultRow}>
              <Text style={styles.resultTitle}>{String(item.id ?? item.consentId ?? `consent-${index + 1}`)}</Text>
              <Text style={styles.resultMeta}>{String(item.status ?? item.scope ?? "Consent directive")}</Text>
            </View>
          ))}

          {opsFeedback ? <Text style={styles.warning}>{opsFeedback}</Text> : null}
          <Text style={styles.preview}>{asPreview(latestResult)}</Text>
        </View>
      </CardBody>
    </Card>
  );
}

const styles = StyleSheet.create({
  panel: { gap: 10 },
  panelTitle: { fontSize: 16, fontWeight: "800", color: "#111827" },
  hint: { fontSize: 12, color: "#6B7280", lineHeight: 17 },
  grid: { gap: 8 },
  familyGrid: { gap: 8 },
  familyCard: { borderWidth: 1, borderColor: "#E0E7FF", borderRadius: 10, padding: 10, backgroundColor: "#EEF2FF" },
  familyTitle: { fontSize: 12, fontWeight: "800", color: "#312E81" },
  familyStatus: { fontSize: 11, color: "#4F46E5", marginTop: 2 },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 10, fontSize: 14 },
  textArea: {
    minHeight: 88,
    borderWidth: 1,
    borderColor: "#D1D5DB",
    borderRadius: 8,
    padding: 10,
    fontSize: 12,
    fontFamily: "monospace",
    textAlignVertical: "top",
  },
  label: { fontSize: 12, fontWeight: "700", color: "#374151", marginTop: 4 },
  resultRow: { borderWidth: 1, borderColor: "#E5E7EB", borderRadius: 8, padding: 8, backgroundColor: "#F9FAFB" },
  resultTitle: { fontSize: 12, fontWeight: "700", color: "#111827" },
  resultMeta: { fontSize: 11, color: "#6B7280", marginTop: 2 },
  error: { fontSize: 12, color: "#B91C1C" },
  warning: { fontSize: 12, color: "#92400E" },
  preview: { fontSize: 11, color: "#374151", backgroundColor: "#F3F4F6", borderRadius: 8, padding: 8 },
});
