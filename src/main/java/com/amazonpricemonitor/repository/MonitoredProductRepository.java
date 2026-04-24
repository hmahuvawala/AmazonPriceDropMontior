package com.amazonpricemonitor.repository;

import com.amazonpricemonitor.domain.MonitoredProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredProductRepository extends JpaRepository<MonitoredProduct, Long> {

    List<MonitoredProduct> findByActiveTrueOrderByIdAsc();
}
