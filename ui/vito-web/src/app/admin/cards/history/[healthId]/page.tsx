"use client";

import { useParams, useRouter } from "next/navigation";
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusChip } from "@/components/common/StatusChip";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { useCardHistory } from "@/hooks/queries/useCards";

export default function CardHistoryPage() {
  const { healthId } = useParams() as { healthId: string };
  const router = useRouter();

  const { data: cards, isLoading, error } = useCardHistory(healthId);

  return (
    <Box>
      <PageHeader
        title={`Card History: ${healthId}`}
        breadcrumbs={[
          { label: "Admin", href: "/admin" },
          { label: "Cards", href: "/admin/cards" },
          { label: "History" },
        ]}
        action={
          <Button
            startIcon={<ArrowBackIcon />}
            onClick={() => router.back()}
          >
            Back
          </Button>
        }
      />

      <Box sx={{ mb: 2 }}>
        <ErrorAlert error={error} />
      </Box>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Card ID</TableCell>
              <TableCell>Card Number</TableCell>
              <TableCell>Type</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Requested At</TableCell>
              <TableCell>Activated At</TableCell>
              <TableCell>Inactivated At</TableCell>
              <TableCell>Revoked At</TableCell>
              <TableCell>Reason</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <LoadingSkeleton columns={9} rows={5} />
            ) : (
              (cards?.items ?? []).map((card) => (
                <TableRow key={card.cardId}>
                  <TableCell sx={{ fontFamily: "monospace", fontSize: "0.8rem" }}>
                    {card.cardId?.substring(0, 8)}...
                  </TableCell>
                  <TableCell>{card.cardNumber || "-"}</TableCell>
                  <TableCell>{card.cardType}</TableCell>
                  <TableCell>
                    <StatusChip status={card.status!} type="card" />
                  </TableCell>
                  <TableCell>
                    {card.requestedAt ? new Date(card.requestedAt).toLocaleDateString() : "-"}
                  </TableCell>
                  <TableCell>
                    {card.activatedAt ? new Date(card.activatedAt).toLocaleDateString() : "-"}
                  </TableCell>
                  <TableCell>
                    {card.inactivatedAt ? new Date(card.inactivatedAt).toLocaleDateString() : "-"}
                  </TableCell>
                  <TableCell>
                    {card.revokedAt ? new Date(card.revokedAt).toLocaleDateString() : "-"}
                  </TableCell>
                  <TableCell>
                    {card.revocationReason || card.inactivationReason || "-"}
                  </TableCell>
                </TableRow>
              ))
            )}
            {!isLoading && !(cards?.items?.length) && (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ py: 4 }}>
                  <Typography variant="body2" color="text.secondary">
                    No card history found for this client
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
