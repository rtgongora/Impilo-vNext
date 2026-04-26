package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ZwAdminDistrictEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ZwAdminWardEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ZwAdminDistrictRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ZwAdminWardRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ZwGeoReferenceService {

    private final ZwAdminDistrictRepository districtRepository;
    private final ZwAdminWardRepository wardRepository;

    public ZwGeoReferenceService(ZwAdminDistrictRepository districtRepository,
                               ZwAdminWardRepository wardRepository) {
        this.districtRepository = districtRepository;
        this.wardRepository = wardRepository;
    }

    public List<Map<String, Object>> listDistricts(String provinceCode) {
        List<ZwAdminDistrictEntity> rows = districtRepository.findByProvinceCodeOrderByNameAsc(provinceCode);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ZwAdminDistrictEntity e : rows) {
            out.add(rowDistrict(e));
        }
        return out;
    }

    public List<Map<String, Object>> listWards(String districtCode) {
        List<ZwAdminWardEntity> rows = wardRepository.findByDistrictCodeOrderByNameAsc(districtCode);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ZwAdminWardEntity e : rows) {
            out.add(rowWard(e));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> bulkUpsert(List<Map<String, Object>> districts, List<Map<String, Object>> wards) {
        int dCount = 0;
        int wCount = 0;
        Instant now = Instant.now();
        if (districts != null) {
            for (Map<String, Object> d : districts) {
                String code = str(d.get("districtCode"));
                if (code == null || code.isBlank()) {
                    continue;
                }
                ZwAdminDistrictEntity e = districtRepository.findByDistrictCode(code).orElseGet(ZwAdminDistrictEntity::new);
                e.setDistrictCode(code);
                e.setProvinceCode(require(str(d.get("provinceCode")), "provinceCode"));
                e.setName(require(str(d.get("name")), "name"));
                e.setSourceRef(str(d.get("sourceRef")));
                e.setImportedAt(now);
                districtRepository.save(e);
                dCount++;
            }
        }
        if (wards != null) {
            for (Map<String, Object> w : wards) {
                String dc = require(str(w.get("districtCode")), "districtCode");
                String wc = require(str(w.get("wardCode")), "wardCode");
                List<ZwAdminWardEntity> existing = wardRepository.findByDistrictCodeOrderByNameAsc(dc);
                ZwAdminWardEntity match = null;
                for (ZwAdminWardEntity e : existing) {
                    if (wc.equals(e.getWardCode())) {
                        match = e;
                        break;
                    }
                }
                ZwAdminWardEntity e = match != null ? match : new ZwAdminWardEntity();
                e.setDistrictCode(dc);
                e.setWardCode(wc);
                e.setName(require(str(w.get("name")), "name"));
                e.setSourceRef(str(w.get("sourceRef")));
                e.setImportedAt(now);
                wardRepository.save(e);
                wCount++;
            }
        }
        return Map.of("districtsUpserted", dCount, "wardsUpserted", wCount);
    }

    private static Map<String, Object> rowDistrict(ZwAdminDistrictEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("districtCode", e.getDistrictCode());
        m.put("provinceCode", e.getProvinceCode());
        m.put("name", e.getName());
        m.put("sourceRef", e.getSourceRef());
        return m;
    }

    private static Map<String, Object> rowWard(ZwAdminWardEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("wardCode", e.getWardCode());
        m.put("districtCode", e.getDistrictCode());
        m.put("name", e.getName());
        m.put("sourceRef", e.getSourceRef());
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().trim();
    }

    private static String require(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return v;
    }
}
