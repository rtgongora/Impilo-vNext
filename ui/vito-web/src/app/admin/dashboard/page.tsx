"use client";

import React from "react";
import {
  Box,
  Card,
  CardContent,
  Grid,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
} from "@mui/material";
import PeopleIcon from "@mui/icons-material/People";
import AssignmentIcon from "@mui/icons-material/Assignment";
import MergeTypeIcon from "@mui/icons-material/MergeType";
import CompareArrowsIcon from "@mui/icons-material/CompareArrows";
import CreditCardIcon from "@mui/icons-material/CreditCard";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { useListClients } from "@/hooks/queries/useClients";
import { useIssuanceQueue } from "@/hooks/queries/useIssuance";
import { usePendingMatches } from "@/hooks/queries/useMatchQueue";
import { useDedupCases } from "@/hooks/queries/useDedup";
import { useCardsByStatus } from "@/hooks/queries/useCards";
import { useAuditLog } from "@/hooks/queries/useAudit";

interface KpiCardProps {
  title: string;
  value: number | undefined;
  icon: React.ReactNode;
  loading: boolean;
  color?: string;
}

function KpiCard({ title, value, icon, loading, color = "primary.main" }: KpiCardProps) {
  return (
    <Card>
      <CardContent>
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <Box>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {title}
            </Typography>
            {loading ? (
              <Typography variant="h4" sx={{ color }}>
                —
              </Typography>
            ) : (
              <Typography variant="h4" sx={{ color }}>
                {value?.toLocaleString() ?? "—"}
              </Typography>
            )}
          </Box>
          <Box sx={{ color, opacity: 0.7 }}>{icon}</Box>
        </Box>
      </CardContent>
    </Card>
  );
}

function formatTimestamp(ts: string | undefined): string {
  if (!ts) return "—";
  try {
    return new Date(ts).toLocaleString();
  } catch {
    return ts;
  }
}

export default function AdminDashboardPage() {
  const clientsQuery = useListClients({ page: 0, size: 1 });
  const issuanceQuery = useIssuanceQueue({ page: 0, size: 1, status: "SUBMITTED" });
  const matchQuery = usePendingMatches({ page: 0, size: 1 });
  const dedupQuery = useDedupCases({ page: 0, size: 1, disposition: "PENDING" });
  const activeCardsQuery = useCardsByStatus("ACTIVE");
  const auditQuery = useAuditLog({ page: 0, size: 10 });

  const anyKpiError =
    clientsQuery.error ||
    issuanceQuery.error ||
    matchQuery.error ||
    dedupQuery.error ||
    activeCardsQuery.error;

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 3 }}>
        Dashboard
      </Typography>

      {anyKpiError && <ErrorAlert error={anyKpiError} />}

      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <KpiCard
            title="Total Clients"
            value={clientsQuery.data?.totalElements}
            icon={<PeopleIcon fontSize="large" />}
            loading={clientsQuery.isLoading}
            color="primary.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <KpiCard
            title="Pending Issuance"
            value={issuanceQuery.data?.totalElements}
            icon={<AssignmentIcon fontSize="large" />}
            loading={issuanceQuery.isLoading}
            color="warning.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <KpiCard
            title="Pending Dedup"
            value={dedupQuery.data?.totalElements}
            icon={<MergeTypeIcon fontSize="large" />}
            loading={dedupQuery.isLoading}
            color="error.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <KpiCard
            title="Open Match Queue"
            value={matchQuery.data?.totalElements}
            icon={<CompareArrowsIcon fontSize="large" />}
            loading={matchQuery.isLoading}
            color="info.main"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4} lg={2.4}>
          <KpiCard
            title="Active Cards"
            value={activeCardsQuery.data?.length}
            icon={<CreditCardIcon fontSize="large" />}
            loading={activeCardsQuery.isLoading}
            color="success.main"
          />
        </Grid>
      </Grid>

      <Typography variant="h6" sx={{ mb: 2 }}>
        Recent Activity
      </Typography>

      {auditQuery.error && <ErrorAlert error={auditQuery.error} />}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Timestamp</TableCell>
              <TableCell>Event Type</TableCell>
              <TableCell>Aggregate Type</TableCell>
              <TableCell>Actor</TableCell>
              <TableCell>Resource</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {auditQuery.isLoading ? (
              <LoadingSkeleton rows={10} columns={5} />
            ) : (
              auditQuery.data?.items.map((event) => (
                <TableRow key={event.id} hover>
                  <TableCell sx={{ whiteSpace: "nowrap" }}>
                    {formatTimestamp(event.publishedAt ?? event.createdAt)}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={event.eventType ?? "—"}
                      size="small"
                      variant="outlined"
                      sx={{ fontFamily: "monospace", fontSize: "0.7rem" }}
                    />
                  </TableCell>
                  <TableCell>{event.aggregateType ?? "—"}</TableCell>
                  <TableCell sx={{ fontFamily: "monospace", fontSize: "0.8rem" }}>
                    {event.actorId ?? "—"}
                  </TableCell>
                  <TableCell sx={{ fontFamily: "monospace", fontSize: "0.8rem" }}>
                    {event.aggregateId ?? "—"}
                  </TableCell>
                </TableRow>
              ))
            )}
            {!auditQuery.isLoading && !auditQuery.data?.items.length && (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 4 }}>
                  <Typography variant="body2" color="text.secondary">
                    No recent activity
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
}
