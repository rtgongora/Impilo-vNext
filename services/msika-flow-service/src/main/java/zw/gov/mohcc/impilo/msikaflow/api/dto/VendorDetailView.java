package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.util.List;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.VendorDocumentEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.VendorProfileEntity;

/** Vendor profile plus its uploaded documents — GET /v1/vendors/{id}. */
public record VendorDetailView(
        VendorProfileEntity profile,
        List<VendorDocumentEntity> documents
) {}
