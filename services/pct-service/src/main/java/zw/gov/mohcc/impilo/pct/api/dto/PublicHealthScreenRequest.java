package zw.gov.mohcc.impilo.pct.api.dto;

/**
 * Public-health / mortality-review screening inputs for a death case (WS#8).
 */
public record PublicHealthScreenRequest(
        Integer ageYears,
        Integer ageDays,
        Boolean maternal,
        Boolean perinatal,
        Boolean stillbirth,
        Boolean notifiableCause,
        Boolean outbreakLinked
) {}
