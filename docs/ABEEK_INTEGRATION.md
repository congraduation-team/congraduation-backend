# ABEEK (공학인증) 모듈

이 브랜치에서 `com.example.congraduation.abeek` 패키지로 공학인증 판정/교과 데이터 API를 추가했습니다.

기존 학생·성적표·졸업진행 API(`/api/students` 등)와 충돌을 피하기 위해 ABEEK 학생 API는 아래 경로를 사용합니다.

- `POST /api/abeek/students`
- `GET /api/abeek/students/{studentId}`
- `POST /api/abeek/students/{studentId}/enrollments`
- `GET /api/abeek/students/{studentId}/abeek-evaluation`
- `POST /api/abeek/evaluate-from-transcript` (기이수성적 엑셀 업로드 → 학과 추론 → ABEEK 판정)
- `GET /api/abeek/timetable-terms` (적재된 강의시간표 학기)
- `GET /api/abeek/offered-courses` (이수체계도 ∩ 강의시간표, 공통교양 제외)
- `GET /api/abeek/offered-courses/by-student`
- `GET /api/abeek/full-roadmap` (1~8학기 전체로드맵: 전문교양/BSM/전공, 설계학점, 이수여부)
- `GET /api/abeek/full-roadmap/by-student`
- `GET /api/curriculum/...`
- `GET /api/departments`

상세 설명은 `docs/ABEEK_README.md`, 데이터 리뷰는 `docs/ABEEK_DATA_REVIEW.md`를 참고하세요.
