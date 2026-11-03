package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.ProductCreateRequest;
import com.ognjen.diplomski.dto.ProductManagementResponse;
import com.ognjen.diplomski.dto.ProductResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductManagementResponse> getProducts(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return productService.getProducts(principal);
    }

    @GetMapping("/available")
    public List<ProductResponse> getAvailableProducts() {
        return productService.getAvailableProducts();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductManagementResponse createProduct(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestPart("product") ProductCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return productService.createProduct(principal, request, image);
    }

    @PatchMapping("/{productId}/deactivate")
    public ProductManagementResponse deactivateProduct(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long productId
    ) {
        return productService.deactivateProduct(principal, productId);
    }
}
