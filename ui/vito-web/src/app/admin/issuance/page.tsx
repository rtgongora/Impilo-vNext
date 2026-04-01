"use client";

import React, { useState } from "react";
import {
  Box,
  Button,
  Card,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import {
  Add as AddIcon,
  Visibility as ViewIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  PlayArrow as StartIcon,
  LocalShipping as DeliverIcon,
  Badge as IssueIcon,
} from "@mui/icons-material";
import Link from "next/link";
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
import { IssuanceStatus, DeliveryChannel, IssuanceSubmitPayload } from "@/types/vito";
import { DELIVERY_CHANNEL_LABEL, GENDER_LABEL } from "@/lib/constants";

const STATUS_TABS: IssuanceStatus[] = [
  "SUBMITTED",
  "PROOFED",
  "APPROVED",
  "ISSUED",
  "DELIVERED",
  "REJECTED",
];

export default function IssuanceQueuePage() {
  const [activeTab, setActiveTab] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const status = STATUS_TABS[activeTab];

  const { data, isLoading, error } = useIssuanceQueue({
    page,
    size: rowsPerPage,
    status,
  });

  const [submitModalOpen, setSubmitModalOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [deliverDialogOpen, setDeliverDialogOpen] = useState(false);
  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);
  const [rejectionReason, setRejectionReason] = useState("");
  const [deliveryChannel, setDeliveryChannel] = useState<DeliveryChannel>("IN_PERSON");

  const [newRequest, setNewRequest] = useState<IssuanceSubmitPayload>({
    givenName: "",
    familyName: "",
    dateOfBirth: "",
    gender: "UNKNOWN",
    facilityId: "",
  });

  const submitMutation = useSubmitIssuance();
  const startProofingMutation = useStartProofing();
  const approveMutation = useApproveIssuance();
  const issueMutation = useIssueIssuance();
  const deliverMutation = useDeliverIssuance();
  const rejectMutation = useRejectIssuance();

  const handleTabChange = (_: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
    setPage(0);
  };

  const handlePageChange = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleRowsPerPageChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleSubmitNew = async () => {
    await submitMutation.mutateAsync(newRequest);
    setSubmitModalOpen(false);
    setNewRequest({
      givenName: "",
      familyName: "",
      dateOfBirth: "",
      gender: "UNKNOWN",
      facilityId: "",
    });
  };

  const handleStartProofing = async (requestId: string) => {
    await startProofingMutation.mutateAsync({
      requestId,
      event: {
        method: "IN_PERSON",
        outcome: "PASSED",
        performedAt: new Date().toISOString(),
      },
    });
  };

  const handleApprove = async (requestId: string) => {
    await approveMutation.mutateAsync({ requestId });
  };

  const handleIssue = async (requestId: string) => {
    await issueMutation.mutateAsync(requestId);
  };

  const handleReject = async () => {
    if (selectedRequestId && rejectionReason) {
      await rejectMutation.mutateAsync({
        requestId: selectedRequestId,
        reason: rejectionReason,
      });
      setRejectDialogOpen(false);
      setRejectionReason("");
      setSelectedRequestId(null);
    }
  };

  const handleDeliver = async () => {
    if (selectedRequestId) {
      await deliverMutation.mutateAsync({
        requestId: selectedRequestId,
        deliveryChannel,
        deliveredAt: new Date().toISOString(),
      });
      setDeliverDialogOpen(false);
      setSelectedRequestId(null);
    }
  };

  const renderActions = (requestId: string, currentStatus: IssuanceStatus) => {
    return (
      <Box sx={{ display: "flex", gap: 1 }}>
        <Button
          component={Link}
          href={`/admin/issuance/${requestId}`}
          size="small"
          startIcon={<ViewIcon />}
        >
          View
        </Button>
        {currentStatus === "SUBMITTED" && (
          <Button
            size="small"
            color="primary"
            variant="contained"
            startIcon={<StartIcon />}
            onClick={() => handleStartProofing(requestId)}
            disabled={startProofingMutation.isPending}
          >
            Start Proofing
          </Button>
        )}
        {currentStatus === "PROOFED" && (
          <>
            <Button
              size="small"
              color="success"
              variant="contained"
              startIcon={<ApproveIcon />}
              onClick={() => handleApprove(requestId)}
              disabled={approveMutation.isPending}
            >
              Approve
            </Button>
            <Button
              size="small"
              color="error"
              variant="outlined"
              startIcon={<RejectIcon />}
              onClick={() => {
                setSelectedRequestId(requestId);
                setRejectDialogOpen(true);
              }}
              disabled={rejectMutation.isPending}
            >
              Reject
            </Button>
          </>
        )}
        {currentStatus === "APPROVED" && (
          <Button
            size="small"
            color="primary"
            variant="contained"
            startIcon={<IssueIcon />}
            onClick={() => handleIssue(requestId)}
            disabled={issueMutation.isPending}
          >
            Issue
          </Button>
        )}
        {currentStatus === "ISSUED" && (
          <Button
            size="small"
            color="success"
            variant="contained"
            startIcon={<DeliverIcon />}
            onClick={() => {
              setSelectedRequestId(requestId);
              setDeliverDialogOpen(true);
            }}
            disabled={deliverMutation.isPending}
          >
            Mark Delivered
          </Button>
        )}
      </Box>
    );
  };

  return (
    <Box>
      <PageHeader
        title="Issuance Queue"
        breadcrumbs={[
          { label: "Admin", href: "/admin" },
          { label: "Issuance Queue" },
        ]}
        action={
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setSubmitModalOpen(true)}
          >
            Submit New Request
          </Button>
        }
      />

      <ErrorAlert error={error as any} />

      <Card sx={{ mb: 4 }}>
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          indicatorColor="primary"
          textColor="primary"
          variant="scrollable"
          scrollButtons="auto"
          sx={{ borderBottom: 1, borderColor: "divider" }}
        >
          {STATUS_TABS.map((s) => (
            <Tab key={s} label={s} />
          ))}
        </Tabs>

        <TableContainer>
          <Table sx={{ minWidth: 650 }}>
            <TableHead>
              <TableRow>
                <TableCell>Request ID</TableCell>
                <TableCell>Applicant</TableCell>
                <TableCell>DOB</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Submitted At</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading ? (
                <LoadingSkeleton rows={rowsPerPage} columns={6} />
              ) : data?.items.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    No requests found in this state.
                  </TableCell>
                </TableRow>
              ) : (
                data?.items.map((row) => (
                  <TableRow key={row.requestId}>
                    <TableCell sx={{ fontFamily: "monospace", fontSize: "0.8rem" }}>
                      {row.requestId}
                    </TableCell>
                    <TableCell>{row.applicantName}</TableCell>
                    <TableCell>{row.dateOfBirth}</TableCell>
                    <TableCell>
                      <StatusChip status={row.status || ""} type="issuance" />
                    </TableCell>
                    <TableCell>
                      {row.submittedAt ? new Date(row.submittedAt).toLocaleString() : "-"}
                    </TableCell>
                    <TableCell align="right">
                      {renderActions(row.requestId!, row.status!)}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          rowsPerPageOptions={[5, 10, 25]}
          component="div"
          count={data?.totalElements || 0}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={handlePageChange}
          onRowsPerPageChange={handleRowsPerPageChange}
        />
      </Card>

      {/* Submit New Request Modal */}
      <Dialog open={submitModalOpen} onClose={() => setSubmitModalOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Submit New Issuance Request</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2, display: "flex", flexDirection: "column", gap: 2 }}>
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  label="Given Name"
                  value={newRequest.givenName}
                  onChange={(e) => setNewRequest({ ...newRequest, givenName: e.target.value })}
                />
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  label="Family Name"
                  value={newRequest.familyName}
                  onChange={(e) => setNewRequest({ ...newRequest, familyName: e.target.value })}
                />
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  label="Date of Birth"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  value={newRequest.dateOfBirth}
                  onChange={(e) => setNewRequest({ ...newRequest, dateOfBirth: e.target.value })}
                />
              </Grid>
              <Grid item xs={6}>
                <FormControl fullWidth>
                  <InputLabel>Gender</InputLabel>
                  <Select
                    value={newRequest.gender}
                    label="Gender"
                    onChange={(e) => setNewRequest({ ...newRequest, gender: e.target.value as any })}
                  >
                    {Object.entries(GENDER_LABEL).map(([val, label]) => (
                      <MenuItem key={val} value={val}>
                        {label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Facility ID"
                  value={newRequest.facilityId}
                  onChange={(e) => setNewRequest({ ...newRequest, facilityId: e.target.value })}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="National ID (Optional)"
                  value={newRequest.nationalId || ""}
                  onChange={(e) => setNewRequest({ ...newRequest, nationalId: e.target.value })}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Phone Number (Optional)"
                  value={newRequest.phoneNumber || ""}
                  onChange={(e) => setNewRequest({ ...newRequest, phoneNumber: e.target.value })}
                />
              </Grid>
            </Grid>
          </Box>
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button onClick={() => setSubmitModalOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleSubmitNew}
            disabled={submitMutation.isPending || !newRequest.givenName || !newRequest.familyName || !newRequest.dateOfBirth}
          >
            Submit
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reject Dialog */}
      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Reject Issuance Request</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2 }}>
            <Typography variant="body2" sx={{ mb: 2 }}>
              Please provide a reason for rejecting this request. This action cannot be undone.
            </Typography>
            <TextField
              fullWidth
              multiline
              rows={3}
              label="Rejection Reason"
              required
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
            />
          </Box>
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button onClick={() => setRejectDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleReject}
            disabled={rejectMutation.isPending || !rejectionReason}
          >
            Reject Request
          </Button>
        </DialogActions>
      </Dialog>

      {/* Deliver Dialog */}
      <Dialog open={deliverDialogOpen} onClose={() => setDeliverDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Mark as Delivered</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2 }}>
            <Typography variant="body2" sx={{ mb: 2 }}>
              Confirm that the card has been delivered to the applicant.
            </Typography>
            <FormControl fullWidth>
              <InputLabel>Delivery Channel</InputLabel>
              <Select
                value={deliveryChannel}
                label="Delivery Channel"
                onChange={(e) => setDeliveryChannel(e.target.value as DeliveryChannel)}
              >
                {Object.entries(DELIVERY_CHANNEL_LABEL).map(([val, label]) => (
                  <MenuItem key={val} value={val}>
                    {label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button onClick={() => setDeliverDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="success"
            onClick={handleDeliver}
            disabled={deliverMutation.isPending}
          >
            Confirm Delivery
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
