package com.example.congraduation.service.graduation;

import com.example.congraduation.domain.Student;
import com.example.congraduation.exception.DepartmentPolicyNotConfiguredException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;

@Service
public class DepartmentCurriculumPolicyService {

    private static final Map<String, DepartmentCurriculumPolicy> POLICIES = loadPolicies();
    private static final Set<String> BUSINESS_HOTEL_MAJOR_FOUNDATION_DEPARTMENTS = Set.of(
            "경영학부",
            "경제학과",
            "호텔관광경영학전공",
            "외식경영학전공"
    );
    private static final Set<String> BUSINESS_HOTEL_MAJOR_FOUNDATION_COURSES = Set.of(
            "경영학원론",
            "경제학원론",
            "Hospitality경영원론"
    );
    private static final Set<String> NATURAL_LIFE_MAJOR_FOUNDATION_DEPARTMENTS = Set.of(
            "물리천문학과",
            "화학과",
            "식품생명공학전공",
            "바이오융합공학전공",
            "바이오산업자원공학전공",
            "스마트생명산업융합학과"
    );
    private static final Set<String> NATURAL_LIFE_MAJOR_FOUNDATION_REQUIRED_COURSES = Set.of(
            "일반물리학1",
            "일반화학1",
            "일반생물학1"
    );
    private static final Set<String> NATURAL_LIFE_MAJOR_FOUNDATION_OPTIONAL_COURSES_2024_2026 = Set.of(
            "미적분학2",
            "기초통계학",
            "기초천문학",
            "기초생물통계학",
            "일반물리학2",
            "일반화학2",
            "일반생물학2"
    );
    private static final Set<String> IT_MAJOR_FOUNDATION_DEPARTMENTS_2024 = Set.of(
            "전자정보통신공학과",
            "반도체시스템공학과",
            "컴퓨터공학과",
            "정보보호학과",
            "소프트웨어학과",
            "AI로봇학과",
            "인공지능데이터사이언스학과"
    );
    private static final Set<String> IT_MAJOR_FOUNDATION_DEPARTMENTS_2025_2026 = Set.of(
            "AI융합전자공학과",
            "반도체시스템공학과",
            "컴퓨터공학과",
            "정보보호학과",
            "양자지능정보학과",
            "AI로봇학과",
            "인공지능데이터사이언스학과",
            "지능정보융합학과",
            "콘텐츠소프트웨어학과"
    );
    private static final Set<String> IT_MAJOR_FOUNDATION_COURSES = Set.of(
            "확률및통계",
            "확률통계및프로그래밍",
            "C프로그래밍및실습",
            "고급C프로그래밍및실습",
            "선형대수",
            "선형대수및프로그래밍",
            "공업수학1"
    );
    private static final Set<String> ENGINEERING_MAJOR_FOUNDATION_COMMON_DEPARTMENTS = Set.of(
            "건축공학과",
            "건설환경공학과",
            "환경에너지공간융합학과",
            "환경융합공학과",
            "지구자원시스템공학과",
            "에너지자원공학과",
            "기계공학과",
            "우주항공공학전공",
            "지능형드론융합전공",
            "항공시스템공학전공",
            "나노신소재공학과",
            "양자원자력공학과"
    );
    private static final Set<String> ENGINEERING_MAJOR_FOUNDATION_COMMON_COURSES = Set.of(
            "공업수학1",
            "공업수학2",
            "일반물리학1",
            "일반화학1"
    );
    private static final Set<String> ENGINEERING_MAJOR_FOUNDATION_ARCHITECTURE_DEPARTMENTS = Set.of("건축학과");
    private static final Set<String> ENGINEERING_MAJOR_FOUNDATION_ARCHITECTURE_COURSES = Set.of(
            "통계학개론",
            "일반물리학1",
            "일반화학1"
    );
    private static final Set<String> ENGINEERING_MAJOR_FOUNDATION_DEFENSE_AIR_2024_2025_DEPARTMENTS = Set.of(
            "국방시스템공학과",
            "항공시스템공학전공"
    );
    private static final Set<String> ENGINEERING_MAJOR_FOUNDATION_DEFENSE_AIR_2026_DEPARTMENTS = Set.of(
            "국방AI융합시스템공학과",
            "항공시스템공학전공"
    );
    private static final Set<String> ENGINEERING_MAJOR_FOUNDATION_DEFENSE_AIR_COURSES = Set.of(
            "공업수학1",
            "공업수학2",
            "일반물리학1",
            "일반물리학2"
    );

