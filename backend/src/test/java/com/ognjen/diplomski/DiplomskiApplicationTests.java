package com.ognjen.diplomski;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.ognjen.diplomski.repository.*;

// Context smoke test only: never require a live database or initialize production persistence.
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "logging.level.root=WARN", "logging.level.org.springframework.web=WARN", "debug=false"
})
@MockitoBean(types = {AppUserRepository.class, RoleRepository.class, CustomerRepository.class,
        ProductRepository.class, RequisitionRepository.class, SalesReportRepository.class,
        GoodsRequestRepository.class, AdvertisingMaterialRepository.class,
        AdvertisingMaterialDistributionRepository.class})
class DiplomskiApplicationTests {

	@Test
	void contextLoads() {
	}

}
