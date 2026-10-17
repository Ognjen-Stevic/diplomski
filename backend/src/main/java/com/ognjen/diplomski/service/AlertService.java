package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.AlertResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;

public interface AlertService {

    AlertResponse getAlerts(AppUserPrincipal principal);
}
