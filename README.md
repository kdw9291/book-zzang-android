# Bookzzang Android

책짱 Android MVP입니다. Kotlin + Jetpack Compose로 만들었으며, P6 Spring Boot API 및 Supabase Auth와 연결됩니다.

## 시작하기

1. Android Studio에서 이 디렉터리를 엽니다.
2. `local.properties.example`을 참고해 Android Studio가 만든 `local.properties`에 SDK 경로와 설정을 넣습니다.
3. 운영 API는 `https://bookzzang.duckdns.org/`를 사용합니다. 로컬 개발 시 에뮬레이터는 `http://10.0.2.2:8080/`, 실제 휴대폰은 같은 Wi-Fi의 PC LAN IP를 사용합니다.
4. Run 버튼으로 `app`을 실행합니다.

`local.properties`는 `.gitignore`에 포함되어 API URL·Supabase URL·Anon key가 커밋되지 않습니다.

로컬 HTTP 허용은 개발용입니다. P8에서 EC2 HTTPS API 주소로 전환하면 `usesCleartextTraffic`을 제거합니다.

## 구현된 흐름

- 비회원: 도서 검색 → 상세/두께 확인
- 회원: Supabase 이메일·비밀번호 회원가입 또는 로그인
- 등록: 읽고 싶어요 / 읽는 중 / 읽었어요 선택 → P6 API에 저장
- 책장: 실측 두께 → 페이지 수 → 기본값 순으로 책등 너비를 계산해 세로 책장으로 표시

> 회원가입 뒤 이메일 인증 메일을 완료한 뒤 로그인합니다. Supabase 프로젝트의 이메일 인증 템플릿·리디렉션 URL 설정은 P8 배포 단계에서 실제 앱 딥링크와 함께 확정합니다.
