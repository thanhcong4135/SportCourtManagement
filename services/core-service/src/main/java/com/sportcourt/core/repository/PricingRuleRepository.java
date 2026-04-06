package com.sportcourt.core.repository;

import com.sportcourt.core.domain.PricingRule;
import com.sportcourt.core.domain.enums.PricingDayType;
import com.sportcourt.core.domain.enums.PricingRuleCustomerTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    @Query("select r from PricingRule r " +
           "where r.court.id = :courtId " +
           "and r.active = true " +
           "and r.customerTier in :tiers " +
           "and r.dayType in :dayTypes " +
           "order by r.priority desc, r.createdAt asc")
    List<PricingRule> findActiveCandidates(@Param("courtId") UUID courtId,
                                           @Param("tiers") Collection<PricingRuleCustomerTier> tiers,
                                           @Param("dayTypes") Collection<PricingDayType> dayTypes);

    List<PricingRule> findByCourtIdOrderByPriorityDescCreatedAtAsc(UUID courtId);
}
