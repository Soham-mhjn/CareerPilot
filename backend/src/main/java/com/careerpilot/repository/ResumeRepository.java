package com.careerpilot.repository;
import com.careerpilot.entity.ResumeDocument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeRepository extends JpaRepository<ResumeDocument, Long> {
    List<ResumeDocument> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    long countByOwnerIdAndStorageName(Long ownerId, String storageName);
}

