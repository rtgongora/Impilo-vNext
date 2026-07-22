"use client";

import { useState } from "react";
import { Search, ChevronLeft, ChevronRight, Filter } from "lucide-react";

export interface SearchFilterPaginationProps {
  totalItems: number;
  pageSize: number;
  currentPage: number;
  onPageChange: (page: number) => void;
  onSearch: (query: string) => void;
  onFilterChange?: (filter: string) => void;
  searchPlaceholder?: string;
  filters?: Array<{ label: string; value: string }>;
}

export function SearchFilterPagination({
  totalItems,
  pageSize,
  currentPage,
  onPageChange,
  onSearch,
  onFilterChange,
  searchPlaceholder = "Search...",
  filters,
}: SearchFilterPaginationProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedFilter, setSelectedFilter] = useState("");

  const totalPages = Math.ceil(totalItems / pageSize);
  const hasNextPage = currentPage < totalPages;
  const hasPrevPage = currentPage > 1;

  const handleSearchChange = (value: string) => {
    setSearchQuery(value);
    onSearch(value);
    onPageChange(1); // Reset to first page on new search
  };

  const handleFilterChange = (value: string) => {
    setSelectedFilter(value);
    onFilterChange?.(value);
    onPageChange(1); // Reset to first page on filter change
  };

  const handlePrevPage = () => {
    if (hasPrevPage) {
      onPageChange(currentPage - 1);
    }
  };

  const handleNextPage = () => {
    if (hasNextPage) {
      onPageChange(currentPage + 1);
    }
  };

  return (
    <div className="space-y-4">
      {/* Search and Filter Row */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        {/* Search Input */}
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder={searchPlaceholder}
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            className="w-full pl-10 pr-3 py-2 rounded-md border border-slate-200 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
          />
        </div>

        {/* Filter Dropdown */}
        {filters && filters.length > 0 && (
          <div className="relative">
            <Filter className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-slate-400 pointer-events-none" />
            <select
              value={selectedFilter}
              onChange={(e) => handleFilterChange(e.target.value)}
              className="pl-10 pr-3 py-2 rounded-md border border-slate-200 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100 bg-white cursor-pointer appearance-none w-full sm:w-auto"
            >
              <option value="">All</option>
              {filters.map((filter) => (
                <option key={filter.value} value={filter.value}>
                  {filter.label}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-3">
          <div className="text-xs text-slate-600">
            Page <span className="font-semibold text-slate-900">{currentPage}</span> of{" "}
            <span className="font-semibold text-slate-900">{totalPages}</span>
            {" • "}
            <span className="font-semibold text-slate-900">{totalItems}</span> total items
          </div>

          <div className="flex gap-1">
            <button
              onClick={handlePrevPage}
              disabled={!hasPrevPage}
              className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2 text-xs font-medium text-slate-700 hover:bg-slate-50 transition disabled:opacity-40 disabled:cursor-not-allowed"
              title="Previous page"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Prev</span>
            </button>

            <button
              onClick={handleNextPage}
              disabled={!hasNextPage}
              className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2 text-xs font-medium text-slate-700 hover:bg-slate-50 transition disabled:opacity-40 disabled:cursor-not-allowed"
              title="Next page"
            >
              <span className="hidden sm:inline">Next</span>
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
