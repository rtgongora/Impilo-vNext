"use client";

import { useState } from "react";
import {
  Box,
  Paper,
  Typography,
  Divider,
  Button,
  Chip,
  TextField,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Stack,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  CircularProgress,
} from "@mui/material";
import { PageHeader } from "@/components/common/PageHeader";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { JsonViewer } from "@/components/common/JsonViewer";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import {
  useRegistryMode,
  useUpdateRegistryMode,
  usePendingProvisional,
  useIssueProvisional,
  useReconcileProvisional,
  useRegistryDedupPending,
  useResolveRegistryDedup,
  useOpenCrMatch,
} from "@/hooks/queries/useRegistry";
import { useClientDetail } from "@/hooks/queries/useClients";
import type { RegistryMode } from "@/types/vito";

export default function RegistryAdminPage() {
  // Mode configuration
  const { data: modeData, isLoading: modeLoading } = useRegistryMode();
  const updateMode = useUpdateRegistryMode();
  const [confirmMode, setConfirmMode] = useState<{ open: boolean; mode: RegistryMode | null }>({
    open: false,
    mode: null,
  });

  // Provisional ID Management
  const { data: pendingProvisional, isLoading: provisionalLoading } = usePendingProvisional();
  const issueProvisional = useIssueProvisional();
  const reconcileProvisional = useReconcileProvisional();
  const [issueModalOpen, setIssueModalOpen] = useState(false);
  const [provisionalForm, setProvisionalForm] = useState({ healthId: "", reason: "" });

  // Dedup Queue
  const { data: dedupData, isLoading: dedupLoading } = useRegistryDedupPending();
  const resolveDedup = useResolveRegistryDedup();

  // OpenCR Match Test
  const [matchHealthId, setMatchHealthId] = useState("");
  const { data: matchClientDemographics, refetch: fetchDemographics } = useClientDetail(matchHealthId);
  const runMatch = useOpenCrMatch();
  const [matchResult, setMatchResult] = useState<any>(null);

  const handleUpdateMode = (mode: RegistryMode) => {
    setConfirmMode({ open: true, mode });
  };

  const onConfirmModeUpdate = () => {
    if (confirmMode.mode) {
      updateMode.mutate({ mode: confirmMode.mode });
    }
    setConfirmMode({ open: false, mode: null });
  };

  const handleIssueProvisional = () => {
    issueProvisional.mutate(
      {
        facilityId: "DEFAULT", // Required in API, but not in user's modal list
        healthId: provisionalForm.healthId,
        reason: provisionalForm.reason,
      },
      {
        onSuccess: () => {
          setIssueModalOpen(false);
          setProvisionalForm({ healthId: "", reason: "" });
        },
      }
    );
  };

  const handleReconcile = (provisionalRef: string, healthId: string) => {
    reconcileProvisional.mutate({ provisionalRef, healthId });
  };

  const handleResolveDedup = (caseId: string) => {
    // Simple resolve as NOT_DUPLICATE for now, as instruction says just "Resolve button"
    resolveDedup.mutate({ caseId, action: "NOT_DUPLICATE" });
  };

  const handleRunMatchTest = async () => {
    const { data: client } = await fetchDemographics();
    if (client) {
      runMatch.mutate(
        {
          givenName: client.givenName,
          familyName: client.familyName,
          dateOfBirth: client.dateOfBirth,
          gender: client.gender,
        },
        {
          onSuccess: (res) => {
            setMatchResult(res);
          },
        }
      );
    }
  };

  const getModeLabel = (mode: string = "") => {
    switch (mode) {
      case "NORMAL":
        return "ONLINE";
      case "OFFLINE":
        return "OFFLINE";
      case "PROVISIONAL":
        return "DEGRADED";
      default:
        return mode;
    }
  };

  const getModeColor = (mode: string = ""): any => {
    switch (mode) {
      case "NORMAL":
        return "success";
      case "OFFLINE":
        return "error";
      case "PROVISIONAL":
        return "warning";
      default:
        return "default";
    }
  };

  return (
    <Box>
      <PageHeader
        title="Registry Administration"
        breadcrumbs={[{ label: "Admin", href: "/admin" }, { label: "Registry" }]}
      />

      {/* Section 1: Registry Mode */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Registry Mode
        </Typography>
        <Stack direction="row" spacing={3} alignItems="center">
          <Chip
            label={getModeLabel(modeData?.mode)}
            color={getModeColor(modeData?.mode)}
            sx={{ fontWeight: "bold", px: 2, height: 40, fontSize: "1rem" }}
          />
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              color="success"
              onClick={() => handleUpdateMode("NORMAL")}
              disabled={modeData?.mode === "NORMAL"}
            >
              Go Online
            </Button>
            <Button
              variant="outlined"
              color="error"
              onClick={() => handleUpdateMode("OFFLINE")}
              disabled={modeData?.mode === "OFFLINE"}
            >
              Go Offline
            </Button>
            <Button
              variant="outlined"
              color="warning"
              onClick={() => handleUpdateMode("PROVISIONAL")}
              disabled={modeData?.mode === "PROVISIONAL"}
            >
              Go Degraded
            </Button>
          </Stack>
        </Stack>
        <ConfirmDialog
          open={confirmMode.open}
          title="Change Registry Mode"
          message={`Are you sure you want to change the registry mode to ${getModeLabel(
            confirmMode.mode || ""
          )}?`}
          onConfirm={onConfirmModeUpdate}
          onCancel={() => setConfirmMode({ open: false, mode: null })}
          loading={updateMode.isPending}
        />
      </Paper>

      <Divider sx={{ mb: 3 }} />

      {/* Section 2: Provisional ID Management */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
          <Typography variant="h6">Provisional ID Management</Typography>
          <Button variant="contained" onClick={() => setIssueModalOpen(true)}>
            Issue Provisional ID
          </Button>
        </Box>

        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Provisional ID</TableCell>
                <TableCell>Health ID</TableCell>
                <TableCell>Issued At</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {provisionalLoading ? (
                <LoadingSkeleton rows={3} columns={5} />
              ) : (
                pendingProvisional?.items?.map((row) => (
                  <TableRow key={row.provisionalRef}>
                    <TableCell>{row.provisionalRef}</TableCell>
                    <TableCell>{row.healthId || "N/A"}</TableCell>
                    <TableCell>{row.issuedAt ? new Date(row.issuedAt).toLocaleString() : ""}</TableCell>
                    <TableCell>{row.reason || "N/A"}</TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => handleReconcile(row.provisionalRef!, row.healthId!)}
                        disabled={!row.healthId}
                      >
                        Reconcile
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
              {!provisionalLoading && !pendingProvisional?.items?.length && (
                <TableRow>
                  <TableCell colSpan={5} align="center">
                    No pending provisional IDs
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>

        <Dialog open={issueModalOpen} onClose={() => setIssueModalOpen(false)}>
          <DialogTitle>Issue Provisional ID</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1, minWidth: 400 }}>
              <TextField
                label="Health ID"
                fullWidth
                value={provisionalForm.healthId}
                onChange={(e) =>
                  setProvisionalForm({ ...provisionalForm, healthId: e.target.value })
                }
              />
              <TextField
                label="Reason"
                fullWidth
                multiline
                rows={3}
                value={provisionalForm.reason}
                onChange={(e) => setProvisionalForm({ ...provisionalForm, reason: e.target.value })}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setIssueModalOpen(false)}>Cancel</Button>
            <Button
              variant="contained"
              onClick={handleIssueProvisional}
              disabled={issueProvisional.isPending}
              startIcon={issueProvisional.isPending ? <CircularProgress size={20} color="inherit" /> : null}
            >
              Issue
            </Button>
          </DialogActions>
        </Dialog>
      </Paper>

      <Divider sx={{ mb: 3 }} />

      {/* Section 3: Registry Dedup Queue */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Registry Dedup Queue
        </Typography>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Case ID</TableCell>
                <TableCell>CPID A</TableCell>
                <TableCell>CPID B</TableCell>
                <TableCell>Score</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {dedupLoading ? (
                <LoadingSkeleton rows={3} columns={5} />
              ) : (
                dedupData?.items?.map((row) => (
                  <TableRow key={row.caseId}>
                    <TableCell>{row.caseId}</TableCell>
                    <TableCell>{row.cpidA}</TableCell>
                    <TableCell>{row.cpidB}</TableCell>
                    <TableCell>{row.matchScore}</TableCell>
                    <TableCell>
                      <Button size="small" variant="outlined" onClick={() => handleResolveDedup(row.caseId!)}>
                        Resolve
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
              {!dedupLoading && !dedupData?.items?.length && (
                <TableRow>
                  <TableCell colSpan={5} align="center">
                    No pending dedup cases
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Divider sx={{ mb: 3 }} />

      {/* Section 4: OpenCR Match Test */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          OpenCR Match Test
        </Typography>
        <Box sx={{ display: "flex", gap: 2, mb: 3 }}>
          <TextField
            label="Health ID"
            size="small"
            value={matchHealthId}
            onChange={(e) => setMatchHealthId(e.target.value)}
          />
          <Button
            variant="contained"
            onClick={handleRunMatchTest}
            disabled={runMatch.isPending}
            startIcon={runMatch.isPending ? <CircularProgress size={20} color="inherit" /> : null}
          >
            Run Match Test
          </Button>
        </Box>

        <Box sx={{ mb: 2 }}>
          <ErrorAlert error={runMatch.error} />
        </Box>

        {matchResult && (
          <Box sx={{ mt: 2 }}>
            <Typography variant="subtitle2" gutterBottom>
              Match Result:
            </Typography>
            <JsonViewer data={matchResult} />
          </Box>
        )}
      </Paper>
    </Box>
  );
}
