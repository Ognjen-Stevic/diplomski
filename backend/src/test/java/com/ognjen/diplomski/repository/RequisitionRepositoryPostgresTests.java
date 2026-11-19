package com.ognjen.diplomski.repository;

import static com.ognjen.diplomski.entity.enums.RequisitionStatus.APPROVED;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RequisitionRepositoryPostgresTests {

    private static final OffsetDateTime START =
            OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime END =
            OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final PageRequest PAGE = PageRequest.of(
            0, 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    @Autowired
    private RequisitionRepository repository;

    @Test
    void allRoleQueriesAcceptDisabledSearchOnPostgres() {
        repository.findMonthlyPage(START, END, null, null, false, "", PAGE);
        repository.findMonthlyPageBySalesRep(-1L, START, END, null, null, false, "", PAGE);
        repository.findMonthlyPageForManagerTeam(-1L, START, END, null, null, false, "", PAGE);
        repository.findMonthlyPageByStatuses(
                Set.of(APPROVED), START, END, null, null, false, "", PAGE);
    }

    @Test
    void allRoleQueriesAcceptTypedNonEmptySearchPatternOnPostgres() {
        repository.findMonthlyPage(START, END, null, null, true, "%shop%", PAGE);
        repository.findMonthlyPageBySalesRep(-1L, START, END, null, null, true, "%shop%", PAGE);
        repository.findMonthlyPageForManagerTeam(-1L, START, END, null, null, true, "%shop%", PAGE);
        repository.findMonthlyPageByStatuses(
                Set.of(APPROVED), START, END, null, null, true, "%shop%", PAGE);
    }
}
