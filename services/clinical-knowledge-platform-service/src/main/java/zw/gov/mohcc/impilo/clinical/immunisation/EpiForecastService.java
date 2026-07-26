package zw.gov.mohcc.impilo.clinical.immunisation;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The immunisation forecast, exposed as knowledge rather than as a record.
 *
 * <p>This service holds no doses. The dose history is the clinical record and belongs to
 * pct-service; the schedule and the reasoning over it are knowledge and belong here. So the caller
 * passes the child's date of birth and what has already been given, and receives a forecast. That
 * split keeps a single system of record for immunisation history and lets the same forecast run
 * unchanged against an offline copy of the same doses.</p>
 */
@Service
public class EpiForecastService {

    static final String SCHEDULE_PATH = "clinical/zw-epi-schedule.json";

    private final EpiScheduleLoader loader;

    public EpiForecastService(EpiScheduleLoader loader) {
        this.loader = loader;
    }

    public EpiSchedule schedule() {
        return loader.load(SCHEDULE_PATH);
    }

    public EpiForecastEngine.Forecast forecast(LocalDate dateOfBirth, String sex,
                                               List<Map<String, Object>> doses, LocalDate asOf) {
        List<EpiForecastEngine.AdministeredDose> administered = doses == null ? List.of()
                : doses.stream().map(EpiForecastEngine::fromMap).toList();
        return EpiForecastEngine.forecast(schedule(), dateOfBirth, sex, administered,
                asOf == null ? LocalDate.now() : asOf);
    }
}
