package com.example.congraduation.service.feedback;

import com.example.congraduation.domain.MajorType;
import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.feedback.Feedback;
import com.example.congraduation.domain.feedback.FeedbackStatus;
import com.example.congraduation.domain.feedback.FeedbackType;
import com.example.congraduation.dto.feedback.CreateFeedbackRequestDto;
import com.example.congraduation.dto.feedback.FeedbackResponseDto;
import com.example.congraduation.dto.feedback.UpdateFeedbackRequestDto;
import com.example.congraduation.repository.feedback.FeedbackRepository;
import com.example.congraduation.repository.student.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private StudentRepository studentRepository;

    private FeedbackService feedbackService;

    private Student student;
    private Student admin;
    private final AtomicLong idSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(feedbackRepository, studentRepository);
        student = Student.create(
                "21012345", "홍길동", "컴퓨터공학과", MajorType.SINGLE,
                null, 3, 2021, "ACTIVE", false
        );
        setId(student, 1L);
        admin = Student.create(
                "20000001", "관리자", "컴퓨터공학과", MajorType.SINGLE,
                null, 4, 2020, "ACTIVE", true
        );
        setId(admin, 2L);
    }

    @Test
    void createFillsStudentSnapshot() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> {
            Feedback feedback = invocation.getArgument(0);
            setId(feedback, idSeq.getAndIncrement());
            return feedback;
        });

        FeedbackResponseDto created = feedbackService.create(new CreateFeedbackRequestDto(
                FeedbackType.BUG,
                "기이수 반영 오류",
                "A+ 과목이 누락됩니다.",
                1L,
                null,
                null,
                null
        ));

        assertThat(created.type()).isEqualTo(FeedbackType.BUG);
        assertThat(created.status()).isEqualTo(FeedbackStatus.OPEN);
        assertThat(created.studentId()).isEqualTo(1L);
        assertThat(created.studentNo()).isEqualTo("21012345");
        assertThat(created.studentName()).isEqualTo("홍길동");
        assertThat(created.major()).isEqualTo("컴퓨터공학과");
        assertThat(created.title()).isEqualTo("기이수 반영 오류");
        assertThat(created.createdAt()).isNotBlank();
    }

    @Test
    void listMine() {
        when(studentRepository.existsById(1L)).thenReturn(true);
        Feedback feedback = Feedback.create(
                FeedbackType.INQUIRY, "문의", "내용", student,
                student.getStudentNo(), student.getName(), student.getMajor()
        );
        setId(feedback, 10L);
        when(feedbackRepository.findAllByStudent_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(feedback));

        List<FeedbackResponseDto> mine = feedbackService.listMine(1L);
        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst().id()).isEqualTo(10L);
    }

    @Test
    void updateAsAdmin() {
        Feedback feedback = Feedback.create(
                FeedbackType.BUG, "버그", "내용", student,
                student.getStudentNo(), student.getName(), student.getMajor()
        );
        setId(feedback, 5L);
        when(feedbackRepository.findById(5L)).thenReturn(Optional.of(feedback));

        FeedbackResponseDto updated = feedbackService.updateAsAdmin(
                5L,
                new UpdateFeedbackRequestDto(FeedbackStatus.RESOLVED, "수정 완료")
        );

        assertThat(updated.status()).isEqualTo(FeedbackStatus.RESOLVED);
        assertThat(updated.adminNote()).isEqualTo("수정 완료");
    }

    @Test
    void requireAdminRejectsNonAdmin() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        assertThatThrownBy(() -> feedbackService.requireAdmin(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자");
    }

    @Test
    void requireAdminAllowsAdmin() {
        when(studentRepository.findById(2L)).thenReturn(Optional.of(admin));
        feedbackService.requireAdmin(2L);
        verify(studentRepository).findById(2L);
    }

    @Test
    void createRejectsBlankTitle() {
        assertThatThrownBy(() -> feedbackService.create(new CreateFeedbackRequestDto(
                FeedbackType.BUG, "  ", "내용", 1L, null, null, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void createPersistsOpenStatus() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        when(feedbackRepository.save(captor.capture())).thenAnswer(inv -> {
            Feedback f = inv.getArgument(0);
            setId(f, 99L);
            return f;
        });

        feedbackService.create(new CreateFeedbackRequestDto(
                FeedbackType.INQUIRY, "문의", "내용입니다", 1L, null, null, null
        ));

        assertThat(captor.getValue().getStatus()).isEqualTo(FeedbackStatus.OPEN);
        assertThat(captor.getValue().getType()).isEqualTo(FeedbackType.INQUIRY);
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