    private static final String POLICY_DATA = """
            2021|국어국문학과|14|0|0|-|60|15|45|130
            2021|영어영문학전공|14|0|0|-|63|18|45|130
            2021|일어일문학전공|14|0|0|-|63|18|45|130
            2021|중국통상학전공|14|0|0|-|66|18|48|130
            2021|역사학과|14|0|0|-|60|15|45|130
            2021|교육학과|14|0|0|-|63|18|45|130
            2021|행정학과|14|0|0|-|60|15|45|130
            2021|미디어커뮤니케이션학과|14|0|0|-|60|18|42|130
            2021|경영학부|14|0|0|-|78|30|48|130
            2021|경제학과|14|0|0|-|72|24|48|130
            2021|호텔관광경영학전공|14|0|0|-|60|24|36|130
            2021|외식경영학전공|14|0|0|-|60|21|39|130
            2021|호텔외식관광프랜차이즈경영학과|0|0|0|-|60|15|45|130
            2021|글로벌조리학과|0|0|0|-|60|15|45|130
            2021|호텔외식비즈니스학과|0|0|0|-|60|15|45|130
            2021|수학전공|14|0|6|-|60|15|45|130
            2021|응용통계학전공|14|0|6|-|60|15|45|130
            2021|물리천문학과|14|0|6|-|60|15|45|130
            2021|화학과|14|0|15|-|68|15|53|130
            2021|식품생명공학전공|14|0|9|-|72|21|51|130
            2021|바이오융합공학전공|14|0|9|-|72|21|51|130
            2021|바이오산업자원공학전공|14|0|9|-|72|24|48|130
            2021|스마트생명산업융합학과|14|0|9|-|72|24|48|130
            2021|전자정보통신공학과|14|0|18|-|72|30|42|130
            2021|컴퓨터공학과|14|0|9|-|72|33|39|130
            2021|정보보호학과|14|0|9|-|72|36|36|130
            2021|소프트웨어학과|14|0|9|-|72|36|36|130
            2021|데이터사이언스학과|14|0|12|-|72|36|36|130
            2021|무인이동체공학전공|14|0|9|-|72|36|36|130
            2021|스마트기기공학전공|14|0|9|-|72|36|36|130
            2021|디자인이노베이션전공|14|0|0|-|63|14|49|130
            2021|만화애니메이션텍전공|14|0|0|-|62|28|34|130
            2021|인공지능학과|14|0|9|-|72|36|36|130
            2021|건축공학과|14|0|24|-|63|27|36|130
            2021|건축학과|14|0|6|-|123|99|24|168
            2021|건설환경공학과|14|0|21|-|69|27|42|130
            2021|환경에너지공간융합학과|14|0|24|-|63|15|48|130
            2021|지구자원시스템공학과|14|0|21|-|60|33|27|130
            2021|기계공학과|14|0|21|-|60|21|39|130
            2021|항공우주공학전공|14|0|21|-|63|24|39|130
            2021|나노신소재공학과|14|0|24|-|69|27|42|130
            2021|양자원자력공학과|14|0|18|-|63|27|36|130
            2021|국방시스템공학과|13|0|18|-|73|45|28|130
            2021|항공시스템공학전공|13|0|18|-|64|38|26|130
            2021|회화과|14|0|0|-|66|26|40|130
            2021|패션디자인학과|14|0|0|-|72|27|45|130
            2021|음악과|14|0|0|-|66|24|42|130
            2021|체육학과|0|0|0|-|65|22|43|130
            2021|무용과|14|0|0|-|66|24|42|130
            2021|영화예술학과|14|0|0|-|72|27|45|130
            2021|법학전공|14|0|0|-|60|24|36|130
            2021|영상디자인 융합전공|0|0|0|-|60|21|39|130
            2021|문화산업경영 융합전공|0|0|0|-|60|21|39|130
            2021|럭셔리브랜드디자인 융합전공|0|0|0|-|60|21|39|130
            2021|뉴미디어퍼포먼스 융합전공|0|0|0|-|60|21|39|130
            2022|국어국문학과|13|6|9|-|60|15|45|130
            2022|영어영문학전공|13|6|6|-|63|18|45|130
            2022|일어일문학전공|13|6|6|-|63|18|45|130
            2022|중국통상학전공|13|6|6|-|66|18|48|130
            2022|역사학과|13|6|6|-|60|15|45|130
            2022|교육학과|13|6|6|-|63|18|45|130
            2022|행정학과|13|6|9|-|60|15|45|130
            2022|미디어커뮤니케이션학과|13|6|6|-|60|18|42|130
            2022|경영학부|13|6|9|-|81|33|48|130
            2022|경제학과|13|6|12|-|72|24|48|130
            2022|호텔관광경영학전공|13|6|9|-|60|24|36|130
            2022|외식경영학전공|13|6|9|-|60|21|39|130
            2022|호텔외식관광프랜차이즈경영학과|0|6|0|-|60|15|45|130
            2022|글로벌조리학과|0|6|0|-|60|15|45|130
            2022|호텔외식비즈니스학과|0|6|0|-|60|15|45|130
            2022|수학통계학과|13|6|15|-|69|24|45|130
            2022|물리천문학과|13|6|15|-|60|15|45|130
            2022|화학과|13|6|24|-|68|15|53|130
            2022|식품생명공학전공|13|6|12|-|72|21|51|130
            2022|바이오융합공학전공|13|6|12|-|72|21|51|130
            2022|바이오산업자원공학전공|13|6|12|-|72|24|48|130
            2022|스마트생명산업융합학과|13|6|15|-|72|24|48|130
            2022|전자정보통신공학과|13|6|24|-|72|33|39|130
            2022|컴퓨터공학과|13|6|15|-|72|33|39|130
            2022|정보보호학과|13|6|15|-|72|36|36|130
            2022|소프트웨어학과|13|6|15|-|72|36|36|130
            2022|데이터사이언스학과|13|6|21|-|72|36|36|130
            2022|무인이동체공학전공|13|6|15|-|72|36|36|130
            2022|스마트기기공학전공|13|6|15|-|72|36|36|130
            2022|디자인이노베이션전공|13|6|6|-|63|14|49|130
            2022|만화애니메이션텍전공|13|6|6|-|62|28|34|130
            2022|인공지능학과|13|6|18|-|72|36|36|130
            2022|건축공학과|13|6|30|-|63|27|36|130
            2022|건축학과|13|6|9|-|123|99|24|168
            2022|건설환경공학과|13|6|30|-|69|27|42|130
            2022|환경에너지공간융합학과|13|6|30|-|63|15|48|130
            2022|지구자원시스템공학과|13|6|30|-|60|33|27|130
            2022|기계공학과|13|6|30|-|60|21|39|130
            2022|항공우주공학전공|13|6|30|-|63|24|39|130
            2022|나노신소재공학과|13|6|30|-|69|24|45|130
            2022|양자원자력공학과|13|6|30|-|63|27|36|130
            2022|국방시스템공학과|12|6|27|-|73|45|28|130
            2022|항공시스템공학전공|12|6|24|-|64|38|26|130
            2022|회화과|13|6|6|-|66|26|40|130
            2022|패션디자인학과|13|6|6|-|72|27|45|130
            2022|음악과|13|6|6|-|66|24|42|130
            2022|체육학과|0|6|6|-|65|22|43|130
            2022|무용과|13|6|6|-|66|24|42|130
            2022|영화예술학과|13|6|6|-|72|27|45|130
            2022|법학전공|13|6|6|-|60|24|36|130
            2022|영상디자인 융합전공|0|0|0|-|60|21|39|130
            2022|문화산업경영 융합전공|0|0|0|-|60|21|39|130
            2022|럭셔리브랜드디자인 융합전공|0|0|0|-|60|21|39|130
            2022|뉴미디어퍼포먼스 융합전공|0|0|0|-|60|21|39|130
            2023|국어국문학과|13|6|9|-|60|15|45|130
            2023|영어영문학전공|13|6|6|-|63|18|45|130
            2023|일어일문학전공|13|6|6|-|63|18|45|130
            2023|중국통상학전공|13|6|6|-|66|18|48|130
            2023|역사학과|13|6|6|-|60|15|45|130
            2023|교육학과|13|6|6|-|63|18|45|130
            2023|행정학과|13|6|9|-|60|15|45|130
            2023|미디어커뮤니케이션학과|13|6|6|-|60|18|42|130
            2023|경영학부|13|6|9|-|81|33|48|130
            2023|경제학과|13|6|12|-|72|21|51|130
            2023|호텔관광경영학전공|13|6|9|-|60|24|36|130
            2023|외식경영학전공|13|6|9|-|60|21|39|130
            2023|호텔외식관광프랜차이즈경영학과|0|6|0|-|60|15|45|130
            2023|글로벌조리학과|0|6|0|-|60|15|45|130
            2023|호텔외식비즈니스학과|0|6|0|-|60|15|45|130
            2023|수학통계학과|13|6|15|-|69|24|45|130
            2023|물리천문학과|13|6|15|-|60|15|45|130
            2023|화학과|13|6|24|-|68|15|53|130
            2023|식품생명공학전공|13|6|12|-|72|21|51|130
            2023|바이오융합공학전공|13|6|12|-|72|21|51|130
            2023|바이오산업자원공학전공|13|6|12|-|72|24|48|130
            2023|스마트생명산업융합학과|13|6|15|-|72|24|48|130
            2023|전자정보통신공학과|13|6|24|-|72|33|39|130
            2023|반도체시스템공학과|13|6|24|-|60|15|45|130
            2023|컴퓨터공학과|13|6|15|-|72|33|39|130
            2023|정보보호학과|13|6|15|-|72|36|36|130
            2023|소프트웨어학과|13|6|15|-|72|36|36|130
            2023|데이터사이언스학과|13|6|21|-|72|36|36|130
            2023|지능기전공학과|13|6|15|-|72|30|42|130
            2023|디자인이노베이션전공|13|6|6|-|63|14|49|130
            2023|만화애니메이션텍전공|13|6|6|-|62|28|34|130
            2023|인공지능학과|13|6|18|-|72|36|36|130
            2023|건축공학과|13|6|30|-|63|27|36|130
            2023|건축학과|13|6|9|-|123|99|24|168
            2023|건설환경공학과|13|6|30|-|69|27|42|130
            2023|환경에너지공간융합학과|13|6|30|-|63|15|48|130
            2023|지구자원시스템공학과|13|6|30|-|69|30|39|130
            2023|기계공학과|13|6|30|-|60|21|39|130
            2023|우주항공공학전공|13|6|30|-|63|21|42|130
            2023|항공시스템공학전공|12|6|24|-|64|38|26|130
            2023|나노신소재공학과|13|6|30|-|69|24|45|130
            2023|양자원자력공학과|13|6|30|-|63|27|36|130
            2023|국방시스템공학과|12|6|27|-|73|45|28|130
            2023|회화과|13|6|6|-|66|26|40|130
            2023|패션디자인학과|13|6|6|-|72|27|45|130
            2023|음악과|13|6|6|-|66|24|42|130
            2023|체육학과|0|6|6|-|65|22|43|130
            2023|무용과|13|6|6|-|66|24|42|130
            2023|영화예술학과|13|6|6|-|72|27|45|130
            2023|법학전공|13|6|6|-|60|24|36|130
            2023|영상디자인 융합전공|0|0|0|-|60|21|39|130
            2023|문화산업경영 융합전공|0|0|0|-|60|21|39|130
            2023|럭셔리브랜드디자인 융합전공|0|0|0|-|60|21|39|130
            2023|뉴미디어퍼포먼스 융합전공|0|0|0|-|60|21|39|130
            2024|국어국문학과|13|9|6|0|60|21|39|130
            2024|영어영문학전공|13|9|6|0|60|18|42|130
            2024|일어일문학전공|13|9|6|0|60|18|42|130
            2024|중국통상학전공|13|9|6|0|60|18|42|130
            2024|역사학과|13|9|6|0|60|18|42|130
            2024|교육학과|13|9|6|0|60|18|42|130
            2024|한국언어문화전공|17|0|0|0|60|18|42|130
            2024|국제통상전공|17|0|0|0|60|18|42|130
            2024|국제협력전공|17|0|0|0|60|18|42|130
            2024|행정학과|13|9|6|0|60|15|45|130
            2024|미디어커뮤니케이션학과|13|9|6|0|60|18|42|130
            2024|법학전공|13|9|6|0|60|21|39|130
            2024|경영학부|13|9|9|9|66|24|42|130
            2024|경제학과|13|9|9|9|60|15|45|130
            2024|호텔관광경영학전공|13|9|9|9|60|21|39|130
            2024|외식경영학전공|13|9|9|9|60|21|39|130
            2024|호텔외식관광프랜차이즈경영학과|0|9|0|0|60|15|45|130
            2024|글로벌조리학과|0|9|0|0|60|15|45|130
            2024|호텔외식비즈니스학과|0|9|0|0|60|15|45|130
            2024|수학통계학과|13|9|9|15|60|21|39|130
            2024|물리천문학과|13|9|9|15|60|15|45|130
            2024|화학과|13|9|9|15|60|15|45|130
            2024|식품생명공학전공|13|9|9|15|60|21|39|130
            2024|바이오융합공학전공|13|9|9|15|60|21|39|130
            2024|바이오산업자원공학전공|13|9|9|15|60|21|39|130
            2024|스마트생명산업융합학과|13|9|9|15|60|21|39|130
            2024|전자정보통신공학과|13|9|9|15|72|33|39|130
            2024|반도체시스템공학과|13|9|9|15|60|18|42|130
            2024|컴퓨터공학과|13|9|9|15|60|21|39|130
            2024|정보보호학과|13|9|9|15|60|24|36|130
            2024|소프트웨어학과|13|9|9|15|60|27|33|130
            2024|AI로봇학과|13|9|9|15|60|21|39|130
            2024|인공지능데이터사이언스학과|13|9|9|15|60|24|36|130
            2024|디자인이노베이션전공|13|9|6|0|63|14|49|130
            2024|만화애니메이션텍전공|13|9|6|0|60|28|32|130
            2024|건축공학과|13|9|12|12|60|21|39|130
            2024|건축학과|13|9|15|9|117|93|24|163
            2024|건설환경공학과|13|9|12|12|60|21|39|130
            2024|환경에너지공간융합학과|13|9|12|12|60|21|39|130
            2024|지구자원시스템공학과|13|9|12|12|60|21|39|130
            2024|기계공학과|13|9|12|12|60|21|39|130
            2024|우주항공공학전공|13|9|12|12|60|21|39|130
            2024|지능형드론융합전공|13|9|12|12|60|21|39|130
            2024|항공시스템공학전공|12|9|12|12|64|38|26|130
            2024|나노신소재공학과|13|9|12|12|60|21|39|130
            2024|양자원자력공학과|13|9|12|12|60|21|39|130
            2024|국방시스템공학과|12|9|12|12|73|45|28|130
            2024|회화과|13|9|6|0|66|26|40|130
            2024|패션디자인학과|13|9|6|0|66|27|39|130
            2024|음악과|13|9|6|0|66|24|42|130
            2024|체육학과|13|9|6|0|66|24|42|130
            2024|무용과|13|9|6|0|66|24|42|130
            2024|영화예술학과|13|9|6|0|66|27|39|130
            2024|영상디자인 융합전공|0|0|0|0|60|21|39|130
            2024|문화산업경영 융합전공|0|0|0|0|60|21|39|130
            2024|럭셔리브랜드디자인 융합전공|0|0|0|0|60|21|39|130
            2024|뉴미디어퍼포먼스 융합전공|0|0|0|0|60|21|39|130
            2025|국어국문학과|13|9|6|0|60|21|39|130
            2025|영어데이터융합전공|13|9|6|0|60|18|42|130
            2025|일어일문학전공|13|9|6|0|60|18|42|130
            2025|중국통상학전공|13|9|6|0|60|18|42|130
            2025|역사학과|13|9|6|0|60|18|42|130
            2025|교육학과|13|9|6|0|60|18|42|130
            2025|한국언어문화전공|17|0|0|0|60|18|42|130
            2025|국제통상전공|17|0|0|0|60|18|42|130
            2025|국제협력전공|17|0|0|0|60|18|42|130
            2025|행정학과|13|9|6|0|60|15|45|130
            2025|미디어커뮤니케이션학과|13|9|6|0|60|18|42|130
            2025|법학전공|13|9|6|0|60|21|39|130
            2025|경영학부|13|9|9|9|66|24|42|130
            2025|경제학과|13|9|9|9|60|15|45|130
            2025|호텔관광경영학전공|13|9|9|9|60|21|39|130
            2025|외식경영학전공|13|9|9|9|60|21|39|130
            2025|호텔외식관광프랜차이즈경영학과|0|9|0|0|60|15|45|130
            2025|글로벌조리학과|0|9|0|0|60|15|45|130
            2025|호텔외식비즈니스학과|0|9|0|0|60|15|45|130
            2025|수학통계학과|13|9|9|15|60|21|39|130
            2025|물리천문학과|13|9|9|15|60|15|45|130
            2025|화학과|13|9|9|15|60|15|45|130
            2025|식품생명공학전공|13|9|9|15|60|21|39|130
            2025|바이오융합공학전공|13|9|9|15|60|21|39|130
            2025|바이오산업자원공학전공|13|9|9|15|60|21|39|130
            2025|스마트생명산업융합학과|13|9|9|15|60|21|39|130
            2025|AI융합전자공학과|13|9|9|15|72|33|39|130
            2025|반도체시스템공학과|13|9|9|15|60|18|42|130
            2025|컴퓨터공학과|13|9|9|15|60|21|39|130
            2025|정보보호학과|13|9|9|15|60|24|36|130
            2025|AI로봇학과|13|9|9|15|60|21|39|130
            2025|인공지능데이터사이언스학과|13|9|9|15|60|24|36|130
            2025|지능정보융합학과|13|9|9|15|60|27|33|130
            2025|콘텐츠소프트웨어학과|13|9|9|15|61|31|30|130
            2025|디자인이노베이션전공|13|9|6|0|63|14|49|130
            2025|만화애니메이션텍전공|13|9|6|0|60|28|32|130
            2025|사이버국방학과|12|9|9|15|76|40|36|140
            2025|건축공학과|13|9|12|12|60|21|39|130
            2025|건축학과|13|9|15|9|117|93|24|163
            2025|건설환경공학과|13|9|12|12|60|21|39|130
            2025|환경융합공학과|13|9|12|12|60|21|39|130
            2025|지구자원시스템공학과|13|9|12|12|60|21|39|130
            2025|기계공학과|13|9|12|12|60|21|39|130
            2025|우주항공공학전공|13|9|12|12|60|21|39|130
            2025|지능형드론융합전공|13|9|12|12|60|21|39|130
            2025|항공시스템공학전공|12|9|12|12|64|38|26|130
            2025|나노신소재공학과|13|9|12|12|60|21|39|130
            2025|양자원자력공학과|13|9|12|12|60|21|39|130
            2025|국방시스템공학과|12|9|12|12|73|45|28|130
            2025|회화과|13|9|6|0|66|26|40|130
            2025|패션디자인학과|13|9|6|0|66|27|39|130
            2025|음악과|13|9|6|0|66|24|42|130
            2025|체육학과|13|9|6|0|66|24|42|130
            2025|무용과|13|9|6|0|66|24|42|130
            2025|영화예술학과|13|9|6|0|66|27|39|130
            2025|영상디자인 융합전공|0|0|0|0|60|21|39|130
            2025|문화산업경영 융합전공|0|0|0|0|60|21|39|130
            2025|럭셔리브랜드디자인 융합전공|0|0|0|0|60|21|39|130
            2025|뉴미디어퍼포먼스 융합전공|0|0|0|0|60|21|39|130
            2026|국어국문학과|12|9|6|0|60|21|39|130
            2026|영어데이터융합전공|12|9|6|0|60|18|42|130
            2026|국제일본학전공|12|9|6|0|60|18|42|130
            2026|중국통상학전공|12|9|6|0|60|18|42|130
            2026|역사학과|12|9|6|0|60|18|42|130
            2026|교육학과|12|9|6|0|60|18|42|130
            2026|한국언어문화전공|14|0|0|0|60|18|42|130
            2026|국제통상전공|14|0|0|0|60|18|42|130
            2026|국제협력전공|14|0|0|0|60|18|42|130
            2026|행정학과|12|9|6|0|60|15|45|130
            2026|미디어커뮤니케이션학과|12|9|6|0|60|18|42|130
            2026|법학전공|12|9|6|0|60|21|39|130
            2026|경영학부|12|9|9|9|66|24|42|130
            2026|경제학과|12|9|9|9|60|15|45|130
            2026|호텔관광경영학전공|12|9|9|9|60|21|39|130
            2026|외식경영학전공|12|9|9|9|60|21|39|130
            2026|호텔외식관광프랜차이즈경영학과|0|9|0|0|60|15|45|130
            2026|조리서비스경영학과|0|9|0|0|60|15|45|130
            2026|호텔외식비즈니스학과|0|9|0|0|60|15|45|130
            2026|수학통계학과|12|9|9|16|60|21|39|130
            2026|물리천문학과|12|9|9|16|60|15|45|130
            2026|화학과|12|9|9|16|60|15|45|130
            2026|식품생명공학전공|12|9|9|16|60|21|39|130
            2026|바이오융합공학전공|12|9|9|16|60|21|39|130
            2026|바이오산업자원공학전공|12|9|9|16|60|21|39|130
            2026|스마트생명산업융합학과|12|9|9|16|60|21|39|130
            2026|AI융합전자공학과|12|9|9|15|69|9|60|130
            2026|반도체시스템공학과|12|9|9|15|66|27|39|130
            2026|컴퓨터공학과|12|9|9|15|60|21|39|130
            2026|정보보호학과|12|9|9|15|60|24|36|130
            2026|양자지능정보학과|12|9|9|15|60|25|35|130
            2026|AI로봇학과|12|9|9|15|60|21|39|130
            2026|인공지능데이터사이언스학과|12|9|9|15|60|24|36|130
            2026|지능정보융합학과|12|9|9|15|60|27|33|130
            2026|콘텐츠소프트웨어학과|12|9|9|15|61|31|30|130
            2026|디자인이노베이션전공|12|9|6|0|63|14|49|130
            2026|만화애니메이션텍전공|12|9|6|0|60|28|32|130
            2026|사이버국방학과|11|9|9|15|76|40|36|140
            2026|국방AI로봇융합공학과|11|9|9|15|73|45|28|130
            2026|건축공학과|12|9|12|13|60|21|39|130
            2026|건축학과|12|9|15|10|117|93|24|163
            2026|건설환경공학과|12|9|12|13|60|21|39|130
            2026|환경융합공학과|12|9|12|13|60|21|39|130
            2026|에너지자원공학과|12|9|12|13|60|21|39|130
            2026|기계공학과|12|9|12|13|60|21|39|130
            2026|우주항공공학전공|12|9|12|13|60|21|39|130
            2026|지능형드론융합전공|12|9|12|13|60|21|39|130
            2026|항공시스템공학전공|11|9|12|14|64|38|26|130
            2026|나노신소재공학과|12|9|12|13|60|21|39|130
            2026|양자원자력공학과|12|9|12|13|60|21|39|130
            2026|국방AI융합시스템공학과|11|9|12|14|73|45|28|130
            2026|회화과|12|9|6|0|66|26|40|130
            2026|패션디자인학과|12|9|6|0|66|27|39|130
            2026|음악과|12|9|6|0|66|24|42|130
            2026|체육학과|12|9|6|0|66|24|42|130
            2026|무용과|12|9|6|0|66|24|42|130
            2026|영화예술학과|12|9|6|0|66|27|39|130
            2026|영상디자인 융합전공|0|0|0|0|60|21|39|130
            2026|문화산업경영 융합전공|0|0|0|0|60|21|39|130
            2026|럭셔리브랜드디자인 융합전공|0|0|0|0|60|21|39|130
            2026|뉴미디어퍼포먼스 융합전공|0|0|0|0|60|21|39|130
            """;

