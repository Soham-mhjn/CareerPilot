package com.careerpilot.repository;
import com.careerpilot.entity.JobAnalysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisRepository extends JpaRepository<JobAnalysis, Long> {
    List<JobAnalysis> findAllByOwnerIdAndApplicationIdOrderByCreatedAtDesc(Long ownerId, Long applicationId);
}

