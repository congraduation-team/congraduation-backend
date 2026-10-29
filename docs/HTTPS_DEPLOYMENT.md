# HTTPS Deployment Guide

이 문서는 `congraduation-backend`를 HTTPS 뒤에서 안전하게 운영하기 위한 최소 설정을 정리합니다.

## 목표

- 외부 트래픽은 반드시 HTTPS로 받는다.
- Spring Boot는 리버스 프록시가 전달하는 `X-Forwarded-*` 헤더를 신뢰한다.
- JWT, 세종 로그인 비밀번호, 학생 데이터가 평문 HTTP로 노출되지 않도록 한다.

## 현재 앱 반영 사항

- `server.forward-headers-strategy=native`
  - Tomcat `RemoteIpValve`가 `X-Forwarded-For`를 오른쪽부터 읽고, 신뢰 프록시(사설망 `10.*`·`172.16-31.*`·`192.168.*`, loopback `127.*`/`::1` 등)만 건너뛴 첫 주소를 클라이언트 IP로 씁니다.
  - 이전 `framework` 방식은 클라이언트가 보낸 맨 왼쪽 값을 그대로 믿어, 헤더 위조로 로그인 시도 제한(IP 기준)을 우회할 수 있었습니다.
  - 프록시가 공인 IP를 가진 별도 서버라면 그 주소를 `server.tomcat.remoteip.internal-proxies`(정규식)에 추가해야 합니다. 그렇지 않으면 모든 요청이 프록시 IP 하나로 보여 IP 제한이 전체 사용자에게 걸립니다.
- HTTPS 요청일 때만 `Strict-Transport-Security` 응답 헤더 추가
- 기본 보안 헤더 추가
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy: geolocation=(), camera=(), microphone=()`
- 인증 응답(`/api/auth/**`)에는 캐시 방지 헤더 추가

## 배포 구조 권장

1. 사용자
2. Nginx 또는 ALB/CloudFront
3. Spring Boot (`localhost:8080`)

Spring Boot 자체에 인증서를 직접 붙이기보다, 앞단 프록시에서 TLS 종료를 하는 편이 운영이 단순합니다.

## Nginx 예시

```nginx
server {
    listen 80;
    server_name api.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.example.com;

    ssl_certificate /etc/letsencrypt/live/api.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Host $host;
    }
}
```

## 로컬 검증

앱 실행:

```bash
./gradlew bootRun --args='--server.port=8081'
```

Forwarded header 기반 HTTPS 인식 확인:

```bash
curl -i http://127.0.0.1:8081/api/students/major-options \
  -H 'X-Forwarded-Proto: https'
```

기대 결과:

- `Strict-Transport-Security` 헤더 존재
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`

일반 HTTP 요청 확인:

```bash
curl -i http://127.0.0.1:8081/api/students/major-options
```

기대 결과:

- 기본 보안 헤더는 존재
- `Strict-Transport-Security`는 없음

## 전환 체크리스트

1. API 도메인에 인증서 발급
2. `80 -> 443` 리다이렉트
3. 프론트 API base URL을 `https://...`로 변경
4. CORS 허용 origin에 프론트 HTTPS 도메인 반영 (기본값: `https://congraduation-frontend.vercel.app`, 바뀌면 `APP_CORS_ALLOWED_ORIGINS`)
5. 로그인, `/api/auth/me`, 성적 업로드, 파일 업로드 검증
6. 브라우저 mixed content 에러 없는지 확인
7. 운영 env에 `SPRINGDOC_ENABLED=true`나 localhost가 들어간 `APP_CORS_ALLOWED_ORIGINS`가 없는지 확인 (`/swagger-ui.html`이 404여야 함)
8. 서로 다른 IP에서 접속한 로그가 실제 클라이언트 IP로 찍히는지 확인 (프록시 IP 하나로만 보이면 위 "현재 앱 반영 사항"의 `internal-proxies` 설정 필요)

