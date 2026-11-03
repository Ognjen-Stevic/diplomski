package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.auth.ApiErrorWriter;
import com.ognjen.diplomski.auth.ApiExceptionHandler;
import com.ognjen.diplomski.config.SpringSecurityConfig;
import com.ognjen.diplomski.dto.*;
import com.ognjen.diplomski.entity.*;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.exception.InvalidRequestException;
import com.ognjen.diplomski.repository.*;
import com.ognjen.diplomski.security.*;
import com.ognjen.diplomski.service.*;
import com.ognjen.diplomski.service.impl.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC, production security chain/JWT, report/requisition rules and POI; mocked persistence/mail. */
@WebMvcTest(controllers = {AuthController.class, CustomerController.class, EmployeeController.class,
        ProductController.class, RequisitionController.class, SalesReportController.class,
        AdvertisingMaterialController.class, GoodsRequestController.class, AlertController.class})
@Import({SpringSecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        ApiErrorWriter.class, ApiExceptionHandler.class, SalesReportServiceImpl.class,
        SalesReportExcelServiceImpl.class, ReportSubjectResolver.class, RequisitionServiceImpl.class})
@TestPropertySource(properties = {"security.jwt.secret=http-security-tests-only-secret-at-least-32-bytes",
        "logging.level.root=WARN", "logging.level.org.springframework.web=WARN", "debug=false",
        "logging.level.com.ognjen.diplomski.auth.ApiExceptionHandler=OFF"})
class ApiHttpSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean AppUserRepository users;
    @MockitoBean SalesReportRepository sales;
    @MockitoBean RequisitionRepository requisitions;
    @MockitoBean CustomerRepository customers;
    @MockitoBean ProductRepository products;
    @MockitoBean RequisitionPdfService pdf;
    @MockitoBean RequisitionEmailService email;
    @MockitoBean CustomerService customerService;
    @MockitoBean EmployeeService employeeService;
    @MockitoBean ProductService productService;
    @MockitoBean AdvertisingMaterialService materialService;
    @MockitoBean GoodsRequestService goodsService;
    @MockitoBean AlertService alertService;
    @MockitoBean AuthServiceImpl authService;

    private static AppUser user(long id, String role, String first, String last) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail("user" + id + "@example.test");
        user.setPasswordHash("unused");
        user.setFirstName(first);
        user.setLastName(last);
        Role assigned = new Role();
        assigned.setName(role);
        user.setRole(assigned);
        return user;
    }

    private AppUser actor(String role, String... permissions) {
        AppUser user = user(switch (role) { case "CEO" -> 1; case "MANAGER" -> 8; default -> 25; },
                role, role.equals("CEO") ? "Ceo" : role.equals("MANAGER") ? "Mira" : "Ana",
                role.equals("CEO") ? "Owner" : role.equals("MANAGER") ? "Manager" : "Rep");
        for (String name : permissions) {
            Permission permission = new Permission();
            permission.setName(name);
            user.getRole().getPermissions().add(permission);
        }
        return user;
    }

    private String token(AppUser user) {
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        return jwt.generateToken(AppUserPrincipal.from(user));
    }

    private ResultActions as(AppUser user, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token(user)));
    }

    private void error(ResultActions result, int status, String code, String path) throws Exception {
        result.andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").isString()).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test void anonymousAndMalformedJwtAreUnauthorized() throws Exception {
        error(mvc.perform(get("/api/customers")), 401, "UNAUTHORIZED", "/api/customers");
        error(mvc.perform(get("/api/customers").header("Authorization", "Bearer invalid")),
                401, "UNAUTHORIZED", "/api/customers");
        verifyNoInteractions(customerService);
    }

    @Test void disabledUserWithSignedJwtIsUnauthorized() throws Exception {
        AppUser user = actor("CEO", Permissions.CUSTOMERS_READ);
        String signed = token(user);
        user.setActive(false);
        error(mvc.perform(get("/api/customers").header("Authorization", "Bearer " + signed)),
                401, "UNAUTHORIZED", "/api/customers");
        verifyNoInteractions(customerService);
    }

    @Test void jwtUsesCurrentPermissionsAndRequiresReadAuthority() throws Exception {
        AppUser user = actor("SALES_REP");
        String signed = token(user);
        error(mvc.perform(get("/api/customers").header("Authorization", "Bearer " + signed)),
                403, "FORBIDDEN", "/api/customers");
        Permission read = new Permission(); read.setName(Permissions.CUSTOMERS_READ);
        user.getRole().getPermissions().add(read);
        mvc.perform(get("/api/customers").header("Authorization", "Bearer " + signed)).andExpect(status().isOk());
        verify(customerService).findAll(any(), isNull(), eq(0), eq(50));
    }

    @Test void customerPaginationStillClamps() throws Exception {
        as(actor("SALES_REP", Permissions.CUSTOMERS_READ), get("/api/customers?page=-3&size=200"))
                .andExpect(status().isOk());
        verify(customerService).findAll(any(), isNull(), eq(0), eq(100));
    }

    @ParameterizedTest
    @CsvSource({"SALES_REP,201", "MANAGER,403", "CEO,403", "BOOKKEEPER,403"})
    void customerCreationRolesArePreserved(String role, int expected) throws Exception {
        as(actor(role), post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("""
                {"objectName":"Shop","companyName":"Company","pib":"123456789",
                 "phone":"","email":"","address":"Address","googleMapsUrl":"","note":""}
                """)).andExpect(status().is(expected));
        if (expected == 201) verify(customerService).create(any(), any());
        else verifyNoInteractions(customerService);
    }

    @Test void invalidCustomerRetainsSerbianValidationMessage() throws Exception {
        error(as(actor("SALES_REP"), post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("""
                {"objectName":"Shop","companyName":"Company","pib":"12345A789",
                 "phone":"","email":"","address":"Address","googleMapsUrl":"","note":""}
                """)), 400, "VALIDATION_FAILED", "/api/customers");
        as(actor("SALES_REP"), post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("""
                {"objectName":"Shop","companyName":"Company","pib":"12345A789",
                 "phone":"","email":"","address":"Address","googleMapsUrl":"","note":""}
                """))
                .andExpect(jsonPath("$.message").value("PIB mora sadržati tačno 9 cifara."))
                .andExpect(jsonPath("$.fieldErrors.pib").value("PIB mora sadržati tačno 9 cifara."));
        verifyNoInteractions(customerService);
    }

    @Test void unauthorizedActivationAndDeactivationAreRejectedBeforeServices() throws Exception {
        AppUser bookkeeper = actor("BOOKKEEPER");
        for (String path : List.of("/api/employees/5/active", "/api/advertising-materials/5/active")) {
            error(as(bookkeeper, patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")),
                    403, "FORBIDDEN", path);
        }
        error(as(bookkeeper, patch("/api/products/5/deactivate")), 403, "FORBIDDEN", "/api/products/5/deactivate");
        verifyNoInteractions(employeeService, materialService, productService);
    }

    @ParameterizedTest
    @CsvSource({"CEO,201", "BOOKKEEPER,403", "SALES_REP,403", "MANAGER,403"})
    void employeeCreationAccessAndCreatedStatus(String role, int expected) throws Exception {
        as(actor(role), post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"New","lastName":"Employee","email":"new@example.test",
                         "password":"password1","phone":"","role":"BOOKKEEPER","managerId":null}
                        """)).andExpect(status().is(expected));
        if (expected == 201) verify(employeeService).createEmployee(any(), any());
        else verifyNoInteractions(employeeService);
    }

    @ParameterizedTest
    @CsvSource({"CEO,201", "BOOKKEEPER,403", "SALES_REP,403"})
    void multipartProductCreationAccessAndCreatedStatus(String role, int expected) throws Exception {
        MockMultipartFile part = new MockMultipartFile("product", "", "application/json", """
                {"name":"Product","code":"P1","price":12,"volumeLiters":1,
                 "stimulationPerUnit":0,"managerStimulationPerUnit":0}
                """.getBytes(StandardCharsets.UTF_8));
        as(actor(role), multipart("/api/products").file(part)).andExpect(status().is(expected));
        if (expected == 201) verify(productService).createProduct(any(), any(), isNull());
        else verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @CsvSource({"CEO,200", "BOOKKEEPER,200", "MANAGER,403", "SALES_REP,403"})
    void productManagementReadRolesArePreserved(String role, int expected) throws Exception {
        as(actor(role), get("/api/products")).andExpect(status().is(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"active\":null}"})
    void activationMustBeExplicit(String body) throws Exception {
        for (String path : List.of("/api/employees/5/active", "/api/advertising-materials/5/active")) {
            error(as(actor("CEO"), patch(path).contentType(MediaType.APPLICATION_JSON).content(body)),
                    400, "VALIDATION_FAILED", path);
        }
        verifyNoInteractions(employeeService, materialService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void explicitActivationValuesReachServices(boolean active) throws Exception {
        as(actor("CEO"), patch("/api/employees/5/active").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + "}")).andExpect(status().isOk());
        as(actor("CEO"), patch("/api/advertising-materials/5/active").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + "}")).andExpect(status().isOk());
        verify(employeeService).setEmployeeActive(any(), eq(5L), eq(new EmployeeActiveRequest(active)));
        verify(materialService).setAdvertisingMaterialActive(any(), eq(5L), eq(new AdvertisingMaterialActiveRequest(active)));
    }

    @Test void invalidMultipartJsonIsValidatedBeforeProductService() throws Exception {
        MockMultipartFile part = new MockMultipartFile("product", "", "application/json",
                "{\"name\":\"\",\"code\":\"P1\",\"price\":-1}".getBytes(StandardCharsets.UTF_8));
        error(as(actor("CEO"), multipart("/api/products").file(part)), 400, "VALIDATION_FAILED", "/api/products");
        verifyNoInteractions(productService);
    }

    @Test void malformedBodyAndInvalidEmployeeFieldsHaveControlledErrors() throws Exception {
        error(as(actor("CEO"), post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("{")),
                400, "MALFORMED_BODY", "/api/employees");
        error(as(actor("CEO"), post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("{}")),
                400, "VALIDATION_FAILED", "/api/employees");
        verifyNoInteractions(employeeService);
    }

    @Test void loginValidationAndBadCredentialsPreserveMessages() throws Exception {
        error(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")),
                400, "VALIDATION_FAILED", "/api/auth/login");
        verifyNoInteractions(authService);
        when(authService.login(any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("internal"));
        error(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.test\",\"password\":\"bad\"}")), 401, "UNAUTHORIZED", "/api/auth/login");
    }

    @Test void duplicateEmployeeEmailIsAnHttpConflict() throws Exception {
        when(employeeService.createEmployee(any(), any()))
                .thenThrow(new com.ognjen.diplomski.exception.EmailAlreadyExistsException());
        error(as(actor("CEO"), post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"New","lastName":"Employee","email":"used@example.test",
                 "password":"password1","role":"BOOKKEEPER"}
                """)), 409, "CONFLICT", "/api/employees");
    }

    @Test void createdResponsesKeepExistingEmployeeAndProductBodies() throws Exception {
        when(employeeService.createEmployee(any(), any())).thenReturn(new EmployeeResponse(
                5L, "New", "Employee", "new@example.test", null, "BOOKKEEPER", true, null));
        as(actor("CEO"), post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"New","lastName":"Employee","email":"new@example.test",
                 "password":"password1","role":"BOOKKEEPER"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.role").value("BOOKKEEPER")).andExpect(jsonPath("$.active").value(true));
        when(productService.createProduct(any(), any(), any())).thenReturn(new ProductManagementResponse(
                6L, "Product", "P1", null, java.math.BigDecimal.TEN, java.math.BigDecimal.ONE,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, true));
        MockMultipartFile part = new MockMultipartFile("product", "", "application/json", """
                {"name":"Product","code":"P1","price":10,"volumeLiters":1,
                 "stimulationPerUnit":0,"managerStimulationPerUnit":0}
                """.getBytes(StandardCharsets.UTF_8));
        as(actor("CEO"), multipart("/api/products").file(part))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.code").value("P1")).andExpect(jsonPath("$.active").value(true));
    }

    @Test void invalidQueryAndPathParametersAreClientErrors() throws Exception {
        AppUser rep = actor("SALES_REP", Permissions.SALES_REPORTS_READ, Permissions.CUSTOMERS_READ);
        for (String query : List.of("year=2026&month=13", "year=1999&month=1", "year=x&month=1", "year=2026")) {
            error(as(rep, get("/api/sales-reports/monthly?" + query)),
                    400, "INVALID_PARAMETER", "/api/sales-reports/monthly");
        }
        error(as(rep, get("/api/customers/not-a-number")), 400, "INVALID_PARAMETER", "/api/customers/not-a-number");
        error(as(actor("SALES_REP"), patch("/api/requisitions/42/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNKNOWN\"}")),
                400, "MALFORMED_BODY", "/api/requisitions/42/status");
    }

    @Test void deliberateInputErrorsAndUnexpectedExceptionsAreDistinguished() throws Exception {
        when(customerService.findAll(any(), any(), anyInt(), anyInt()))
                .thenThrow(new InvalidRequestException("Invalid search"));
        error(as(actor("CEO", Permissions.CUSTOMERS_READ), get("/api/customers")),
                400, "INVALID_REQUEST", "/api/customers");
        doThrow(new IllegalArgumentException("internal SQL credentials must not leak"))
                .when(customerService).findAll(any(), any(), anyInt(), anyInt());
        error(as(actor("CEO", Permissions.CUSTOMERS_READ), get("/api/customers")),
                500, "INTERNAL_ERROR", "/api/customers");
        as(actor("CEO", Permissions.CUSTOMERS_READ), get("/api/customers"))
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred"));
    }

    @Test void representativesCannotRequestOtherRepresentativesReports() throws Exception {
        error(as(actor("SALES_REP", Permissions.SALES_REPORTS_READ),
                get("/api/sales-reports/monthly?year=2026&month=6&salesRepId=99")),
                403, "FORBIDDEN", "/api/sales-reports/monthly");
        verifyNoInteractions(sales);
    }

    @Test void managerCannotSelectAnotherManagerOrUnrelatedRepresentative() throws Exception {
        AppUser manager = actor("MANAGER", Permissions.SALES_REPORTS_READ);
        error(as(manager, get("/api/sales-reports/ytd?year=2026&managerId=9")),
                403, "FORBIDDEN", "/api/sales-reports/ytd");
        error(as(manager, get("/api/sales-reports/ytd?year=2026&salesRepId=99")),
                404, "NOT_FOUND", "/api/sales-reports/ytd");
        verify(users).findByIdAndManagerIdAndRoleNameIgnoreCase(99L, 8L, "SALES_REP");
        verifyNoInteractions(sales);
    }

    @Test void ceoJsonReportScopesAndRepresentativeMetadataStayUnchanged() throws Exception {
        AppUser ceo = actor("CEO", Permissions.SALES_REPORTS_READ);
        as(ceo, get("/api/sales-reports/monthly?year=2026&month=6"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.salesRep").isEmpty());
        verify(sales).findAllForReport(eq(RequisitionType.NORMAL), any(), any());
        when(users.findByIdAndRoleNameIgnoreCase(8L, "MANAGER")).thenReturn(Optional.of(actor("MANAGER")));
        as(ceo, get("/api/sales-reports/monthly?year=2026&month=6&managerId=8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.salesRep").isEmpty());
        verify(sales).findTeamProductAggregates(eq(8L), eq(RequisitionType.NORMAL), any(), any());
        when(users.findByIdAndRoleNameIgnoreCase(25L, "SALES_REP")).thenReturn(Optional.of(actor("SALES_REP")));
        as(ceo, get("/api/sales-reports/monthly?year=2026&month=6&salesRepId=25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.salesRep.id").value(25))
                .andExpect(jsonPath("$.salesRep.firstName").value("Ana"));
    }

    @Test void invalidReportExportScopeNeverBuildsAWorkbook() throws Exception {
        error(as(actor("MANAGER", Permissions.SALES_REPORTS_READ),
                get("/api/sales-reports/gratis-ytd/export?year=2026&managerId=99")),
                403, "FORBIDDEN", "/api/sales-reports/gratis-ytd/export");
        verifyNoInteractions(sales);
        verify(users, never()).findByIdAndRoleNameIgnoreCase(99L, "MANAGER");
    }

    private Requisition requisition(RequisitionStatus status) {
        AppUser rep = actor("SALES_REP");
        rep.setManager(actor("MANAGER"));
        Customer customer = new Customer(); customer.setId(10L); customer.setEmail("customer@example.test");
        customer.setSalesRep(rep);
        Requisition req = new Requisition(); req.setId(42L); req.setSalesRep(rep); req.setCustomer(customer);
        req.setType(RequisitionType.NORMAL); req.setStatus(status); req.setCreatedAt(OffsetDateTime.now());
        return req;
    }

    @Test void salesRepCannotApproveAndUnrelatedManagerCannotApprove() throws Exception {
        when(requisitions.findForUpdate(42L)).thenReturn(Optional.of(requisition(RequisitionStatus.SUBMITTED)));
        for (AppUser actor : List.of(actor("SALES_REP"), user(99L, "MANAGER", "Other", "Manager"))) {
            error(as(actor, patch("/api/requisitions/42/status").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"APPROVED\"}")), 409, "CONFLICT", "/api/requisitions/42/status");
        }
        verify(requisitions, never()).saveAndFlush(any());
    }

    @Test void requisitionReadOwnershipAndNotFoundAreEnforced() throws Exception {
        Requisition req = requisition(RequisitionStatus.SUBMITTED);
        when(requisitions.findDetailedById(42L)).thenReturn(Optional.of(req));
        AppUser other = actor("SALES_REP", Permissions.REQUISITIONS_READ); other.setId(99L);
        error(as(other, get("/api/requisitions/42")),
                404, "NOT_FOUND", "/api/requisitions/42");
        AppUser otherManager = actor("MANAGER", Permissions.REQUISITIONS_READ); otherManager.setId(99L);
        error(as(otherManager, get("/api/requisitions/42")),
                404, "NOT_FOUND", "/api/requisitions/42");
        error(as(actor("CEO", Permissions.REQUISITIONS_READ), get("/api/requisitions/404")),
                404, "NOT_FOUND", "/api/requisitions/404");
        verifyNoInteractions(pdf, email);
    }

    @Test void staleRevisionAndLockContentionRemainConflicts() throws Exception {
        when(requisitions.findForUpdate(42L)).thenReturn(Optional.of(requisition(RequisitionStatus.SUBMITTED)));
        error(as(actor("MANAGER"), patch("/api/requisitions/42/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\",\"expectedRevision\":\"stale\"}")),
                409, "CONFLICT", "/api/requisitions/42/status");
        when(requisitions.findForUpdate(42L)).thenThrow(new PessimisticLockingFailureException("database details"));
        error(as(actor("MANAGER"), patch("/api/requisitions/42/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}")), 409, "CONFLICT", "/api/requisitions/42/status");
        verify(requisitions, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SALES_REP", "MANAGER", "CEO"})
    void onlyBookkeeperCanReachEmailOperation(String role) throws Exception {
        error(as(actor(role), post("/api/requisitions/42/email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipient\":\"CUSTOMER\"}")), 403, "FORBIDDEN", "/api/requisitions/42/email");
        verifyNoInteractions(requisitions, pdf, email);
    }

    @Test void bookkeeperEmailRunsRealWorkflowWithMockedDelivery() throws Exception {
        Requisition req = requisition(RequisitionStatus.COMPLETED);
        when(requisitions.findDetailedById(42L)).thenReturn(Optional.of(req));
        byte[] attachment = {37, 80, 68, 70}; when(pdf.createPdf(req)).thenReturn(attachment);
        as(actor("BOOKKEEPER"), post("/api/requisitions/42/email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipient\":\"CUSTOMER\"}")).andExpect(status().isNoContent());
        verify(email).sendPdf(req, "customer@example.test", attachment);
    }

    record ExportCase(String role, Long repId, Long managerId, String label, String type) {}

    static Stream<ExportCase> exportCases() {
        return Stream.of(
                new ExportCase("CEO", null, null, "Cela kompanija", ""),
                new ExportCase("CEO", null, 8L, "Tim menadžera Mira Manager", ""),
                new ExportCase("CEO", 25L, null, "Ana Rep", ""),
                new ExportCase("CEO", 25L, 8L, "Ana Rep", ""),
                new ExportCase("MANAGER", null, null, "Tim menadžera Mira Manager", ""),
                new ExportCase("MANAGER", 25L, null, "Ana Rep", ""),
                new ExportCase("SALES_REP", null, null, "Ana Rep", ""),
                new ExportCase("SALES_REP", 25L, null, "Ana Rep", "")
        ).flatMap(scope -> Stream.of("monthly", "ytd", "gratis-monthly", "gratis-ytd")
                .map(type -> new ExportCase(scope.role(), scope.repId(), scope.managerId(), scope.label(), type)));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("exportCases")
    void exportsUseAuthorizedScopeForWorkbookAndFilename(ExportCase scope) throws Exception {
        AppUser manager = actor("MANAGER"); AppUser rep = actor("SALES_REP"); rep.setManager(manager);
        when(users.findByIdAndRoleNameIgnoreCase(8L, "MANAGER")).thenReturn(Optional.of(manager));
        when(users.findByIdAndRoleNameIgnoreCase(25L, "SALES_REP")).thenReturn(Optional.of(rep));
        when(users.findByIdAndManagerIdAndRoleNameIgnoreCase(25L, 8L, "SALES_REP")).thenReturn(Optional.of(rep));
        String query = "?year=2026" + (scope.type().contains("monthly") ? "&month=6" : "")
                + (scope.repId() == null ? "" : "&salesRepId=" + scope.repId())
                + (scope.managerId() == null ? "" : "&managerId=" + scope.managerId());
        var response = as(actor(scope.role(), Permissions.SALES_REPORTS_READ),
                get("/api/sales-reports/" + scope.type() + "/export" + query))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        String filename = ContentDisposition.parse(response.getHeader("Content-Disposition")).getFilename();
        assertNotNull(filename);
        assertTrue(filename.startsWith(scope.label().replace(' ', '-') + "-"), filename);
        assertEquals(response.getContentAsByteArray().length, response.getContentLength());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            StringBuilder text = new StringBuilder(); DataFormatter format = new DataFormatter();
            workbook.forEach(sheet -> sheet.forEach(row -> row.forEach(cell -> text.append(format.formatCellValue(cell)).append('\n'))));
            assertTrue(text.toString().contains(scope.label()), text.toString());
            assertEquals("Izveštaj za", workbook.getSheetAt(0).getRow(3).getCell(0).getStringCellValue());
            assertEquals(scope.label(), workbook.getSheetAt(0).getRow(3).getCell(1).getStringCellValue());
            if (scope.role().equals("CEO")) assertFalse(text.toString().contains("Ceo Owner"));
        }
        if (scope.role().equals("CEO") && scope.managerId() != null && scope.repId() == null
                && !scope.type().startsWith("gratis")) {
            verify(sales).findTeamProductAggregates(eq(8L), eq(RequisitionType.NORMAL), any(), any());
        }
    }
}
