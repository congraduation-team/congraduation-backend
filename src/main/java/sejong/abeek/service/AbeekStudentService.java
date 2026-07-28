package sejong.abeek.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sejong.abeek.domain.CourseMaster;
import sejong.abeek.domain.AbeekStudent;
import sejong.abeek.domain.StudentEnrollment;
import sejong.abeek.dto.AddEnrollmentRequest;
import sejong.abeek.dto.CreateStudentRequest;
import sejong.abeek.repository.CourseMasterRepository;
import sejong.abeek.repository.AbeekStudentRepository;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AbeekStudentService {

    private final AbeekStudentRepository studentRepository;
    private final CourseMasterRepository courseMasterRepository;

    @Transactional
    public Student create(CreateStudentRequest request) {
        if (studentRepository.findByStudentId(request.getStudentId()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 학번: " + request.getStudentId());
        }

        AbeekStudent student = AbeekStudent.builder()
                .studentId(request.getStudentId())
                .name(request.getName())
                .entranceYear(request.getEntranceYear())
                .graduationAbeekYear(request.getGraduationAbeekYear())
                .department(request.getDepartment() == null ? "컴퓨터공학과" : request.getDepartment())
                .departmentCode(resolveDepartmentCode(request.getDepartmentCode(), request.getDepartment()))
                .build();

        if (request.getEnrollments() != null) {
            for (CreateStudentRequest.EnrollmentRequest e : request.getEnrollments()) {
                student.addEnrollment(toEnrollment(e.getCourseCode(), e.getCredits(),
                        e.getDesignCredits(), e.getTakenYear(), e.getTakenSemester(), e.isPassed()));
            }
        }

        return studentRepository.save(student);
    }

    @Transactional
    public Student addEnrollment(String studentId, AddEnrollmentRequest request) {
        AbeekStudent student = studentRepository.findWithEnrollmentsByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 없음: " + studentId));
        student.addEnrollment(toEnrollment(
                request.getCourseCode(),
                request.getCredits(),
                request.getDesignCredits(),
                request.getTakenYear(),
                request.getTakenSemester(),
                request.isPassed()
        ));
        return studentRepository.save(student);
    }

    @Transactional(readOnly = true)
    public Student get(String studentId) {
        return studentRepository.findWithEnrollmentsByStudentId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 없음: " + studentId));
    }

    private StudentEnrollment toEnrollment(
            String courseCode, int credits, double designCredits,
            int takenYear, int takenSemester, boolean passed
    ) {
        CourseMaster master = courseMasterRepository.findByCourseCode(courseCode)
                .orElseThrow(() -> new IllegalArgumentException("과목 코드 없음: " + courseCode));
        return StudentEnrollment.builder()
                .courseMaster(master)
                .credits(credits)
                .designCredits(designCredits)
                .takenYear(takenYear)
                .takenSemester(takenSemester)
                .passed(passed)
                .build();
    }

    private String resolveDepartmentCode(String departmentCode, String departmentName) {
        if (departmentCode != null && !departmentCode.isBlank()) {
            return departmentCode.trim().toUpperCase(Locale.ROOT);
        }
        return "컴퓨터공학과".equals(departmentName) ? "CSE" : "CSE";
    }
}
