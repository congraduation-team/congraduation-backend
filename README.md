# Congraduation Backend

세종대학교 학생을 위한 **졸업요건 진단 · 수강 시뮬레이션** 백엔드입니다.

기이수 성적표와 수강편람·강의시간표·교과과정(ABEEK)을 묶어,  
“지금 어디까지 충족했는지”와 “남은 학기에 무엇을 넣으면 어떻게 되는지”를 API로 제공합니다.

> 취업 포트폴리오용 백엔드 레포지토리입니다.  
> 프론트엔드와 분리되어 있으며, REST API + JWT 인증 기반으로 동작합니다.

---

## Why this project

대학 졸업요건은 학번·학과·복수전공·교양 영역·공학인증 규칙이 얽혀 있어,  
학생이 포털/편람만으로 정확히 판단하기 어렵습니다.

이 프로젝트는 그 판단을 **도메인 규칙 엔진 + 시뮬레이션 API**로 옮긴 서비스입니다.

- 실데이터 기반: 기이수 엑셀, 세종 포털 로그인 연동, 학기별 시간표 JSON
- 규칙 중심: 공필/균필/전필/전선, 복수전공(복필·복선), 단과대 타과 인정, ABEEK
- 예측 가능: 남은 학기에 과목을 담아 졸업요건 `simulation` 결과를 바로 확인

---

## Core Features

### 1. 졸업요건 평가
- 총 학점, 교양(공필·균필·교선), 전공(전필·전선), 학문기초 진행도 계산
- 영어졸업인증 · 고전독서 · SW코딩 인증 반영
- 학번별 공통교양 필수 과목 매칭 및 대체과목 인정
- 부족 요건(`graduationBlockers`)과 화면용 표시 값 분리

### 2. 졸업 시뮬레이션 (What-if)
- 남은 학기 카드에 계획 과목을 추가하면 별도 `simulation` 결과로 재평가
- 시간표 기반 수강 가능 과목 카탈로그 제공
- 주전공/복수전공에 맞게 이수구분 재분류
  - 주전공 전필·전선 유지
  - 복수전공 학과 과목 → `복필` / `복선`
  - 동일 단과대 타과 전공 → 전선 인정(수강편람 기준)
  - 교과과정상 전필이면 시간표 라벨이 전선이어도 전필로 보정
- 같은 학수번호는 학생 기준으로 **카드 1장**으로 병합

### 3. 복수전공 / 부전공 트랙
- 학과별 지정 필수과목 정책 (`ALL_OF`, `CHOOSE_COUNT`, `CHOOSE_CREDITS`, `ANY_TRACK`)
- 예체능 졸작, IoT 교류형·공유형 MD, 국방 주전공 허용 학과 등 특수 규칙 처리
- 학점 요건 + 지정필수 + 추가요건을 트랙 단위로 집계

### 4. 세종 포털 연동 로그인
- classic 포털 로그인 후 프로필 / 이수 학기 / 고전독서 / 영어인증 수집
- JWT 발급 및 학생 API 보호
- 군E러닝 등으로 기이수 학기 라벨이 부풀어도, 공식 **이수 학기**를 standing에 우선 반영

### 5. 기이수 · 시간표 · 로드맵
- 기이수성적 엑셀 업로드/파싱(Apache POI)
- 학기별 강의시간표 JSON 적재 및 관리자 업로드
- 학생별 이수 로드맵 / ABEEK 공학인증 평가

---

## Tech Stack

| Layer | Stack |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Persistence | Spring Data JPA, MySQL 8 |
| API Docs | springdoc-openapi (Swagger UI) |
| Auth | JWT |
| Parsing | Apache POI (Excel), Jsoup (HTML) |
| Build / CI | Gradle, GitHub Actions → EC2 배포 |
| Local Infra | Docker Compose (MySQL) |

---

## Architecture Overview

```text
[Frontend]
    |  REST + JWT
[Spring Boot API]
    ├── Auth / Student
    ├── Transcript (기이수)
    ├── Graduation Progress + Simulation
    ├── Planned Courses / Catalog
    ├── Roadmap / ABEEK
    └── Admin (시간표, 피드백)
           |
      [MySQL]
           |
  [Timetable JSON / Curriculum Seed / Portal Crawl]
```

핵심은 Controller → Service → Domain/Policy 분리입니다.  
특히 졸업·복수전공·카탈로그 이수구분은 **정책 서비스**로 빼서 학번/학과별 분기 복잡도를 관리합니다.