    public DepartmentCurriculumPolicy resolve(Student student) {
        String departmentKey = normalizeMajor(student.getMajor());
        int admissionYear = student.getAdmissionYear() == null ? 0 : student.getAdmissionYear();

        DepartmentCurriculumPolicy policy = POLICIES.get(key(departmentKey, admissionYear));
        if (policy == null) {
            throw new DepartmentPolicyNotConfiguredException(
                    "학과/입학년도 교과과정 정책이 아직 설정되지 않았습니다: "
                            + departmentKey + " / " + admissionYear
            );
        }

        return policy;
    }

    public MajorFoundationCourseRule resolveMajorFoundationCourseRule(Student student) {
        if (student == null || student.getAdmissionYear() == null) {
            return MajorFoundationCourseRule.none();
        }

        String departmentKey = normalizeMajor(student.getMajor());
        int admissionYear = student.getAdmissionYear();

        if (admissionYear <= 2023) {
            return MajorFoundationCourseRule.none();
        }

        if (BUSINESS_HOTEL_MAJOR_FOUNDATION_DEPARTMENTS.contains(departmentKey)) {
            return MajorFoundationCourseRule.required(BUSINESS_HOTEL_MAJOR_FOUNDATION_COURSES);
        }

        if (NATURAL_LIFE_MAJOR_FOUNDATION_DEPARTMENTS.contains(departmentKey)) {
            return MajorFoundationCourseRule.requiredWithOptionalPool(
                    NATURAL_LIFE_MAJOR_FOUNDATION_REQUIRED_COURSES,
                    NATURAL_LIFE_MAJOR_FOUNDATION_OPTIONAL_COURSES_2024_2026,
                    2
            );
        }

        if (admissionYear == 2024 && IT_MAJOR_FOUNDATION_DEPARTMENTS_2024.contains(departmentKey)) {
            return MajorFoundationCourseRule.required(IT_MAJOR_FOUNDATION_COURSES);
        }

        if (admissionYear >= 2025 && IT_MAJOR_FOUNDATION_DEPARTMENTS_2025_2026.contains(departmentKey)) {
            return MajorFoundationCourseRule.required(IT_MAJOR_FOUNDATION_COURSES);
        }

        if (ENGINEERING_MAJOR_FOUNDATION_ARCHITECTURE_DEPARTMENTS.contains(departmentKey)) {
            return MajorFoundationCourseRule.required(ENGINEERING_MAJOR_FOUNDATION_ARCHITECTURE_COURSES);
        }

        if (admissionYear >= 2026 && ENGINEERING_MAJOR_FOUNDATION_DEFENSE_AIR_2026_DEPARTMENTS.contains(departmentKey)) {
            return MajorFoundationCourseRule.required(ENGINEERING_MAJOR_FOUNDATION_DEFENSE_AIR_COURSES);
        }

        if (admissionYear >= 2024 && admissionYear <= 2025
                && ENGINEERING_MAJOR_FOUNDATION_DEFENSE_AIR_2024_2025_DEPARTMENTS.contains(departmentKey)) {
            return MajorFoundationCourseRule.required(ENGINEERING_MAJOR_FOUNDATION_DEFENSE_AIR_COURSES);
        }

        if (ENGINEERING_MAJOR_FOUNDATION_COMMON_DEPARTMENTS.contains(departmentKey)) {
            return MajorFoundationCourseRule.required(ENGINEERING_MAJOR_FOUNDATION_COMMON_COURSES);
        }

        return MajorFoundationCourseRule.none();
    }

