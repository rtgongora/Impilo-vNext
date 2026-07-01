package zw.gov.mohcc.impilo.pct.core.forms;

import zw.gov.mohcc.impilo.pct.persistence.entity.FormResponseEntity;

/**
 * Seam invoked when a form response becomes eligible for extraction to clinical resources
 * (on submit, or on countersign when extraction was deferred). Implemented by the extraction
 * service (Wave 4). Extraction is best-effort / eventually consistent and must never fail the submit.
 */
public interface FormExtractionHook {

    void extract(FormResponseEntity response);
}
