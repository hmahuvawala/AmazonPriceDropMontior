package com.amazonpricemonitor.repository;

import com.amazonpricemonitor.domain.PriceCheck;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceCheckRepository extends JpaRepository<PriceCheck, Long> {

    List<PriceCheck> findByProductIdOrderByCreatedAtAsc(Long productId, Pageable pageable);

    Optional<PriceCheck> findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(Long productId);

    /**
     * All checks for a product within a recent window, oldest first. Used to build
     * 7-day price-trend stats; callers filter to {@code success=true} in code so
     * they can also see the failure count when narrating the window.
     */
    List<PriceCheck> findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            Long productId, Instant since);
}
