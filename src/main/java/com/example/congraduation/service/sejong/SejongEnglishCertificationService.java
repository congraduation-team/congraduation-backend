package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongEnglishCertificationResponseDto;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Service
public class SejongEnglishCertificationService {

    private static final List<String> POSITIVE_STATUSES = List.of(
            "인증완료",
            "승인완료",
            "인증확정",
            "승인",
            "통과",
            "합격",
            "이수"
    );
    private static final List<String> NEGATIVE_STATUSES = List.of(
            "미인증",
            "반려",
            "불합격",
            "취소",
            "대기",
            "심사",
            "검토"
    );

    public SejongEnglishCertificationResponseDto parseCertificationStatus(String html) {
        Document document = Jsoup.parse(html);
        Element titleBox = document.selectFirst(".b-title-box");
        if (titleBox == null) {
            return SejongEnglishCertificationResponseDto.notSubmitted();
        }

        String status = text(titleBox, ".b-status");
        String examType = text(titleBox, ".b-exam");
        String score = text(titleBox, ".b-score");
        String submitDate = text(titleBox, ".b-submit-date");
        boolean certified = isCertified(status);

        return new SejongEnglishCertificationResponseDto(
                true,
                certified,
                status.isBlank() ? "SUBMITTED" : status,
                emptyToNull(examType),
                emptyToNull(score),
                emptyToNull(submitDate),
                buildDetail(certified, status, examType, score, submitDate)
        );
    }

    private boolean isCertified(String status) {
        String normalized = normalize(status);
        if (normalized.isBlank()) {
            return false;
        }
        for (String negative : NEGATIVE_STATUSES) {
            if (normalized.contains(normalize(negative))) {
                return false;
            }
        }
        for (String positive : POSITIVE_STATUSES) {
            if (normalized.contains(normalize(positive))) {
                return true;
            }
        }
        return false;
    }

    private String buildDetail(
            boolean certified,
            String status,
            String examType,
            String score,
            String submitDate
    ) {
        String examSummary = joinNonBlank(examType, score);
        String dateSummary = blankToDefault(submitDate, "제출일 미확인");
        if (certified) {
            return joinNonBlank(
                    "세종 영어인증 사이트에서 인증 완료로 확인되었습니다.",
                    examSummary,
                    dateSummary
            );
        }
        return joinNonBlank(
                "세종 영어인증 사이트 제출 상태: " + blankToDefault(status, "상태 미확인"),
                examSummary,
                dateSummary
        );
    }

    private String joinNonBlank(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" / ");
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    private String text(Element root, String selector) {
        Element element = root.selectFirst(selector);
        return element == null ? "" : element.text().trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
