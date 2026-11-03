package com.ognjen.diplomski.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RequestValidationTests {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void setUp() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void tearDown() { factory.close(); }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void explicitBooleansAreValid(boolean active) {
        assertTrue(validator.validate(new EmployeeActiveRequest(active)).isEmpty());
        assertTrue(validator.validate(new AdvertisingMaterialActiveRequest(active)).isEmpty());
    }

    @Test void nullActivationIsInvalid() {
        assertFalse(validator.validate(new EmployeeActiveRequest(null)).isEmpty());
        assertFalse(validator.validate(new AdvertisingMaterialActiveRequest(null)).isEmpty());
    }

    @Test void normalizedTextConstraintsRemainServiceOwned() {
        // The services trim these before measuring them; annotations must not reject valid padded values.
        assertTrue(validator.validate(new EmployeeCreateRequest("  " + "A".repeat(50) + "  ", "B",
                "  a@example.test  ", "password1", "  " + "1".repeat(50) + "  ", "MANAGER", null)).isEmpty());
        assertTrue(validator.validate(new ProductCreateRequest("  " + "A".repeat(200) + "  ", "P",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)).isEmpty());
    }

    @Test void productAmountsAndRequiredTextAreValidated() {
        var invalid = new ProductCreateRequest(" ", "", BigDecimal.ZERO, null,
                new BigDecimal("-1"), null);
        assertEquals(6, validator.validate(invalid).size());
    }

    @Test void nestedNullItemsCannotReachServices() {
        assertFalse(validator.validate(new GoodsRequestRequest(Arrays.asList((GoodsRequestItemRequest) null), null)).isEmpty());
        assertFalse(validator.validate(new RequisitionRequest(1L,
                com.ognjen.diplomski.entity.enums.RequisitionType.NORMAL, null, null, null,
                Arrays.asList((RequisitionItemRequest) null))).isEmpty());
        assertFalse(validator.validate(new CreateAdvertisingMaterialDistributionRequest(1L,
                Arrays.asList((AdvertisingMaterialDistributionItemRequest) null), null)).isEmpty());
    }

    @Test void requisitionQuantityMinimumAndFractionalSemanticsAreUnchanged() {
        assertTrue(validator.validate(new RequisitionItemRequest("P", new BigDecimal("0.01"))).isEmpty());
        assertTrue(validator.validate(new RequisitionItemRequest("P", new BigDecimal("1.125"))).isEmpty());
        assertFalse(validator.validate(new RequisitionItemRequest("P", new BigDecimal("0.001"))).isEmpty());
    }
}
