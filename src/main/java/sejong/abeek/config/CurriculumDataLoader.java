package sejong.abeek.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sejong.abeek.domain.AbeekYearRequirement;
import sejong.abeek.domain.CourseMaster;
import sejong.abeek.domain.CurriculumCourse;
import sejong.abeek.domain.enums.*;
import sejong.abeek.repository.AbeekYearRequirementRepository;
import sejong.abeek.repository.CourseMasterRepository;
import sejong.abeek.repository.CurriculumCourseRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * 컴퓨터공학과 2020~2026 ABEEK 요건 및 이수체계도 시드.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class CurriculumDataLoader implements CommandLineRunner {

    private final CourseMasterRepository courseMasterRepository;
    private final CurriculumCourseRepository curriculumCourseRepository;
    private final AbeekYearRequirementRepository requirementRepository;

    private final Map<String, CourseMaster> masters = new HashMap<>();

    @Override
    @Transactional
    public void run(String... args) {
        if (requirementRepository.existsByDepartmentCode("CSE")) {
            return;
        }
        seedMasters();
        seedRequirements();
        seedCurricula();
    }

    private void seedRequirements() {
        // 2020~2023: 전공60 / 설계12
        for (int y = 2020; y <= 2023; y++) {
            boolean certElective = y >= 2022;
            saveReq(AbeekYearRequirement.builder()
                    .year(y)
                    .departmentCode("CSE")
                    .generalMinCredits(14)
                    .bsmMinCredits(18)
                    .majorMinCredits(60)
                    .designMinCredits(12)
                    .certElectiveMinCourses(certElective ? 2 : 0)
                    .certElectiveMinCredits(certElective ? 6 : 0)
                    .certElectiveMinAreas(certElective ? 2 : 0)
                    .note(y + "년도 컴퓨터공학과 ABEEK")
                    .build());
        }
        // 2024~2026: 전공45 / 설계10
        for (int y = 2024; y <= 2026; y++) {
            saveReq(AbeekYearRequirement.builder()
                    .year(y)
                    .departmentCode("CSE")
                    .generalMinCredits(14)
                    .bsmMinCredits(18)
                    .majorMinCredits(45)
                    .designMinCredits(10)
                    .certElectiveMinCourses(2)
                    .certElectiveMinCredits(6)
                    .certElectiveMinAreas(y == 2026 ? 2 : 2)
                    .note(y + "년도 컴퓨터공학과 ABEEK (전공45·설계10)")
                    .build());
        }
    }

    private void saveReq(AbeekYearRequirement r) {
        requirementRepository.save(r);
    }

    private void seedMasters() {
        // —— 교양 ——
        m("GEN_WRITE", "문제해결을위한글쓰기와발표", CourseCategory.GENERAL, "GEN_WRITE", ElectiveArea.NONE);
        m("GEN_WRITE_2026", "비판적사고와창의적글쓰기", CourseCategory.GENERAL, "GEN_WRITE", ElectiveArea.NONE);
        m("GEN_CAREER1", "대학생활과진로설계1", CourseCategory.GENERAL, "GEN_CAREER1", ElectiveArea.NONE);
        m("GEN_CAREER_EXP", "대학생활과진로탐색", CourseCategory.GENERAL, "GEN_CAREER1", ElectiveArea.NONE);
        m("GEN_STARTUP1", "창업과기업가정신1", CourseCategory.GENERAL, "GEN_STARTUP1", ElectiveArea.NONE);
        m("GEN_VOLUNTEER1", "세종사회봉사1", CourseCategory.GENERAL, "GEN_VOLUNTEER1", ElectiveArea.NONE);
        m("GEN_ENG_LISTEN", "English Listening Practice 1", CourseCategory.GENERAL, "GEN_ENG", ElectiveArea.NONE);
        m("GEN_ENG_READ", "English Reading Practice 1", CourseCategory.GENERAL, "GEN_ENG_READ", ElectiveArea.NONE);
        m("GEN_UNI_ENG", "대학영어", CourseCategory.GENERAL, "GEN_ENG", ElectiveArea.NONE);
        m("GEN_PHILOSOPHY", "서양철학:쟁점과토론", CourseCategory.GENERAL, "GEN_PHILOSOPHY", ElectiveArea.NONE);
        m("GEN_WORLD_HIST", "세계사:인간과문명", CourseCategory.GENERAL, "GEN_WORLD_HIST", ElectiveArea.HISTORY_THOUGHT);
        m("GEN_WORLD_HIST2", "세계사", CourseCategory.GENERAL, "GEN_WORLD_HIST", ElectiveArea.HISTORY_THOUGHT);
        m("GEN_EAST_WEST", "동서양의사상과윤리", CourseCategory.GENERAL, "GEN_EAST_WEST", ElectiveArea.HISTORY_THOUGHT);
        m("GEN_ECON", "경제학", CourseCategory.GENERAL, "GEN_ECON", ElectiveArea.ECONOMY_SOCIETY);
        m("GEN_MGMT", "경영학", CourseCategory.GENERAL, "GEN_MGMT", ElectiveArea.ECONOMY_SOCIETY);
        m("GEN_METAVERSE", "컴퓨터게임과메타버스", CourseCategory.GENERAL, "GEN_METAVERSE", ElectiveArea.CULTURE_ART);
        m("GEN_FUSION_ART", "융합예술의이해", CourseCategory.GENERAL, "GEN_FUSION_ART", ElectiveArea.CULTURE_ART);
        m("GEN_SEMINAR", "신입생세미나", CourseCategory.GENERAL, "GEN_SEMINAR", ElectiveArea.NONE);
        m("GEN_ADV_PROG_P", "고급프로그래밍입문-P", CourseCategory.GENERAL, "GEN_ADV_PROG_P", ElectiveArea.NONE);
        m("GEN_JOB", "취업역량개발론", CourseCategory.GENERAL, "GEN_JOB", ElectiveArea.NONE);
        m("GEN_TECH_WRITE", "Technical Writing 기초", CourseCategory.GENERAL, "GEN_TECH_WRITE", ElectiveArea.NONE);
        m("GEN_GRAD1", "졸업연구및진로1", CourseCategory.MAJOR, "GEN_GRAD1", ElectiveArea.NONE);
        m("GEN_GRAD2", "졸업연구및진로2", CourseCategory.MAJOR, "GEN_GRAD2", ElectiveArea.NONE);

        // —— BSM ——
        m("BSM_CALC", "기초미적분학", CourseCategory.BSM, "BSM_CALC", ElectiveArea.NONE);
        m("BSM_CALC1", "미적분학1", CourseCategory.BSM, "BSM_CALC", ElectiveArea.NONE);
        m("BSM_PHYS_LAB", "일반물리학및실험1", CourseCategory.BSM, "BSM_PHYS", ElectiveArea.NONE);
        m("BSM_PHYS", "일반물리학1", CourseCategory.BSM, "BSM_PHYS", ElectiveArea.NONE);
        m("BSM_EMATH1", "공업수학1", CourseCategory.BSM, "BSM_EMATH1", ElectiveArea.NONE);
        m("BSM_DISC", "이산수학및프로그래밍", CourseCategory.BSM, "BSM_DISC", ElectiveArea.NONE);
        m("BSM_PROB_PROG", "확률통계및프로그래밍", CourseCategory.BSM, "BSM_PROB", ElectiveArea.NONE);
        m("BSM_PROB", "확률및통계", CourseCategory.BSM, "BSM_PROB", ElectiveArea.NONE);
        m("BSM_LINEAR_PROG", "선형대수및프로그래밍", CourseCategory.BSM, "BSM_LINEAR", ElectiveArea.NONE);
        m("BSM_LINEAR", "선형대수", CourseCategory.BSM, "BSM_LINEAR", ElectiveArea.NONE);

        // —— 전공 필수 코어 ——
        m("MAJ_C", "C프로그래밍및실습", CourseCategory.MAJOR, "MAJ_C", ElectiveArea.NONE);
        m("MAJ_ADV_C", "고급C프로그래밍및실습", CourseCategory.MAJOR, "MAJ_ADV_C", ElectiveArea.NONE);
        m("MAJ_BASIC_DESIGN", "공학설계기초(산학프로젝트입문)", CourseCategory.MAJOR, "MAJ_BASIC_DESIGN", ElectiveArea.NONE);
        m("MAJ_DS", "자료구조및실습", CourseCategory.MAJOR, "MAJ_DS", ElectiveArea.NONE);
        m("MAJ_ALGO", "알고리즘및실습", CourseCategory.MAJOR, "MAJ_ALGO", ElectiveArea.NONE);
        m("MAJ_DIGITAL", "디지털시스템", CourseCategory.MAJOR, "MAJ_DIGITAL", ElectiveArea.NONE);
        m("MAJ_ARCH", "컴퓨터구조", CourseCategory.MAJOR, "MAJ_ARCH", ElectiveArea.NONE);
        m("MAJ_OS", "운영체제", CourseCategory.MAJOR, "MAJ_OS", ElectiveArea.NONE);
        m("MAJ_NETWORK", "컴퓨터네트워크", CourseCategory.MAJOR, "MAJ_NETWORK", ElectiveArea.NONE);
        m("MAJ_CAPSTONE", "Capstone디자인(산학협력프로젝트)", CourseCategory.MAJOR, "MAJ_CAPSTONE", ElectiveArea.NONE);
        m("MAJ_SW_AI", "SW-AI종합설계", CourseCategory.MAJOR, "MAJ_SW_AI", ElectiveArea.NONE);
        m("MAJ_ADV_DESIGN", "심화프로그래밍설계", CourseCategory.MAJOR, "MAJ_ADV_DESIGN", ElectiveArea.NONE);

        // —— 요소설계·선택 전공 (주요) ——
        m("MAJ_CPP", "문제해결및실습:C++", CourseCategory.MAJOR, "MAJ_CPP", ElectiveArea.NONE);
        m("MAJ_JAVA", "문제해결및실습:JAVA", CourseCategory.MAJOR, "MAJ_JAVA", ElectiveArea.NONE);
        m("MAJ_WIN", "윈도우즈프로그래밍", CourseCategory.MAJOR, "MAJ_WIN", ElectiveArea.NONE);
        m("MAJ_WEB", "웹프로그래밍", CourseCategory.MAJOR, "MAJ_WEB", ElectiveArea.NONE);
        m("MAJ_WEB_DESIGN", "웹프로그래밍설계", CourseCategory.MAJOR, "MAJ_WEB_DESIGN", ElectiveArea.NONE);
        m("MAJ_OSS_INTRO", "오픈소스SW개론", CourseCategory.MAJOR, "MAJ_OSS_INTRO", ElectiveArea.NONE);
        m("MAJ_OSS_ENG", "오픈소스SW공학", CourseCategory.MAJOR, "MAJ_OSS_ENG", ElectiveArea.NONE);
        m("MAJ_DB", "데이터베이스", CourseCategory.MAJOR, "MAJ_DB", ElectiveArea.NONE);
        m("MAJ_CS", "C#프로그래밍", CourseCategory.MAJOR, "MAJ_CS", ElectiveArea.NONE);
        m("MAJ_MICRO", "마이크로컴퓨터", CourseCategory.MAJOR, "MAJ_MICRO", ElectiveArea.NONE);
        m("MAJ_GRAPHICS", "컴퓨터그래픽스", CourseCategory.MAJOR, "MAJ_GRAPHICS", ElectiveArea.NONE);
        m("MAJ_UNIX", "Unix프로그래밍", CourseCategory.MAJOR, "MAJ_UNIX", ElectiveArea.NONE);
        m("MAJ_LINUX", "리눅스프로그래밍및실습", CourseCategory.MAJOR, "MAJ_UNIX", ElectiveArea.NONE);
        m("MAJ_XML", "XML프로그래밍", CourseCategory.MAJOR, "MAJ_XML", ElectiveArea.NONE);
        m("MAJ_IMG", "영상처리", CourseCategory.MAJOR, "MAJ_IMG", ElectiveArea.NONE);
        m("MAJ_EMBED", "임베디드시스템", CourseCategory.MAJOR, "MAJ_EMBED", ElectiveArea.NONE);
        m("MAJ_NET_PROG", "네트워크프로그래밍", CourseCategory.MAJOR, "MAJ_NET_PROG", ElectiveArea.NONE);
        m("MAJ_INTEL_NET", "지능형네트워크프로그래밍", CourseCategory.MAJOR, "MAJ_NET_PROG", ElectiveArea.NONE);
        m("MAJ_INTEL_EDGE", "지능형엣지시스템", CourseCategory.MAJOR, "MAJ_EMBED", ElectiveArea.NONE);
        m("MAJ_SE", "소프트웨어공학", CourseCategory.MAJOR, "MAJ_SE", ElectiveArea.NONE);
        m("MAJ_AI", "인공지능", CourseCategory.MAJOR, "MAJ_AI", ElectiveArea.NONE);
        m("MAJ_ML", "기계학습", CourseCategory.MAJOR, "MAJ_ML", ElectiveArea.NONE);
        m("MAJ_SIGNAL", "신호및시스템", CourseCategory.MAJOR, "MAJ_SIGNAL", ElectiveArea.NONE);
        m("MAJ_DSP", "디지털신호처리", CourseCategory.MAJOR, "MAJ_DSP", ElectiveArea.NONE);
        m("MAJ_PL", "프로그래밍언어의개념", CourseCategory.MAJOR, "MAJ_PL", ElectiveArea.NONE);
        m("MAJ_SERVER", "서버프로그래밍설계", CourseCategory.MAJOR, "MAJ_SERVER", ElectiveArea.NONE);
        m("MAJ_VHDL", "VHDL프로그래밍", CourseCategory.MAJOR, "MAJ_VHDL", ElectiveArea.NONE);
        m("MAJ_HW", "하드웨어시스템설계", CourseCategory.MAJOR, "MAJ_HW", ElectiveArea.NONE);
        m("MAJ_MULTI", "멀티미디어", CourseCategory.MAJOR, "MAJ_MULTI", ElectiveArea.NONE);
        m("MAJ_NOMAD", "창의SW융합노마드", CourseCategory.MAJOR, "MAJ_NOMAD", ElectiveArea.NONE);
        m("MAJ_MOBILE_DESIGN", "모바일응용설계", CourseCategory.MAJOR, "MAJ_MOBILE_DESIGN", ElectiveArea.NONE);
        m("MAJ_HCI", "HCI개론", CourseCategory.MAJOR, "MAJ_HCI", ElectiveArea.NONE);
        m("MAJ_SEC", "정보보호개론", CourseCategory.MAJOR, "MAJ_SEC", ElectiveArea.NONE);
        m("MAJ_GEN_AI", "생성인공지능입문", CourseCategory.MAJOR, "MAJ_GEN_AI", ElectiveArea.NONE);
        m("MAJ_PATENT", "특허와창업", CourseCategory.MAJOR, "MAJ_PATENT", ElectiveArea.NONE);
        m("MAJ_COMPILER", "컴파일러", CourseCategory.MAJOR, "MAJ_COMPILER", ElectiveArea.NONE);
        m("MAJ_CV", "컴퓨터비전시스템", CourseCategory.MAJOR, "MAJ_CV", ElectiveArea.NONE);
        m("MAJ_APP", "앱프로그래밍", CourseCategory.MAJOR, "MAJ_APP", ElectiveArea.NONE);
        m("MAJ_MOBILE", "모바일프로그래밍", CourseCategory.MAJOR, "MAJ_MOBILE", ElectiveArea.NONE);
    }

    private void m(String code, String name, CourseCategory cat, String group, ElectiveArea area) {
        CourseMaster master = courseMasterRepository.save(CourseMaster.builder()
                .courseCode(code)
                .name(name)
                .category(cat)
                .equivalenceGroup(group)
                .electiveArea(area)
                .departmentCourse(cat == CourseCategory.MAJOR || cat == CourseCategory.BSM || true)
                .build());
        masters.put(code, master);
    }

    private void seedCurricula() {
        seed2020();
        seed2021();
        seed2022();
        seed2023();
        seed2024();
        seed2025();
        seed2026();
    }

    // ===================== 연도별 =====================

    private void seed2020() {
        int y = 2020;
        // 교양
        cc(y, "GEN_WRITE", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_CAREER1", 1, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_STARTUP1", 1, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_VOLUNTEER1", 1, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_ENG_LISTEN", 2, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-2");
        cc(y, "GEN_PHILOSOPHY", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-2");
        cc(y, "GEN_ENG_READ", 2, 0, DesignLevel.NONE, CourseRole.REQUIRED, "2-2");
        // BSM
        bsm(y, "BSM_CALC", 3, "1-1");
        bsm(y, "BSM_PHYS_LAB", 3, "1-1");
        bsm(y, "BSM_EMATH1", 3, "1-2");
        bsm(y, "BSM_DISC", 3, "2-1");
        bsm(y, "BSM_PROB_PROG", 3, "2-2");
        bsm(y, "BSM_LINEAR_PROG", 3, "3-1");
        // 전공 필수
        majReq(y, "MAJ_C", 4, 0, DesignLevel.NONE, "1-1");
        majReq(y, "MAJ_ADV_C", 4, 1, DesignLevel.ELEMENT, "1-2");
        majReq(y, "MAJ_BASIC_DESIGN", 3, 3, DesignLevel.BASIC, "1-2");
        majReq(y, "MAJ_DIGITAL", 3, 1, DesignLevel.ELEMENT, "2-1");
        majReq(y, "MAJ_DS", 4, 0, DesignLevel.NONE, "2-1");
        majReq(y, "MAJ_ALGO", 4, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_ARCH", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_OS", 3, 0, DesignLevel.NONE, "3-1");
        majReq(y, "MAJ_NETWORK", 3, 0, DesignLevel.NONE, "3-2");
        majReq(y, "MAJ_CAPSTONE", 6, 6, DesignLevel.COMPREHENSIVE, "4-1");
        addCommonElementElectives(y, true);
    }

    private void seed2021() {
        int y = 2021;
        cc(y, "GEN_WRITE", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_CAREER_EXP", 1, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_STARTUP1", 1, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_VOLUNTEER1", 1, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_PHILOSOPHY", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-2");
        cc(y, "GEN_ENG_LISTEN", 2, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-2");
        cc(y, "GEN_ENG_READ", 2, 0, DesignLevel.NONE, CourseRole.REQUIRED, "2-2");
        cc(y, "GEN_SEMINAR", 1, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_WORLD_HIST", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "2-1");
        cc(y, "GEN_ADV_PROG_P", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");

        bsm(y, "BSM_CALC", 3, "1-1");
        bsm(y, "BSM_PHYS_LAB", 3, "1-1");
        bsm(y, "BSM_EMATH1", 3, "1-2");
        bsm(y, "BSM_DISC", 3, "2-2");
        bsm(y, "BSM_PROB_PROG", 3, "3-1");
        bsm(y, "BSM_LINEAR_PROG", 3, "3-2");

        majReq(y, "MAJ_C", 4, 0, DesignLevel.NONE, "1-1");
        majReq(y, "MAJ_ADV_C", 4, 1, DesignLevel.ELEMENT, "1-2");
        majReq(y, "MAJ_BASIC_DESIGN", 3, 3, DesignLevel.BASIC, "1-2");
        majReq(y, "MAJ_DIGITAL", 3, 1, DesignLevel.ELEMENT, "2-1");
        majReq(y, "MAJ_DS", 4, 0, DesignLevel.NONE, "2-1");
        majReq(y, "MAJ_ALGO", 4, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_ARCH", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_OS", 3, 0, DesignLevel.NONE, "3-1");
        majReq(y, "MAJ_NETWORK", 3, 0, DesignLevel.NONE, "3-2");
        majReq(y, "MAJ_CAPSTONE", 6, 6, DesignLevel.COMPREHENSIVE, "4-1");
        addCommonElementElectives(y, true);
    }

    private void seed2022() {
        int y = 2022;
        seedModernGeneral(y, false);
        bsm(y, "BSM_CALC", 3, "1-1");
        bsm(y, "BSM_PHYS_LAB", 3, "1-1");
        bsm(y, "BSM_EMATH1", 3, "1-2");
        bsm(y, "BSM_PROB_PROG", 3, "2-1");
        bsm(y, "BSM_DISC", 3, "2-2");
        bsm(y, "BSM_LINEAR_PROG", 3, "2-2");

        majReq(y, "MAJ_C", 3, 0, DesignLevel.NONE, "1-1");
        majReq(y, "MAJ_ADV_C", 3, 1, DesignLevel.ELEMENT, "1-2");
        majReq(y, "MAJ_BASIC_DESIGN", 3, 3, DesignLevel.BASIC, "1-2");
        majReq(y, "MAJ_DIGITAL", 3, 1, DesignLevel.ELEMENT, "2-1");
        majReq(y, "MAJ_DS", 3, 0, DesignLevel.NONE, "2-1");
        majReq(y, "MAJ_ALGO", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_OS", 3, 0, DesignLevel.NONE, "3-1");
        majReq(y, "MAJ_NETWORK", 3, 0, DesignLevel.NONE, "3-2");
        majReq(y, "MAJ_ADV_DESIGN", 3, 2, DesignLevel.ELEMENT, "3-2");
        majReq(y, "MAJ_CAPSTONE", 6, 6, DesignLevel.COMPREHENSIVE, "4-1");
        // 2022: 컴퓨터구조는 선택
        cc(y, "MAJ_ARCH", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "2-2");
        addCommonElementElectives(y, false);
    }

    private void seed2023() {
        int y = 2023;
        seedModernGeneral(y, false);
        bsm(y, "BSM_CALC", 3, "1-1");
        bsm(y, "BSM_PHYS", 3, "1-1");
        bsm(y, "BSM_EMATH1", 3, "1-2");
        bsm(y, "BSM_PROB_PROG", 3, "2-1");
        bsm(y, "BSM_DISC", 3, "2-2");
        bsm(y, "BSM_LINEAR_PROG", 3, "2-2");

        majReq(y, "MAJ_C", 3, 0, DesignLevel.NONE, "1-1");
        majReq(y, "MAJ_ADV_C", 3, 1, DesignLevel.ELEMENT, "1-2");
        majReq(y, "MAJ_BASIC_DESIGN", 3, 3, DesignLevel.BASIC, "1-2");
        majReq(y, "MAJ_DIGITAL", 3, 1, DesignLevel.ELEMENT, "2-1");
        majReq(y, "MAJ_DS", 3, 0, DesignLevel.NONE, "2-1");
        majReq(y, "MAJ_ALGO", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_ARCH", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_OS", 3, 0, DesignLevel.NONE, "3-1");
        majReq(y, "MAJ_NETWORK", 3, 1, DesignLevel.ELEMENT, "3-2");
        majReq(y, "MAJ_ADV_DESIGN", 3, 2, DesignLevel.ELEMENT, "3-2");
        majReq(y, "MAJ_CAPSTONE", 6, 6, DesignLevel.COMPREHENSIVE, "4-1");
        addCommonElementElectives(y, false);
    }

    private void seed2024() {
        int y = 2024;
        seedModernGeneral(y, false);
        bsm(y, "BSM_CALC1", 3, "1-1");
        bsm(y, "BSM_PROB", 3, "1-1");
        bsm(y, "BSM_LINEAR", 3, "1-2");
        bsm(y, "BSM_EMATH1", 3, "1-2");
        bsm(y, "BSM_DISC", 3, "2-2");
        bsm(y, "BSM_PHYS", 3, "2-2");

        majReq(y, "MAJ_C", 3, 0, DesignLevel.NONE, "1-1");
        majReq(y, "MAJ_ADV_C", 3, 1, DesignLevel.ELEMENT, "1-2");
        majReq(y, "MAJ_DIGITAL", 3, 1, DesignLevel.ELEMENT, "2-1");
        majReq(y, "MAJ_DS", 3, 0, DesignLevel.NONE, "2-1");
        majReq(y, "MAJ_BASIC_DESIGN", 3, 3, DesignLevel.BASIC, "2-1");
        majReq(y, "MAJ_ALGO", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_ARCH", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_OS", 3, 0, DesignLevel.NONE, "3-1");
        majReq(y, "MAJ_NETWORK", 3, 0, DesignLevel.NONE, "3-2");
        majReq(y, "MAJ_CAPSTONE", 6, 6, DesignLevel.COMPREHENSIVE, "4-1");
        addCommonElementElectives(y, false);
        cc(y, "MAJ_ADV_DESIGN", 3, 2, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-2");
    }

    private void seed2025() {
        int y = 2025;
        seedModernGeneral(y, false);
        bsm(y, "BSM_CALC1", 3, "1-1");
        bsm(y, "BSM_PROB", 3, "1-1");
        bsm(y, "BSM_LINEAR", 3, "1-2");
        bsm(y, "BSM_EMATH1", 3, "1-2");
        bsm(y, "BSM_DISC", 3, "2-2");
        bsm(y, "BSM_PHYS", 3, "2-2");

        majReq(y, "MAJ_C", 3, 0, DesignLevel.NONE, "1-1");
        majReq(y, "MAJ_ADV_C", 3, 1, DesignLevel.ELEMENT, "1-2");
        majReq(y, "MAJ_DIGITAL", 3, 1, DesignLevel.ELEMENT, "2-1");
        majReq(y, "MAJ_DS", 3, 0, DesignLevel.NONE, "2-1");
        majReq(y, "MAJ_BASIC_DESIGN", 3, 3, DesignLevel.BASIC, "2-1");
        majReq(y, "MAJ_ALGO", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_ARCH", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_OS", 3, 0, DesignLevel.NONE, "3-1");
        majReq(y, "MAJ_NETWORK", 3, 0, DesignLevel.NONE, "3-2");
        // 2025 신설 필수
        CurriculumCourse swai = majReq(y, "MAJ_SW_AI", 1, 0, DesignLevel.NONE, "4-1");
        swai.setNewlyIntroducedRequired(true);
        curriculumCourseRepository.save(swai);
        majReq(y, "MAJ_CAPSTONE", 6, 6, DesignLevel.COMPREHENSIVE, "4-1");
        addCommonElementElectives(y, false);
        cc(y, "MAJ_LINUX", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-2");
        cc(y, "MAJ_INTEL_NET", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "4-1");
        cc(y, "MAJ_INTEL_EDGE", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "4-1");
        cc(y, "MAJ_ADV_DESIGN", 3, 2, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-2");
    }

    private void seed2026() {
        int y = 2026;
        // 교양: 글쓰기 과목명 변경
        cc(y, "GEN_WRITE_2026", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_UNI_ENG", 2, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-2");
        cc(y, "GEN_PHILOSOPHY", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-2");
        seedCertElectives(y);

        bsm(y, "BSM_CALC1", 3, "1-1");
        bsm(y, "BSM_PROB", 3, "1-1");
        bsm(y, "BSM_LINEAR", 3, "1-2");
        bsm(y, "BSM_EMATH1", 3, "1-2");
        bsm(y, "BSM_DISC", 3, "2-2");
        bsm(y, "BSM_PHYS", 4, "2-2"); // 2026: 4학점

        majReq(y, "MAJ_C", 3, 0, DesignLevel.NONE, "1-1");
        majReq(y, "MAJ_ADV_C", 3, 1, DesignLevel.ELEMENT, "1-2");
        majReq(y, "MAJ_DIGITAL", 3, 1, DesignLevel.ELEMENT, "2-1");
        majReq(y, "MAJ_DS", 3, 0, DesignLevel.NONE, "2-1");
        majReq(y, "MAJ_BASIC_DESIGN", 3, 3, DesignLevel.BASIC, "2-1");
        majReq(y, "MAJ_ALGO", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_ARCH", 3, 0, DesignLevel.NONE, "2-2");
        majReq(y, "MAJ_OS", 3, 0, DesignLevel.NONE, "3-1");
        majReq(y, "MAJ_NETWORK", 3, 0, DesignLevel.NONE, "3-2");
        CurriculumCourse swai = majReq(y, "MAJ_SW_AI", 1, 0, DesignLevel.NONE, "4-1");
        swai.setNewlyIntroducedRequired(true);
        curriculumCourseRepository.save(swai);
        majReq(y, "MAJ_CAPSTONE", 6, 6, DesignLevel.COMPREHENSIVE, "4-1");
        addCommonElementElectives(y, false);
        cc(y, "MAJ_LINUX", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-2");
        cc(y, "MAJ_INTEL_NET", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "4-1");
        cc(y, "MAJ_INTEL_EDGE", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "4-1");
        cc(y, "MAJ_ADV_DESIGN", 3, 2, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-2");
    }

    private void seedModernGeneral(int y, boolean ignored) {
        cc(y, "GEN_WRITE", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-1");
        cc(y, "GEN_UNI_ENG", 2, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-2");
        cc(y, "GEN_PHILOSOPHY", 3, 0, DesignLevel.NONE, CourseRole.REQUIRED, "1-2");
        seedCertElectives(y);
    }

    private void seedCertElectives(int y) {
        cc(y, "GEN_WORLD_HIST2", 3, 0, DesignLevel.NONE, CourseRole.CERT_ELECTIVE, "2-1");
        cc(y, "GEN_EAST_WEST", 3, 0, DesignLevel.NONE, CourseRole.CERT_ELECTIVE, "2-1");
        cc(y, "GEN_ECON", 3, 0, DesignLevel.NONE, CourseRole.CERT_ELECTIVE, "2-1");
        cc(y, "GEN_MGMT", 3, 0, DesignLevel.NONE, CourseRole.CERT_ELECTIVE, "2-1");
        cc(y, "GEN_METAVERSE", 3, 0, DesignLevel.NONE, CourseRole.CERT_ELECTIVE, "2-2");
        cc(y, "GEN_FUSION_ART", 3, 0, DesignLevel.NONE, CourseRole.CERT_ELECTIVE, "2-2");
    }

    private void addCommonElementElectives(int y, boolean includeAdvDesignAsElective) {
        cc(y, "MAJ_CPP", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "2-1");
        cc(y, "MAJ_JAVA", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "2-2");
        cc(y, "MAJ_WIN", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "2-1");
        cc(y, "MAJ_WEB", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "2-1");
        cc(y, "MAJ_OSS_INTRO", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "2-2");
        cc(y, "MAJ_OSS_ENG", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-1");
        cc(y, "MAJ_DB", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-1");
        cc(y, "MAJ_CS", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-1");
        cc(y, "MAJ_MICRO", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-1");
        cc(y, "MAJ_GRAPHICS", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-1");
        cc(y, "MAJ_UNIX", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-2");
        cc(y, "MAJ_XML", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-2");
        cc(y, "MAJ_IMG", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "4-1");
        cc(y, "MAJ_EMBED", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "4-1");
        cc(y, "MAJ_NET_PROG", 3, 1, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "4-1");
        cc(y, "MAJ_SE", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "3-2");
        cc(y, "MAJ_AI", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "3-2");
        cc(y, "MAJ_SIGNAL", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "3-1");
        cc(y, "MAJ_PL", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "3-1");
        cc(y, "MAJ_SERVER", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "3-1");
        cc(y, "MAJ_NOMAD", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "1-2");
        if (includeAdvDesignAsElective) {
            cc(y, "MAJ_ADV_DESIGN", 3, 2, DesignLevel.ELEMENT, CourseRole.ELECTIVE, "3-2");
        }
        cc(y, "MAJ_HCI", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "4-1");
        cc(y, "MAJ_SEC", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "4-1");
        cc(y, "MAJ_COMPILER", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "4-2");
        cc(y, "MAJ_PATENT", 3, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "4-2");
        cc(y, "GEN_GRAD1", 1, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "4-1");
        cc(y, "GEN_GRAD2", 1, 0, DesignLevel.NONE, CourseRole.ELECTIVE, "4-2");
    }

    private void bsm(int y, String code, int credits, String term) {
        cc(y, code, credits, 0, DesignLevel.NONE, CourseRole.BSM_REQUIRED, term);
    }

    private CurriculumCourse majReq(int y, String code, int credits, double design, DesignLevel level, String term) {
        return cc(y, code, credits, design, level, CourseRole.REQUIRED, term);
    }

    private CurriculumCourse cc(
            int year, String code, int credits, double designCredits,
            DesignLevel level, CourseRole role, String term
    ) {
        CourseMaster master = masters.get(code);
        if (master == null) {
            throw new IllegalStateException("미등록 과목코드: " + code);
        }
        return curriculumCourseRepository.save(CurriculumCourse.builder()
                .curriculumYear(year)
                .courseMaster(master)
                .credits(credits)
                .designCredits(designCredits)
                .designLevel(level == DesignLevel.NONE && designCredits > 0 ? DesignLevel.ELEMENT : level)
                .role(role)
                .recommendedTerm(term)
                .newlyIntroducedRequired(false)
                .build());
    }
}
