package com.eduaccess.repository;

import com.eduaccess.domain.ManagerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManagerFeedbackRepository extends JpaRepository<ManagerFeedback, Long> {

    List<ManagerFeedback> findTop20ByOrderByCreatedAtDesc();

    List<ManagerFeedback> findAllByOrderByCreatedAtDesc();
}
