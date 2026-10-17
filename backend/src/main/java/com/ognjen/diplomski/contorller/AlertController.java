package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.AlertResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public AlertResponse getAlerts(@AuthenticationPrincipal AppUserPrincipal principal) {
        return alertService.getAlerts(principal);
    }
}
