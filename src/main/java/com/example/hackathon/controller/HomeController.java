package com.example.hackathon.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("projectName", "Congraduation");
        model.addAttribute("tagline", "세종대학교 졸업요건 점검을 위한 개인 프로젝트");
        model.addAttribute("highlights", List.of(
                "성적표 업로드 기반 졸업 진행도 분석",
                "복수전공 복필·복선 진행도 계산",
                "학과·입학년도별 교과과정 정책 반영"
        ));
        model.addAttribute("apiLinks", List.of(
                new NavLink("로그인 API", "/api/auth/login", "POST"),
                new NavLink("전공 선택지 조회", "/api/students/major-options", "GET"),
                new NavLink("졸업 진행도 조회", "/api/evaluate/graduation-progress/{studentId}", "GET"),
                new NavLink("Swagger UI", "/swagger-ui/index.html", "OPEN")
        ));
        return "home";
    }

    public record NavLink(String label, String href, String method) {
    }
}
