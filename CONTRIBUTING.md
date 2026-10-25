# 팀원 설정 안내 — 개인정보가 저장소에 남지 않게

이 저장소는 제출 직전에 **공개**로 바뀐다. 커밋 기록은 나중에 지우기 어려우므로, 처음 커밋하기 전에 아래를 모두 끝낸다.

## 1. GitHub 계정 설정 (웹에서, 한 번만)

1. https://github.com/settings/profile — Name·Bio·Company·Location·Website·소셜 링크를 비우고, 프로필 사진은 기본 이미지나 본인을 알 수 없는 그림으로 둔다. 공개 저장소에서 내 계정 페이지로 바로 들어올 수 있다.
2. https://github.com/settings/emails
   - **Keep my email addresses private** 켜기
   - **Block command line pushes that expose my email** 켜기
   - 이 페이지에 나오는 `숫자+아이디@users.noreply.github.com` 주소를 복사해 둔다.

## 2. 저장소 받은 뒤 (이 저장소 폴더 안에서, 한 번만)

```sh
git config user.name  "<GitHub 아이디>"
git config user.email "<숫자>+<GitHub 아이디>@users.noreply.github.com"
git config core.hooksPath .githooks
```

- `--global` 을 붙이지 않는다. 이 저장소에만 적용된다.
- 마지막 줄을 해야 커밋할 때마다 개인정보 점검이 돈다. 이메일이 noreply 가 아니거나, 추가한 줄에 사용자 폴더 경로·개인 메일·휴대전화 번호가 있으면 커밋이 막힌다.

## 3. 개인 금지어 목록 (권장)

저장소 맨 위에 `.privacy-blocklist` 파일을 만들고 본인 실명(한글·영문)·학교·Windows 사용자 이름 등을 한 줄에 하나씩 적는다. 이 파일은 `.gitignore` 에 있어 커밋되지 않고, 점검할 때 함께 검사한다.

```
# 대소문자 무시
홍길동
gildong
OO대학교
```

## 그 밖에 조심할 것

- 패키지 이름·모듈 이름에 개인 아이디를 쓰지 않는다.
- 스크린샷·시연영상에 이름·알림창·바탕화면이 찍히지 않게 한다.
- 릴리스 서명 키를 만들 때 이름·조직 칸을 비우거나 팀명만 넣는다. 인증서 정보는 APK 안에 그대로 남는다.
- `local.properties`·`.env`·`*.jks` 는 커밋되지 않는다. 비밀값은 여기에만 둔다.

## 점검이 막았을 때

- 진짜 개인정보면 지우고 다시 커밋한다.
- 오탐이면(예: 예시용 경로) `<사용자>` 같은 자리표시로 바꾼다. `--no-verify` 로 넘기지 않는다. push 하면 GitHub Actions 에서 기록 전체를 다시 검사하므로 결국 걸린다.
- 이미 push 한 커밋에 개인정보가 들어갔으면 혼자 고치지 말고 저장소 관리자에게 알린다(기록을 다시 써야 한다).

## 공개 전환 직전 (관리자)

```sh
bash scripts/check-privacy.sh --history
```

모든 커밋의 작성자 이메일과 모든 시점의 파일 내용을 검사한다.
