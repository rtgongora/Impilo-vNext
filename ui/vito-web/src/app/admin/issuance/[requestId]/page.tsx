"use client";

import { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import {
  Box,
  Paper,
  Grid,
  Typography,
  Button,
  Divider,
  List,
  ListItem,
  ListItemText,
  TextField,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CancelIcon from "@mui/icons-material/Cancel";
import PrintIcon from "@mui/icons-material/Print";
import LocalShippingIcon from "@mui/icons-material/LocalShipping";

import {
  useIssuanceDetail,
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
import { GENDER_LABEL, PROOFING_METHOD_LABEL, PROOFING_OUTCOME_LABEL } from "@/lib/constants";

export default function IssuanceDetailPage() {
  const router = useRouter();
  const { requestId } = useParams() as { requestId: string };

  const { data: request, isLoading, error } = useIssuanceDetail(requestId);

  const [isRejectDialogOpen, setIsRejectDialogOpen] = useState(false);
  const [isApproveDialogOpen, setIsApproveDialogOpen] = useState(false);
  const [isIssueDialogOpen, setIsIssueDialogOpen] = useState(false);
  const [isDeliverDialogOpen, setIsDeliverDialogOpen] = useState(false);

  const [rejectionReason, setRejectionReason] = useState("");
  const [approvalNotes, setApprovalNotes] = useState("");

  const approveMutation = useApproveIssuance();
  const issueMutation = useIssueIssuance();
  const deliverMutation = useDeliverIssuance();
  const rejectMutation = useRejectIssuance();

  const handleBack = () => {
    router.push("/admin/issuance");
  };

  const handleReject = async () => {
    if (rejectionReason) {
      try {
        await rejectMutation.mutateAsync({
          requestId,
          reason: rejectionReason,
        });
        setIsRejectDialogOpen(false);
        setRejectionReason("");
      } catch (err) {
        console.error("Failed to reject request", err);
      }
    }
  };

  const handleApprove = async () => {
    try {
      await approveMutation.mutateAsync({
        requestId,
        notes: approvalNotes,
      });
      setIsApproveDialogOpen(false);
      setApprovalNotes("");
    } catch (err) {
      console.error("Failed to approve request", err);
    }
  };

  const handleIssue = async () => {
    try {
      await issueMutation.mutateAsync(requestId);
      setIsIssueDialogOpen(false);
    } catch (err) {
      console.error("Failed to issue request", err);
    }
  };

  const handleDeliver = async () => {
    try {
      await deliverMutation.mutateAsync({
        requestId,
        deliveryChannel: "IN_PERSON",
        deliveredAt: new Date().toISOString(),
      });
      setIsDeliverDialogOpen(false);
    } catch (err) {
      console.error("Failed to deliver request", err);
    }
  };

  if (isLoading) {
    return (
      <Box>
        <PageHeader title="Issuance Details" />
        <LoadingSkeleton rows={10} columns={1} />
      </Box>
    );
  }

  if (error || !request) {
    return (
      <Box>
        <PageHeader title="Issuance Details" />
        <ErrorAlert error={error} />
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack} sx={{ mt: 2 }}>
          Back to Queue
        </Button>
      </Box>
    );
  }

  return (
    <Box>
      <PageHeader
        title={`Request: ${request.requestId}`}
        breadcrumbs={[
          { label: "Admin", href: "/admin" },
          { label: "Issuance", href: "/admin/issuance" },
          { label: "Details" },
        ]}
        action={
          <Button startIcon={<ArrowBackIcon />} onClick={handleBack}>
            Back to Queue
          </Button>
        }
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3, mb: 3 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
              <Typography variant="h6">Applicant Information</Typography>
              <StatusChip status={request.status!} type="issuance" size="medium" />
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <Typography variant="caption" color="text.secondary">Full Name</Typography>
                <Typography variant="body1" fontWeight="medium">{request.applicantName}</Typography>
              </Grid>
              <Grid item xs={12} sm={6}>
                <Typography variant="caption" color="text.secondary">Health ID</Typography>
                <Typography variant="body1">{request.healthId || "N/A"}</Typography>
              </Grid>
              <Grid item xs={12} sm={6}>
                <Typography variant="caption" color="text.secondary">Date of Birth</Typography>
                <Typography variant="body1">{request.dateOfBirth}</Typography>
              </Grid>
              <Grid item xs={12} sm={6}>
                <Typography variant="caption" color="text.secondary">Submitted At</Typography>
                <Typography variant="body1">
                  {request.submittedAt ? new Date(request.submittedAt).toLocaleString() : "N/A"}
                </Typography>
              </Grid>
            </Grid>

            {request.status === "REJECTED" && (
              <Box sx={{ mt: 3, p: 2, bgcolor: "error.light", borderRadius: 1 }}>
                <Typography variant="subtitle2" color="error.dark">Rejection Reason</Typography>
                <Typography variant="body2">{request.rejectionReason}</Typography>
                <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                  Rejected on {request.rejectedAt ? new Date(request.rejectedAt).toLocaleString() : "unknown date"}
                </Typography>
              </Box>
            )}
          </Paper>

          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>Proofing Events</Typography>
            <Divider sx={{ mb: 2 }} />
            {(request as any).proofingEvents && (request as any).proofingEvents.length > 0 ? (
              <List disablePadding>
                {((request as any).proofingEvents as any[]).map((event: any, index: number) => (
                  <ListItem key={index} divider={index !== (request as any).proofingEvents.length - 1} sx={{ px: 0 }}>
                    <ListItemText
                      primary={PROOFING_METHOD_LABEL[event.method!] || event.method}
                      secondary={
                        <>
                          <Typography component="span" variant="body2" color="text.primary">
                            Outcome: {PROOFING_OUTCOME_LABEL[event.outcome!] || event.outcome}
                          </Typography>
                          {` — Confidence: ${Math.round((event.confidence || 0) * 100)}%`}
                          <br />
                          {`Performed by ${event.performedBy} on ${new Date(event.performedAt!).toLocaleString()}`}
                          {event.notes && (
                            <>
                              <br />
                              <Typography component="span" variant="body2" fontStyle="italic">
                                Notes: {event.notes}
                              </Typography>
                            </>
                          )}
                        </>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            ) : (
              <Typography variant="body2" color="text.secondary">No proofing events recorded</Typography>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, mb: 3 }}>
            <Typography variant="h6" gutterBottom>Actions</Typography>
            <Divider sx={{ mb: 2 }} />
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
              {request.status === "SUBMITTED" && (
                <Button
                  variant="outlined"
                  color="error"
                  fullWidth
                  startIcon={<CancelIcon />}
                  onClick={() => setIsRejectDialogOpen(true)}
                >
                  Reject Request
                </Button>
              )}

              {request.status === "PROOFING" && (
                <>
                  <Button
                    variant="contained"
                    color="success"
                    fullWidth
                    startIcon={<CheckCircleIcon />}
                    onClick={() => setIsApproveDialogOpen(true)}
                  >
                    Approve Request
                  </Button>
                  <Button
                    variant="outlined"
                    color="error"
                    fullWidth
                    startIcon={<CancelIcon />}
                    onClick={() => setIsRejectDialogOpen(true)}
                  >
                    Reject Request
                  </Button>
                </>
              )}

              {request.status === "APPROVED" && (
                <>
                  <Button
                    variant="contained"
                    color="primary"
                    fullWidth
                    startIcon={<PrintIcon />}
                    onClick={() => setIsIssueDialogOpen(true)}
                  >
                    Issue Smart Card
                  </Button>
                  <Button
                    variant="outlined"
                    color="error"
                    fullWidth
                    startIcon={<CancelIcon />}
                    onClick={() => setIsRejectDialogOpen(true)}
                  >
                    Reject Request
                  </Button>
                </>
              )}

              {request.status === "ISSUED" && (
                <Button
                  variant="contained"
                  color="primary"
                  fullWidth
                  startIcon={<LocalShippingIcon />}
                  onClick={() => setIsDeliverDialogOpen(true)}
                >
                  Mark Delivered
                </Button>
              )}

              {["DELIVERED", "REJECTED"].includes(request.status!) && (
                <Typography variant="body2" color="text.secondary" align="center">
                  No further actions available for this request status.
                </Typography>
              )}
            </Box>
          </Paper>

          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>Request Lifecycle</Typography>
            <Divider sx={{ mb: 2 }} />
            <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
              <Box>
                <Typography variant="caption" color="text.secondary" display="block">Submitted</Typography>
                <Typography variant="body2">{request.submittedAt ? new Date(request.submittedAt).toLocaleString() : "-"}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary" display="block">Approved</Typography>
                <Typography variant="body2">{request.approvedAt ? new Date(request.approvedAt).toLocaleString() : "-"}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary" display="block">Issued</Typography>
                <Typography variant="body2">{request.issuedAt ? new Date(request.issuedAt).toLocaleString() : "-"}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary" display="block">Delivered</Typography>
                <Typography variant="body2">{request.deliveredAt ? new Date(request.deliveredAt).toLocaleString() : "-"}</Typography>
              </Box>
            </Box>
          </Paper>
        </Grid>
      </Grid>

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
