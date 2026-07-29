# Sejong Login Stabilization Summary

이 문서는 `congraduation-backend` 저장소에서 진행된 세종대학교 로그인 연동 안정화 작업을 통합 정리하기 위한 문서입니다.

특히 PR `#6`부터 PR `#32`까지 이어진 로그인 관련 수정 흐름을 한 번에 이해할 수 있도록, 문제 발생 배경, 단계별 수정 내용, 최종 반영 사항, 운영 체크포인트를 정리합니다.

## 목적

이번 통합 정리 문서의 목적은 다음과 같습니다.

- 로그인 관련 자잘한 PR이 많아 전체 흐름을 한 번에 파악하기 어려운 문제 해소
- 중간 실험성 수정과 최종 반영된 수정의 차이 명확화
- 운영 중 발생했던 주요 이슈와 실제 해결 과정을 기록
- 이후 동일 이슈 재발 시 참고할 수 있는 운영 문서 확보

## 작업 배경

Congraduation 백엔드는 세종대학교 포털 로그인 이후 학생 정보와 성적 정보를 조회해 졸업요건 진단에 활용합니다.

이 과정에서 로그인 API(`/api/auth/login`)는 단순한 아이디/비밀번호 검증으로 끝나지 않고, 다음 외부 연동 단계를 연속적으로 수행해야 합니다.

1. 세종 포털 로그인 요청
2. 포털 SSO 토큰 확보
3. classic 시스템 진입
4. classic 내부 프로필 페이지 접근
5. 학생 기본 정보 파싱
6. 이후 성적표 및 졸업요건 계산 로직 수행

문제는 이 흐름이 로컬 환경, 브라우저, EC2 배포 환경에서 서로 다르게 동작했고, 그 과정에서 다수의 작은 수정 PR이 연속적으로 발생했다는 점입니다.

## 주요 증상

로그인 안정화 작업 중 실제로 확인된 주요 증상은 아래와 같습니다.

### 1. 프론트엔드에서 CORS 차단

- Vercel 프론트엔드 도메인에서 백엔드 로그인 요청 시 CORS 오류 발생
- 운영 백엔드에서 특정 origin을 허용하도록 수정 필요

### 2. 배포 자동화 실패

- GitHub Actions에서 Gradle 빌드 실패
- EC2 SSH 연결 실패
- GitHub Secrets 누락 또는 형식 오류
- EC2 보안 그룹 및 SSH key 설정 문제

### 3. 로그인 API 500 에러

- Swagger 또는 프론트엔드에서 `/api/auth/login` 호출 시 `500 Internal Server Error`
- 초기에 외부 연동 실패인지, 내부 DB/비즈니스 로직 실패인지 구분이 어려웠음

### 4. 세종 연동 흐름 불안정

- Java HTTP 클라이언트 사용 시 `Connection reset`
- classic 시스템으로 넘어가는 과정에서 redirect 흐름 불일치
- `reading/status.do` 진입 시 `login.jsp`로 재리다이렉트
- EC2 환경에서 `curl` 응답 timeout 발생

## 수정 흐름 요약

PR `#6`부터 `#32`까지의 수정은 큰 흐름으로 보면 아래 단계로 정리할 수 있습니다.

### 단계 1. 운영 접속 가능 상태 확보

- Vercel 프론트엔드 origin을 백엔드 CORS 허용 목록에 추가
- 백엔드 변경사항을 운영 환경에 반영할 수 있도록 GitHub Actions 배포 workflow 구성
- EC2 SSH 접속 정보와 Repository Secrets 정리
- EC2 보안 그룹, 공개 IP, 배포 경로, systemd 서비스 확인

이 단계의 목적은 “코드가 맞더라도 배포되지 않으면 확인할 수 없다”는 문제를 먼저 해결하는 것이었습니다.

### 단계 2. 빌드 및 배포 파이프라인 안정화

- Gradle 컴파일 오류 수정
- 의존성/호환성 문제로 인한 bootJar 실패 수정
- SSH key 형식 및 권한 문제 해결
- GitHub Actions에서 jar 업로드 및 service restart까지 성공하도록 조정

이 단계에서 운영 배포 자체는 정상 동작하기 시작했습니다.

### 단계 3. 로그인 500의 실제 실패 지점 분리

처음에는 로그인 API의 500이 어디서 나는지 불명확했습니다.

확인 과정:

- EC2에서 `curl`로 `/api/auth/login` 직접 호출
- `journalctl`로 예외 로그 추적
- `systemctl status`, `ss -lntp`, nginx 응답 확인

이 과정을 통해 아래 사실이 확인되었습니다.

- 초기에는 Java HTTP 클라이언트 단계에서 `Connection reset` 발생
- 일부 시점에는 Gradle/배포 문제와 로그인 문제가 함께 섞여 있었음
- 이후에는 `/api/auth/login` 내부에서 “세종 프로필 조회 중 오류”가 핵심 원인으로 좁혀짐

### 단계 4. Java HTTP 클라이언트에서 curl subprocess 방식으로 전환

세종 포털/classic 시스템은 서버 환경에서 Java HTTP Client로 직접 붙을 때 응답이 불안정했습니다.

확인된 문제:

- `SocketException: Connection reset`
- 브라우저에서는 정상인데 서버 코드에서는 같은 요청이 실패

대응:

- 세종 연동 흐름을 Java HTTP 클라이언트 대신 `curl` subprocess 기반으로 우회
- 포털 워밍업
- 포털 로그인 POST
- 쿠키 jar 유지
- classic SSO redirect 수동 처리

이 단계는 “브라우저와 더 가까운 네트워크 동작”을 재현하기 위한 수정이었습니다.

