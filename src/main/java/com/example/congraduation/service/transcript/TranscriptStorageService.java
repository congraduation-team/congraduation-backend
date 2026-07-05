package com.example.congraduation.service.transcript;

import com.example.congraduation.domain.Student;
import com.example.congraduation.domain.transcript.CompletedCourse;
import com.example.congraduation.domain.transcript.TranscriptUpload;
import com.example.congraduation.dto.transcript.CompletedCourseUploadRowDto;
import com.example.congraduation.exception.TranscriptNotFoundException;
import com.example.congraduation.repository.student.StudentRepository;
import com.example.congraduation.repository.transcript.TranscriptUploadRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranscriptStorageService {

    private final StudentRepository studentRepository;
    private final TranscriptUploadRepository transcriptUploadRepository;
    private final TranscriptExcelParser transcriptExcelParser;

    public TranscriptStorageService(
            StudentRepository studentRepository,
            TranscriptUploadRepository transcriptUploadRepository,
            TranscriptExcelParser transcriptExcelParser
    ) {
        this.studentRepository = studentRepository;
        this.transcriptUploadRepository = transcriptUploadRepository;
        this.transcriptExcelParser = transcriptExcelParser;
    }

    @Transactional
    public List<CompletedCourseUploadRowDto> replaceTranscript(Long studentId, MultipartFile file) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));

        List<CompletedCourseUploadRowDto> rows = transcriptExcelParser.parse(file);
        deleteExistingUploads(studentId);

        TranscriptUpload transcriptUpload = TranscriptUpload.create(student, originalFilename(file));
        for (CompletedCourseUploadRowDto row : rows) {
            transcriptUpload.addCompletedCourse(CompletedCourse.create(
                    row.year(),
                    row.semester(),
                    row.courseCode(),
                    row.courseName(),
                    row.category(),
                    row.credit(),
                    row.evaluationMethod(),
                    row.grade(),
                    row.gradePoint()
            ));
        }

        transcriptUploadRepository.save(transcriptUpload);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<CompletedCourseUploadRowDto> getLatestTranscriptRows(Long studentId) {
        TranscriptUpload upload = transcriptUploadRepository.findTopByStudentIdOrderByUploadedAtDesc(studentId)
                .orElseThrow(() -> new TranscriptNotFoundException("업로드된 성적표가 없습니다."));

        return upload.getCompletedCourses().stream()
                .map(course -> new CompletedCourseUploadRowDto(
                        course.getYear(),
                        course.getSemester(),
                        course.getCourseCode(),
                        course.getCourseName(),
                        course.getCategory(),
                        course.getCredit(),
                        course.getEvaluationMethod(),
                        course.getGrade(),
                        course.getGradePoint()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasTranscript(Long studentId) {
        return transcriptUploadRepository.findTopByStudentIdOrderByUploadedAtDesc(studentId).isPresent();
    }

    private void deleteExistingUploads(Long studentId) {
        List<TranscriptUpload> uploads = transcriptUploadRepository.findAllByStudentId(studentId);
        if (!uploads.isEmpty()) {
            transcriptUploadRepository.deleteAll(uploads);
        }
    }

    private String originalFilename(MultipartFile file) {
        return file.getOriginalFilename() == null ? "transcript.xlsx" : file.getOriginalFilename();
    }
}
