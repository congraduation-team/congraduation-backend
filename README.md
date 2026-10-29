# Congraduation Backend

세종대학교 졸업요건은 학번·학과·복수전공·교양 영역·공학인증 규칙이 얽혀 있어,  
학생이 포털/편람만으로 정확히 판단하기 어렵습니다.

이 프로젝트는 그 판단을 **도메인 규칙 엔진 + 시뮬레이션 API**로 옮긴, 세종대학교 학생을 위한 졸업요건 진단 및 미래 학기 설계 서비스입니다.

기이수 성적표와 수강편람·강의시간표·교과과정을 묶어,  
“지금 어디까지 충족했는지”와 “남은 학기에 무엇을 넣으면 어떻게 되는지”를 API로 제공합니다.

- 실데이터 기반: 기이수 엑셀, 세종 포털 로그인 연동, 학기별 시간표 JSON
- 규칙 중심: 공필/균필/전필/전선, 복수전공(복필·복선), 단과대 타과 인정, 공학인증(ABEEK)
- 예측 가능: 남은 학기에 과목을 담아 졸업요건 `simulation` 결과를 바로 확인

### At a glance

| | |
|---|---|
| 운영 | AWS EC2에서 실제 세종대학교 학생 데이터로 운영 |
| 테스트 | 233개 (학번·학과별 졸업 판정, 복수전공 정책, 공학인증 평가 회귀 검증) |
| 배포 | `main` 머지 시 GitHub Actions에서 테스트 통과 후에만 EC2 배포 |
| 성능 | 공학인증 성적표 평가 1회당 DB 조회 58건 → 16건 |

---

## Core Features

### 1. 졸업요건 평가
- 총 학점, 교양(공필·균필·교선), 전공(전필·전선), 학문기초 진행도 계산
- 영어졸업인증 · 고전독서 · SW코딩 인증 반영
- 학번별 공통교양 필수 과목 매칭 및 대체과목 인정
- 부족 요건(`graduationBlockers`)과 화면용 표시 값 분리

### 2. 졸업 시뮬레이션
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
- JWT 발급, API마다 JWT의 학번과 요청 대상 학번을 대조해 본인 데이터만 조회
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
| Build / CI | Gradle, GitHub Actions (테스트 통과 후 EC2 배포) |
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

## Domain Highlights

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

## Engineering Highlights

### 1. 공학인증 성적표 평가 DB 조회 58건 → 16건

- **문제:** 평가 1회에 SELECT가 58건(53과목 성적표 기준) 나갔고, 과목 수가 늘수록 조회도 비례해 늘었습니다.
- **분석:** 처음에는 인덱스를 의심해 MySQL 실데이터로 실행계획을 확인했습니다. 두 쿼리 모두 이미 유니크 인덱스(`type: const`, `rows: 1`)를 타고 있었습니다. 원인은 인덱스가 아니라, 성적표 과목마다 학과·연도별 커리큘럼을 다시 조회하는 **호출 횟수**였습니다.
- **해결:** 평가 1회 범위의 커리큘럼 캐시를 두어 같은 학과·연도 커리큘럼은 한 번만 읽고, 설계학점은 메모리에서 찾도록 바꿨습니다. 커리큘럼을 찾는 연도 우선순위는 기존과 같게 유지했습니다.
- **결과:** 16건으로 줄었고, 106과목 성적표에서도 16건을 유지합니다.
- **재발 방지:** Hibernate Statistics로 SELECT 수를 세는 `AbeekTranscriptEvaluationQueryCountTest`를 추가했습니다. 성적표 행 수를 두 배로 늘려도 조회 수가 늘지 않는지 검사해, 다시 과목 수에 비례하는 구조로 돌아가면 테스트가 깨집니다.

### 2. 테스트를 통과해야만 배포되도록 CI 수정

- **문제:** 배포 워크플로가 `./gradlew bootJar -x test`로 테스트를 건너뛰어, 통합 테스트 3개가 실패한 상태로도 EC2 배포가 정상 종료되고 있었습니다.
- **원인:** 실패는 검증 로직이 아니라 스프링 컨텍스트가 뜨지 않는 문제였습니다. 테스트용 H2에서 식별자 인용 옵션이 켜진 상태로, 엔티티에 문자열로 박힌 MySQL 전용 컬럼 정의를 H2가 타입이 아닌 도메인 이름으로 해석해 테이블 생성 단계에서 실패했고, 이어서 데이터 로더가 연쇄로 실패했습니다.
- **해결:** 엔티티에서 DB 종속 정의를 제거해 Hibernate가 각 DB에 맞는 타입을 고르게 하고, MySQL에서만 필요한 스키마 보정은 실행 시점에 DB 종류를 확인해 건너뛰도록 했습니다. 워크플로는 `test` → `deploy` 두 단계로 나누고 의존 관계를 걸었습니다.
- **결과:** 테스트가 깨진 커밋은 배포되지 않습니다.

