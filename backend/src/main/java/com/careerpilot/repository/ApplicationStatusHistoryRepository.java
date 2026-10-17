package com.careerpilot.repository;

import com.careerpilot.entity.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {
    @Query("select h from ApplicationStatusHistory h join fetch h.application where h.owner.id = :ownerId and h.application.id = :applicationId order by h.changedAt asc")
    List<ApplicationStatusHistory> findHistory(@Param("ownerId") Long ownerId, @Param("applicationId") Long applicationId);

    @Query("select h from ApplicationStatusHistory h join fetch h.application where h.owner.id = :ownerId and h.changedAt >= :from order by h.changedAt asc")
    List<ApplicationStatusHistory> findSince(@Param("ownerId") Long ownerId, @Param("from") Instant from);

    @Query("select h from ApplicationStatusHistory h join fetch h.application where h.owner.id = :ownerId and h.application.id in :applicationIds order by h.changedAt asc")
    List<ApplicationStatusHistory> findForApplications(@Param("ownerId") Long ownerId, @Param("applicationIds") List<Long> applicationIds);
}
