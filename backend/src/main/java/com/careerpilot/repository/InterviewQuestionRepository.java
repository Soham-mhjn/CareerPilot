package com.careerpilot.repository;
import com.careerpilot.entity.InterviewQuestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    List<InterviewQuestion> findAllByOwnerIdAndApplicationIdOrderByCreatedAtDesc(Long ownerId, Long applicationId);
}
