"use client";

import { useState } from "react";
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
  LinearProgress,
  IconButton,
  Tooltip,
  MenuItem,
  TextField,
} from "@mui/material";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import { usePendingMatches, useResolveMatch } from "@/hooks/queries/useMatchQueue";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusChip } from "@/components/common/StatusChip";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import type { MatchDisposition } from "@/types/vito";
import { MATCH_DISPOSITION_LABEL } from "@/lib/constants";

export default function MatchQueuePage() {
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  
  const [selectedMatchId, setSelectedMatchId] = useState<string | null>(null);
  const [disposition, setDisposition] = useState<MatchDisposition>("MANUAL_LINKED");
  const [isResolveDialogOpen, setIsResolveDialogOpen] = useState(false);

  const { data, isLoading, error } = usePendingMatches({
    page,
    size: rowsPerPage,
  });

  const resolveMutation = useResolveMatch();

  const handleChangePage = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const openResolveDialog = (matchId: string) => {
    setSelectedMatchId(matchId);
    setIsResolveDialogOpen(true);
  };

  const handleResolve = async () => {
    if (selectedMatchId) {
      try {
        await resolveMutation.mutateAsync({
          matchId: selectedMatchId,
          disposition,
        });
        setIsResolveDialogOpen(false);
        setSelectedMatchId(null);
      } catch (err) {
        console.error("Failed to resolve match", err);
      }
    }
  };

  return (
    <Box>
      <PageHeader
        title="Match Queue"
        breadcrumbs={[{ label: "Admin", href: "/admin" }, { label: "Match Queue" }]}
      />

      <Paper sx={{ width: "100%", mb: 3 }}>
        <Box sx={{ p: 2 }}>
          <ErrorAlert error={error} />

          {isLoading ? (
            <TableContainer>
              <Table>
                <TableBody>
                  <LoadingSkeleton rows={rowsPerPage} columns={5} />
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <TableContainer>
              <Table size="medium">
                <TableHead>
                  <TableRow>
                    <TableCell>Health ID</TableCell>
                    <TableCell>Candidate CRID</TableCell>
                    <TableCell>Score</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data?.items.map((match) => (
                    <TableRow key={match.id || match.matchId} hover>
                      <TableCell>{match.sourceHealthId}</TableCell>
                      <TableCell>{match.candidateHealthId}</TableCell>
                      <TableCell sx={{ minWidth: 150 }}>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                          <Box sx={{ width: "100%", mr: 1 }}>
                            <LinearProgress
                              variant="determinate"
                              value={match.matchScore * 100}
                              color={match.matchScore > 0.8 ? "success" : match.matchScore > 0.5 ? "warning" : "error"}
                            />
                          </Box>
                          <Typography variant="body2" color="text.secondary">
                            {(match.matchScore * 100).toFixed(0)}%
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <StatusChip status={match.disposition} type="match_disposition" />
                      </TableCell>
                      <TableCell align="right">
                        <Tooltip title="Resolve Match">
                          <IconButton
                            size="small"
                            color="primary"
                            onClick={() => openResolveDialog(String(match.id || match.matchId))}
                          >
                            <CheckCircleIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                  {!data?.items.length && (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        <Typography variant="body2" sx={{ py: 4 }}>
                          No pending matches found
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

      <ConfirmDialog
        open={isResolveDialogOpen}
        title="Resolve Match"
        message="Please select the disposition for this identity match."
        onConfirm={handleResolve}
        onCancel={() => setIsResolveDialogOpen(false)}
        confirmLabel="Resolve"
        loading={resolveMutation.isPending}
      >
        <TextField
          select
          fullWidth
          label="Disposition"
          value={disposition}
          onChange={(e) => setDisposition(e.target.value as MatchDisposition)}
          sx={{ mt: 2 }}
        >
          <MenuItem value="MANUAL_LINKED">Manually Linked</MenuItem>
          <MenuItem value="REJECTED">Rejected</MenuItem>
          <MenuItem value="DEFERRED">Deferred</MenuItem>
        </TextField>
      </ConfirmDialog>
    </Box>
  );
}
