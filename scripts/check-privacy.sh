#!/usr/bin/env bash
# 개인정보 점검 — 커밋 작성자 이메일과 파일 내용에서 신원이 드러나는 흔적을 찾는다.
#   (인자 없음)  전체 커밋의 작성자 이메일 + 현재 HEAD 파일 내용
#   --staged     커밋하려는 변경분만 (pre-commit 훅이 쓴다)
#   --history    모든 커밋의 파일 내용까지 (공개 전환 직전에 돌린다)
# 각자 실명·학교 등은 커밋되지 않는 .privacy-blocklist 에 한 줄씩 적어 두면 함께 검사한다.
set -euo pipefail

MODE="${1:-}"
ROOT="$(git rev-parse --show-toplevel)"
BLOCKLIST="$ROOT/.privacy-blocklist"
ALLOWED_EMAIL='@users\.noreply\.github\.com$|^noreply@github\.com$'
PATTERNS=(
  '[A-Za-z]:[\/]+Users[\/]+[A-Za-z0-9._-]'                                                        # Windows 사용자 폴더 경로
  '/(Users|home)/[A-Za-z0-9._-]+/'                                                   # macOS·Linux 사용자 폴더 경로
  '[A-Za-z0-9._%+-]+@(gmail|naver|daum|hanmail|kakao|nate|outlook|hotmail|icloud|yahoo)\.'  # 개인 메일 주소
  '01[016789][-. ]?[0-9]{3,4}[-. ]?[0-9]{4}'                                           # 휴대전화 번호
)
# 패턴 자체를 담은 파일은 검사에서 뺀다
EXCLUDES=(':!scripts/check-privacy.sh' ':!.githooks/' ':!CONTRIBUTING.md')

fail=0
report() { echo "[개인정보 점검] $*" >&2; fail=1; }

check_email() {  # $1=설명 $2=이메일
  if ! grep -Eq "$ALLOWED_EMAIL" <<<"$2"; then
    report "$1 이메일이 noreply 주소가 아님: $2"
  fi
}

