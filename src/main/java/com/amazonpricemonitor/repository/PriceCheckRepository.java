package com.amazonpricemonitor.repository;

import com.amazonpricemonitor.domain.PriceCheck;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceCheckRepository extends JpaRepository<PriceCheck, Long> {

    List<PriceCheck> findByProductIdOrderByCreatedAtAsc(Long productId, Pageable pageable);

    Optional<PriceCheck> findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(Long productId);
}
