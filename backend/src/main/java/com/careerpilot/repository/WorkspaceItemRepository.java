package com.careerpilot.repository;
import com.careerpilot.entity.WorkspaceItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceItemRepository extends JpaRepository<WorkspaceItem, Long> {
    List<WorkspaceItem> findAllByOwnerIdOrderByStartsAtAsc(Long ownerId);
}

