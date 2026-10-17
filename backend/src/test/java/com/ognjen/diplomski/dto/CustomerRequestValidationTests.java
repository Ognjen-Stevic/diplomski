package com.ognjen.diplomski.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerRequestValidationTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void maximumLengthValuesAreAccepted() {
        CustomerRequest request = request(
                "O".repeat(300),
                "C".repeat(300),
                "123456789",
                "+381 64 123-4567",
                emailWithLength(254)
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void overMaximumValuesAreRejected() {
        assertInvalidProperty(request(
                "O".repeat(301), "Company", "123456789", null, null
        ), "objectName");
        assertInvalidProperty(request(
                "Object", "C".repeat(301), "123456789", null, null
        ), "companyName");
        assertInvalidProperty(request(
                "Object", "Company", "123456789", "1".repeat(26), null
        ), "phone");
        assertInvalidProperty(request(
                "Object", "Company", "123456789", null, emailWithLength(255)
        ), "email");
    }

    @Test
    void pibMustContainExactlyNineDigits() {
        for (String pib : new String[]{"12345678", "1234567890", "12345A789"}) {
            assertInvalidProperty(request(
                    "Object", "Company", pib, null, null
            ), "pib");
        }
    }

    @Test
    void optionalFieldsAcceptNullAndEmptyValues() {
        CustomerRequest nullValues = new CustomerRequest(
                "Object", "Company", "123456789", null, null, "Address", null, null
        );
        CustomerRequest emptyValues = new CustomerRequest(
                "Object", "Company", "123456789", "", "", "Address", "", ""
        );

        assertTrue(validator.validate(nullValues).isEmpty());
        assertTrue(validator.validate(emptyValues).isEmpty());
    }

    @Test
    void phoneMapUrlAndNoteFollowFrontendValidation() {
        CustomerRequest invalid = new CustomerRequest(
                "Object",
                "Company",
                "123456789",
                "not-a-phone",
                null,
                "Address",
                "http://example.com/location",
                "N".repeat(2001)
        );

        assertInvalidProperty(invalid, "phone");
        assertInvalidProperty(invalid, "googleMapsUrl");
        assertInvalidProperty(invalid, "note");
    }

    private static void assertInvalidProperty(CustomerRequest request, String property) {
        assertFalse(validator.validate(request).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(property))
                .toList()
                .isEmpty());
    }

    private static String emailWithLength(int length) {
        return "a".repeat(64)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(length - 193);
    }

    private static CustomerRequest request(
            String objectName,
            String companyName,
            String pib,
            String phone,
            String email
    ) {
        return new CustomerRequest(
                objectName,
                companyName,
                pib,
                phone,
                email,
                "Address",
                "https://www.google.com/maps?q=44.7866,20.4489",
                "Note"
        );
    }
}
