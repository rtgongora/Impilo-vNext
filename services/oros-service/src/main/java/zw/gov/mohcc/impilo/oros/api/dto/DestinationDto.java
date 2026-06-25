package zw.gov.mohcc.impilo.oros.api.dto;

/**
 * A routing destination option for the diagnostic provider directory (spec §4 routing).
 *
 * @param destinationType maps to {@link zw.gov.mohcc.impilo.oros.domain.RouteDestinationType}
 * @param id              the destination's reference id (facility id / provider public id)
 * @param name            display name
 * @param source          which registry it came from (TUSO / VARAPI)
 */
public record DestinationDto(String destinationType, String id, String name, String source) {}