### 3. 세종 포털 로그인 연동 안정화

- **문제:** 같은 로그인 흐름이 로컬, 브라우저, EC2에서 서로 다르게 실패했습니다. `Connection reset`, classic 시스템 진입 후 `login.jsp`로 되돌아가는 리다이렉트, EC2에서의 응답 timeout이 섞여 있었습니다.
- **해결:** 가설을 하나씩 세우고 EC2에서 확인하는 방식으로 원인을 좁혔습니다. 포털 워밍업 요청, SSO 리다이렉트 수동 추적, 프로필 조회 전 세션 준비와 `login.jsp` 게이트 선행 통과를 반영했습니다.
- **정리:** 여러 PR로 나뉜 수정 흐름과 최종 반영 사항, 재발 시 확인할 지점을 [`docs/SEJONG_LOGIN_STABILIZATION_SUMMARY.md`](docs/SEJONG_LOGIN_STABILIZATION_SUMMARY.md)에 운영 문서로 남겼습니다.

### 4. 운영 문의를 테스트로 재현한 뒤 수정

- 이수구분·학번별 공필·시간표/교과과정 불일치 같은 오판 문의가 오면, 바로 분기를 추가하지 않고 그 케이스를 테스트로 먼저 재현한 뒤 정책 코드를 고쳤습니다.
- 그 결과 쌓인 233개 테스트가 학번·학과별 판정이 바뀌면 먼저 깨지도록 회귀를 막고 있습니다.

### 5. 인증 기본 차단 전환과 운영 설정 점검

- **문제:** JWT 인증을 보호할 경로를 하나씩 등록하는 방식이라, 목록에서 빠진 API 4개가 인증 없이 호출되고 있었습니다. 다른 학생의 계획 과목을 수정·삭제하거나, 학번만으로 학과·입학연도를 알아낼 수 있었습니다.
- **해결:** 인터셉터를 `/api/**` 전체에 걸고 공개 API만 명시하는 **기본 차단** 구조로 바꿔, 새 API는 별도 설정 없이 보호되게 했습니다.
- **로그인 시도 제한:** 포털 계정 정보를 받는 API라 IP·학번 기준으로 시도 횟수를 제한하고(초과 시 429), 클라이언트가 보낸 `X-Forwarded-For`를 그대로 믿던 설정도 바로잡았습니다.
- **민감정보:** 포털 비밀번호가 curl 명령행 인자로 노출되던 문제를 표준입력 전달로 바꾸고, 로그의 학번·URL 토큰을 가렸습니다. 운영 기본값은 Swagger 꺼짐, CORS는 실제 프론트 주소만 허용으로 바꿨습니다.
- **운영 점검:** 배포 후 서버 설정까지 확인해, JWT 서명 키가 설정되지 않아 저장소의 기본 키로 서명하던 문제를 찾아 고쳤습니다. 서비스 파일에 적혀 있던 비밀값은 관리자만 읽을 수 있는 환경 파일로 옮겼습니다.

---

## Getting Started

### Prerequisites
- JDK 21
- Docker (MySQL)

### 1) 환경변수 (선택)
```bash
cp .env.example .env
# 운영/공유 환경에서는 APP_JWT_SECRET, DB_PASSWORD 등을 반드시 교체하세요.
```

### 2) DB 실행
```bash
docker compose up -d
```

### 3) 애플리케이션 실행
```bash
./gradlew bootRun
```

`bootRun`은 기본으로 `local` 프로필(`application-local.properties`)을 켭니다. 다른 프로필로 띄우려면 `SPRING_PROFILES_ACTIVE`를 지정하세요.

### 4) 확인 (local 프로필)
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API Docs: `http://localhost:8080/api-docs`

> `application.properties`의 기본값은 **운영 기준**입니다. 프로필 없이 jar를 실행하면 Swagger가 꺼지고, CORS는 실제 프론트 주소(`https://congraduation-frontend.vercel.app`)만 허용합니다.

> 로컬 기본값은 `.env.example` / `docker-compose.yml`을 참고하세요.  
> Spring Boot는 OS 환경변수 또는 IDE env로 `DB_*`, `APP_JWT_SECRET` 등을 주입받습니다.  
> 운영에서는 `APP_JWT_SECRET`, DB 비밀번호, (사용 시) `TIMETABLE_GITHUB_TOKEN`을 반드시 설정하세요.

### Test
```bash
./gradlew test
```

