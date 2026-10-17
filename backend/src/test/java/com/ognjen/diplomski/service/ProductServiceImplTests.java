package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.ProductCreateRequest;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.enums.PackageType;
import com.ognjen.diplomski.repository.ProductRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.ProductServiceImpl;
import com.ognjen.diplomski.service.ProductImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceImplTests {

    private ProductRepository repository;
    private ProductServiceImpl service;
    private ProductImageStorage imageStorage;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        imageStorage = mock(ProductImageStorage.class);
        service = new ProductServiceImpl(repository, imageStorage);
        when(imageStorage.store(nullable(org.springframework.web.multipart.MultipartFile.class)))
                .thenReturn("products/test-image.webp");
        when(repository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            if (product.getId() == null) product.setId(10L);
            return product;
        });
    }

    @Test
    void ceoCreatesAnActiveProduct() {
        MockMultipartFile image = new MockMultipartFile(
                "image", "article.webp", "image/webp", new byte[]{1, 2, 3});
        var response = service.createProduct(ceo(), request("ART-1", "0.700"), image);

        assertEquals("ART-1", response.code());
        assertEquals(new BigDecimal("0.700"), response.volumeLiters());
        assertTrue(response.active());
        assertEquals("products/test-image.webp", response.pictureUrl());
        verify(imageStorage).store(image);
        verify(repository).saveAndFlush(argThat(product ->
                product.getPackageType() == PackageType.BOTTLE && product.isActive()));
    }

    @Test
    void productCodeMustBeUnique() {
        when(repository.existsByCodeIgnoreCase("ART-1")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createProduct(ceo(), request("art-1", "1.000"), null));

        assertEquals(409, exception.getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void ceoCanDeactivateButCannotReactivateAProduct() {
        Product product = product(true);
        when(repository.findById(10L)).thenReturn(Optional.of(product));

        assertFalse(service.deactivateProduct(ceo(), 10L).active());
        assertFalse(product.isActive());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.deactivateProduct(ceo(), 10L));
        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void nonCeoCannotManageProducts() {
        AppUserPrincipal manager = principal(7L, "ROLE_MANAGER");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.getProducts(manager)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.createProduct(manager, request("ART-1", "1.000"), null)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.deactivateProduct(manager, 10L)).getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void bookkeeperCanReadProductsButCannotModifyThem() {
        AppUserPrincipal bookkeeper = principal(8L, "ROLE_BOOKKEEPER");
        Product product = product(true);
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(product));

        var products = service.getProducts(bookkeeper);

        assertEquals(1, products.size());
        assertEquals("Artikal", products.getFirst().name());
        assertEquals("ART-1", products.getFirst().code());
        assertEquals(new BigDecimal("100.00"), products.getFirst().price());
        assertEquals(BigDecimal.ONE, products.getFirst().volumeLiters());
        assertEquals(BigDecimal.ZERO, products.getFirst().stimulationPerUnit());
        assertEquals(BigDecimal.ZERO, products.getFirst().managerStimulationPerUnit());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.createProduct(bookkeeper, request("ART-2", "1.000"), null))
                .getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.deactivateProduct(bookkeeper, 10L))
                .getStatusCode().value());
        verify(repository).findAllByOrderByNameAsc();
        verify(repository, never()).saveAndFlush(any());
    }

    private static ProductCreateRequest request(String code, String volume) {
        return new ProductCreateRequest(
                "Artikal", code, new BigDecimal("100.00"), new BigDecimal(volume),
                new BigDecimal("5.00"), new BigDecimal("2.00")
        );
    }

    private static Product product(boolean active) {
        Product product = new Product();
        product.setId(10L); product.setName("Artikal"); product.setCode("ART-1");
        product.setPrice(new BigDecimal("100.00")); product.setVolumeLiters(BigDecimal.ONE);
        product.setStimulationPerUnit(BigDecimal.ZERO);
        product.setManagerStimulationPerUnit(BigDecimal.ZERO);
        product.setActive(active);
        return product;
    }

    private static AppUserPrincipal ceo() { return principal(1L, "ROLE_CEO"); }

    private static AppUserPrincipal principal(Long id, String role) {
        return new AppUserPrincipal(id, "user@example.com", "hash", "Test", "User", true,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
