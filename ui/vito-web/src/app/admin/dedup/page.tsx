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
  Tabs,
  Tab,
  Button,
  IconButton,
  Tooltip,
  Collapse,
  TextField,
  Stack,
} from "@mui/material";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import MergeTypeIcon from "@mui/icons-material/MergeType";
import BlockIcon from "@mui/icons-material/Block";
import RefreshIcon from "@mui/icons-material/Refresh";
import CallSplitIcon from "@mui/icons-material/CallSplit";

import {
  useDedupCases,
  useResolveDedupCase,
  useUnmergeCase,
  useRecalculateScore,
} from "@/hooks/queries/useDedup";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusChip } from "@/components/common/StatusChip";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { JsonViewer } from "@/components/common/JsonViewer";
import type { DedupCase, DedupStatus } from "@/types/vito";
import { DEDUP_STATUS_LABEL } from "@/lib/constants";

const STATUS_TABS: DedupStatus[] = ["PENDING", "MERGED", "NOT_DUPLICATE", "DEFERRED"];

function DedupRow({
  row,
  onMerge,
  onNotDuplicate,
  onRecalculate,
  onUnmerge,
}: {
  row: DedupCase;
  onMerge: (caseId: string) => void;
  onNotDuplicate: (caseId: string) => void;
  onRecalculate: (caseId: string) => void;
  onUnmerge: (caseId: string) => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <TableRow hover sx={{ "& > *": { borderBottom: "unset" } }}>
        <TableCell width={50}>
          <IconButton size="small" onClick={() => setOpen(!open)}>
            {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell>{row.caseId}</TableCell>
        <TableCell>{row.cpidA}</TableCell>
        <TableCell>{row.cpidB}</TableCell>
        <TableCell>{row.matchScore ? (row.matchScore * 100).toFixed(1) : "0"}%</TableCell>
        <TableCell>
          <StatusChip status={row.status!} type="dedup" />
        </TableCell>
        <TableCell align="right">
          <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
            {row.status === "PENDING" && (
              <>
                <Tooltip title="Merge Records">
                  <IconButton size="small" color="primary" onClick={() => onMerge(row.caseId!)}>
                    <MergeTypeIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Not a Duplicate">
                  <IconButton size="small" color="error" onClick={() => onNotDuplicate(row.caseId!)}>
                    <BlockIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Recalculate Score">
                  <IconButton size="small" color="info" onClick={() => onRecalculate(row.caseId!)}>
                    <RefreshIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </>
            )}
            {row.status === "MERGED" && (
              <Tooltip title="Unmerge Records">
                <IconButton size="small" color="warning" onClick={() => onUnmerge(row.caseId!)}>
                  <CallSplitIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          </Box>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell style={{ paddingBottom: 0, paddingTop: 0 }} colSpan={7}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box sx={{ margin: 2 }}>
              <Typography variant="subtitle2" gutterBottom component="div" fontWeight="bold">
                Score Breakdown
              </Typography>
              <JsonViewer data={{ matchedFields: row.matchedFields, score: row.matchScore }} />
              {row.mergeHistory && (
                <Box sx={{ mt: 2 }}>
                  <Typography variant="subtitle2" gutterBottom component="div" fontWeight="bold">
                    Merge History
                  </Typography>
                  <JsonViewer data={row.mergeHistory} />
                </Box>
              )}
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

export default function DedupPage() {
  const [activeTab, setActiveTab] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(null);
  const [survivorCpid, setSurvivorCpid] = useState("");
  const [isMergeDialogOpen, setIsMergeDialogOpen] = useState(false);
  const [isNotDuplicateDialogOpen, setIsNotDuplicateDialogOpen] = useState(false);
  const [isUnmergeDialogOpen, setIsUnmergeDialogOpen] = useState(false);

  const currentStatus = STATUS_TABS[activeTab];

  const { data, isLoading, error } = useDedupCases({
    page,
    size: rowsPerPage,
    disposition: currentStatus,
  });

  const resolveMutation = useResolveDedupCase();
  const unmergeMutation = useUnmergeCase();
  const recalculateMutation = useRecalculateScore();

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

  const handleMerge = async () => {
    if (selectedCaseId && survivorCpid) {
      try {
        await resolveMutation.mutateAsync({
          caseId: selectedCaseId,
          action: "MERGE",
          survivorCpid,
        });
        setIsMergeDialogOpen(false);
        setSurvivorCpid("");
        setSelectedCaseId(null);
      } catch (err) {
        console.error("Failed to merge records", err);
      }
    }
  };

  const handleNotDuplicate = async () => {
    if (selectedCaseId) {
      try {
        await resolveMutation.mutateAsync({
          caseId: selectedCaseId,
          action: "NOT_DUPLICATE",
        });
        setIsNotDuplicateDialogOpen(false);
        setSelectedCaseId(null);
      } catch (err) {
        console.error("Failed to mark as not duplicate", err);
      }
    }
  };

  const handleUnmerge = async () => {
    if (selectedCaseId) {
      try {
        await unmergeMutation.mutateAsync(selectedCaseId);
        setIsUnmergeDialogOpen(false);
        setSelectedCaseId(null);
      } catch (err) {
        console.error("Failed to unmerge records", err);
      }
    }
  };

  const handleRecalculate = async (caseId: string) => {
    try {
      await recalculateMutation.mutateAsync(caseId);
    } catch (err) {
      console.error("Failed to recalculate score", err);
    }
  };

  return (
    <Box>
      <PageHeader
        title="Deduplication"
        breadcrumbs={[{ label: "Admin", href: "/admin" }, { label: "Dedup" }]}
      />

      <Paper sx={{ width: "100%", mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          indicatorColor="primary"
          textColor="primary"
          sx={{ borderBottom: 1, borderColor: "divider" }}
        >
          {STATUS_TABS.map((status) => (
            <Tab key={status} label={DEDUP_STATUS_LABEL[status]} />
          ))}
        </Tabs>

        <Box sx={{ p: 2 }}>
          <ErrorAlert error={error} />

          {isLoading ? (
            <TableContainer>
              <Table>
                <TableBody>
                  <LoadingSkeleton rows={rowsPerPage} columns={7} />
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <TableContainer>
              <Table size="medium">
                <TableHead>
                  <TableRow>
                    <TableCell width={50} />
                    <TableCell>Case ID</TableCell>
                    <TableCell>CPID A</TableCell>
                    <TableCell>CPID B</TableCell>
                    <TableCell>Score</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data?.items?.map((row) => (
                    <DedupRow
                      key={row.caseId}
                      row={row}
                      onMerge={(id) => {
                        setSelectedCaseId(id);
                        setIsMergeDialogOpen(true);
                      }}
                      onNotDuplicate={(id) => {
                        setSelectedCaseId(id);
                        setIsNotDuplicateDialogOpen(true);
                      }}
                      onRecalculate={handleRecalculate}
                      onUnmerge={(id) => {
                        setSelectedCaseId(id);
                        setIsUnmergeDialogOpen(true);
                      }}
                    />
                  ))}
                  {!data?.items?.length && (
                    <TableRow>
                      <TableCell colSpan={7} align="center">
                        <Typography variant="body2" sx={{ py: 4 }}>
                          No deduplication cases found in this state
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

      {/* Merge Dialog */}
      <ConfirmDialog
        open={isMergeDialogOpen}
        title="Merge Records"
        message="Please enter the Health ID (CPID) of the survivor record. All other records in this case will be merged into it."
        onConfirm={handleMerge}
        onCancel={() => setIsMergeDialogOpen(false)}
        confirmLabel="Merge"
        loading={resolveMutation.isPending}
      >
        <TextField
          fullWidth
          label="Survivor CPID"
          value={survivorCpid}
          onChange={(e) => setSurvivorCpid(e.target.value)}
          placeholder="e.g. CPID-123456"
          sx={{ mt: 2 }}
        />
      </ConfirmDialog>

      {/* Not Duplicate Dialog */}
      <ConfirmDialog
        open={isNotDuplicateDialogOpen}
        title="Not a Duplicate"
        message="Are you sure these records are not duplicates? This will dismiss the case."
        onConfirm={handleNotDuplicate}
        onCancel={() => setIsNotDuplicateDialogOpen(false)}
        confirmLabel="Confirm"
        confirmColor="error"
        loading={resolveMutation.isPending}
      />

      {/* Unmerge Dialog */}
      <ConfirmDialog
        open={isUnmergeDialogOpen}
        title="Unmerge Records"
        message="Are you sure you want to unmerge these records? This will restore them as separate identities."
        onConfirm={handleUnmerge}
        onCancel={() => setIsUnmergeDialogOpen(false)}
        confirmLabel="Unmerge"
        confirmColor="warning"
        loading={unmergeMutation.isPending}
      />
    </Box>
  );
}
