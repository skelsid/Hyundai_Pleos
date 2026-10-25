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
      mapfile -t revs < <(git rev-list --all)
      scan_tree "${revs[@]}"
    else
      scan_tree
    fi
    ;;
  *) echo "사용법: $0 [--staged|--history]" >&2; exit 2 ;;
esac

if [[ $fail -ne 0 ]]; then
  echo "[개인정보 점검] 실패 — 위 항목을 고친 뒤 다시 시도하세요. 오탐이면 CONTRIBUTING.md 를 보세요." >&2
  exit 1
fi
echo "[개인정보 점검] 통과 (${MODE:-기본})"
