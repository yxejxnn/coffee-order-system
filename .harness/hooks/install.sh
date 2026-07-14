#!/bin/sh
# 공유용 git 훅을 이 저장소의 .git/hooks/ 에 설치한다.
# git은 .git/hooks/ 를 커밋·클론하지 않으므로, 새 클론마다 한 번 실행한다.
#
# 사용: sh .harness/hooks/install.sh

set -e

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || {
	echo "❌ git 저장소가 아닙니다. 먼저 'git init' 후 실행하세요." >&2
	exit 1
}

SRC="$ROOT/.harness/hooks"
DEST="$ROOT/.git/hooks"

for hook in pre-commit pre-push; do
	if [ -f "$SRC/$hook" ]; then
		cp "$SRC/$hook" "$DEST/$hook"
		chmod +x "$DEST/$hook"
		case "$hook" in
			pre-commit) echo "✅ 설치: .git/hooks/$hook (main·dev 직접 커밋 차단)" ;;
			pre-push)   echo "✅ 설치: .git/hooks/$hook (main·dev 직접 push 차단)" ;;
			*)          echo "✅ 설치: .git/hooks/$hook" ;;
		esac
	fi
done

echo "완료. 이제 main·dev 직접 커밋/push가 차단되고, feature 브랜치만 push·PR할 수 있습니다."
