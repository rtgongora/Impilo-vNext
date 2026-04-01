"use client";

import { useState } from "react";
import {
  Box,
  Paper,
  Typography,
  Grid,
  TextField,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  InputAdornment,
  Stack,
  Alert,
  CircularProgress,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import AddIcon from "@mui/icons-material/Add";
import PaymentIcon from "@mui/icons-material/Payment";
import { PageHeader } from "@/components/common/PageHeader";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { StatusChip } from "@/components/common/StatusChip";
import {
  useWallet,
  useWalletJournal,
  useCreateWallet,
  useTopUpWallet,
  usePayWallet,
} from "@/hooks/queries/useWallets";
import { useCardHistory } from "@/hooks/queries/useCards";
import type { WalletTransactionPayload } from "@/types/vito";
import { VitoApiError } from "@/lib/apiClient";

export default function WalletsPage() {
  const [searchHealthId, setSearchHealthId] = useState("");
  const [activeHealthId, setActiveHealthId] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  // Modals state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [topUpModalOpen, setTopUpModalOpen] = useState(false);
  const [payModalOpen, setPayModalOpen] = useState(false);

  // Form states
  const [amount, setAmount] = useState<string>("0");
  const [reference, setReference] = useState("");
  const [description, setDescription] = useState("");

  const {
    data: wallet,
    isLoading: walletLoading,
    error: walletError,
  } = useWallet(activeHealthId);

  const {
    data: cards,
    isLoading: cardsLoading,
  } = useCardHistory(activeHealthId);

  const {
    data: journal,
    isLoading: journalLoading,
    error: journalError,
  } = useWalletJournal({
    walletId: wallet?.walletId || "",
    page,
    size: rowsPerPage,
  });

  const createWallet = useCreateWallet();
  const topUpWallet = useTopUpWallet();
  const payWallet = usePayWallet();

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setActiveHealthId(searchHealthId);
    setPage(0);
  };

  const handleCreateWallet = async () => {
    try {
      await createWallet.mutateAsync(activeHealthId);
      setCreateModalOpen(false);
    } catch (e) {
      // handled by mutation
    }
  };

  const handleTopUp = async () => {
    try {
      const payload: WalletTransactionPayload = {
        healthId: activeHealthId,
        amount: parseFloat(amount),
        reference,
        description,
      };
      await topUpWallet.mutateAsync(payload);
      setTopUpModalOpen(false);
      resetForm();
    } catch (e) {
      // handled by mutation
    }
  };

  const handlePay = async () => {
    try {
      const payload: WalletTransactionPayload = {
        healthId: activeHealthId,
        amount: parseFloat(amount),
        reference,
        description,
      };
      await payWallet.mutateAsync(payload);
      setPayModalOpen(false);
      resetForm();
    } catch (e) {
      // handled by mutation
    }
  };

  const resetForm = () => {
    setAmount("0");
    setReference("");
    setDescription("");
  };

  const activeCard = cards?.find((c) => c.status === "ACTIVE");
  const isWalletNotFound = walletError instanceof VitoApiError && walletError.status === 404;

  return (
    <Box>
      <PageHeader
        title="Wallet Management"
        breadcrumbs={[{ label: "Admin", href: "/admin" }, { label: "Wallets" }]}
      />

      <Paper sx={{ p: 2, mb: 3 }}>
        <form onSubmit={handleSearch}>
          <Box sx={{ display: "flex", gap: 2 }}>
            <TextField
              fullWidth
              label="Search Health ID"
              variant="outlined"
              size="small"
              value={searchHealthId}
              onChange={(e) => setSearchHealthId(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
              placeholder="Enter Health ID to view wallet details"
            />
            <Button type="submit" variant="contained">
              Search
            </Button>
          </Box>
        </form>
      </Paper>

      {activeHealthId && (
        <>
          {(walletLoading || cardsLoading) ? (
            <LoadingSkeleton rows={3} columns={1} />
          ) : isWalletNotFound ? (
            <Box sx={{ mb: 3 }}>
              <Alert 
                severity="info" 
                action={
                  <Button 
                    color="inherit" 
                    size="small" 
                    onClick={() => setCreateModalOpen(true)}
                    disabled={createWallet.isPending}
                  >
                    {createWallet.isPending ? <CircularProgress size={20} color="inherit" /> : "Create Wallet"}
                  </Button>
                }
              >
                No wallet found for Health ID: <strong>{activeHealthId}</strong>. You can create one now.
              </Alert>
            </Box>
          ) : walletError ? (
            <Box sx={{ mb: 3 }}>
              <ErrorAlert error={walletError} />
            </Box>
          ) : wallet && (
            <Paper sx={{ p: 3, mb: 3 }}>
              <Grid container spacing={3}>
                <Grid item xs={12} md={8}>
                  <Stack spacing={2}>
                    <Typography variant="h6">Wallet Summary</Typography>
                    <Box sx={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                      <Box>
                        <Typography variant="body2" color="text.secondary">Balance</Typography>
                        <Typography variant="h4" sx={{ fontWeight: "bold" }}>
                          {wallet.currency} {wallet.balance?.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </Typography>
                      </Box>
                      <Box>
                        <Typography variant="body2" color="text.secondary">Status</Typography>
                        <StatusChip status={wallet.status!} type="wallet" />
                      </Box>
                      <Box>
                        <Typography variant="body2" color="text.secondary">Linked Card</Typography>
                        <Typography variant="body1">
                          {activeCard ? activeCard.cardNumber : "None active"}
                        </Typography>
                      </Box>
                    </Box>
                  </Stack>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Stack direction="row" spacing={1} justifyContent="flex-end" sx={{ height: '100%', alignItems: 'center' }}>
                    <Button
                      variant="outlined"
                      startIcon={<AddIcon />}
                      onClick={() => setTopUpModalOpen(true)}
                    >
                      Top-up
                    </Button>
                    <Button
                      variant="outlined"
                      startIcon={<PaymentIcon />}
                      onClick={() => setPayModalOpen(true)}
                    >
                      Pay
                    </Button>
                  </Stack>
                </Grid>
              </Grid>
            </Paper>
          )}

          {wallet && (
            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" sx={{ mb: 2 }}>Transaction Journal</Typography>
              <ErrorAlert error={journalError} />
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Type</TableCell>
                      <TableCell align="right">Amount</TableCell>
                      <TableCell>Reference</TableCell>
                      <TableCell>Description / Counterparty</TableCell>
                      <TableCell>Timestamp</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {journalLoading ? (
                      <LoadingSkeleton rows={5} columns={5} />
                    ) : (
                      journal?.items.map((entry) => (
                        <TableRow key={entry.journalId}>
                          <TableCell>
                            <StatusChip status={entry.type!} type="journal" />
                          </TableCell>
                          <TableCell align="right" sx={{ 
                            color: entry.amount && entry.amount < 0 ? 'error.main' : 'success.main',
                            fontWeight: 'bold'
                          }}>
                            {entry.amount?.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                          </TableCell>
                          <TableCell>{entry.reference}</TableCell>
                          <TableCell>{entry.description}</TableCell>
                          <TableCell>
                            {entry.createdAt ? new Date(entry.createdAt).toLocaleString() : "-"}
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                    {wallet && journal && journal.items.length === 0 && !journalLoading && (
                      <TableRow>
                        <TableCell colSpan={5} align="center" sx={{ py: 4 }}>
                          <Typography variant="body2" color="text.secondary">
                            No transaction history found for this wallet
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination
                component="div"
                count={journal?.totalElements || 0}
                page={page}
                onPageChange={(_, newPage) => setPage(newPage)}
                rowsPerPage={rowsPerPage}
                onRowsPerPageChange={(e) => {
                  setRowsPerPage(parseInt(e.target.value, 10));
                  setPage(0);
                }}
              />
            </Paper>
          )}
        </>
      )}

      {/* Modals */}
      <Dialog open={createModalOpen} onClose={() => !createWallet.isPending && setCreateModalOpen(false)}>
        <DialogTitle>Create Wallet</DialogTitle>
        <DialogContent>
          <Typography sx={{ mt: 1 }}>
            Do you want to initialize a digital wallet for Health ID <strong>{activeHealthId}</strong>?
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setCreateModalOpen(false)} disabled={createWallet.isPending}>
            Cancel
          </Button>
          <Button 
            onClick={handleCreateWallet} 
            variant="contained" 
            disabled={createWallet.isPending}
            startIcon={createWallet.isPending ? <CircularProgress size={20} color="inherit" /> : null}
          >
            Create Wallet
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={topUpModalOpen} onClose={() => !topUpWallet.isPending && setTopUpModalOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Wallet Top-up</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Add funds to the wallet for Health ID: {activeHealthId}
            </Typography>
            <TextField
              fullWidth
              label="Amount"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              InputProps={{
                startAdornment: <InputAdornment position="start">{wallet?.currency || "$"}</InputAdornment>,
              }}
            />
            <TextField
              fullWidth
              label="Reference"
              placeholder="e.g. Bank Transfer Ref, Cash Receipt #"
              value={reference}
              onChange={(e) => setReference(e.target.value)}
            />
            <TextField
              fullWidth
              label="Description"
              multiline
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setTopUpModalOpen(false)} disabled={topUpWallet.isPending}>
            Cancel
          </Button>
          <Button 
            onClick={handleTopUp} 
            variant="contained" 
            disabled={topUpWallet.isPending}
            startIcon={topUpWallet.isPending ? <CircularProgress size={20} color="inherit" /> : null}
          >
            Confirm Top-up
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={payModalOpen} onClose={() => !payWallet.isPending && setPayModalOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Process Payment</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Debit funds from the wallet for Health ID: {activeHealthId}
            </Typography>
            <TextField
              fullWidth
              label="Amount"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              InputProps={{
                startAdornment: <InputAdornment position="start">{wallet?.currency || "$"}</InputAdornment>,
              }}
            />
            <TextField
              fullWidth
              label="Reference"
              placeholder="e.g. Invoice #, Service Code"
              value={reference}
              onChange={(e) => setReference(e.target.value)}
            />
            <TextField
              fullWidth
              label="Description / Counterparty"
              placeholder="e.g. Pharmacy A, Consultation Fee"
              multiline
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setPayModalOpen(false)} disabled={payWallet.isPending}>
            Cancel
          </Button>
          <Button 
            onClick={handlePay} 
            variant="contained" 
            color="primary"
            disabled={payWallet.isPending}
            startIcon={payWallet.isPending ? <CircularProgress size={20} color="inherit" /> : null}
          >
            Process Payment
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
