package zw.gov.mohcc.impilo.support.api.dto;

public record DashboardStatsResponse(long openCount, long inProgressCount, long resolvedCount,
                                      long closedCount, long criticalCount, long highCount,
                                      long escalatedCount, double avgResolutionHours) {}
