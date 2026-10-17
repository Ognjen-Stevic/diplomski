package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.AdvertisingMaterialResponse;
import com.ognjen.diplomski.dto.AdvertisingMaterialDistributionResponse;
import com.ognjen.diplomski.dto.CreateAdvertisingMaterialDistributionRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialDistributionItemRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialCreateRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialActiveRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialManagementResponse;
import com.ognjen.diplomski.dto.CustomerSummaryResponse;
import com.ognjen.diplomski.dto.SalesRepresentativeOptionResponse;
import com.ognjen.diplomski.entity.AdvertisingMaterial;
import com.ognjen.diplomski.entity.AdvertisingMaterialDistribution;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.AdvertisingMaterialDistributionRepository;
import com.ognjen.diplomski.repository.AdvertisingMaterialRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.AdvertisingMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AdvertisingMaterialServiceImpl implements AdvertisingMaterialService {

    private final AdvertisingMaterialRepository advertisingMaterialRepository;
    private final AdvertisingMaterialDistributionRepository advertisingMaterialDistributionRepository;
    private final CustomerRepository customerRepository;
    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdvertisingMaterialResponse> getAvailableAdvertisingMaterials() {
        return advertisingMaterialRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(material -> new AdvertisingMaterialResponse(material.getId(), material.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdvertisingMaterialManagementResponse> getAdvertisingMaterials(
            AppUserPrincipal principal
    ) {
        requireCeo(principal);
        return advertisingMaterialRepository.findAllByOrderByNameAsc().stream()
                .map(AdvertisingMaterialServiceImpl::toManagementResponse)
                .toList();
    }

    @Override
    @Transactional
    public AdvertisingMaterialManagementResponse createAdvertisingMaterial(
            AppUserPrincipal principal,
            AdvertisingMaterialCreateRequest request
    ) {
        requireCeo(principal);
        if (request == null || request.name() == null || request.name().isBlank()
                || request.name().trim().length() > 150) {
            throw new IllegalArgumentException(
                    "Naziv je obavezan i može imati najviše 150 karaktera."
            );
        }
        String name = request.name().trim();
        if (advertisingMaterialRepository.existsByNameIgnoreCase(name)) {
            throw duplicateName();
        }

        AdvertisingMaterial material = new AdvertisingMaterial();
        material.setName(name);
        material.setActive(true);
        try {
            return toManagementResponse(advertisingMaterialRepository.saveAndFlush(material));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateName();
        }
    }

    @Override
    @Transactional
    public AdvertisingMaterialManagementResponse setAdvertisingMaterialActive(
            AppUserPrincipal principal,
            Long materialId,
            AdvertisingMaterialActiveRequest request
    ) {
        requireCeo(principal);
        if (request == null) throw new IllegalArgumentException("Request body is required");
        AdvertisingMaterial material = advertisingMaterialRepository.findById(materialId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Reklamni materijal nije pronađen."
                ));
        material.setActive(request.active());
        return toManagementResponse(advertisingMaterialRepository.saveAndFlush(material));
    }

    @Override
    @Transactional
    public List<AdvertisingMaterialDistributionResponse> recordAdvertisingMaterialDistribution(
            AppUserPrincipal principal,
            CreateAdvertisingMaterialDistributionRequest request
    ) {
        requireSalesRep(principal);

        var customer = customerRepository.findByIdAndSalesRepIdAndStatus(
                        request.customerId(),
                        principal.id(),
                        CustomerStatus.APPROVED
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        List<Long> materialIds = request.items().stream()
                .map(AdvertisingMaterialDistributionItemRequest::materialId)
                .toList();
        var uniqueMaterialIds = new LinkedHashSet<>(materialIds);
        if (uniqueMaterialIds.size() != materialIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A material may appear only once");
        }

        Map<Long, AdvertisingMaterial> materialsById = advertisingMaterialRepository
                .findAllByIdInAndActiveTrue(uniqueMaterialIds)
                .stream()
                .collect(Collectors.toMap(AdvertisingMaterial::getId, Function.identity()));
        if (materialsById.size() != uniqueMaterialIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more materials are unavailable");
        }

        String note = normalizeOptional(request.note());
        var salesRep = appUserRepository.getReferenceById(principal.id());
        List<AdvertisingMaterialDistribution> distributions = request.items().stream()
                .map(itemRequest -> {
                    AdvertisingMaterialDistribution distribution =
                            new AdvertisingMaterialDistribution();
                    distribution.setCustomer(customer);
                    distribution.setSalesRep(salesRep);
                    distribution.setMaterial(materialsById.get(itemRequest.materialId()));
                    distribution.setQuantity(itemRequest.quantity());
                    distribution.setNote(note);
                    return distribution;
                })
                .toList();

        return advertisingMaterialDistributionRepository.saveAllAndFlush(distributions).stream()
                .map(AdvertisingMaterialServiceImpl::toDistributionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdvertisingMaterialDistributionResponse> getMonthlyAdvertisingMaterials(
            AppUserPrincipal principal,
            int year,
            int month
    ) {
        YearMonth period = YearMonth.of(year, month);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime start = period.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = period.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();

        List<AdvertisingMaterialDistribution> distributions;
        if (principal.hasRole("CEO")) {
            distributions = advertisingMaterialDistributionRepository.findAllMonthly(start, end);
        } else if (principal.hasRole("MANAGER")) {
            distributions = advertisingMaterialDistributionRepository.findMonthlyByManagerTeam(
                    principal.id(), start, end
            );
        } else if (principal.hasRole("SALES_REP")) {
            distributions = advertisingMaterialDistributionRepository.findMonthlyBySalesRep(
                    principal.id(), start, end
            );
        } else {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User cannot access advertising material distribution history"
            );
        }

        return distributions.stream()
                .map(AdvertisingMaterialServiceImpl::toDistributionResponse)
                .toList();
    }

    private static AdvertisingMaterialDistributionResponse toDistributionResponse(
            AdvertisingMaterialDistribution distribution
    ) {
        return new AdvertisingMaterialDistributionResponse(
                distribution.getId(),
                SalesRepresentativeOptionResponse.from(distribution.getSalesRep()),
                CustomerSummaryResponse.from(distribution.getCustomer()),
                distribution.getMaterial().getName(),
                distribution.getQuantity(),
                distribution.getNote(),
                distribution.getDistributedAt()
        );
    }

    private static AdvertisingMaterialManagementResponse toManagementResponse(
            AdvertisingMaterial material
    ) {
        return new AdvertisingMaterialManagementResponse(
                material.getId(), material.getName(), material.isActive()
        );
    }

    private static ResponseStatusException duplicateName() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Reklamni materijal sa ovim nazivom već postoji."
        );
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void requireSalesRep(AppUserPrincipal principal) {
        if (!principal.hasRole("SALES_REP")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sales representatives can assign advertising materials");
        }
    }

    private static void requireCeo(AppUserPrincipal principal) {
        if (principal == null || !principal.hasRole("CEO")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Samo CEO može upravljati reklamnim materijalom."
            );
        }
    }
}
