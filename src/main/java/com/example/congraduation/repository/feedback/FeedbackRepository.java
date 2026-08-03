package com.example.congraduation.repository.feedback;

import com.example.congraduation.domain.feedback.Feedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findAllByStudent_IdOrderByCreatedAtDesc(Long studentId);

    List<Feedback> findAllByOrderByCreatedAtDesc();
}