    public record MajorFoundationCourseRule(
            Set<String> requiredCourseNames,
            Set<String> optionalCourseNames,
            int optionalCourseLimit
    ) {
        static MajorFoundationCourseRule none() {
            return new MajorFoundationCourseRule(Set.of(), Set.of(), 0);
        }

        static MajorFoundationCourseRule required(Set<String> requiredCourseNames) {
            return new MajorFoundationCourseRule(Set.copyOf(requiredCourseNames), Set.of(), 0);
        }

        static MajorFoundationCourseRule requiredWithOptionalPool(
                Set<String> requiredCourseNames,
                Set<String> optionalCourseNames,
                int optionalCourseLimit
        ) {
            return new MajorFoundationCourseRule(
                    Set.copyOf(requiredCourseNames),
                    Set.copyOf(optionalCourseNames),
                    optionalCourseLimit
            );
        }
    }

    private static Map<String, DepartmentCurriculumPolicy> loadPolicies() {
        Map<String, DepartmentCurriculumPolicy> policies = new HashMap<>();

        Arrays.stream(POLICY_DATA.strip().split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .forEach(line -> {
                    String[] tokens = line.split("\\|");
                    int admissionYear = Integer.parseInt(tokens[0]);
                    String departmentKey = tokens[1];
                    int commonLiberalCredits = Integer.parseInt(tokens[2]);
                    int balancedLiberalCredits = Integer.parseInt(tokens[3]);
                    int academicFoundationCredits = Integer.parseInt(tokens[4]);
                    Integer majorFoundationCredits = "-".equals(tokens[5]) ? null : Integer.parseInt(tokens[5]);
                    int majorTotalCredits = Integer.parseInt(tokens[6]);
                    int majorRequiredCredits = Integer.parseInt(tokens[7]);
                    int majorElectiveCredits = Integer.parseInt(tokens[8]);
                    int graduationCredits = Integer.parseInt(tokens[9]);

                    policies.put(
                            key(departmentKey, admissionYear),
                            policy(
                                    departmentKey,
                                    admissionYear,
                                    commonLiberalCredits,
                                    balancedLiberalCredits,
                                    academicFoundationCredits,
                                    majorFoundationCredits,
                                    graduationCredits,
                                    majorTotalCredits,
                                    majorRequiredCredits,
                                    majorElectiveCredits,
                                    buildCategoryRequirements(
                                            commonLiberalCredits,
                                            balancedLiberalCredits,
                                            academicFoundationCredits,
                                            majorFoundationCredits,
                                            majorRequiredCredits,
                                            majorElectiveCredits
                                    )
                            )
                    );
                });

        return Map.copyOf(policies);
    }

    private static DepartmentCurriculumPolicy policy(
            String departmentKey,
            int admissionYear,
            int commonLiberalCredits,
            int balancedLiberalCredits,
            int academicFoundationCredits,
            Integer majorFoundationCredits,
            int graduationCredits,
            int majorTotalCredits,
            int majorRequiredCredits,
            int majorElectiveCredits,
            Map<String, Integer> categoryRequiredCredits
    ) {
        return new DepartmentCurriculumPolicy(
                departmentKey,
                admissionYear,
                commonLiberalCredits,
                balancedLiberalCredits,
                academicFoundationCredits,
                majorFoundationCredits,
                graduationCredits,
                majorTotalCredits,
                majorRequiredCredits,
                majorElectiveCredits,
                categoryRequiredCredits
        );
    }

    private static String key(String departmentKey, int admissionYear) {
        return departmentKey + ":" + admissionYear;
    }

    private static Map<String, Integer> buildCategoryRequirements(
            int commonLiberalCredits,
            int balancedLiberalCredits,
            int academicFoundationCredits,
            Integer majorFoundationCredits,
            int majorRequiredCredits,
            int majorElectiveCredits
    ) {
        Map<String, Integer> requirements = new HashMap<>();
        if (commonLiberalCredits > 0) {
            requirements.put("공필", commonLiberalCredits);
        }
        if (balancedLiberalCredits > 0) {
            requirements.put("균필", balancedLiberalCredits);
        }
        if (academicFoundationCredits > 0) {
            requirements.put("기필", academicFoundationCredits);
        }
        if (majorFoundationCredits != null && majorFoundationCredits > 0) {
            requirements.put("전기", majorFoundationCredits);
        }
        if (majorRequiredCredits > 0) {
            requirements.put("전필", majorRequiredCredits);
        }
        if (majorElectiveCredits > 0) {
            requirements.put("전선", majorElectiveCredits);
        }
        return Map.copyOf(requirements);
    }

    private String normalizeMajor(String major) {
        if (major == null) {
            return "";
        }

        String normalized = major.trim();
        return switch (normalized) {
            case "컴퓨터공학" -> "컴퓨터공학과";
            case "영어영문학과" -> "영어영문학전공";
            case "일어일문학과" -> "일어일문학전공";
            case "수학전공", "응용통계학전공" -> "수학통계학과";
            case "전자공학과", "전자정보공학과" -> "전자정보통신공학과";
            case "데이터사이언스전공" -> "데이터사이언스학과";
            case "건축공학전공" -> "건축공학과";
            case "건축학전공" -> "건축학과";
            case "기계공학전공" -> "기계공학과";
            case "항공우주공학전공" -> "우주항공공학전공";
            case "법학", "법학과" -> "법학전공";
            default -> normalized;
        };
    }
}
