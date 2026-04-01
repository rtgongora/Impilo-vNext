"use client";

import React from "react";
import { TableRow, TableCell, Skeleton } from "@mui/material";

interface LoadingSkeletonProps {
  rows?: number;
  columns: number;
}

export const LoadingSkeleton: React.FC<LoadingSkeletonProps> = ({
  rows = 5,
  columns,
}) => {
  return (
    <>
      {[...Array(rows)].map((_, rowIndex) => (
        <TableRow key={rowIndex}>
          {[...Array(columns)].map((_, colIndex) => (
            <TableCell key={colIndex}>
              <Skeleton variant="text" height={30} />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  );
};
