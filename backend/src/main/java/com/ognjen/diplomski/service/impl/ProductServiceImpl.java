package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.exception.InvalidRequestException;

import com.ognjen.diplomski.dto.ProductCreateRequest;
import com.ognjen.diplomski.dto.ProductManagementResponse;
import com.ognjen.diplomski.dto.ProductPageResponse;
import com.ognjen.diplomski.dto.ProductResponse;
import com.ognjen.diplomski.dto.ProductStatusFilter;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.enums.PackageType;
import com.ognjen.diplomski.repository.ProductRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.ProductService;
import com.ognjen.diplomski.storage.ProductImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageStorage productImageStorage;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAvailableProducts() {
        return productRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(product -> new ProductResponse(
                        product.getCode(),
                        product.getName(),
                        product.getPrice(),
                        product.getPictureUrl(),
                        product.getPackageType()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPageResponse getProducts(
            AppUserPrincipal principal,
            String search,
            ProductStatusFilter status,
            Pageable pageable
    ) {
        requireProductReader(principal);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        ProductStatusFilter effectiveStatus = status == null ? ProductStatusFilter.ALL : status;
        Boolean active = switch (effectiveStatus) {
            case ACTIVE -> true;
            case INACTIVE -> false;
            case ALL -> null;
        };
        return ProductPageResponse.from(
                productRepository.searchManagement(
                        normalizedSearch,
                        !normalizedSearch.isEmpty(),
                        active,
                        pageable
                ).map(ProductServiceImpl::toManagementResponse)
        );
    }

    @Override
    @Transactional
    public ProductManagementResponse createProduct(
            AppUserPrincipal principal,
            ProductCreateRequest request,
            MultipartFile image
    ) {
        requireCeo(principal);
        validateCreateRequest(request);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Artikal sa ovom šifrom već postoji.");
        }

        Product product = new Product();
        product.setName(request.name().trim());
        product.setCode(code);
        product.setPrice(request.price());
        product.setVolumeLiters(request.volumeLiters());
        product.setStimulationPerUnit(request.stimulationPerUnit());
        product.setManagerStimulationPerUnit(request.managerStimulationPerUnit());
        product.setPackageType(packageTypeFor(request.volumeLiters()));
        product.setActive(true);
        String pictureUrl = productImageStorage.store(image);
        product.setPictureUrl(pictureUrl);

        try {
            return toManagementResponse(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            productImageStorage.deleteIfUploaded(pictureUrl);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Artikal sa ovom šifrom već postoji.");
        } catch (RuntimeException exception) {
            productImageStorage.deleteIfUploaded(pictureUrl);
            throw exception;
        }
    }

    @Override
    @Transactional
    public ProductManagementResponse deactivateProduct(AppUserPrincipal principal, Long productId) {
        requireCeo(principal);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artikal nije pronađen."));
        if (!product.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Artikal je već deaktiviran i ne može biti ponovo aktiviran.");
        }
        product.setActive(false);
        return toManagementResponse(productRepository.saveAndFlush(product));
    }

    private static ProductManagementResponse toManagementResponse(Product product) {
        return new ProductManagementResponse(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getPictureUrl(),
                product.getPrice(),
                product.getVolumeLiters(),
                product.getStimulationPerUnit(),
                product.getManagerStimulationPerUnit(),
                product.isActive()
        );
    }

    private static void validateCreateRequest(ProductCreateRequest request) {
        if (request == null) throw new InvalidRequestException("Request body is required");
        requireText(request.name(), "Naziv", 200);
        requireText(request.code(), "Šifra", 100);
        requirePositive(request.price(), "Cena");
        requirePositive(request.volumeLiters(), "Zapremina");
        requireNonNegative(request.stimulationPerUnit(), "Stimulacija komercijaliste");
        requireNonNegative(request.managerStimulationPerUnit(), "Stimulacija menadžera");
        if (request.price().scale() > 2
                || request.stimulationPerUnit().scale() > 2
                || request.managerStimulationPerUnit().scale() > 2) {
            throw new InvalidRequestException("Novčane vrednosti mogu imati najviše dve decimale");
        }
        if (integerDigits(request.price()) > 8
                || integerDigits(request.stimulationPerUnit()) > 8
                || integerDigits(request.managerStimulationPerUnit()) > 8) {
            throw new InvalidRequestException("Novčane vrednosti su prevelike");
        }
        if (request.volumeLiters().scale() > 3) {
            throw new InvalidRequestException("Zapremina može imati najviše tri decimale");
        }
        if (integerDigits(request.volumeLiters()) > 7) {
            throw new InvalidRequestException("Zapremina je prevelika");
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new InvalidRequestException(field + " je obavezna i može imati najviše " + maxLength + " karaktera");
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new InvalidRequestException(field + " mora biti veća od nule");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new InvalidRequestException(field + " ne može biti negativna");
        }
    }

    private static PackageType packageTypeFor(BigDecimal volumeLiters) {
        return volumeLiters.compareTo(BigDecimal.ONE) <= 0 ? PackageType.BOTTLE : PackageType.JUG;
    }

    private static int integerDigits(BigDecimal value) {
        return Math.max(0, value.precision() - value.scale());
    }

    private static void requireProductReader(AppUserPrincipal principal) {
        if (principal == null
                || (!principal.hasRole("CEO") && !principal.hasRole("BOOKKEEPER"))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only CEO and bookkeepers can view products."
            );
        }
    }

    private static void requireCeo(AppUserPrincipal principal) {
        if (principal == null || !principal.hasRole("CEO")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Samo CEO može upravljati artiklima.");
        }
    }
}
