package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.GoodsRequestItemResponse;
import com.ognjen.diplomski.dto.GoodsRequestRowResponse;
import com.ognjen.diplomski.dto.GoodsRequestRequest;
import com.ognjen.diplomski.dto.SalesRepresentativeOptionResponse;
import com.ognjen.diplomski.entity.AdvertisingMaterial;
import com.ognjen.diplomski.entity.GoodsRequest;
import com.ognjen.diplomski.entity.GoodsRequestItem;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.enums.GoodsRequestItemStatus;
import com.ognjen.diplomski.repository.AdvertisingMaterialRepository;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.GoodsRequestRepository;
import com.ognjen.diplomski.repository.ProductRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.GoodsRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsRequestServiceImpl implements GoodsRequestService {

    private final GoodsRequestRepository goodsRequestRepository;
    private final ProductRepository productRepository;
    private final AdvertisingMaterialRepository advertisingMaterialRepository;
    private final AppUserRepository appUserRepository;

    @Override
    @Transactional
    public GoodsRequestRowResponse create(
            AppUserPrincipal principal,
            GoodsRequestRequest request
    ) {
        requireSalesRep(principal);

        List<String> productCodes = request.items().stream()
                .filter(item -> item.productCode() != null && !item.productCode().isBlank())
                .map(item -> item.productCode().trim())
                .toList();
        List<Long> materialIds = request.items().stream()
                .filter(item -> item.materialId() != null)
                .map(item -> item.materialId())
                .toList();

        request.items().forEach(item -> {
            boolean hasProduct = item.productCode() != null && !item.productCode().isBlank();
            boolean hasMaterial = item.materialId() != null;
            if (hasProduct == hasMaterial) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each item must contain exactly one source: productCode or materialId"
                );
            }
        });

        var uniqueProductCodes = new LinkedHashSet<>(productCodes);
        var uniqueMaterialIds = new LinkedHashSet<>(materialIds);
        if (uniqueProductCodes.size() != productCodes.size()
                || uniqueMaterialIds.size() != materialIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An item may appear only once");
        }

        Map<String, Product> productsByCode = productRepository
                .findAllByCodeInAndActiveTrue(uniqueProductCodes)
                .stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity()));
        Map<Long, AdvertisingMaterial> materialsById = advertisingMaterialRepository
                .findAllByIdInAndActiveTrue(uniqueMaterialIds)
                .stream()
                .collect(Collectors.toMap(AdvertisingMaterial::getId, Function.identity()));
        if (productsByCode.size() != uniqueProductCodes.size()
                || materialsById.size() != uniqueMaterialIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more items are unavailable");
        }

        GoodsRequest goodsRequest = new GoodsRequest();
        goodsRequest.setSalesRep(appUserRepository.getReferenceById(principal.id()));
        goodsRequest.setNote(normalizeOptional(request.note()));

        request.items().forEach(itemRequest -> {
            GoodsRequestItem item = new GoodsRequestItem();
            item.setQuantity(itemRequest.quantity());
            if (itemRequest.productCode() != null && !itemRequest.productCode().isBlank()) {
                item.setProduct(productsByCode.get(itemRequest.productCode().trim()));
            } else {
                item.setMaterial(materialsById.get(itemRequest.materialId()));
            }
            goodsRequest.addItem(item);
        });

        return toResponse(goodsRequestRepository.saveAndFlush(goodsRequest));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoodsRequestRowResponse> getMonthlyGoodsRequests(
            AppUserPrincipal principal,
            int year,
            int month
    ) {
        YearMonth period = YearMonth.of(year, month);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime start = period.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = period.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();

        List<GoodsRequest> requests;
        if (principal.hasRole("CEO") || principal.hasRole("BOOKKEEPER")) {
            requests = goodsRequestRepository.findAllMonthlyCatalogRequests(start, end);
        } else if (principal.hasRole("MANAGER")) {
            requests = goodsRequestRepository.findMonthlyCatalogRequestsByManagerTeam(
                    principal.id(), start, end
            );
        } else if (principal.hasRole("SALES_REP")) {
            requests = goodsRequestRepository.findMonthlyCatalogRequestsBySalesRep(
                    principal.id(), start, end
            );
        } else {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User cannot access goods requests"
            );
        }

        return requests
                .stream()
                .map(GoodsRequestServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public GoodsRequestRowResponse updateStatus(
            AppUserPrincipal principal,
            Long goodsRequestId,
            GoodsRequestItemStatus status
    ) {
        requireBookkeeper(principal);
        if (status == null || status == GoodsRequestItemStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status must be APPROVED or REJECTED"
            );
        }

        GoodsRequest goodsRequest = goodsRequestRepository.findByIdForUpdate(goodsRequestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Goods request was not found"
                ));

        List<GoodsRequestItem> pendingItems = goodsRequest.getItems().stream()
                .filter(item -> item.getStatus() == GoodsRequestItemStatus.PENDING)
                .toList();
        if (pendingItems.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Goods request has no pending items"
            );
        }

        pendingItems.forEach(item -> item.setStatus(status));
        return toResponse(goodsRequestRepository.saveAndFlush(goodsRequest));
    }

    private static GoodsRequestRowResponse toResponse(GoodsRequest goodsRequest) {
        List<GoodsRequestItemResponse> items = goodsRequest.getItems().stream()
                .map(item -> new GoodsRequestItemResponse(
                        item.getProduct() != null
                                ? item.getProduct().getName()
                                : item.getMaterial() != null
                                ? item.getMaterial().getName()
                                : item.getCustomItemName(),
                        item.getQuantity(),
                        item.getProduct() != null ? item.getProduct().getPackageType() : null
                ))
                .toList();

        GoodsRequestItemStatus status = goodsRequest.getItems().stream()
                .anyMatch(item -> item.getStatus() == GoodsRequestItemStatus.REJECTED)
                ? GoodsRequestItemStatus.REJECTED
                : goodsRequest.getItems().stream()
                        .anyMatch(item -> item.getStatus() == GoodsRequestItemStatus.PENDING)
                        ? GoodsRequestItemStatus.PENDING
                        : GoodsRequestItemStatus.APPROVED;

        return new GoodsRequestRowResponse(
                goodsRequest.getId(),
                SalesRepresentativeOptionResponse.from(goodsRequest.getSalesRep()),
                items,
                goodsRequest.getNote(),
                goodsRequest.getCreatedAt(),
                status,
                goodsRequest.getItems().stream()
                        .anyMatch(item -> item.getStatus() == GoodsRequestItemStatus.PENDING)
        );
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void requireSalesRep(AppUserPrincipal principal) {
        if (!principal.hasRole("SALES_REP")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sales representatives can create goods requests");
        }
    }

    private static void requireBookkeeper(AppUserPrincipal principal) {
        if (!principal.hasRole("BOOKKEEPER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only bookkeepers can process goods requests");
        }
    }
}
