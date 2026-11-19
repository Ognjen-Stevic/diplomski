package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.ProductCreateRequest;
import com.ognjen.diplomski.dto.ProductManagementResponse;
import com.ognjen.diplomski.dto.ProductPageResponse;
import com.ognjen.diplomski.dto.ProductResponse;
import com.ognjen.diplomski.dto.ProductStatusFilter;
import com.ognjen.diplomski.security.AppUserPrincipal;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    List<ProductResponse> getAvailableProducts();

    ProductPageResponse getProducts(
            AppUserPrincipal principal,
            String search,
            ProductStatusFilter status,
            Pageable pageable
    );

    ProductManagementResponse createProduct(
            AppUserPrincipal principal,
            ProductCreateRequest request,
            MultipartFile image
    );

    ProductManagementResponse deactivateProduct(AppUserPrincipal principal, Long productId);
}
