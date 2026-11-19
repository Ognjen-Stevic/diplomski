package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.ProductCreateRequest;
import com.ognjen.diplomski.dto.ProductStatusFilter;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.enums.PackageType;
import com.ognjen.diplomski.repository.ProductRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.ProductServiceImpl;
import com.ognjen.diplomski.storage.ProductImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceImplTests {

    private static final PageRequest PAGE = PageRequest.of(
            0, 15, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));

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
                () -> service.getProducts(manager, "", ProductStatusFilter.ALL, PAGE))
                .getStatusCode().value());
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
        when(repository.searchManagement("", false, null, PAGE))
                .thenReturn(new PageImpl<>(List.of(product), PAGE, 1));

        var products = service.getProducts(bookkeeper, "", ProductStatusFilter.ALL, PAGE);

        assertEquals(1, products.content().size());
        assertEquals(1, products.totalElements());
        assertEquals(1, products.totalPages());
        assertEquals(0, products.pageNumber());
        assertEquals("Artikal", products.content().getFirst().name());
        assertEquals("ART-1", products.content().getFirst().code());
        assertEquals(new BigDecimal("100.00"), products.content().getFirst().price());
        assertEquals(BigDecimal.ONE, products.content().getFirst().volumeLiters());
        assertEquals(BigDecimal.ZERO, products.content().getFirst().stimulationPerUnit());
        assertEquals(BigDecimal.ZERO, products.content().getFirst().managerStimulationPerUnit());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.createProduct(bookkeeper, request("ART-2", "1.000"), null))
                .getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.deactivateProduct(bookkeeper, 10L))
                .getStatusCode().value());
        verify(repository).searchManagement("", false, null, PAGE);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void searchAndInactiveFilterAreAppliedByRepositoryBeforePagination() {
        var secondPage = PageRequest.of(
                1, 15, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        when(repository.searchManagement("rakija", true, false, secondPage))
                .thenReturn(new PageImpl<>(List.of(), secondPage, 18));

        var result = service.getProducts(
                ceo(), "  RAKIJA  ", ProductStatusFilter.INACTIVE, secondPage);

        assertEquals(18, result.totalElements());
        assertEquals(2, result.totalPages());
        assertEquals(1, result.pageNumber());
        verify(repository).searchManagement("rakija", true, false, secondPage);
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
