"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  Box,
  Paper,
  Grid,
  Typography,
  Button,
  Divider,
  Stack,
  TextField,
  Snackbar,
  Alert,
} from "@mui/material";
import {
  useClientDetail,
  useVerifyClient,
  useDeactivateClient,
  useMarkDeceased,
} from "@/hooks/queries/useClients";
import { useRotateCpid } from "@/hooks/queries/useIdentity";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusChip } from "@/components/common/StatusChip";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";

export default function ClientDetailPage() {
  const { healthId } = useParams() as { healthId: string };
  const router = useRouter();
  const { data: client, isLoading, error } = useClientDetail(healthId);

  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: "success" | "error";
  }>({ open: false, message: "", severity: "success" });

  const [dialog, setDialog] = useState<{
    type: "verify" | "deactivate" | "deceased" | "rotate" | null;
    reason: string;
    date: string;
  }>({ type: null, reason: "", date: "" });

  const verifyMutation = useVerifyClient();
  const deactivateMutation = useDeactivateClient();
  const deceasedMutation = useMarkDeceased();
  const rotateMutation = useRotateCpid();

  const handleActionSuccess = (message: string) => {
    setSnackbar({ open: true, message, severity: "success" });
    setDialog({ type: null, reason: "", date: "" });
  };

  const handleActionError = (err: any) => {
    setSnackbar({
      open: true,
      message: err?.message || "Action failed",
      severity: "error",
    });
  };

  const handleVerify = () => {
    verifyMutation.mutate(
      { healthId, payload: { method: "BIOMETRIC" } },
      {
        onSuccess: () => handleActionSuccess("Client verified successfully"),
        onError: handleActionError,
      }
    );
  };

  const handleDeactivate = () => {
    deactivateMutation.mutate(
      { healthId, reason: dialog.reason },
      {
        onSuccess: () => handleActionSuccess("Client deactivated successfully"),
        onError: handleActionError,
      }
    );
  };

  const handleMarkDeceased = () => {
    deceasedMutation.mutate(
      { healthId, dateOfDeath: dialog.date },
      {
        onSuccess: () => handleActionSuccess("Client marked as deceased"),
        onError: handleActionError,
      }
    );
  };

  const handleRotate = () => {
    rotateMutation.mutate(
      { healthId, reason: dialog.reason },
      {
        onSuccess: () => handleActionSuccess("CPID rotated successfully"),
        onError: handleActionError,
      }
    );
  };

  if (isLoading) return <LoadingSkeleton rows={10} />;
  if (error) return <ErrorAlert error={error} />;
  if (!client) return <Typography>Client not found</Typography>;

  return (
    <Box>
      <PageHeader
        title={`${client.givenName} ${client.familyName}`}
        breadcrumbs={[
          { label: "Admin", href: "/admin" },
          { label: "Clients", href: "/admin/clients" },
          { label: healthId },
        ]}
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
              <Typography variant="h6">Identity Details</Typography>
              <StatusChip status={client.status!} />
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Typography variant="caption" color="textSecondary">Health ID</Typography>
                <Typography variant="body1">{client.healthId}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="textSecondary">CPID</Typography>
                <Typography variant="body1" sx={{ wordBreak: "break-all" }}>{client.cpid}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="textSecondary">Given Name</Typography>
                <Typography variant="body1">{client.givenName}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="textSecondary">Family Name</Typography>
                <Typography variant="body1">{client.familyName}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="textSecondary">Gender</Typography>
                <Typography variant="body1">{client.gender}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="textSecondary">Date of Birth</Typography>
                <Typography variant="body1">{client.dateOfBirth}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="textSecondary">National ID</Typography>
                <Typography variant="body1">{client.nationalId || "N/A"}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="caption" color="textSecondary">Phone Number</Typography>
                <Typography variant="body1">{client.phoneNumber || "N/A"}</Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="caption" color="textSecondary">Email</Typography>
                <Typography variant="body1">{client.email || "N/A"}</Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="caption" color="textSecondary">Registered At</Typography>
                <Typography variant="body1">{client.registeredAt}</Typography>
              </Grid>
            </Grid>
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>Actions</Typography>
            <Stack spacing={2}>
              <Button
                variant="contained"
                onClick={() => setDialog({ ...dialog, type: "verify" })}
                disabled={client.status !== "ACTIVE"}
              >
                Verify Identity
              </Button>
              <Button
                variant="outlined"
                color="warning"
                onClick={() => setDialog({ ...dialog, type: "rotate" })}
              >
                Rotate CPID
              </Button>
              <Button
                variant="outlined"
                color="error"
                onClick={() => setDialog({ ...dialog, type: "deactivate" })}
                disabled={client.status === "INACTIVE" || client.status === "DECEASED"}
              >
                Deactivate Client
              </Button>
              <Button
                variant="outlined"
                color="error"
                onClick={() => setDialog({ ...dialog, type: "deceased" })}
                disabled={client.status === "DECEASED"}
              >
                Mark as Deceased
              </Button>
            </Stack>
          </Paper>
        </Grid>
      </Grid>

      {/* Verify Dialog */}
      <ConfirmDialog
        open={dialog.type === "verify"}
        title="Verify Client"
        content="Are you sure you want to verify this client's identity using biometrics?"
        onConfirm={handleVerify}
        onCancel={() => setDialog({ ...dialog, type: null })}
        isLoading={verifyMutation.isPending}
      />

      {/* Deactivate Dialog */}
      <ConfirmDialog
        open={dialog.type === "deactivate"}
        title="Deactivate Client"
        onConfirm={handleDeactivate}
        onCancel={() => setDialog({ ...dialog, type: null })}
        isLoading={deactivateMutation.isPending}
        confirmColor="error"
      >
        <Typography gutterBottom>
          Please provide a reason for deactivating this client.
        </Typography>
        <TextField
          fullWidth
          label="Reason"
          value={dialog.reason}
          onChange={(e) => setDialog({ ...dialog, reason: e.target.value })}
          sx={{ mt: 1 }}
        />
      </ConfirmDialog>

      {/* Mark Deceased Dialog */}
      <ConfirmDialog
        open={dialog.type === "deceased"}
        title="Mark as Deceased"
        onConfirm={handleMarkDeceased}
        onCancel={() => setDialog({ ...dialog, type: null })}
        isLoading={deceasedMutation.isPending}
        confirmColor="error"
      >
        <Typography gutterBottom>
          Please provide the date of death.
        </Typography>
        <TextField
          fullWidth
          type="date"
          label="Date of Death"
          InputLabelProps={{ shrink: true }}
          value={dialog.date}
          onChange={(e) => setDialog({ ...dialog, date: e.target.value })}
          sx={{ mt: 1 }}
        />
      </ConfirmDialog>

      {/* Rotate CPID Dialog */}
      <ConfirmDialog
        open={dialog.type === "rotate"}
        title="Rotate CPID"
        onConfirm={handleRotate}
        onCancel={() => setDialog({ ...dialog, type: null })}
        isLoading={rotateMutation.isPending}
        confirmColor="warning"
      >
        <Typography gutterBottom>
          Rotating the CPID will generate a new unique identifier for this client.
        </Typography>
        <TextField
          fullWidth
          label="Reason"
          value={dialog.reason}
          onChange={(e) => setDialog({ ...dialog, reason: e.target.value })}
          sx={{ mt: 1 }}
        />
      </ConfirmDialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} sx={{ width: "100%" }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
