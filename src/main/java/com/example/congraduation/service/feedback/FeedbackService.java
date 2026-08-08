package com.example.congraduation.service.feedback;

import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.feedback.Feedback;
import com.example.congraduation.dto.feedback.CreateFeedbackRequestDto;
import com.example.congraduation.dto.feedback.FeedbackResponseDto;
import com.example.congraduation.dto.feedback.UpdateFeedbackRequestDto;
import com.example.congraduation.repository.feedback.FeedbackRepository;
import com.example.congraduation.repository.student.StudentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private static final int TITLE_MAX = 80;
    private static final int CONTENT_MAX = 2000;
    private static final int ADMIN_NOTE_MAX = 1000;

    private final FeedbackRepository feedbackRepository;
    private final StudentRepository studentRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, StudentRepository studentRepository) {
        this.feedbackRepository = feedbackRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public FeedbackResponseDto create(CreateFeedbackRequestDto request, Long authenticatedStudentId) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 비어 있습니다.");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("type은 BUG 또는 INQUIRY 이어야 합니다.");
        }
        String title = requireText(request.title(), "title", TITLE_MAX);
        String content = requireText(request.content(), "content", CONTENT_MAX);

        Student student = null;
        String studentNo = blankToNull(request.studentNo());
        String studentName = blankToNull(request.studentName());
        String major = blankToNull(request.major());

        if (authenticatedStudentId == null) {
            throw new IllegalArgumentException("인증된 학생 정보가 없습니다.");
        }

        if (request.studentId() != null && !authenticatedStudentId.equals(request.studentId())) {
            throw new IllegalArgumentException("요청 본문의 studentId는 로그인한 사용자와 같아야 합니다.");
        }

        student = studentRepository.findById(authenticatedStudentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다: " + authenticatedStudentId));

        if (studentNo == null) {
            studentNo = student.getStudentNo();
        }
        if (studentName == null) {
            studentName = student.getName();
        }
        if (major == null) {
            major = student.getMajor();
        }

        Feedback saved = feedbackRepository.save(
                Feedback.create(request.type(), title, content, student, studentNo, studentName, major)
        );
        return FeedbackResponseDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponseDto> listMine(Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId는 필수입니다.");
        }
        if (!studentRepository.existsById(studentId)) {
            throw new IllegalArgumentException("학생을 찾을 수 없습니다: " + studentId);
        }
        return feedbackRepository.findAllByStudent_IdOrderByCreatedAtDesc(studentId).stream()
                .map(FeedbackResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponseDto> listAllForAdmin() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(FeedbackResponseDto::from)
                .toList();
    }

    @Transactional
    public FeedbackResponseDto updateAsAdmin(Long id, UpdateFeedbackRequestDto request) {
        if (id == null) {
            throw new IllegalArgumentException("feedback id는 필수입니다.");
        }
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 비어 있습니다.");
        }
        if (request.status() == null && request.adminNote() == null) {
            throw new IllegalArgumentException("status 또는 adminNote 중 하나 이상 필요합니다.");
        }
        if (request.adminNote() != null && request.adminNote().length() > ADMIN_NOTE_MAX) {
            throw new IllegalArgumentException("adminNote는 " + ADMIN_NOTE_MAX + "자를 넘을 수 없습니다.");
        }

        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다: " + id));
        feedback.updateAdmin(request.status(), request.adminNote());
        return FeedbackResponseDto.from(feedback);
    }

    /** 관리자 API soft-guard. adminStudentId가 있으면 admin 여부를 검사한다. */
    @Transactional(readOnly = true)
    public void requireAdmin(Long adminStudentId) {
        if (adminStudentId == null) {
            return;
        }
        Student student = studentRepository.findById(adminStudentId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 학생을 찾을 수 없습니다: " + adminStudentId));
        if (!student.isAdmin()) {
            throw new IllegalArgumentException("관리자 권한이 없습니다.");
        }
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은(는) 필수입니다.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + "은(는) " + max + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
