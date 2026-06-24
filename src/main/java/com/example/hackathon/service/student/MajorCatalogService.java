package com.example.hackathon.service.student;

import com.example.hackathon.dto.student.MajorOptionDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MajorCatalogService {

    private static final List<String> MAJORS = List.of(
            "컴퓨터공학과",
            "소프트웨어학과",
            "인공지능데이터사이언스학과",
            "정보보호학과",
            "전자정보통신공학과",
            "전자공학과",
            "건축공학과",
            "건축학과",
            "기계공학과",
            "나노신소재공학과",
            "양자원자력공학과",
            "우주항공시스템공학부",
            "에너지자원공학과",
            "화학공학과",
            "환경에너지공간융합학과",
            "국어국문학과",
            "국제학부",
            "교육학과",
            "미디어커뮤니케이션학과",
            "법학과",
            "역사학과",
            "영어영문학과",
            "행정학과",
            "경제학과",
            "경영학부",
            "호텔관광외식경영학부",
            "수학통계학부",
            "물리천문학과",
            "생명시스템학부",
            "화학과",
            "패션디자인학과",
            "음악과",
            "체육학과",
            "회화과"
    );

    public List<MajorOptionDto> getMajorOptions() {
        return MAJORS.stream()
                .map(MajorOptionDto::new)
                .toList();
    }
}
