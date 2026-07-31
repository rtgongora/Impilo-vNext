/**
 * My Regulatory Affairs — provider self-service (person journey, not operator desk).
 * Mirrors web /professional/regulatory.
 */

import React, { useCallback, useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  TextInput,
} from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  Badge,
  Button,
  LoadingSpinner,
  EmptyState,
  ErrorState,
  colors,
} from "@impilo/mobile-design-system";
import {
  getMyPracticeEstablishments,
  getMyRegulatoryApplications,
  getMyRegulatoryComplaints,
  getMyRegulatorySummary,
  getMyRenewalEligibility,
  getStudentApplicationSections,
  resubmitStudentSection,
  getContributionSections,
  completeContributionSection,
  type ApplicationSection,
  type MyRegulatoryApplication,
  type MyRegulatoryComplaint,
  type MyRegulatorySummary,
  type MyRenewalEligibility,
} from "@impilo/mobile-api-client";
import { appStore, useAppStore } from "../../stores/appStore";

type Surface =
  | "hub"
  | "applications"
  | "complaints"
  | "practice"
  | "student"
  | "contribute";

export function MyRegulatoryAffairsScreen({ onClose }: { onClose?: () => void }) {
  const { contributeInviteRequest } = useAppStore();
  const [surface, setSurface] = useState<Surface>("hub");
  const [summary, setSummary] = useState<MyRegulatorySummary | null>(null);
  const [renewal, setRenewal] = useState<MyRenewalEligibility | null>(null);
  const [applications, setApplications] = useState<MyRegulatoryApplication[]>([]);
  const [complaints, setComplaints] = useState<MyRegulatoryComplaint[]>([]);
  const [establishments, setEstablishments] = useState<unknown[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [studentAppId, setStudentAppId] = useState("");
  const [inviteId, setInviteId] = useState("");
  const [sections, setSections] = useState<ApplicationSection[]>([]);
  const [sectionDrafts, setSectionDrafts] = useState<Record<string, string>>({});

  useEffect(() => {
    if (contributeInviteRequest) {
      setInviteId(contributeInviteRequest);
      appStore.getState().setContributeInviteRequest(null);
    }
  }, [contributeInviteRequest]);

  const loadHub = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [s, r, a] = await Promise.all([
        getMyRegulatorySummary(),
        getMyRenewalEligibility().catch(() => null),
        getMyRegulatoryApplications().catch(() => []),
      ]);
      setSummary(s);
      setRenewal(r);
      setApplications(a);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load regulatory standing");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadHub();
  }, [loadHub]);

  const loadComplaints = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setComplaints(await getMyRegulatoryComplaints());
      setSurface("complaints");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load complaints");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadPractice = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setEstablishments(await getMyPracticeEstablishments());
      setSurface("practice");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load practice establishments");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadStudent = useCallback(async () => {
    if (!studentAppId.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const rows = await getStudentApplicationSections(studentAppId.trim());
      setSections(rows);
      const drafts: Record<string, string> = {};
      for (const row of rows) {
        if (row.state === "RETURNED") {
          const content = row.content ?? {};
          const keys = Object.keys(content);
          drafts[row.sectionKey] = keys.length
            ? String(content[keys[0]] ?? "")
            : "";
        }
      }
      setSectionDrafts(drafts);
      setSurface("student");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load student application");
    } finally {
      setLoading(false);
    }
  }, [studentAppId]);

  const loadContribute = useCallback(async () => {
    if (!inviteId.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const rows = await getContributionSections(inviteId.trim());
      setSections(rows);
      setSurface("contribute");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load contribution invite");
    } finally {
      setLoading(false);
    }
  }, [inviteId]);

  if (loading && !summary && surface === "hub") {
    return (
      <Screen>
        <Header title="My Regulatory" onBack={onClose} />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error && surface === "hub" && !summary) {
    return (
      <Screen>
        <Header title="My Regulatory" onBack={onClose} />
        <ErrorState title="Error" message={error} onRetry={loadHub} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header
        title={
          surface === "hub"
            ? "My Regulatory Affairs"
            : surface === "applications"
              ? "My applications"
              : surface === "complaints"
                ? "Complaints involving me"
                : surface === "practice"
                  ? "Practice establishments"
                  : surface === "student"
                    ? "Student application"
                    : "Contributor invite"
        }
        onBack={
          surface === "hub"
            ? onClose
            : () => {
                setSurface("hub");
                setError(null);
              }
        }
      />
      <ScrollView testID="my-regulatory-affairs" style={styles.container}>
        {error ? <Text style={styles.error}>{error}</Text> : null}

        {surface === "hub" ? (
          <>
            {!summary?.linked ? (
              <EmptyState
                title="Not linked"
                message="Your Health ID is not linked to a provider regulatory record yet."
              />
            ) : (
              <>
                <CardHeader>Standing</CardHeader>
                {(summary.councils ?? []).map((c) => (
                  <Card key={c.councilCode}>
                    <CardBody>
                      <Text style={styles.name}>{c.councilName}</Text>
                      <Text style={styles.meta}>
                        {c.registrationNumber ?? "—"} · {c.status}
                      </Text>
                      <Badge variant={c.standing === "GOOD" ? "secondary" : "warning"}>
                        {c.standing}
                      </Badge>
                      {(c.restrictions ?? []).map((r, i) => (
                        <Text key={i} style={styles.meta}>
                          Restriction: {r.type} — {r.description}
                        </Text>
                      ))}
                    </CardBody>
                  </Card>
                ))}
                {renewal ? (
                  <Card>
                    <CardBody>
                      <Text style={styles.name}>Renewal eligibility</Text>
                      <Text style={styles.meta}>
                        {renewal.eligible ? "Eligible" : "Not yet eligible"} · CPD{" "}
                        {renewal.earnedPoints}/{renewal.requiredPoints}
                      </Text>
                    </CardBody>
                  </Card>
                ) : null}
              </>
            )}

            <Pressable
              testID="my-regulatory-applications"
              style={styles.linkRow}
              onPress={() => setSurface("applications")}
            >
              <Text style={styles.linkText}>My applications ({applications.length})</Text>
            </Pressable>
            <Pressable
              testID="my-regulatory-complaints"
              style={styles.linkRow}
              onPress={() => void loadComplaints()}
            >
              <Text style={styles.linkText}>Complaints involving me</Text>
            </Pressable>
            <Pressable
              testID="my-regulatory-practice"
              style={styles.linkRow}
              onPress={() => void loadPractice()}
            >
              <Text style={styles.linkText}>Practice establishments</Text>
            </Pressable>

            <CardHeader>Student registration</CardHeader>
            <Card>
              <CardBody>
                <TextInput
                  testID="student-application-id"
                  style={styles.input}
                  placeholder="Application id"
                  value={studentAppId}
                  onChangeText={setStudentAppId}
                />
                <Button title="Open student application" onPress={() => void loadStudent()} />
                <TextInput
                  testID="contributor-invite-id"
                  style={[styles.input, { marginTop: 12 }]}
                  placeholder="Contributor invite id"
                  value={inviteId}
                  onChangeText={setInviteId}
                />
                <Button title="Redeem contributor invite" onPress={() => void loadContribute()} />
              </CardBody>
            </Card>
          </>
        ) : null}

        {surface === "applications" ? (
          applications.length === 0 ? (
            <EmptyState title="No applications" message="You have no regulatory applications on file." />
          ) : (
            applications.map((a) => (
              <Card key={a.id}>
                <CardBody>
                  <Text style={styles.name}>
                    {a.applicationType} · {a.councilCode}
                  </Text>
                  <Text style={styles.meta}>
                    #{a.id} · {a.workflowState}
                  </Text>
                </CardBody>
              </Card>
            ))
          )
        ) : null}

        {surface === "complaints" ? (
          complaints.length === 0 ? (
            <EmptyState title="No complaints" message="No disciplinary matters list you as respondent." />
          ) : (
            complaints.map((c) => (
              <Card key={String(c.id)}>
                <CardBody>
                  <Text style={styles.name}>{c.summary ?? `Complaint ${c.id}`}</Text>
                  <Text style={styles.meta}>
                    {c.councilCode ?? "—"} · {c.status ?? "—"}
                  </Text>
                </CardBody>
              </Card>
            ))
          )
        ) : null}

        {surface === "practice" ? (
          establishments.length === 0 ? (
            <EmptyState title="No establishments" message="No practice establishment applications yet." />
          ) : (
            establishments.map((e, i) => (
              <Card key={i}>
                <CardBody>
                  <Text style={styles.meta}>{JSON.stringify(e)}</Text>
                </CardBody>
              </Card>
            ))
          )
        ) : null}

        {(surface === "student" || surface === "contribute") &&
          sections.map((section) => (
            <Card key={section.sectionKey}>
              <CardBody>
                <Text style={styles.name}>{section.label}</Text>
                <Text style={styles.meta}>{section.state}</Text>
                {section.state === "RETURNED" && surface === "student" ? (
                  <>
                    <Text style={styles.meta}>{section.returnedReason}</Text>
                    <TextInput
                      style={styles.input}
                      value={sectionDrafts[section.sectionKey] ?? ""}
                      onChangeText={(t) =>
                        setSectionDrafts((d) => ({ ...d, [section.sectionKey]: t }))
                      }
                      placeholder="Corrected content"
                    />
                    <Button
                      title="Send this part again"
                      onPress={() => {
                        const text = (sectionDrafts[section.sectionKey] ?? "").trim();
                        if (!text) {
                          setError("Enter the corrected information before sending.");
                          return;
                        }
                        void resubmitStudentSection(studentAppId.trim(), section.sectionKey, {
                          notes: text,
                        }).then(loadStudent);
                      }}
                    />
                  </>
                ) : null}
                {surface === "contribute" ? (
                  <Button
                    title="Complete this part"
                    onPress={() => {
                      void completeContributionSection(inviteId.trim(), section.sectionKey, {
                        confirmed: true,
                      }).then(loadContribute);
                    }}
                  />
                ) : null}
                {surface === "contribute" ? (
                  <Text style={styles.meta}>
                    Other parts of the student&apos;s application are not shared with you and you
                    cannot open them from this invite.
                  </Text>
                ) : null}
              </CardBody>
            </Card>
          ))}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  name: { fontSize: 16, fontWeight: "600", color: colors.gray[900] },
  meta: { marginTop: 4, fontSize: 13, color: colors.gray[500] },
  linkRow: {
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.gray[200],
  },
  linkText: { fontSize: 15, fontWeight: "600", color: "#059669" },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 8,
    backgroundColor: "#fff",
  },
  error: { color: colors.ui.error.main, marginBottom: 8, fontSize: 13 },
});
