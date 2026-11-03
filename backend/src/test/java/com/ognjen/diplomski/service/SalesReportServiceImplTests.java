package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.MonthlySalesReportResponse;
import com.ognjen.diplomski.dto.MonthlyGratisReportResponse;
import com.ognjen.diplomski.dto.GratisYtdReportResponse;
import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.PackageType;
import com.ognjen.diplomski.repository.SalesReportRepository;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.SalesReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesReportServiceImplTests {

    @Test
    void ceoCanViewReportsWithoutManagerOrSalesRepresentativeSelection() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        when(repository.findAllForReport(eq(RequisitionType.NORMAL), any(), any()))
                .thenReturn(List.of());
        when(repository.findAllForReport(eq(RequisitionType.GRATIS), any(), any()))
                .thenReturn(List.of());
        AppUserRepository users = mock(AppUserRepository.class);
        SalesReportServiceImpl service = new SalesReportServiceImpl(repository, users);

        MonthlySalesReportResponse response = service.getMonthlyReport(
                ceoPrincipal(), 2026, 6, null, null
        );
        service.getYtdReport(ceoPrincipal(), 2026, null, null);
        service.getMonthlyGratisReport(ceoPrincipal(), 2026, 6, null, null);
        service.getGratisYtdReport(ceoPrincipal(), 2026, null, null);

        assertEquals(null, response.salesRep());
        verify(repository, org.mockito.Mockito.times(2))
                .findAllForReport(eq(RequisitionType.NORMAL), any(), any());
        verify(repository, org.mockito.Mockito.times(2))
                .findAllForReport(eq(RequisitionType.GRATIS), any(), any());
        verify(users, never()).findByIdAndRoleNameIgnoreCase(any(), any());
    }

    @Test
    void managerComparisonUsesOneTeamAggregationAndSeparateStimulationRates() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        SalesReportRepository.TeamSalesComparisonProjection first = comparisonRow(
                25L, "Ana", "Anić", "10", "2", "3", "1", "80.00", "30.00"
        );
        SalesReportRepository.TeamSalesComparisonProjection second = comparisonRow(
                26L, "Marko", "Marković", "4", "1", "2", "0", "35.00", "12.00"
        );
        when(repository.findTeamSalesComparison(eq(8L), any(), any()))
                .thenReturn(List.of(first, second));
        SalesReportServiceImpl service = new SalesReportServiceImpl(
                repository,
                mock(AppUserRepository.class)
        );

        var response = service.getTeamComparison(managerPrincipal(), 2026, 9);

        assertEquals(2, response.salesRepresentatives().size());
        assertEquals(new BigDecimal("80.00"), response.salesRepresentatives().getFirst().stimulation());
        assertEquals(new BigDecimal("3"), response.salesRepresentatives().getFirst().gratisBottles());
        assertEquals(new BigDecimal("1"), response.salesRepresentatives().getFirst().gratisBalloons());
        assertEquals(new BigDecimal("42.00"), response.managerStimulation());
        verify(repository).findTeamSalesComparison(eq(8L), any(), any());
    }

    @Test
    void ceoComparisonUsesTheSelectedValidatedManager() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        AppUser manager = new AppUser();
        manager.setId(8L);
        when(appUserRepository.findByIdAndRoleNameIgnoreCase(8L, "MANAGER"))
                .thenReturn(Optional.of(manager));
        when(repository.findTeamSalesComparison(eq(8L), any(), any())).thenReturn(List.of());

        var response = new SalesReportServiceImpl(repository, appUserRepository)
                .getTeamComparison(ceoPrincipal(), 2026, 9, 8L);

        assertEquals(0, response.salesRepresentatives().size());
        verify(repository).findTeamSalesComparison(eq(8L), any(), any());
    }

    @Test
    void ceoComparesManagersUsingAggregatedManagerStimulation() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        SalesReportRepository.ManagerSalesComparisonProjection manager = mock(
                SalesReportRepository.ManagerSalesComparisonProjection.class
        );
        when(manager.getManagerId()).thenReturn(8L);
        when(manager.getFirstName()).thenReturn("Milan");
        when(manager.getLastName()).thenReturn("Milić");
        when(manager.getBottlesSold()).thenReturn(new BigDecimal("20"));
        when(manager.getBalloonsSold()).thenReturn(new BigDecimal("4"));
        when(manager.getGratisBottles()).thenReturn(new BigDecimal("6"));
        when(manager.getGratisBalloons()).thenReturn(new BigDecimal("2"));
        when(manager.getStimulation()).thenReturn(new BigDecimal("75.00"));
        when(repository.findManagersSalesComparison(any(), any())).thenReturn(List.of(manager));

        var response = new SalesReportServiceImpl(repository, mock(AppUserRepository.class))
                .getManagersComparison(ceoPrincipal(), 2026, 9);

        assertEquals(1, response.managers().size());
        assertEquals(8L, response.managers().getFirst().managerId());
        assertEquals(new BigDecimal("75.00"), response.managers().getFirst().stimulation());
        assertEquals(new BigDecimal("6"), response.managers().getFirst().gratisBottles());
        assertEquals(new BigDecimal("2"), response.managers().getFirst().gratisBalloons());
        verify(repository).findManagersSalesComparison(any(), any());
    }

    @Test
    void managerCanSelectOnlyAnActiveSalesRepresentativeFromTheirTeam() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        AppUser teamMember = new AppUser();
        teamMember.setId(25L);
        teamMember.setFirstName("Ana");
        teamMember.setLastName("Anić");
        when(appUserRepository.findByIdAndManagerIdAndRoleNameIgnoreCase(
                25L, 8L, "SALES_REP"
        )).thenReturn(Optional.of(teamMember));
        Product product = product(10L, "P-1", "Proizvod", "products/p-1.webp", "0.750");
        product.setStimulationPerUnit(new BigDecimal("8.00"));
        product.setManagerStimulationPerUnit(new BigDecimal("3.00"));
        when(repository.findForReport(eq(25L), eq(RequisitionType.NORMAL), any(), any()))
                .thenReturn(List.of(requisition(product, "2.00", "8.00")));
        SalesReportServiceImpl service = new SalesReportServiceImpl(repository, appUserRepository);

        MonthlySalesReportResponse response = service.getMonthlyReport(managerPrincipal(), 2026, 6, 25L);

        assertEquals(25L, response.salesRep().id());
        assertEquals("Ana", response.salesRep().firstName());
        assertEquals(new BigDecimal("3.00"), response.products().getFirst().incentivePerItem());
        assertEquals(new BigDecimal("6.00"), response.products().getFirst().totalIncentive());
        assertEquals(new BigDecimal("6.00"), response.summary().totalIncentive());
        verify(repository).findForReport(eq(25L), eq(RequisitionType.NORMAL), any(), any());

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.getMonthlyReport(managerPrincipal(), 2026, 6, 99L)
        );
    }

    @Test
    void managerMonthlyReportAggregatesTeamUsingManagerStimulation() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        SalesReportRepository.TeamProductSalesProjection row = mock(
                SalesReportRepository.TeamProductSalesProjection.class
        );
        when(row.getProductCode()).thenReturn("P-1");
        when(row.getProductName()).thenReturn("Proizvod");
        when(row.getPictureUrl()).thenReturn("products/p-1.webp");
        when(row.getUnits()).thenReturn(new BigDecimal("10"));
        when(row.getVolumeLiters()).thenReturn(new BigDecimal("0.750"));
        when(row.getPackageType()).thenReturn(PackageType.BOTTLE);
        when(row.getIncentivePerItem()).thenReturn(new BigDecimal("3.00"));
        when(row.getTotalIncentive()).thenReturn(new BigDecimal("30.00"));
        when(repository.findTeamProductAggregates(eq(8L), eq(RequisitionType.NORMAL), any(), any()))
                .thenReturn(List.of(row));
        SalesReportServiceImpl service = new SalesReportServiceImpl(repository, mock(AppUserRepository.class));

        MonthlySalesReportResponse response = service.getMonthlyReport(managerPrincipal(), 2026, 6, null);

        assertEquals(null, response.salesRep());
        assertEquals(0, new BigDecimal("10").compareTo(response.summary().totalBottles()));
        assertEquals(new BigDecimal("7.500"), response.summary().totalLiters());
        assertEquals(new BigDecimal("30.00"), response.summary().totalIncentive());
        verify(repository, never()).findForReport(any(), any(), any(), any());
    }

    @Test
    void monthlyReportAggregatesProductsAndUsesCurrentProductStimulation() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        SalesReportServiceImpl service = new SalesReportServiceImpl(repository, mock(AppUserRepository.class));
        Product product = product(10L, "P-1", "Proizvod", "products/p-1.webp", "0.750");
        product.setStimulationPerUnit(new BigDecimal("8.00"));
        Product jug = product(11L, "P-2", "Kanister", "products/p-2.webp", "5.000");
        jug.setStimulationPerUnit(new BigDecimal("6.00"));
        jug.setPackageType(PackageType.JUG);
        Requisition first = requisition(product, "2.00", "10.00");
        Requisition second = requisition(product, "3.00", "12.00");
        Requisition third = requisition(jug, "4.00", "5.00");
        when(repository.findForReport(eq(4L), eq(RequisitionType.NORMAL), any(), any()))
                .thenReturn(List.of(first, second, third));

        MonthlySalesReportResponse response = service.getMonthlyReport(principal(), 2026, 6, null);

        assertEquals(new BigDecimal("9"), response.summary().totalItems());
        assertEquals(new BigDecimal("5"), response.summary().totalBottles());
        assertEquals(new BigDecimal("4"), response.summary().totalJugs());
        assertEquals(new BigDecimal("23.750"), response.summary().totalLiters());
        assertEquals(new BigDecimal("64.00"), response.summary().totalIncentive());
        assertEquals(2, response.products().size());
        assertEquals(new BigDecimal("8.00"), response.products().getFirst().incentivePerItem());
        assertEquals(new BigDecimal("40.00"), response.products().getFirst().totalIncentive());
        assertEquals(PackageType.BOTTLE, response.products().getFirst().packageType());
        verify(repository).findForReport(eq(4L), eq(RequisitionType.NORMAL), any(), any());
    }

    @Test
    void gratisYtdReportCountsBottlesAndJugsWithoutMixingNormalSales() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        SalesReportServiceImpl service = new SalesReportServiceImpl(repository, mock(AppUserRepository.class));
        Product bottle = product(10L, "P-1", "Boca", "products/p-1.webp", "0.750");
        Product jug = product(11L, "P-2", "Balon", "products/p-2.webp", "5.000");
        jug.setPackageType(PackageType.JUG);
        Requisition giftedBottles = requisition(bottle, "2.00", "0.00");
        giftedBottles.setType(RequisitionType.GRATIS);
        giftedBottles.setGratisReason(GratisReason.GIFT);
        Requisition giftedJug = requisition(jug, "3.00", "0.00");
        giftedJug.setType(RequisitionType.GRATIS);
        giftedJug.setGratisReason(GratisReason.GIFT);
        when(repository.findForReport(eq(4L), eq(RequisitionType.GRATIS), any(), any()))
                .thenReturn(List.of(giftedBottles, giftedJug));

        GratisYtdReportResponse response = service.getGratisYtdReport(principal(), 2026);

        assertEquals(new BigDecimal("2"), response.summary().totalBottles());
        assertEquals(new BigDecimal("3"), response.summary().totalJugs());
        assertEquals(new BigDecimal("16.500"), response.summary().totalLiters());
        assertEquals(2, response.products().size());
        assertEquals(1, response.reasons().size());
        assertEquals(GratisReason.GIFT, response.reasons().getFirst().reason());
        assertEquals(new BigDecimal("16.500"), response.reasons().getFirst().totalLiters());
        verify(repository).findForReport(eq(4L), eq(RequisitionType.GRATIS), any(), any());
    }

    @Test
    void monthlyGratisReportUsesSelectedMonthAndAggregatesGiftedPackages() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        SalesReportServiceImpl service = new SalesReportServiceImpl(repository, mock(AppUserRepository.class));
        Product bottle = product(10L, "P-1", "Boca", "products/p-1.webp", "0.750");
        Product jug = product(11L, "P-2", "Balon", "products/p-2.webp", "5.000");
        jug.setPackageType(PackageType.JUG);
        Requisition giftedBottles = requisition(bottle, "2.00", "0.00");
        giftedBottles.setType(RequisitionType.GRATIS);
        giftedBottles.setGratisReason(GratisReason.GIFT);
        Requisition giftedJug = requisition(jug, "3.00", "0.00");
        giftedJug.setType(RequisitionType.GRATIS);
        giftedJug.setGratisReason(GratisReason.GIFT);
        when(repository.findForReport(eq(4L), eq(RequisitionType.GRATIS), any(), any()))
                .thenReturn(List.of(giftedBottles, giftedJug));

        MonthlyGratisReportResponse response = service.getMonthlyGratisReport(principal(), 2026, 6);

        assertEquals(2026, response.year());
        assertEquals(6, response.month());
        assertEquals(new BigDecimal("2"), response.summary().totalBottles());
        assertEquals(new BigDecimal("3"), response.summary().totalJugs());
        assertEquals(new BigDecimal("16.500"), response.summary().totalLiters());
        assertEquals(2, response.products().size());
        assertEquals(1, response.reasons().size());
        verify(repository).findForReport(eq(4L), eq(RequisitionType.GRATIS), any(), any());
    }

    @Test
    void ceoManagerReportScopesGratisDataToTheSelectedManager() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        AppUser manager = new AppUser();
        manager.setId(8L);
        when(appUserRepository.findByIdAndRoleNameIgnoreCase(8L, "MANAGER"))
                .thenReturn(Optional.of(manager));
        when(repository.findTeamForReport(eq(8L), eq(RequisitionType.GRATIS), any(), any()))
                .thenReturn(List.of());

        new SalesReportServiceImpl(repository, appUserRepository)
                .getMonthlyGratisReport(ceoPrincipal(), 2026, 6, 8L);

        verify(repository).findTeamForReport(eq(8L), eq(RequisitionType.GRATIS), any(), any());
    }

    @Test
    void managerGratisMonthlyAndYtdReportsUseTheirWholeTeam() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        when(repository.findTeamForReport(eq(8L), eq(RequisitionType.GRATIS), any(), any()))
                .thenReturn(List.of());
        SalesReportServiceImpl service = new SalesReportServiceImpl(
                repository, mock(AppUserRepository.class)
        );

        service.getMonthlyGratisReport(managerPrincipal(), 2026, 6);
        service.getGratisYtdReport(managerPrincipal(), 2026);

        verify(repository, org.mockito.Mockito.times(2)).findTeamForReport(
                eq(8L), eq(RequisitionType.GRATIS), any(), any()
        );
        verify(repository, never()).findForReport(any(), any(), any(), any());
    }

    @Test
    void managerCanSelectOwnTeamMemberForGratisReportButNotCrossTeamMember() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser teamMember = new AppUser();
        teamMember.setId(25L); teamMember.setFirstName("Ana"); teamMember.setLastName("Anić");
        when(users.findByIdAndManagerIdAndRoleNameIgnoreCase(25L, 8L, "SALES_REP"))
                .thenReturn(Optional.of(teamMember));
        when(repository.findForReport(eq(25L), eq(RequisitionType.GRATIS), any(), any()))
                .thenReturn(List.of());
        SalesReportServiceImpl service = new SalesReportServiceImpl(repository, users);

        service.getMonthlyGratisReport(managerPrincipal(), 2026, 6, 25L, null);
        verify(repository).findForReport(eq(25L), eq(RequisitionType.GRATIS), any(), any());

        var exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.getGratisYtdReport(managerPrincipal(), 2026, 99L, null)
        );
        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void ceoCanSelectSalesRepresentativeOnlyWithinSelectedManagerTeamForGratisReport() {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser manager = new AppUser(); manager.setId(8L);
        AppUser teamMember = new AppUser();
        teamMember.setId(25L); teamMember.setFirstName("Ana"); teamMember.setLastName("Anić");
        when(users.findByIdAndRoleNameIgnoreCase(8L, "MANAGER"))
                .thenReturn(Optional.of(manager));
        when(users.findByIdAndManagerIdAndRoleNameIgnoreCase(25L, 8L, "SALES_REP"))
                .thenReturn(Optional.of(teamMember));
        when(repository.findForReport(eq(25L), eq(RequisitionType.GRATIS), any(), any()))
                .thenReturn(List.of());
        SalesReportServiceImpl service = new SalesReportServiceImpl(repository, users);

        service.getGratisYtdReport(ceoPrincipal(), 2026, 25L, 8L);
        verify(repository).findForReport(eq(25L), eq(RequisitionType.GRATIS), any(), any());

        var exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.getMonthlyGratisReport(ceoPrincipal(), 2026, 6, 99L, 8L)
        );
        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void companyMonthlyAndYtdIncludeBothIncentivesAndExcelMatches() throws Exception {
        SalesReportRepository repository = mock(SalesReportRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        Product product = product(1L, "TEST", "Test", "/test.png", "1.5");
        product.setStimulationPerUnit(new BigDecimal("20"));
        product.setManagerStimulationPerUnit(new BigDecimal("5"));
        when(repository.findAllForReport(eq(RequisitionType.NORMAL), any(), any()))
                .thenReturn(List.of(requisition(product, "10", "20"), requisition(product, "2", "20")));
        SalesReportServiceImpl service = new SalesReportServiceImpl(repository, users);
        var monthly = service.getMonthlyReport(ceoPrincipal(), 2026, 6, null, null);
        var ytd = service.getYtdReport(ceoPrincipal(), 2026, null, null);
        for (var summary : List.of(monthly.summary(), ytd.summary())) {
            assertEquals(new BigDecimal("240.00"), summary.salesRepIncentive());
            assertEquals(new BigDecimal("60.00"), summary.managerIncentive());
            assertEquals(new BigDecimal("300.00"), summary.totalIncentive());
        }
        assertEquals(new BigDecimal("300.00"), monthly.products().getFirst().totalIncentive());
        assertEquals(new BigDecimal("300.00"), ytd.products().getFirst().totalIncentive());
        assertEquals(new BigDecimal("300.00"), ytd.monthlyIncentives().stream()
                .map(com.ognjen.diplomski.dto.MonthlyIncentiveResponse::totalIncentive)
                .reduce(new BigDecimal("0.00"), BigDecimal::add));
        var excel = new com.ognjen.diplomski.service.impl.SalesReportExcelServiceImpl();
        for (byte[] bytes : List.of(excel.createMonthlyWorkbook(monthly, "CEO", ""),
                excel.createYtdWorkbook(ytd, "CEO", ""))) {
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
                var sheet = workbook.getSheetAt(0);
                assertEquals(300d, sheet.getRow(9).getCell(1).getNumericCellValue());
                assertEquals(240d, sheet.getRow(10).getCell(1).getNumericCellValue());
                assertEquals(60d, sheet.getRow(11).getCell(1).getNumericCellValue());
                assertEquals(300d, sheet.getRow(14).getCell(6).getNumericCellValue());
            }
        }
        verify(repository, never()).findTeamProductAggregates(any(), any(), any(), any());
    }

    private static Product product(Long id, String code, String name, String pictureUrl, String volumeLiters) {
        Product product = new Product();
        product.setId(id);
        product.setCode(code);
        product.setName(name);
        product.setPictureUrl(pictureUrl);
        product.setVolumeLiters(new BigDecimal(volumeLiters));
        product.setPackageType(PackageType.BOTTLE);
        return product;
    }

    private static Requisition requisition(Product product, String quantity, String incentivePerUnit) {
        Requisition requisition = new Requisition();
        requisition.setType(RequisitionType.NORMAL);
        requisition.setCreatedAt(OffsetDateTime.parse("2026-06-15T10:00:00+02:00"));
        RequisitionItem item = new RequisitionItem();
        item.setRequisition(requisition);
        item.setProduct(product);
        item.setQuantity(new BigDecimal(quantity));
        item.setStimulationPerUnit(new BigDecimal(incentivePerUnit));
        requisition.getItems().add(item);
        return requisition;
    }

    private static AppUserPrincipal principal() {
        return new AppUserPrincipal(
                4L,
                "sales@example.com",
                "password-hash",
                "Test",
                "Sales",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_SALES_REP"))
        );
    }

    private static AppUserPrincipal managerPrincipal() {
        return new AppUserPrincipal(
                8L,
                "manager@example.com",
                "password-hash",
                "Test",
                "Manager",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );
    }

    private static AppUserPrincipal ceoPrincipal() {
        return new AppUserPrincipal(
                1L,
                "ceo@example.com",
                "password-hash",
                "Test",
                "CEO",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_CEO"))
        );
    }

    private static SalesReportRepository.TeamSalesComparisonProjection comparisonRow(
            Long id,
            String firstName,
            String lastName,
            String bottles,
            String balloons,
            String gratisBottles,
            String gratisBalloons,
            String stimulation,
            String managerStimulation
    ) {
        SalesReportRepository.TeamSalesComparisonProjection row = mock(
                SalesReportRepository.TeamSalesComparisonProjection.class
        );
        when(row.getSalesRepId()).thenReturn(id);
        when(row.getFirstName()).thenReturn(firstName);
        when(row.getLastName()).thenReturn(lastName);
        when(row.getBottlesSold()).thenReturn(new BigDecimal(bottles));
        when(row.getBalloonsSold()).thenReturn(new BigDecimal(balloons));
        when(row.getGratisBottles()).thenReturn(new BigDecimal(gratisBottles));
        when(row.getGratisBalloons()).thenReturn(new BigDecimal(gratisBalloons));
        when(row.getStimulation()).thenReturn(new BigDecimal(stimulation));
        when(row.getManagerStimulation()).thenReturn(new BigDecimal(managerStimulation));
        return row;
    }
}
