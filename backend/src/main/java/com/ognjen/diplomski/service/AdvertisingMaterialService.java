package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.AdvertisingMaterialResponse;
import com.ognjen.diplomski.dto.AdvertisingMaterialDistributionResponse;
import com.ognjen.diplomski.dto.CreateAdvertisingMaterialDistributionRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialCreateRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialActiveRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialManagementResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;

import java.util.List;

public interface AdvertisingMaterialService {

    List<AdvertisingMaterialResponse> getAvailableAdvertisingMaterials();

    List<AdvertisingMaterialManagementResponse> getAdvertisingMaterials(AppUserPrincipal principal);

    AdvertisingMaterialManagementResponse createAdvertisingMaterial(
            AppUserPrincipal principal,
            AdvertisingMaterialCreateRequest request
    );

    AdvertisingMaterialManagementResponse setAdvertisingMaterialActive(
            AppUserPrincipal principal,
            Long materialId,
            AdvertisingMaterialActiveRequest request
    );

    List<AdvertisingMaterialDistributionResponse> recordAdvertisingMaterialDistribution(
            AppUserPrincipal principal,
            CreateAdvertisingMaterialDistributionRequest request
    );

    List<AdvertisingMaterialDistributionResponse> getMonthlyAdvertisingMaterials(
            AppUserPrincipal principal,
            int year,
            int month
    );
}