테스트는 H2(MySQL 모드)에서 실행되며, 같은 테스트가 CI에서 배포 전에 돌아갑니다.

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

2인 팀에서 백엔드의 아래 영역을 설계·구현·운영했습니다.

- 졸업요건 평가 파이프라인 및 시뮬레이션 응답 구조
- 복수전공·부전공 지정필수/학점/특수학과 정책
- 계획학기 카탈로그 이수구분 재분류(전필·전선·복필·복선·교양)
- 이수 학기(standing) 보정 및 군E러닝 과대 집계 이슈 대응
- 기이수 카테고리 정규화(교선1/2 등)와 read-only 트랜잭션 부작용 제거
- 세종 포털 연동 로그인 안정화와 API별 본인 소유 검증
- GitHub Actions 기반 EC2 배포와 테스트 게이트
- 공학인증 성적표 평가 조회 성능 개선

---

## Known Limitations

- **스키마 관리:** 현재는 `spring.jpa.hibernate.ddl-auto=update`로 스키마를 갱신합니다. 운영 DB에서 의도치 않은 변경이 생길 수 있어, Flyway 기반 마이그레이션으로 옮길 계획입니다.
- **배포 방식:** jar 업로드 후 systemd 재시작 방식이라 배포 중 짧은 중단이 있습니다.
- **외부 의존성:** 세종 포털/classic의 페이지 구조가 바뀌면 로그인·프로필 수집이 영향을 받습니다.

---

## Privacy / Security notes

- 세종 포털 **비밀번호는 DB에 저장하지 않습니다.** 로그인 요청 시 신원 확인·프로필/인증 조회에만 사용한 뒤 세션 쿠키를 폐기합니다.
- 포털 로그인용 curl에는 비밀번호를 **명령행 인자가 아닌 표준입력**으로 넘깁니다. 명령행 인자는 `ps`·`/proc/<pid>/cmdline`이나 감사 로그(auditd 등)에 원문이 남을 수 있기 때문입니다.
- 로그인 API(`POST /api/auth/login`)는 시도 횟수를 제한합니다. 한도를 넘으면 `429 TOO_MANY_LOGIN_ATTEMPTS`와 `Retry-After`를 반환합니다.

  | 규칙 | 기본값 | 환경변수 |
  |---|---|---|
  | IP별 전체 요청 | 1분 20회 | `LOGIN_RATE_LIMIT_IP_REQUESTS_MAX` / `_WINDOW` |
  | IP별 실패 | 15분 20회 | `LOGIN_RATE_LIMIT_IP_FAILURES_MAX` / `_WINDOW` |
  | 학번별 실패 | 15분 5회 | `LOGIN_RATE_LIMIT_USER_FAILURES_MAX` / `_WINDOW` |

  카운터는 메모리에 있어 서버 재시작 시 초기화되며, 단일 인스턴스 운영을 전제로 합니다. 인스턴스를 늘리면 Redis 등 공유 저장소로 옮겨야 합니다.
- 로그에는 비밀번호·성적을 남기지 않습니다. 학번은 `2101****`처럼 가리고, SSO 리다이렉트 URL은 쿼리스트링(토큰)을 지워 기록합니다. DB 오류 원문은 서버 로그에만 남기고 클라이언트에는 일반 메시지만 반환합니다.
- 운영에서는 Swagger를 끄고(`SPRINGDOC_ENABLED` 미설정 또는 `false`), CORS를 실제 프론트 주소로만 제한합니다(`APP_CORS_ALLOWED_ORIGINS`, `*` 사용 불가).
- 졸업 계산을 위해 학번·이름·학과·기이수 과목(성적 포함)·인증 정보 등은 서비스 DB에 저장됩니다.
- 학생 API는 JWT의 학번과 요청 대상 학번이 일치할 때만 응답합니다.
- 인증은 **기본 차단** 방식입니다. `/api/**` 전체에 JWT 인증·본인 확인이 걸리고, 학과·교육과정·시간표 같은 공용 데이터와 로그인만 `WebConfig.PUBLIC_API_PATHS`에 명시해 공개합니다. 새 API는 별도 설정 없이 보호됩니다.
- 레포에는 실제 학생 성적·계정 덤프를 포함하지 않습니다. 시크릿은 `.env`로 관리하고 `.env.example`만 커밋합니다.
- 운영 환경에서는 반드시 `APP_JWT_SECRET`, DB 비밀번호, (사용 시) `TIMETABLE_GITHUB_TOKEN`을 환경변수로 설정하세요.

## License

Team project.  
포트폴리오/코드 리뷰용으로 공개할 수 있으며, 실제 학생 성적·계정 정보는 포함하지 않습니다.
