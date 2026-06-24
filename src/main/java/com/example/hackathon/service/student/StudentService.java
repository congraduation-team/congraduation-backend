package com.example.hackathon.service.student;

import com.example.hackathon.domain.MajorType;
import com.example.hackathon.domain.Student;
import com.example.hackathon.dto.student.StudentMajorTrackUpdateRequestDto;
import com.example.hackathon.dto.sejong.SejongProfileResponseDto;
import com.example.hackathon.repository.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional
    public Student findOrCreate(SejongProfileResponseDto profile) {
        String studentNo = normalizeStudentNo(profile.getStudentId());
        Integer admissionYear = extractAdmissionYear(studentNo);

        return studentRepository.findByStudentNo(studentNo)
                .map(student -> updateStudent(student, profile, admissionYear))
                .orElseGet(() -> studentRepository.save(
                        Student.create(
                                studentNo,
                                profile.getName(),
                                profile.getMajor(),
                                MajorType.SINGLE,
                                null,
                                profile.getGradeLevel(),
                                admissionYear,
                                "ACTIVE"
                        )
                ));
    }

    private Student updateStudent(Student student, SejongProfileResponseDto profile, Integer admissionYear) {
        student.updateAcademicInfo(
                profile.getName(),
                profile.getMajor(),
                profile.getGradeLevel(),
                admissionYear,
                student.getStatus() == null ? "ACTIVE" : student.getStatus()
        );
        return student;
    }

    @Transactional
    public Student updateMajorTrack(Long studentId, StudentMajorTrackUpdateRequestDto request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));

        validateMajorTrack(student, request);
        student.updateMajorTrack(
                request.majorType(),
                request.majorType() == MajorType.DOUBLE ? request.secondaryMajor().trim() : null
        );
        return student;
    }

    private void validateMajorTrack(Student student, StudentMajorTrackUpdateRequestDto request) {
        if (request.majorType() == null) {
            throw new IllegalArgumentException("전공 유형이 필요합니다.");
        }

        if (request.majorType() == MajorType.DOUBLE) {
            if (request.secondaryMajor() == null || request.secondaryMajor().isBlank()) {
                throw new IllegalArgumentException("복수전공 학과를 입력해야 합니다.");
            }
            if (student.getMajor().equals(request.secondaryMajor().trim())) {
                throw new IllegalArgumentException("주전공과 복수전공은 같을 수 없습니다.");
            }
        }
    }

    private String normalizeStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            throw new IllegalArgumentException("학번이 없습니다.");
        }
        return studentNo.trim();
    }

    private Integer extractAdmissionYear(String studentNo) {
        if (studentNo.length() < 2) {
            return null;
        }

        String candidate = studentNo.substring(0, 2);
        if (!candidate.chars().allMatch(Character::isDigit)) {
            return null;
        }

        return 2000 + Integer.parseInt(candidate);
    }
}
