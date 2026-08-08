package com.example.congraduation.repository.transcript;

import com.example.congraduation.domain.transcript.TranscriptUpload;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TranscriptUploadRepository extends JpaRepository<TranscriptUpload, Long> {

    List<TranscriptUpload> findAllByStudentId(Long studentId);

    Optional<TranscriptUpload> findTopByStudentIdOrderByUploadedAtDesc(Long studentId);

    @Query("select count(distinct t.student.id) from TranscriptUpload t")
    long countDistinctStudents();
}
