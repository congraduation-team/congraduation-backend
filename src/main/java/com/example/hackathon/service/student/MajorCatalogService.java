package com.example.hackathon.service.student;

import com.example.hackathon.dto.student.MajorOptionDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MajorCatalogService {

    private static final List<String> MAJORS = List.of(
            "AI로봇학과",
            "건설환경공학과",
            "건축공학과",
            "건축학과",
            "경영학부",
            "경제학과",
            "교육학과",
            "국방시스템공학과",
            "국어국문학과",
            "영어영문학전공",
            "일어일문학전공",
            "중국통상학전공",
            "글로벌조리학과",
            "기계공학과",
            "나노신소재공학과",
            "뉴미디어퍼포먼스 융합전공",
            "럭셔리브랜드디자인 융합전공",
            "무용과",
            "문화산업경영 융합전공",
            "물리천문학과",
            "미디어커뮤니케이션학과",
            "반도체시스템공학과",
            "법학전공",
            "바이오산업자원공학전공",
            "바이오융합공학전공",
            "식품생명공학전공",
            "소프트웨어학과",
            "수학통계학과",
            "스마트생명산업융합학과",
            "양자원자력공학과",
            "역사학과",
            "영상디자인 융합전공",
            "영화예술학과",
            "항공시스템공학전공",
            "우주항공공학전공",
            "음악과",
            "인공지능데이터사이언스학과",
            "전자정보통신공학과",
            "정보보호학과",
            "지구자원시스템공학과",
            "지능형드론융합전공",
            "디자인이노베이션전공",
            "만화애니메이션텍전공",
            "체육학과",
            "컴퓨터공학과",
            "패션디자인학과",
            "행정학과",
            "외식경영학전공",
            "호텔관광경영학전공",
            "호텔외식관광프랜차이즈경영학과",
            "호텔외식비즈니스학과",
            "화학과",
            "환경에너지공간융합학과",
            "회화과",
            "국제통상전공",
            "국제협력전공",
            "한국언어문화전공"
    );

    public List<MajorOptionDto> getMajorOptions() {
        return MAJORS.stream()
                .map(MajorOptionDto::new)
                .toList();
    }
}
