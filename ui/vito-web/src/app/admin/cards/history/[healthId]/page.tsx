"use client";

import React from "react";
import {
  Box,
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { ArrowBack as ArrowBackIcon } from "@mui/icons-material";
import Link from "next/link";
import { useParams } from "next/navigation";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusChip } from "@/components/common/StatusChip";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { useCardHistory } from "@/hooks/queries/useCards";

export default function CardHistoryPage() {
  const params = useParams();
  const healthId = params.healthId as string;
  const { data: history, isLoading, error } = useCardHistory(healthId);

  return (
    <Box>
      <PageHeader
        title={`Card History: ${healthId}`}
        breadcrumbs={[
          { label: "Admin", href: "/admin/dashboard" },
          { label: "Cards", href: "/admin/cards" },
          { label: "History" },
        ]}
      />

      <Box sx={{ mb: 3 }}>
        <Button
          startIcon={<ArrowBackIcon />}
          component={Link}
          href="/admin/cards"
        >
          Back to Cards
        </Button>
      </Box>

      <Paper sx={{ p: 2 }}>
        <ErrorAlert error={error as any} />
        
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Card ID</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Card Number</TableCell>
                <TableCell>Requested</TableCell>
                <TableCell>Activated</TableCell>
                <TableCell>Revoked</TableCell>
                <TableCell>Revocation Reason</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading ? (
                <LoadingSkeleton rows={5} columns={7} />
              ) : history?.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center">
                    <Typography variant="body2" sx={{ py: 2 }}>No card history found for this client</Typography>
                  </TableCell>
                </TableRow>
              ) : (
                history?.map((card) => (
                  <TableRow key={card.cardId}>
                    <TableCell>{card.cardId?.substring(0, 8)}...</TableCell>
                    <TableCell>
                      <StatusChip status={card.status!} type="card" />
                    </TableCell>
                    <TableCell>{card.cardNumber || "-"}</TableCell>
                    <TableCell>
                      {card.requestedAt ? new Date(card.requestedAt).toLocaleDateString() : "-"}
                    </TableCell>
                    <TableCell>
                      {card.activatedAt ? new Date(card.activatedAt).toLocaleDateString() : "-"}
                    </TableCell>
                    <TableCell>
                      {card.revokedAt ? new Date(card.revokedAt).toLocaleDateString() : "-"}
                    </TableCell>
                    <TableCell>{card.revocationReason || "-"}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  );
}