### 단계 5. SSO redirect 흐름 수동 보정

브라우저 DevTools와 EC2 수동 `curl` 결과를 비교한 결과,

- `classic/index.do`는 빠르게 열림
- 하지만 `reading/status.do`는 바로 열리지 않고 `/_custom/sejong/sso/login.jsp?...`로 다시 리다이렉트되는 경우가 확인됨

이에 따라:

- 프로필 조회 전에 classic index 세션을 먼저 준비
- `status.do` 응답 헤더를 먼저 확인
- `login.jsp` SSO 게이트로 튕기면 해당 게이트를 먼저 통과
- 다시 `status.do`를 재요청

하도록 흐름을 보완했습니다.

### 단계 6. subprocess 출력 처리 및 운영 안정성 보강

로그인 기능은 동작하기 시작했지만, 운영 관점에서는 추가 위험이 있었습니다.

보완한 항목:

- `curl` 응답 본문을 stdout 파이프가 아니라 임시 파일로 받도록 수정
- 큰 HTML 응답으로 인한 subprocess 파이프 block 가능성 완화
- redirect 응답의 status code 파싱 추가
- 프로필 조회 redirect를 명시적으로 검증
- 허용하지 않은 redirect는 즉시 실패 처리
- 프로필 조회 재시도 한도 추가
- 운영 로그 포인트 보강

이 단계까지 오면서 “일단 된다”에서 “운영 중 깨질 가능성을 줄이는” 방향으로 정리되었습니다.

## 최종 반영된 핵심 수정 요소

최종적으로 로그인 안정화 작업에서 의미 있게 남은 수정 요소는 아래와 같습니다.

### 1. CORS 설정 반영

- Vercel 프론트엔드에서 운영 백엔드 호출 허용

### 2. 배포 자동화 구성

- `main` 머지 이후 GitHub Actions 배포
- EC2에 jar 업로드
- `congraduation` systemd service 재시작

### 3. 세종 포털 로그인 흐름 보강

- 포털 워밍업 요청 수행
- 세종 포털 로그인 POST 요청을 `curl` 기반으로 처리
- SSO 토큰 쿠키 확인

### 4. classic SSO 흐름 보강

- SSO redirect를 수동 추적
- classic index 진입 흐름 보정

### 5. 프로필 조회 흐름 보강

- `reading/status.do` 접근 전 세션 준비
- `login.jsp` 게이트 감지 후 선행 통과
- 이후 프로필 페이지 재조회

### 6. 운영 안정성 보강

- subprocess body 처리 안정화
- redirect/status 검증 강화
- 예외 메시지와 로그 포인트 보강

## 최종 상태

이 문서 작성 시점 기준으로 로그인 기능은 정상 동작하는 상태까지 도달했습니다.

즉,

- 프론트엔드 또는 Swagger에서 로그인 호출 가능
- 운영 백엔드가 세종 포털/classic 연동을 수행
- 학생 프로필 조회 및 이후 처리 흐름이 실제 동작하는 상태

다만 이번 작업은 외부 시스템(세종 포털/classic)의 구조에 의존하는 영역이므로, 완전히 끝난 작업이라기보다는 “운영 가능한 상태로 안정화한 작업”으로 보는 것이 맞습니다.

## 남아 있는 리스크

### 1. 외부 사이트 구조 변경 리스크

- 세종 포털/classic HTML 구조
- redirect 경로
- SSO 중간 게이트
- 쿠키 정책

중 하나만 바뀌어도 로그인 흐름이 다시 깨질 수 있습니다.

### 2. HTML 파싱 의존 리스크

- `학과명`, `학번`, `이름`, `학년` selector는 현재 HTML 구조에 의존
- 마크업이 바뀌면 파싱 실패 가능

### 3. 운영 환경별 네트워크 차이

- 로컬 브라우저
- 로컬 서버
- EC2 서버

이 세 환경에서 세종 시스템 응답이 항상 같다고 보장할 수 없습니다.

### 4. 로그/관측성 보완 필요

- 운영 중 다시 실패할 경우 어느 단계에서 깨졌는지 빠르게 볼 수 있도록 로그 정리가 중요

## 운영 확인 방법

문제 재발 시 우선 아래 순서로 확인합니다.

### 1. 서비스 상태 확인

```bash
sudo systemctl status congraduation --no-pager -l
```

### 2. 로그인 API 직접 호출

```bash
curl -i -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userId":"<학번>","password":"<비밀번호>"}'
```

### 3. 실시간 로그 확인

```bash
sudo journalctl -u congraduation -f --no-pager | grep -E "Sejong|세종|ERROR|Exception|status.do|login.jsp|index.do"
```

### 4. 세종 수동 curl 비교

- `classic/index.do`
- `classic/reading/status.do`
- `/_custom/sejong/sso/login.jsp`

를 수동 `curl`로 호출해 redirect/status 차이를 확인합니다.

## 왜 통합 정리가 필요한가

이번 로그인 안정화 작업은 단일 기능 개발이라기보다,

- 배포 환경 문제
- 외부 연동 문제
- 운영 서버 설정 문제
- 세종 SSO 흐름 문제
- subprocess 처리 문제

가 겹친 운영 안정화 작업에 가까웠습니다.

그래서 여러 개의 작은 PR은 각각 당시 필요한 수정이었지만, 최종적으로는 “무엇이 남았고 왜 그렇게 되었는지”를 한 번에 볼 수 있는 문서가 필요했습니다.

이 문서는 그 목적을 위한 통합 정리 문서이며, 이후에는 로그인 관련 변경 시 이 문서를 기준점으로 삼아 변경 이유와 영향을 명확히 기록하는 것을 권장합니다.
