"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Typography,
  Tabs,
  Tab,
  Button,
  IconButton,
  Tooltip,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Grid,
} from "@mui/material";
import VisibilityIcon from "@mui/icons-material/Visibility";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CancelIcon from "@mui/icons-material/Cancel";
import PrintIcon from "@mui/icons-material/Print";
import LocalShippingIcon from "@mui/icons-material/LocalShipping";
import AddIcon from "@mui/icons-material/Add";
import AssignmentIndIcon from "@mui/icons-material/AssignmentInd";

import {
  useIssuanceQueue,
  useSubmitIssuance,
  useStartProofing,
  useApproveIssuance,
  useIssueIssuance,
  useDeliverIssuance,
  useRejectIssuance,
} from "@/hooks/queries/useIssuance";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusChip } from "@/components/common/StatusChip";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import type { IssuanceStatus, IssuanceSubmitPayload } from "@/types/vito";
import { ISSUANCE_STATE_LABEL } from "@/lib/constants";

const STATUS_TABS: IssuanceStatus[] = [
  "SUBMITTED",
  "PROOFING",
  "APPROVED",
  "ISSUED",
  "DELIVERED",
  "REJECTED",
];

export default function IssuancePage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  // Modals state
  const [isSubmitModalOpen, setIsSubmitModalOpen] = useState(false);
  const [isRejectDialogOpen, setIsRejectDialogOpen] = useState(false);
  const [isApproveDialogOpen, setIsApproveDialogOpen] = useState(false);
  const [isIssueDialogOpen, setIsIssueDialogOpen] = useState(false);
  const [isDeliverDialogOpen, setIsDeliverDialogOpen] = useState(false);

  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);
  const [rejectionReason, setRejectionReason] = useState("");
  const [approvalNotes, setApprovalNotes] = useState("");

  const [submitPayload, setSubmitPayload] = useState<IssuanceSubmitPayload>({
    givenName: "",
    familyName: "",
    dateOfBirth: "",
  });

  const currentStatus = STATUS_TABS[activeTab];

  const { data, isLoading, error } = useIssuanceQueue({
    page,
    size: rowsPerPage,
    status: currentStatus,
  });

  const submitMutation = useSubmitIssuance();
  const startProofingMutation = useStartProofing();
  const approveMutation = useApproveIssuance();
  const issueMutation = useIssueIssuance();
  const deliverMutation = useDeliverIssuance();
  const rejectMutation = useRejectIssuance();

  const handleTabChange = (_: unknown, newValue: number) => {
    setActiveTab(newValue);
    setPage(0);
  };

  const handleChangePage = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleRowClick = (requestId: string) => {
    router.push(`/admin/issuance/${requestId}`);
  };

  const handleSubmitRequest = async () => {
    try {
      await submitMutation.mutateAsync(submitPayload);
      setIsSubmitModalOpen(false);
      setSubmitPayload({
        givenName: "",
        familyName: "",
        dateOfBirth: "",
      });
    } catch (err) {
      console.error("Failed to submit issuance request", err);
    }
  };

  const handleReject = async () => {
    if (selectedRequestId && rejectionReason) {
      try {
        await rejectMutation.mutateAsync({
          requestId: selectedRequestId,
          reason: rejectionReason,
        });
        setIsRejectDialogOpen(false);
        setRejectionReason("");
        setSelectedRequestId(null);
      } catch (err) {
        console.error("Failed to reject request", err);
      }
    }
  };

  const handleApprove = async () => {
    if (selectedRequestId) {
      try {
        await approveMutation.mutateAsync({
          requestId: selectedRequestId,
          notes: approvalNotes,
        });
        setIsApproveDialogOpen(false);
        setApprovalNotes("");
        setSelectedRequestId(null);
      } catch (err) {
        console.error("Failed to approve request", err);
      }
    }
  };

  const handleIssue = async () => {
    if (selectedRequestId) {
      try {
        await issueMutation.mutateAsync(selectedRequestId);
        setIsIssueDialogOpen(false);
        setSelectedRequestId(null);
      } catch (err) {
        console.error("Failed to issue request", err);
      }
    }
  };

  const handleDeliver = async () => {
    if (selectedRequestId) {
      try {
        await deliverMutation.mutateAsync({
          requestId: selectedRequestId,
          deliveryChannel: "IN_PERSON",
          deliveredAt: new Date().toISOString(),
        });
        setIsDeliverDialogOpen(false);
        setSelectedRequestId(null);
      } catch (err) {
        console.error("Failed to deliver request", err);
      }
    }
  };

  const handleStartProofing = async (requestId: string) => {
    try {
      await startProofingMutation.mutateAsync({ requestId, event: {} });
    } catch (err) {
      console.error("Failed to start proofing", err);
    }
  };

  const openRejectDialog = (requestId: string) => {
    setSelectedRequestId(requestId);
    setIsRejectDialogOpen(true);
  };

  const openApproveDialog = (requestId: string) => {
    setSelectedRequestId(requestId);
    setIsApproveDialogOpen(true);
  };

  const openIssueDialog = (requestId: string) => {
    setSelectedRequestId(requestId);
    setIsIssueDialogOpen(true);
  };

  const openDeliverDialog = (requestId: string) => {
    setSelectedRequestId(requestId);
    setIsDeliverDialogOpen(true);
  };

  return (
    <Box>
      <PageHeader
        title="Issuance Queue"
        breadcrumbs={[{ label: "Admin", href: "/admin" }, { label: "Issuance" }]}
        action={
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setIsSubmitModalOpen(true)}
          >
            Submit Request
          </Button>
        }
      />

      <Paper sx={{ width: "100%", mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          indicatorColor="primary"
          textColor="primary"
          variant="scrollable"
          scrollButtons="auto"
          sx={{ borderBottom: 1, borderColor: "divider" }}
        >
          {STATUS_TABS.map((status) => (
            <Tab key={status} label={ISSUANCE_STATE_LABEL[status]} />
          ))}
        </Tabs>

        <Box sx={{ p: 2 }}>
          <ErrorAlert error={error} />

          {isLoading ? (
            <TableContainer>
              <Table>
                <TableBody>
                  <LoadingSkeleton rows={rowsPerPage} columns={6} />
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <TableContainer>
              <Table size="medium">
                <TableHead>
                  <TableRow>
                    <TableCell>ID</TableCell>
                    <TableCell>Health ID</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Submitted At</TableCell>
                    <TableCell>State</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(data?.items ?? []).map((request) => (
                    <TableRow key={request.requestId} hover>
                      <TableCell>{request.requestId}</TableCell>
                      <TableCell>{request.applicantName}</TableCell>
                      <TableCell>{request.type}</TableCell>
                      <TableCell>
                        {request.submittedAt
                          ? new Date(request.submittedAt).toLocaleDateString()
                          : "-"}
                      </TableCell>
                      <TableCell>
                        <StatusChip status={request.status!} type="issuance" />
                      </TableCell>
                      <TableCell align="right">
                        <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
                          <Tooltip title="View Details">
                            <IconButton
                              size="small"
                              onClick={() => handleRowClick(request.requestId!)}
                            >
                              <VisibilityIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>

                          {currentStatus === "SUBMITTED" && (
                            <>
                              <Tooltip title="Start Proofing">
                                <IconButton
                                  size="small"
                                  color="primary"
                                  onClick={() => handleStartProofing(request.requestId!)}
                                >
                                  <AssignmentIndIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Reject">
                                <IconButton
                                  size="small"
                                  color="error"
                                  onClick={() => openRejectDialog(request.requestId!)}
                                >
                                  <CancelIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </>
                          )}

                          {currentStatus === "PROOFING" && (
                            <>
                              <Tooltip title="Approve">
                                <IconButton
                                  size="small"
                                  color="success"
                                  onClick={() => openApproveDialog(request.requestId!)}
                                >
                                  <CheckCircleIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Reject">
                                <IconButton
                                  size="small"
                                  color="error"
                                  onClick={() => openRejectDialog(request.requestId!)}
                                >
                                  <CancelIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </>
                          )}

                          {currentStatus === "APPROVED" && (
                            <>
                              <Tooltip title="Issue Card">
                                <IconButton
                                  size="small"
                                  color="primary"
                                  onClick={() => openIssueDialog(request.requestId!)}
                                >
                                  <PrintIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Reject">
                                <IconButton
                                  size="small"
                                  color="error"
                                  onClick={() => openRejectDialog(request.requestId!)}
                                >
                                  <CancelIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </>
                          )}

                          {currentStatus === "ISSUED" && (
                            <Tooltip title="Mark Delivered">
                              <IconButton
                                size="small"
                                color="primary"
                                onClick={() => openDeliverDialog(request.requestId!)}
                              >
                                <LocalShippingIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                  {!data?.items?.length && (
                    <TableRow>
                      <TableCell colSpan={6} align="center">
                        <Typography variant="body2" sx={{ py: 4 }}>
                          No issuance requests found in this state
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <TablePagination
                rowsPerPageOptions={[5, 10, 25]}
                component="div"
                count={data?.totalElements || 0}
                rowsPerPage={rowsPerPage}
                page={page}
                onPageChange={handleChangePage}
                onRowsPerPageChange={handleChangeRowsPerPage}
              />
            </TableContainer>
          )}
        </Box>
      </Paper>

      {/* Submit Modal */}
      <Dialog
        open={isSubmitModalOpen}
        onClose={() => { setIsSubmitModalOpen(false); submitMutation.reset(); }}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Submit Issuance Request</DialogTitle>
        <DialogContent dividers>
          {submitMutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {(submitMutation.error as Error)?.message ?? "Failed to submit issuance request"}
            </Alert>
          )}
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Given Name"
                value={submitPayload.givenName}
                onChange={(e) => setSubmitPayload({ ...submitPayload, givenName: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Family Name"
                value={submitPayload.familyName}
                onChange={(e) => setSubmitPayload({ ...submitPayload, familyName: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Date of Birth"
                type="date"
                InputLabelProps={{ shrink: true }}
                value={submitPayload.dateOfBirth}
                onChange={(e) => setSubmitPayload({ ...submitPayload, dateOfBirth: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                select
                label="Gender"
                value={submitPayload.gender ?? ""}
                onChange={(e) => setSubmitPayload({ ...submitPayload, gender: e.target.value as IssuanceSubmitPayload["gender"] })}
              >
                <MenuItem value="MALE">Male</MenuItem>
                <MenuItem value="FEMALE">Female</MenuItem>
                <MenuItem value="OTHER">Other</MenuItem>
                <MenuItem value="UNKNOWN">Unknown</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="National ID"
                value={submitPayload.nationalId ?? ""}
                onChange={(e) => setSubmitPayload({ ...submitPayload, nationalId: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Phone Number"
                value={submitPayload.phoneNumber ?? ""}
                onChange={(e) => setSubmitPayload({ ...submitPayload, phoneNumber: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Facility ID"
                value={submitPayload.facilityId ?? ""}
                onChange={(e) => setSubmitPayload({ ...submitPayload, facilityId: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                select
                label="Source"
                value={submitPayload.source ?? ""}
                onChange={(e) => setSubmitPayload({ ...submitPayload, source: e.target.value as IssuanceSubmitPayload["source"] })}
              >
                <MenuItem value="FACILITY">Facility</MenuItem>
                <MenuItem value="PORTAL">Portal</MenuItem>
                <MenuItem value="FIELD">Field</MenuItem>
                <MenuItem value="MIGRATION">Migration</MenuItem>
              </TextField>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setIsSubmitModalOpen(false)}>Cancel</Button>
          <Button
            onClick={handleSubmitRequest}
            variant="contained"
            disabled={submitMutation.isPending}
          >
            Submit
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reject Dialog */}
      <ConfirmDialog
        open={isRejectDialogOpen}
        title="Reject Request"
        message="Please provide a reason for rejecting this issuance request."
        onConfirm={handleReject}
        onCancel={() => setIsRejectDialogOpen(false)}
        confirmLabel="Reject"
        confirmColor="error"
        loading={rejectMutation.isPending}
      >
        <TextField
          fullWidth
          label="Rejection Reason"
          multiline
          rows={3}
          value={rejectionReason}
          onChange={(e) => setRejectionReason(e.target.value)}
          sx={{ mt: 2 }}
        />
      </ConfirmDialog>

      {/* Approve Dialog */}
      <ConfirmDialog
        open={isApproveDialogOpen}
        title="Approve Request"
        message="Are you sure you want to approve this issuance request?"
        onConfirm={handleApprove}
        onCancel={() => setIsApproveDialogOpen(false)}
        confirmLabel="Approve"
        confirmColor="success"
        loading={approveMutation.isPending}
      >
        <TextField
          fullWidth
          label="Notes (Optional)"
          multiline
          rows={2}
          value={approvalNotes}
          onChange={(e) => setApprovalNotes(e.target.value)}
          sx={{ mt: 2 }}
        />
      </ConfirmDialog>

      {/* Issue Dialog */}
      <ConfirmDialog
        open={isIssueDialogOpen}
        title="Issue Smart Card"
        message="This will mark the card as issued and ready for delivery. Continue?"
        onConfirm={handleIssue}
        onCancel={() => setIsIssueDialogOpen(false)}
        confirmLabel="Issue"
        loading={issueMutation.isPending}
      />

      {/* Deliver Dialog */}
      <ConfirmDialog
        open={isDeliverDialogOpen}
        title="Confirm Delivery"
        message="Mark this issuance request as delivered to the applicant?"
        onConfirm={handleDeliver}
        onCancel={() => setIsDeliverDialogOpen(false)}
        confirmLabel="Deliver"
        loading={deliverMutation.isPending}
      />
    </Box>
  );
}
