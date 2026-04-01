"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  Box,
  Paper,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  IconButton,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import PrintIcon from "@mui/icons-material/Print";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import BlockIcon from "@mui/icons-material/Block";
import HistoryIcon from "@mui/icons-material/History";
import CancelIcon from "@mui/icons-material/Cancel";
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
import type { CardStatus, CardType, InactivationReason } from "@/types/vito";

const TABS: { label: string; value: CardStatus }[] = [
  { label: "REQUESTED", value: "REQUESTED" },
  { label: "PRINT_QUEUED", value: "PRINTING" },
  { label: "ACTIVE", value: "ACTIVE" },
  { label: "INACTIVE", value: "INACTIVE" },
  { label: "REVOKED", value: "REVOKED" },
];

export default function CardsPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<CardStatus>("REQUESTED");
  const [requestModalOpen, setRequestModalOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState<{
    type: "ACTIVATE" | "INACTIVATE" | "REVOKE";
    cardId: string;
  } | null>(null);
  
  // Form states
  const [healthId, setHealthId] = useState("");
  const [cardType, setCardType] = useState<CardType>("PHYSICAL");
  const [publicKey, setPublicKey] = useState(""); // Mentioned by user
  const [inactivationReason, setInactivationReason] = useState<InactivationReason>("OTHER");
  const [revocationReason, setRevocationReason] = useState("");

  const { data: cards, isLoading, error } = useCardsByStatus(activeTab);
  const requestCardMutation = useRequestCard();
  const activateCardMutation = useActivateCard();
  const inactivateCardMutation = useInactivateCard();
  const revokeCardMutation = useRevokeCard();

  const handleRequestCard = async () => {
    try {
      await requestCardMutation.mutateAsync({
        healthId,
        cardType,
      });
      setRequestModalOpen(false);
      setHealthId("");
      setPublicKey("");
    } catch (e) {
      // Error handled by mutation
    }
  };

  const handleConfirmAction = async () => {
    if (!confirmAction) return;

    try {
      if (confirmAction.type === "ACTIVATE") {
        await activateCardMutation.mutateAsync(confirmAction.cardId);
      } else if (confirmAction.type === "INACTIVATE") {
        await inactivateCardMutation.mutateAsync({
          cardId: confirmAction.cardId,
          reason: inactivationReason,
        });
      } else if (confirmAction.type === "REVOKE") {
        await revokeCardMutation.mutateAsync({
          cardId: confirmAction.cardId,
          revocationReason,
        });
      }
      setConfirmAction(null);
      setRevocationReason("");
    } catch (e) {
      // Error handled by mutation
    }
  };

  return (
    <Box>
      <PageHeader
        title="SMART Cards"
        breadcrumbs={[{ label: "Admin", href: "/admin" }, { label: "Cards" }]}
        action={
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setRequestModalOpen(true)}
          >
            Request New Card
          </Button>
        }
      />

      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, value) => setActiveTab(value)}
          indicatorColor="primary"
          textColor="primary"
          variant="fullWidth"
        >
          {TABS.map((tab) => (
            <Tab key={tab.value} label={tab.label} value={tab.value} />
          ))}
        </Tabs>
      </Paper>

      <Box sx={{ mb: 2 }}>
        <ErrorAlert error={error} />
      </Box>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Card ID</TableCell>
              <TableCell>Health ID</TableCell>
              <TableCell>Card Number</TableCell>
              <TableCell>Type</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <LoadingSkeleton columns={6} rows={5} />
            ) : (
              (cards?.items ?? []).map((card) => (
                <TableRow key={card.cardId}>
                  <TableCell sx={{ fontFamily: "monospace", fontSize: "0.8rem" }}>
                    {card.cardId?.substring(0, 8)}...
                  </TableCell>
                  <TableCell>{card.healthId}</TableCell>
                  <TableCell>{card.cardNumber || "-"}</TableCell>
                  <TableCell>{card.cardType}</TableCell>
                  <TableCell>
                    <StatusChip status={card.status!} type="card" />
                  </TableCell>
                  <TableCell align="right">
                    <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
                      {(card.status === "REQUESTED" || card.status === "PRINTING") && (
                        <Tooltip title="Submit to Print">
                          <IconButton
                            size="small"
                            color="primary"
                            onClick={() => router.push(`/admin/print?cardId=${card.cardId}`)}
                          >
                            <PrintIcon />
                          </IconButton>
                        </Tooltip>
                      )}
                      {card.status === "INACTIVE" && (
                        <Tooltip title="Activate">
                          <IconButton
                            size="small"
                            color="success"
                            onClick={() =>
                              setConfirmAction({ type: "ACTIVATE", cardId: card.cardId! })
                            }
                          >
                            <CheckCircleIcon />
                          </IconButton>
                        </Tooltip>
                      )}
                      {card.status === "ACTIVE" && (
                        <Tooltip title="Inactivate">
                          <IconButton
                            size="small"
                            color="warning"
                            onClick={() =>
                              setConfirmAction({ type: "INACTIVATE", cardId: card.cardId! })
                            }
                          >
                            <BlockIcon />
                          </IconButton>
                        </Tooltip>
                      )}
                      {(card.status === "ACTIVE" || card.status === "INACTIVE") && (
                        <Tooltip title="Revoke">
                          <IconButton
                            size="small"
                            color="error"
                            onClick={() =>
                              setConfirmAction({ type: "REVOKE", cardId: card.cardId! })
                            }
                          >
                            <CancelIcon />
                          </IconButton>
                        </Tooltip>
                      )}
                      <Tooltip title="View History">
                        <IconButton
                          size="small"
                          onClick={() => router.push(`/admin/cards/history/${card.healthId}`)}
                        >
                          <HistoryIcon />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </TableCell>
                </TableRow>
              ))
            )}
            {!isLoading && !(cards?.items?.length) && (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                  <Typography variant="body2" color="text.secondary">
                    No cards found for status {activeTab}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Request New Card Modal */}
      <Dialog
        open={requestModalOpen}
        onClose={() => setRequestModalOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Request New SMART Card</DialogTitle>
        <DialogContent>
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
            <TextField
              label="Health ID"
              fullWidth
              value={healthId}
              onChange={(e) => setHealthId(e.target.value)}
              required
            />
            <TextField
              select
              label="Card Type"
              fullWidth
              value={cardType}
              onChange={(e) => setCardType(e.target.value as CardType)}
            >
              <MenuItem value="PHYSICAL">Physical Card</MenuItem>
              <MenuItem value="VIRTUAL">Virtual Card</MenuItem>
            </TextField>
            <TextField
              label="Public Key (Hex/Base64)"
              fullWidth
              multiline
              rows={3}
              value={publicKey}
              onChange={(e) => setPublicKey(e.target.value)}
              placeholder="Required for virtual cards or secure physical cards..."
            />
          </Box>
          {requestCardMutation.error && (
            <Box sx={{ mt: 2 }}>
              <ErrorAlert error={requestCardMutation.error} />
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button onClick={() => setRequestModalOpen(false)} color="inherit">
            Cancel
          </Button>
          <Button
            onClick={handleRequestCard}
            variant="contained"
            disabled={!healthId || requestCardMutation.isPending}
          >
            Submit Request
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirmation Dialogs */}
      <ConfirmDialog
        open={confirmAction?.type === "ACTIVATE"}
        title="Activate SMART Card"
        message="Are you sure you want to activate this card? This will enable it for use in the platform."
        onConfirm={handleConfirmAction}
        onCancel={() => setConfirmAction(null)}
        confirmLabel="Activate"
        confirmColor="success"
        loading={activateCardMutation.isPending}
      />

      <ConfirmDialog
        open={confirmAction?.type === "INACTIVATE"}
        title="Inactivate SMART Card"
        message="Are you sure you want to temporarily inactivate this card?"
        onConfirm={handleConfirmAction}
        onCancel={() => setConfirmAction(null)}
        confirmLabel="Inactivate"
        confirmColor="warning"
        loading={inactivateCardMutation.isPending}
      >
        <TextField
          select
          fullWidth
          label="Inactivation Reason"
          value={inactivationReason}
          onChange={(e) => setInactivationReason(e.target.value as InactivationReason)}
          sx={{ mt: 2 }}
        >
          <MenuItem value="LOST">Lost</MenuItem>
          <MenuItem value="STOLEN">Stolen</MenuItem>
          <MenuItem value="DAMAGED">Damaged</MenuItem>
          <MenuItem value="OTHER">Other</MenuItem>
        </TextField>
      </ConfirmDialog>

      <ConfirmDialog
        open={confirmAction?.type === "REVOKE"}
        title="Revoke SMART Card"
        message="WARNING: Revocation is permanent. This card will be blacklisted and cannot be used again."
        onConfirm={handleConfirmAction}
        onCancel={() => setConfirmAction(null)}
        confirmLabel="Permanently Revoke"
        confirmColor="error"
        loading={revokeCardMutation.isPending}
      >
        <TextField
          fullWidth
          label="Revocation Reason"
          value={revocationReason}
          onChange={(e) => setRevocationReason(e.target.value)}
          placeholder="Enter reason for permanent revocation..."
          sx={{ mt: 2 }}
          required
        />
      </ConfirmDialog>
    </Box>
  );
}