grep_args=()
for p in "${PATTERNS[@]}"; do grep_args+=(-e "$p"); done
fixed_args=()
if [[ -f "$BLOCKLIST" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    fixed_args+=(-e "$line")
  done <"$BLOCKLIST"
fi

scan_tree() {  # $@=git grep 대상 리비전 (비면 HEAD)
  local revs=("$@")
  [[ ${#revs[@]} -eq 0 ]] && revs=(HEAD)
  local out
  if out="$(git grep -nIE "${grep_args[@]}" "${revs[@]}" -- . "${EXCLUDES[@]}" 2>/dev/null)"; then
    report "파일 내용에 개인정보로 보이는 문자열:"; echo "$out" | head -50 >&2
  fi
  if [[ ${#fixed_args[@]} -gt 0 ]] && out="$(git grep -nIiF "${fixed_args[@]}" "${revs[@]}" -- . "${EXCLUDES[@]}" 2>/dev/null)"; then
    report ".privacy-blocklist 항목이 파일 내용에 있음:"; echo "$out" | head -50 >&2
  fi
}

# 오피스 문서(xlsx·docx·pptx)는 zip 이라 git grep 이 속을 못 본다. 풀어서 XML 을 검사한다
OFFICE_RE='\.(xlsx|xlsm|docx|pptx)$'
OFFICE_META='<dc:creator>[^<]|<cp:lastModifiedBy>[^<]|absPath url="|w:author="[^"]'
scan_office_blob() {  # $1=표시 이름 $2=blob 이름(git cat-file 이 읽는 것)
  local tmp text out
  tmp="$(mktemp)"
  git cat-file blob "$2" >"$tmp" 2>/dev/null || { rm -f "$tmp"; return; }
  # unzip 의 와일드카드는 '[Content_Types].xml' 같은 이름의 대괄호 때문에 일부만 맞는다.
  # 멤버 이름을 하나씩 받아 대괄호를 이스케이프해서 꺼낸다
  local member names=()
  while IFS= read -r member; do
    if [[ "$member" =~ \.(xml|rels)$ ]]; then
      member="${member//\[/\\[}"
      names+=("${member//\]/\\]}")
    fi
  done < <(unzip -Z1 "$tmp" 2>/dev/null || true)
  text=""
  if [[ ${#names[@]} -gt 0 ]]; then text="$(unzip -p "$tmp" "${names[@]}" 2>/dev/null || true)"; fi
  rm -f "$tmp"
  # set -e 아래에서 '[[ ]] && …' 가 함수 끝 상태로 남으면 스크립트가 조용히 끝난다. if 로 쓴다
  if [[ -z "$text" ]]; then return 0; fi
  if out="$(grep -oE "$OFFICE_META" <<<"$text" | sort -u)"; then
    report "$1 문서 속성에 작성자·저장 경로가 남아 있음: $(tr '\n' ' ' <<<"$out")"
  fi
  if out="$(grep -oE "${grep_args[@]}" <<<"$text" | sort -u)"; then
    report "$1 안에 개인정보로 보이는 문자열:"; echo "$out" | head -20 >&2
  fi
  if [[ ${#fixed_args[@]} -gt 0 ]] && grep -qiF "${fixed_args[@]}" <<<"$text"; then
    report "$1 안에 .privacy-blocklist 항목이 있음"
  fi
}

scan_office() {  # $1=staged|head|history
  local mode="$1" path sha
  case "$mode" in
    staged)
      while IFS= read -r -d '' path; do
        if [[ "$path" =~ $OFFICE_RE ]]; then scan_office_blob "$path" ":$path"; fi
      done < <(git diff --cached --name-only --diff-filter=AM -z)
      ;;
    head)
      while IFS= read -r -d '' path; do
        if [[ "$path" =~ $OFFICE_RE ]]; then scan_office_blob "$path" "HEAD:$path"; fi
      done < <(git ls-tree -r -z --name-only HEAD)
      ;;
    history)
      while read -r sha path; do
        if [[ "$path" =~ $OFFICE_RE ]]; then scan_office_blob "$path (blob ${sha:0:7})" "$sha"; fi
      done < <(git rev-list --all --objects | awk 'NF>=2' | sort -u -k1,1)
      ;;
  esac
}

case "$MODE" in
  --staged)
    check_email "커밋 작성자(author)" "$(git config user.email || true)"
    added="$(git diff --cached -U0 --no-color -- . "${EXCLUDES[@]}" | grep -E '^\+[^+]' || true)"
    if [[ -n "$added" ]]; then
      if out="$(grep -nE "${grep_args[@]}" <<<"$added")"; then
        report "추가한 줄에 개인정보로 보이는 문자열:"; echo "$out" | head -50 >&2
      fi
      if [[ ${#fixed_args[@]} -gt 0 ]] && out="$(grep -niF "${fixed_args[@]}" <<<"$added")"; then
        report ".privacy-blocklist 항목이 추가한 줄에 있음:"; echo "$out" | head -50 >&2
      fi
    fi
    scan_office staged
    ;;
  ""|--history)
    while IFS= read -r e; do check_email "커밋 기록의" "$e"; done \
      < <(git log --all --format='%ae%n%ce' | sort -u)
    # Co-authored-by 꼬리말에 적힌 공동 작성자 이메일도 본다 (Claude 꼬리말은 허용)
    while IFS= read -r e; do
      [[ "$e" == "noreply@anthropic.com" ]] && continue
      check_email "Co-authored-by 꼬리말의" "$e"
    done < <(git log --all --format='%(trailers:key=Co-authored-by,valueonly)' \
               | grep -oE '<[^>]+>' | tr -d '<>' | sort -u)
    if [[ "$MODE" == "--history" ]]; then
      # mapfile 은 bash 4 부터라 macOS 기본 bash(3.2)에 없다. 한 줄씩 읽는다
      revs=()
      while IFS= read -r rev; do revs+=("$rev"); done < <(git rev-list --all)
      scan_tree "${revs[@]}"
      scan_office history
    else
      scan_tree
      scan_office head
    fi
    ;;
  *) echo "사용법: $0 [--staged|--history]" >&2; exit 2 ;;
esac

if [[ $fail -ne 0 ]]; then
  echo "[개인정보 점검] 실패 — 위 항목을 고친 뒤 다시 시도하세요. 오탐이면 CONTRIBUTING.md 를 보세요." >&2
  exit 1
fi
echo "[개인정보 점검] 통과 (${MODE:-기본})"
