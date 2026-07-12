package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.util.List;

public record PagedAuditView(List<AuditEntryView> items, int page, int size, long totalElements) {}
