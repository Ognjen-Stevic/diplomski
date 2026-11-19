package com.ognjen.diplomski.contorller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ognjen.diplomski.dto.ProductPageResponse;
import com.ognjen.diplomski.dto.ProductStatusFilter;
import com.ognjen.diplomski.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class ProductControllerTests {
    @Test
    void managementListingUsesFixedSizeAndDeterministicSorting() {
        ProductService service = mock(ProductService.class);
        when(service.getProducts(isNull(), eq("rakija"), eq(ProductStatusFilter.ACTIVE), any()))
                .thenReturn(new ProductPageResponse(List.of(), 31, 3, 2));
        var controller = new ProductController(service);

        var response = controller.getProducts(null, "rakija", ProductStatusFilter.ACTIVE, 2);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getProducts(isNull(), eq("rakija"), eq(ProductStatusFilter.ACTIVE), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(15, pageable.getValue().getPageSize());
        assertEquals("name: ASC,id: ASC", pageable.getValue().getSort().toString());
        assertEquals(3, response.totalPages());
        assertEquals(2, response.pageNumber());
    }
}
