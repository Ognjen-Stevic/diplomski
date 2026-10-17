package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.MonthlyRequisitionResponse;
import com.ognjen.diplomski.dto.RequisitionEmailRequest;
import com.ognjen.diplomski.dto.RequisitionRequest;
import com.ognjen.diplomski.dto.RequisitionResponse;
import com.ognjen.diplomski.dto.RequisitionStatusUpdateRequest;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.RequisitionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/requisitions")
@RequiredArgsConstructor
@Validated
public class RequisitionController {

    private final RequisitionService requisitionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequisitionResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody RequisitionRequest request
    ) {
        return requisitionService.create(principal, request);
    }

    @GetMapping("/{requisitionId}")
    public RequisitionResponse findById(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long requisitionId
    ) {
        return requisitionService.findById(principal, requisitionId);
    }

    @GetMapping(value = "/{requisitionId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long requisitionId
    ) {
        byte[] pdf = requisitionService.generatePdf(principal, requisitionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("trebovanje-%d.pdf".formatted(requisitionId), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(pdf.length)
                .body(pdf);
    }

    @PostMapping("/{requisitionId}/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendPdfByEmail(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long requisitionId,
            @Valid @RequestBody RequisitionEmailRequest request
    ) {
        requisitionService.sendPdfByEmail(principal, requisitionId, request.recipient());
    }

    @PutMapping("/{requisitionId}")
    public RequisitionResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long requisitionId,
            @Valid @RequestBody RequisitionRequest request
    ) {
        return requisitionService.update(principal, requisitionId, request);
    }

    @PatchMapping("/{requisitionId}/status")
    public RequisitionResponse updateStatus(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long requisitionId,
            @Valid @RequestBody RequisitionStatusUpdateRequest request
    ) {
        return requisitionService.updateStatus(principal, requisitionId, request);
    }

    @GetMapping
    public MonthlyRequisitionResponse getMonthlyRequisition(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month
    ) {
        return requisitionService.getMonthlyRequisition(principal, year, month);
    }

    @GetMapping("/customer/{customerId}")
    public MonthlyRequisitionResponse getMonthlyCustomerRequisitions(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long customerId,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month
    ) {
        return requisitionService.getMonthlyCustomerRequisitions(principal, customerId, year, month);
    }
}
