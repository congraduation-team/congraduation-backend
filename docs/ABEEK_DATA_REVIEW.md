# ABEEK 데이터 검수 가이드

스크래퍼가 [abeek.sejong.ac.kr](https://abeek.sejong.ac.kr/abeek/program0702.html)에서
2020~2026 공학인증요건·입학자 교과과정·이수체계도(OCR)를 수집했습니다.
**컴퓨터공학(CSE)** 은 기존 하드코딩 시드를 유지합니다.

## 데이터 위치

- JSON: `src/main/resources/abeek-data/{학과코드}/{연도}.json`
- 요약: `src/main/resources/abeek-data/_summary.json`
- 재수집: `python scripts/scrape_abeek.py --html-only`
- OCR만: `python scripts/scrape_abeek.py --ocr-only [--dept MECH]`

이미지는 임시 파일로만 읽어 OCR 후 삭제합니다(영구 다운로드 없음).

## 학과 코드

| 코드 | 학과 | 2020~2026 |
|------|------|-----------|
| ARCH | 건축공학 | ✅ |
| CIVIL | 토목공학 | ✅ |
| ENV | 환경융합공학 | ✅ |
| ENERGY | 에너지자원공학 | ✅ |
| MECH | 기계공학 | ✅ |
| AERO | 항공우주공학 | ✅ |
| NANO | 나노신소재공학 | ✅ |
| NUCLEAR | 원자력공학 | ✅ |
| EICE | 전자정보통신공학 | ✅ |
| EE | 전자공학 | 2020~2021만 (이후 사이트에 없음) |
| SW | 소프트웨어 | ✅ |
| SEC | 정보보호학 | ✅ (일부 연도 교과표 없음→OCR 의존) |
| DS | 데이터사이언스학 | 일부 연도만 교과표 |
| AIROBOT | AI로봇학 | ✅ (2020 교과표 약함→OCR) |
| GEO / ICE / DCON / AI | — | 사이트에 2020~2026 페이지 없음 |
| CSE | 컴퓨터공학 | 기존 시드 (웹에는 2020만) |

## 검수 포인트 (`needsReview: true`)

1. **설계학점** — 특히 `1.5` 같은 소수, 기초/요소/종합 분류
2. **선수과목** — `flowchartOcr.prerequisites` 는 같은 행 좌→우 휴리스틱이라 **반드시 검수**
3. **필수/선택(role)** — OCR로 채운 과목(`fromOcr: true`)은 role이 ELECTIVE로 들어가 있음
4. **MSC↔BSM** — 백엔드에서는 MSC를 BSM 카테고리로 취급
5. **신설 필수** — 졸업연도 전용 과목 면제 로직은 CSE와 동일하게 `departmentCode` 기준으로 동작

## JSON 필드

```json
{
  "requirement": { "generalMinCredits", "bsmMinCredits", "majorMinCredits", "designMinCredits", "designCourses", "certElective" },
  "courses": [{ "name", "role", "category", "credits", "designCredits", "designLevel", "recommendedTerm" }],
  "flowchartOcr": {
    "courses": [{ "text", "xRatio", "yRatio", "estimatedTerm", "confidence" }],
    "prerequisites": [{ "from", "to", "type": "RECOMMENDED_OCR_HEURISTIC", "needsReview": true }]
  }
}
```

## 사이트 한계 (2020~2026 미제공)

아래는 ABEEK 사이트에 해당 연도 페이지/이미지가 없어 수집 불가입니다.

| 학과 | 비고 |
|------|------|
| 전자공학(EE) | 2020~2021만 존재 |
| 인공지능(AI) | 2021~2023만 존재 |
| 지구정보공학 / 정보통신공학 / 디지털콘텐츠 | 2020~2026 페이지 없음 |

AERO·CIVIL 2026 이수체계도는 외부 이미지가 아니라 **HTML base64 임베드**라 별도 파서로 OCR했습니다.

