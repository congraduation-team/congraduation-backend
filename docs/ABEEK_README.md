# Sejong Graduate Backend (공학인증 ABEEK)

세종대학교 공학인증(ABEEK) 이수 판정 Spring Boot 백엔드입니다.  
컴퓨터공학(CSE) + 다수 공학·소프트웨어 계열 학과의 2020~2026 요건을 지원합니다.

## 핵심 규칙

### 1. 유리한 요건 적용
- 학점 최소 기준은 **입학 연도 vs 졸업(막학기) 연도** 중 더 유리한(낮은) 값
- 예: 2021 입학(설계 12) + 2026 졸업ABEEK(설계 10) → **설계 10**

### 2. 신설 필수 면제
- 졸업 연도에만 생긴 필수 과목은 입학 연도 교과과정에 없으면 면제

### 3. 설계 순서
- 기초 → 요소 → 종합 (병수 가능, 순서 위반 시 요소설계 불인정)
- 설계학점은 소수(예: 1.5) 지원

## 실행

```bash
./gradlew bootRun
```

- Swagger: `http://localhost:8080/swagger-ui.html`

## API

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/departments` | 학과 목록 |
| GET | `/api/curriculum/{dept}/{year}/courses` | 학과·연도 교과 |
| GET | `/api/curriculum/{dept}/{year}/abeek-requirement` | 학과·연도 요건 |
| GET | `/api/curriculum/{year}/...` | CSE 기본(하위호환) |
| GET | `/api/curriculum/effective-requirement?entranceYear=&graduationAbeekYear=&departmentCode=` | 유리한 적용 요건 |
| POST | `/api/students` | 학생 등록 (`departmentCode` 포함) |
| GET | `/api/students/{id}/abeek-evaluation` | 공학인증 판정 |

## 데이터

- CSE: `CurriculumDataLoader` 하드코딩 시드
- 기타 학과: `src/main/resources/abeek-data/{DEPT}/{year}.json` (`AbeekJsonDataLoader`)
- 수집 스크립트: `python scripts/scrape_abeek.py`
- **검수 가이드**: [docs/ABEEK_DATA_REVIEW.md](docs/ABEEK_DATA_REVIEW.md)

```bash
# HTML 요건·교과표만
python scripts/scrape_abeek.py --html-only

# 이수체계도 OCR (선수과목 초안, 검수 필요)
python scripts/scrape_abeek.py --ocr-only --dept MECH
```

## 테스트

```bash
./gradlew test
```
