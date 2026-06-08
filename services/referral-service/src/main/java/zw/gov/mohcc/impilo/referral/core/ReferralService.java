package zw.gov.mohcc.impilo.referral.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.referral.domain.ReferralRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReferralService {

    private final ConcurrentHashMap<String, ReferralRecord> referrals = new ConcurrentHashMap<>();

    public List<Map<String, Object>> listReferrals(String patientId) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (ReferralRecord record : referrals.values()) {
            if (patientId.equals(record.getPatientId())) {
                results.add(record.toEnvelope());
            }
        }
        return results;
    }

    public Map<String, Object> createReferral(Map<String, Object> body) {
        Object patientId = body.get("patientId");
        if (patientId == null || patientId.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>(body);
        ReferralRecord record = ReferralRecord.create(patientId.toString(), payload);
        referrals.put(record.getId(), record);
        return record.toEnvelope();
    }

    public Map<String, Object> getReferral(String id) {
        return require(id).toEnvelope();
    }

    public Map<String, Object> acceptReferral(String id) {
        ReferralRecord record = require(id);
        record.accept();
        return record.toEnvelope();
    }

    public Map<String, Object> respondReferral(String id, Map<String, Object> body) {
        ReferralRecord record = require(id);
        String notes = body != null && body.get("notes") != null ? body.get("notes").toString() : null;
        record.respond(notes);
        return record.toEnvelope();
    }

    public Map<String, Object> completeReferral(String id, Map<String, Object> body) {
        ReferralRecord record = require(id);
        record.complete();
        return record.toEnvelope();
    }

    private ReferralRecord require(String id) {
        ReferralRecord record = referrals.get(id);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral not found: " + id);
        }
        return record;
    }
}
