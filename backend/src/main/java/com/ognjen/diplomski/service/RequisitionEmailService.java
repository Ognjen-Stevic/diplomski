package com.ognjen.diplomski.service;

import com.ognjen.diplomski.entity.Requisition;

public interface RequisitionEmailService {

    void sendPdf(Requisition requisition, String recipientEmail, byte[] pdf);
}
