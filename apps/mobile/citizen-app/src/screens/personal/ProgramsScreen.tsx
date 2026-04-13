/**
 * ProgramsScreen — Wellness programs and enrollments.
 */
import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Card,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchPrograms,
  enrollInProgram,
  fetchMyEnrollments,
} from "../../services/programService";
import type { WellnessProgram, Enrollment } from "../../types";

export function ProgramsScreen() {
  const [programs, setPrograms] = useState<WellnessProgram[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [enrollingId, setEnrollingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [progs, enrs] = await Promise.all([
        fetchPrograms(),
        fetchMyEnrollments(),
      ]);
      setPrograms(progs);
      setEnrollments(enrs);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleEnroll = useCallback(
    async (programId: string) => {
      setEnrollingId(programId);
      try {
        await enrollInProgram(programId);
        await load();
      } catch (err) {
        setError(err instanceof Error ? err : new Error(String(err)));
      } finally {
        setEnrollingId(null);
      }
    },
    [load],
  );

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  const enrolledProgramIds = new Set(enrollments.map((e) => e.programId));

  return (
    <ScrollView
      testID="programs-screen"
      style={styles.scrollView}
      contentContainerStyle={styles.container}
    >
      {/* Available Programs */}
      <Text style={styles.sectionTitle}>Available Programs</Text>
      {programs.length === 0 ? (
        <EmptyState
          title="No programs available"
          message="New wellness programs will appear here"
        />
      ) : (
        programs.map((program) => (
          <Card key={program.id}>
            <CardBody>
              <View testID={`program-${program.id}`} style={styles.programContent}>
                <Text style={styles.programName}>{program.name}</Text>
                <Text style={styles.programDesc}>{program.description}</Text>
                <View style={styles.programMeta}>
                  <Text style={styles.metaText}>Duration: {program.duration}</Text>
                  <Badge variant="secondary">{program.category}</Badge>
                </View>
                {enrolledProgramIds.has(program.id) ? (
                  <Badge variant="default">Enrolled</Badge>
                ) : (
                  <Button
                    title={
                      enrollingId === program.id ? "Enrolling..." : "Enroll"
                    }
                    variant="primary"
                    size="sm"
                    onPress={() => handleEnroll(program.id)}
                    disabled={enrollingId === program.id}
                    testID={`enroll-${program.id}`}
                  />
                )}
              </View>
            </CardBody>
          </Card>
        ))
      )}

      {/* My Programs */}
      <Text style={[styles.sectionTitle, styles.myProgramsTitle]}>My Programs</Text>
      {enrollments.length === 0 ? (
        <EmptyState
          title="No enrollments"
          message="Enroll in a program above to get started"
        />
      ) : (
        enrollments.map((enrollment) => {
          const statusVariant =
            enrollment.status === "ACTIVE"
              ? "default"
              : enrollment.status === "COMPLETED"
                ? "secondary"
                : "outline";

          return (
            <Card key={enrollment.id}>
              <CardBody>
                <View testID={`enrollment-${enrollment.id}`} style={styles.enrollmentContent}>
                  <View style={styles.enrollmentHeaderRow}>
                    <Text style={styles.enrollmentName}>
                      {enrollment.programName}
                    </Text>
                    <Badge variant={statusVariant}>{enrollment.status}</Badge>
                  </View>
                  <Text style={styles.enrollmentProgress}>
                    Progress: {enrollment.progress}%
                  </Text>
                  <View style={styles.progressBar}>
                    <View
                      style={[
                        styles.progressFill,
                        { width: `${Math.min(enrollment.progress, 100)}%` },
                      ]}
                    />
                  </View>
                  <Text style={styles.enrollmentDate}>
                    Enrolled:{" "}
                    {new Date(enrollment.enrolledAt).toLocaleDateString()}
                  </Text>
                </View>
              </CardBody>
            </Card>
          );
        })
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 12,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  myProgramsTitle: {
    marginTop: 8,
  },
  programContent: {
    gap: 6,
  },
  programName: {
    fontSize: 15,
    fontWeight: "700",
    color: "#111827",
  },
  programDesc: {
    fontSize: 13,
    color: "#6B7280",
  },
  programMeta: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  metaText: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  enrollmentContent: {
    gap: 6,
  },
  enrollmentHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  enrollmentName: {
    fontSize: 15,
    fontWeight: "600",
    color: "#111827",
    flex: 1,
  },
  enrollmentProgress: {
    fontSize: 13,
    fontWeight: "500",
    color: "#374151",
  },
  progressBar: {
    height: 6,
    backgroundColor: "#E5E7EB",
    borderRadius: 3,
  },
  progressFill: {
    height: 6,
    backgroundColor: "#16A34A",
    borderRadius: 3,
  },
  enrollmentDate: {
    fontSize: 12,
    color: "#9CA3AF",
  },
});
