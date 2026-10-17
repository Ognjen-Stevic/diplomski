package com.ognjen.diplomski.service;

import com.ognjen.diplomski.entity.Requisition;

public interface RequisitionPdfService {

    byte[] createPdf(Requisition requisition);
}
