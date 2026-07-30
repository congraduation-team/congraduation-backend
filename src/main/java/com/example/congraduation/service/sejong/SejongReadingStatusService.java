package com.example.congraduation.service.sejong;

import com.example.congraduation.dto.sejong.SejongReadingStatusResponseDto;
import com.example.congraduation.dto.sejong.SejongReadingStatusResponseDto.AreaStatusDto;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
public class SejongReadingStatusService {

    private static final String STATUS_SECTION_TITLE = "영역별 인증현황";
    private static final String DEFAULT_SUBTITLE = "고전독서인증 현황";
    private static final Pattern REQUIRED_COUNT_PATTERN = Pattern.compile("\\((\\d+)권\\)");
    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+)권");

    public SejongReadingStatusResponseDto parseReadingStatus(String html) {
        Document document = Jsoup.parse(html);
        Element sectionTitle = document.selectFirst("*:matchesOwn(^\\s*" + STATUS_SECTION_TITLE + "\\s*$)");
        if (sectionTitle == null) {
            throw new IllegalStateException("고전독서 인증 현황 섹션을 찾지 못했습니다.");
        }

        Element table = findStatusTable(sectionTitle);
        if (table == null) {
            throw new IllegalStateException("고전독서 인증 현황 표를 찾지 못했습니다.");
        }

        List<AreaStatusDto> areas = new ArrayList<>();
        for (Element row : table.select("tr")) {
            Elements cells = row.select("th, td");
            if (cells.size() < 3) {
                continue;
            }

            String areaText = cells.get(0).text().trim();
            if (areaText.isBlank() || "구분".equals(areaText) || "합계".equals(areaText)) {
                continue;
            }

            int requiredCount = parseRequiredCount(areaText);
            int completedCount = parseCount(cells.get(1).text());
            int certifiedCount = parseCount(cells.get(2).text());
            String areaName = areaText.replaceAll("\\s*\\(\\d+권\\)\\s*$", "").trim();
            boolean satisfied = certifiedCount >= requiredCount;

            areas.add(new AreaStatusDto(
                    areaName,
                    completedCount,
                    certifiedCount,
                    requiredCount,
                    satisfied
            ));
        }

        if (areas.isEmpty()) {
            throw new IllegalStateException("고전독서 인증 현황 영역 데이터가 비어 있습니다.");
        }

        int totalCompletedCount = areas.stream().mapToInt(AreaStatusDto::completedCount).sum();
        int totalCertifiedCount = areas.stream().mapToInt(AreaStatusDto::certifiedCount).sum();
        int totalRequiredCount = areas.stream().mapToInt(AreaStatusDto::requiredCount).sum();
        boolean completed = areas.stream().allMatch(AreaStatusDto::satisfied);

        return new SejongReadingStatusResponseDto(
                completed,
                completed ? "현재 고전독서인증 완료!" : "현재 고전독서인증 진행 중",
                DEFAULT_SUBTITLE,
                completed ? "고전독서인증을 모두 완료하였습니다." : "고전독서인증이 아직 남아 있습니다.",
                List.copyOf(areas),
                totalCompletedCount,
                totalCertifiedCount,
                totalRequiredCount
        );
    }

    private Element findStatusTable(Element sectionTitle) {
        Element current = sectionTitle;
        while (current != null) {
            Element sibling = current.nextElementSibling();
            while (sibling != null) {
                if ("table".equalsIgnoreCase(sibling.tagName())) {
                    return sibling;
                }
                Element nestedTable = sibling.selectFirst("table");
                if (nestedTable != null) {
                    return nestedTable;
                }
                sibling = sibling.nextElementSibling();
            }
            current = current.parent();
        }
        return null;
    }

    private int parseRequiredCount(String text) {
        Matcher matcher = REQUIRED_COUNT_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("고전독서 인증 필요 권수를 파싱하지 못했습니다: " + text);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private int parseCount(String text) {
        Matcher matcher = COUNT_PATTERN.matcher(text.replaceAll("\\s+", ""));
        if (!matcher.find()) {
            return 0;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
