package zw.gov.mohcc.impilo.tshepo.authz.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record AuthorityUpdateRequest(
        @NotNull Map<String, Object> authority
) {}
