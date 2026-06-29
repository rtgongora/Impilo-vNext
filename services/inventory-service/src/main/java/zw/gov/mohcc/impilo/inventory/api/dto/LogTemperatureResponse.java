package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.core.ColdChainService;

/**
 * Result of logging a temperature: the log and, if the reading breached the
 * safe range, the excursion raised ({@code null} otherwise).
 */
public record LogTemperatureResponse(
        TemperatureLogDto log,
        ColdChainExcursionDto excursion
) {
    public static LogTemperatureResponse from(ColdChainService.LogResult result) {
        return new LogTemperatureResponse(
                TemperatureLogDto.from(result.log()),
                result.excursion() != null ? ColdChainExcursionDto.from(result.excursion()) : null);
    }
}
