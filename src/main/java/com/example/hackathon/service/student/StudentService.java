package com.example.hackathon.service.student;

import com.example.hackathon.domain.MajorType;
import com.example.hackathon.domain.Student;
import com.example.hackathon.domain.StudentMajorTrack;
import com.example.hackathon.dto.student.StudentMajorTrackUpdateRequestDto;
import com.example.hackathon.dto.student.StudentMajorTrackRequestDto;
import com.example.hackathon.dto.sejong.SejongProfileResponseDto;
import com.example.hackathon.repository.student.StudentRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        Student student = getStudent(studentId);

        List<StudentMajorTrack> tracks = resolveRequestedTracks(request);
        validateMajorTrack(student, tracks, request);

        student.replaceMajorTracks(tracks);
        student.updateMajorTrackSummary(
                deriveLegacyMajorType(tracks),
                deriveLegacySecondaryMajor(tracks)
        );
        return student;
    }

    @Transactional(readOnly = true)
    public Student getStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
    }

    private void validateMajorTrack(
            Student student,
            List<StudentMajorTrack> tracks,
            StudentMajorTrackUpdateRequestDto request
    ) {
        if (tracks.isEmpty()) {
            if (request.majorType() != null && request.majorType() != MajorType.SINGLE) {
                throw new IllegalArgumentException("전공 트랙 정보가 비어 있습니다.");
            }
            return;
        }

        Set<String> duplicateCheck = new LinkedHashSet<>();
        for (StudentMajorTrack track : tracks) {
            if (track.getTrackType() == null) {
                throw new IllegalArgumentException("전공 트랙 유형이 필요합니다.");
            }
            if (track.getDepartmentCode() == null || track.getDepartmentCode().isBlank()) {
                throw new IllegalArgumentException("전공 트랙 학과를 입력해야 합니다.");
            }

            String departmentCode = track.getDepartmentCode().trim();
            if (student.getMajor().equals(departmentCode)) {
                throw new IllegalArgumentException("주전공과 추가 전공은 같을 수 없습니다.");
            }

            String duplicateKey = track.getTrackType().name() + ":" + departmentCode;
            if (!duplicateCheck.add(duplicateKey)) {
                throw new IllegalArgumentException("동일한 전공 트랙이 중복되었습니다.");
            }
        }
    }

    private List<StudentMajorTrack> resolveRequestedTracks(StudentMajorTrackUpdateRequestDto request) {
        if (request.tracks() != null && !request.tracks().isEmpty()) {
            List<StudentMajorTrack> tracks = new ArrayList<>();
            for (StudentMajorTrackRequestDto trackRequest : request.tracks()) {
                tracks.add(StudentMajorTrack.create(
                        normalizeTrackType(trackRequest.trackType()),
                        normalizeDepartment(trackRequest.departmentCode()),
                        trackRequest.approvedAtSemester(),
                        trackRequest.teachingCert()
                ));
            }
            return tracks;
        }

        if (isDoubleMajorType(request.majorType())) {
            if (request.secondaryMajor() == null || request.secondaryMajor().isBlank()) {
                throw new IllegalArgumentException("복수전공 학과를 입력해야 합니다.");
            }
            return List.of(StudentMajorTrack.create(
                    MajorType.DOUBLE_MAJOR,
                    normalizeDepartment(request.secondaryMajor()),
                    null,
                    false
            ));
        }

        return List.of();
    }

    private MajorType deriveLegacyMajorType(List<StudentMajorTrack> tracks) {
        if (tracks.isEmpty()) {
            return MajorType.SINGLE;
        }

        boolean hasDoubleMajor = tracks.stream()
                .anyMatch(track -> isDoubleMajorType(track.getTrackType()));
        if (hasDoubleMajor) {
            return MajorType.DOUBLE;
        }

        return tracks.getFirst().getTrackType();
    }

    private String deriveLegacySecondaryMajor(List<StudentMajorTrack> tracks) {
        return tracks.stream()
                .filter(track -> isDoubleMajorType(track.getTrackType()))
                .map(StudentMajorTrack::getDepartmentCode)
                .findFirst()
                .orElse(null);
    }

    private MajorType normalizeTrackType(MajorType majorType) {
        if (majorType == null) {
            return null;
        }
        if (majorType == MajorType.DOUBLE) {
            return MajorType.DOUBLE_MAJOR;
        }
        return majorType;
    }

    private boolean isDoubleMajorType(MajorType majorType) {
        return majorType == MajorType.DOUBLE || majorType == MajorType.DOUBLE_MAJOR;
    }

    private String normalizeDepartment(String department) {
        return department == null ? null : department.trim();
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
