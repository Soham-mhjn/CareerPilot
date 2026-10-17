package com.careerpilot.repository;
import com.careerpilot.entity.JobApplication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    long countByOwnerId(Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, String status);
}

