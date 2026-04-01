"use client";

import React, { useState } from "react";
import {
  Box,
  Button,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Snackbar,
  Alert,
  IconButton,
} from "@mui/material";
import { Add as AddIcon, History as HistoryIcon, Print as PrintIcon } from "@mui/icons-material";
import Link from "next/link";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusChip } from "@/components/common/StatusChip";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import {
  useCardsByStatus,
  useRequestCard,
  useActivateCard,
  useInactivateCard,
  useRevokeCard,
} from "@/hooks/queries/useCards";
import type { CardStatus, SmartCard, CardType } from "@/types/vito";

const STATUSES: CardStatus[] = ["REQUESTED", "PRINTING", "PRINTED", "ACTIVE", "INACTIVE", "REVOKED"];

export default function CardsPage() {
  const [activeTab, setActiveTab] = useState(0);
  const [requestModalOpen, setRequestModalOpen] = useState(false);
  const [revokeDialogOpen, setRevokeDialogOpen] = useState(false);
  const [selectedCard, setSelectedCard] = useState<SmartCard | null>(null);
  const [revocationReason, setRevocationReason] = useState("");
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false,
    message: "",
    severity: "success",
  });

  const currentStatus = STATUSES[activeTab];
  const { data: cards, isLoading, error } = useCardsByStatus(currentStatus);

  const requestCardMutation = useRequestCard();
  const activateCardMutation = useActivateCard();
  const inactivateCardMutation = useInactivateCard();
  const revokeCardMutation = useRevokeCard();

  const handleTabChange = (_: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleRequestSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const healthId = formData.get("healthId") as string;
    const cardType = formData.get("cardType") as CardType;

    try {
      await requestCardMutation.mutateAsync({ healthId, cardType });
      setSnackbar({ open: true, message: "Card requested successfully", severity: "success" });
      setRequestModalOpen(false);
    } catch (err) {
      setSnackbar({ open: true, message: "Failed to request card", severity: "error" });
    }
  };

  const handleRevoke = async () => {
    if (!selectedCard) return;
    try {
      await revokeCardMutation.mutateAsync({
        cardId: selectedCard.cardId!,
        revocationReason,
      });
      setSnackbar({ open: true, message: "Card revoked successfully", severity: "success" });
      setRevokeDialogOpen(false);
      setSelectedCard(null);
      setRevocationReason("");
    } catch (err) {
      setSnackbar({ open: true, message: "Failed to revoke card", severity: "error" });
    }
  };

  const handleAction = async (action: () => Promise<any>, successMsg: string, errorMsg: string) => {
    try {
      await action();
      setSnackbar({ open: true, message: successMsg, severity: "success" });
    } catch (err) {
      setSnackbar({ open: true, message: errorMsg, severity: "error" });
    }
  };

  return (
    <Box>
      <PageHeader
        title="SMART Cards"
        breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Cards" }]}
      />

      <Box sx={{ mb: 3, display: "flex", justifyContent: "flex-end" }}>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setRequestModalOpen(true)}
        >
          Request New Card
        </Button>
      </Box>

      <Paper sx={{ width: "100%", mb: 2 }}>
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          indicatorColor="primary"
          textColor="primary"
          variant="scrollable"
          scrollButtons="auto"
        >
          {STATUSES.map((status) => (
            <Tab key={status} label={status} />
          ))}
        </Tabs>

        <Box sx={{ p: 2 }}>
          <ErrorAlert error={error as any} />
          
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Card ID</TableCell>
                  <TableCell>Health ID</TableCell>
                  <TableCell>Card Number</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Requested At</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {isLoading ? (
                  <LoadingSkeleton rows={5} columns={6} />
                ) : cards?.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      <Typography variant="body2" sx={{ py: 2 }}>No cards found in this status</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  cards?.map((card) => (
                    <TableRow key={card.cardId}>
                      <TableCell>{card.cardId?.substring(0, 8)}...</TableCell>
                      <TableCell>{card.healthId}</TableCell>
                      <TableCell>{card.cardNumber || "-"}</TableCell>
                      <TableCell>
                        <StatusChip status={card.status!} type="card" />
                      </TableCell>
                      <TableCell>{card.requestedAt ? new Date(card.requestedAt).toLocaleDateString() : "-"}</TableCell>
                      <TableCell align="right">
                        <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
                          {card.status === "REQUESTED" && (
                            <Button
                              size="small"
                              component={Link}
                              href={`/admin/print?cardId=${card.cardId}`}
                              startIcon={<PrintIcon />}
                            >
                              Print
                            </Button>
                          )}
                          {card.status === "ACTIVE" && (
                            <Button
                              size="small"
                              color="warning"
                              onClick={() => handleAction(
                                () => inactivateCardMutation.mutateAsync({ cardId: card.cardId!, reason: "OTHER" }),
                                "Card inactivated",
                                "Failed to inactivate card"
                              )}
                            >
                              Inactivate
                            </Button>
                          )}
                          {card.status === "INACTIVE" && (
                            <Button
                              size="small"
                              color="success"
                              onClick={() => handleAction(
                                () => activateCardMutation.mutateAsync(card.cardId!),
                                "Card activated",
                                "Failed to activate card"
                              )}
                            >
                              Activate
                            </Button>
                          )}
                          {card.status !== "REVOKED" && (
                            <Button
                              size="small"
                              color="error"
                              onClick={() => {
                                setSelectedCard(card);
                                setRevokeDialogOpen(true);
                              }}
                            >
                              Revoke
                            </Button>
                          )}
                          <IconButton
                            size="small"
                            component={Link}
                            href={`/admin/cards/history/${card.healthId}`}
                            title="History"
                          >
                            <HistoryIcon fontSize="small" />
                          </IconButton>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      </Paper>

      {/* Request Modal */}
      <Dialog open={requestModalOpen} onClose={() => setRequestModalOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleRequestSubmit}>
          <DialogTitle>Request New SMART Card</DialogTitle>
          <DialogContent>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
              <TextField
                name="healthId"
                label="Health ID"
                required
                fullWidth
              />
              <TextField
                name="cardType"
                label="Card Type"
                select
                defaultValue="PHYSICAL"
                required
                fullWidth
              >
                <MenuItem value="PHYSICAL">Physical</MenuItem>
                <MenuItem value="VIRTUAL">Virtual</MenuItem>
              </TextField>
              {/* Note: The plan mentioned Public Key input but the CardRequestPayload only has healthId and cardType and deliveryFacilityId */}
              <TextField
                name="deliveryFacilityId"
                label="Delivery Facility ID"
                fullWidth
              />
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setRequestModalOpen(false)}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={requestCardMutation.isPending}>
              Submit Request
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Revoke Dialog */}
      <Dialog open={revokeDialogOpen} onClose={() => setRevokeDialogOpen(false)}>
        <DialogTitle>Revoke Card</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2 }}>
            Are you sure you want to permanently revoke card {selectedCard?.cardId}? This action cannot be undone.
          </Typography>
          <TextField
            select
            fullWidth
            label="Revocation Reason"
            value={revocationReason}
            onChange={(e) => setRevocationReason(e.target.value)}
            required
          >
            <MenuItem value="LOST">Lost</MenuItem>
            <MenuItem value="STOLEN">Stolen</MenuItem>
            <MenuItem value="DAMAGED">Damaged</MenuItem>
            <MenuItem value="EXPIRED">Expired</MenuItem>
            <MenuItem value="COMPROMISED">Compromised</MenuItem>
            <MenuItem value="OTHER">Other</MenuItem>
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRevokeDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleRevoke}
            color="error"
            variant="contained"
            disabled={!revocationReason || revokeCardMutation.isPending}
          >
            Revoke
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