---

## Domain Highlights (면접용 포인트)

1. **규칙이 많은 학사 도메인을 코드로 모델링**
   - 이수구분 정규화 (`공통교양필수` → `공필`, `교선1/2` → `교선` 등)
   - 학번별 공필 목록, 균형교양 영역, 전공학점 정책
2. **시간표 vs 교과과정 불일치 보정**
   - 예: 컴공 `컴퓨터구조`는 시간표상 전선이어도 교과과정상 전필이면 전필로 표시
3. **시뮬레이션 isolation**
   - 실제 기이수 결과와 계획 반영 결과를 분리해 응답
4. **읽기 API의 부작용 제거**
   - 졸업요건 조회(readOnly)에서 계획 학기 INSERT가 나지 않도록 경로 분리
5. **실데이터 연동**
   - 포털 HTML 파싱, 엑셀 업로드, 시간표 GitHub 동기화

---

## Getting Started

### Prerequisites
- JDK 21
- Docker (MySQL)

### 1) DB 실행
```bash
docker compose up -d
```

### 2) 애플리케이션 실행
```bash
./gradlew bootRun
```

### 3) 확인
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API Docs: `http://localhost:8080/api-docs`

> 로컬 DB 기본값은 `docker-compose.yml` / `application.properties`를 참고하세요.  
> 운영 JWT·GitHub 토큰 등은 환경변수로 주입합니다.

### Test
```bash
./gradlew test
```

---

## Main APIs

| Area | Method | Path | Description |
|---|---|---|---|
| Auth | `POST` | `/api/auth/login` | 세종 로그인 + JWT |
| Auth | `GET` | `/api/auth/me` | 현재 사용자 |
| Transcript | `POST` | `/api/transcripts/upload/{studentId}` | 기이수 엑셀 업로드 |
| Graduation | `GET` | `/api/evaluate/graduation-progress/{studentId}` | 졸업요건 + simulation |
| Plan | `GET` | `/api/planned-courses/catalog` | 수강 가능 과목 카탈로그 |
| Plan | `GET/POST` | `/api/students/{id}/planned-courses` | 계획 과목 조회/추가 |
| Roadmap | `GET` | `/api/roadmap/by-student` | 학생 로드맵 |
| ABEEK | `GET` | `/api/abeek/students/{id}/abeek-evaluation` | 공학인증 판정 |

상세 스펙은 Swagger를 기준으로 확인하는 것을 권장합니다.

---

## Project Structure

```text
src/main/java/com/example/congraduation
├── auth/                 # JWT 인증/인가
├── controller/           # REST 컨트롤러
├── service/
│   ├── graduation/       # 졸업요건 · 복수전공 정책
│   ├── plan/             # 계획학기 · 카탈로그 이수구분
│   ├── transcript/       # 기이수 파싱/집계
│   ├── sejong/           # 포털 연동
│   └── student/
├── abeek/                # 공학인증 교과/평가/시간표
├── roadmap/              # 이수 로드맵
└── domain/               # JPA 엔티티
```

---

## Documentation

- [`docs/ABEEK_README.md`](docs/ABEEK_README.md) — 공학인증 규칙/API
- [`docs/ABEEK_INTEGRATION.md`](docs/ABEEK_INTEGRATION.md) — ABEEK 연동
- [`docs/HTTPS_DEPLOYMENT.md`](docs/HTTPS_DEPLOYMENT.md) — HTTPS 배포
- [`docs/SEJONG_LOGIN_STABILIZATION_SUMMARY.md`](docs/SEJONG_LOGIN_STABILIZATION_SUMMARY.md) — 로그인 안정화

---

## Responsibilities / What I worked on

백엔드 중심으로 아래 영역을 설계·구현·디버깅했습니다.

- 졸업요건 평가 파이프라인 및 시뮬레이션 응답 구조
- 복수전공 지정필수/학점/특수학과 정책
- 계획학기 카탈로그 이수구분 재분류(전필·전선·복필·복선·교양)
- 이수 학기(standing) 보정 및 군E러닝 과대 집계 이슈 대응
- 기이수 카테고리 정규화(교선1/2 등)와 read-only 트랜잭션 부작용 제거

---

## License

Private / team project.  
포트폴리오 열람용으로 공개하는 경우에도, 실제 학생 성적·계정 정보는 포함하지 않습니다.
