#!/usr/bin/env bash
# 이슈 #9 다중 인스턴스 검증 오케스트레이션.
# docker compose 인프라(mysql/redis/kafka) 기동 -> 앱 2인스턴스(SERVER_PORT 다름) 기동
# -> MultiInstanceVerificationTest(verifyMultiInstance 태스크) 실행 -> 인스턴스 정리.
#
# 사전조건: .env가 프로젝트 루트에 있어야 한다(docker-compose.yml이 읽는 크리덴셜).
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f .env ]; then
	set -a
	# shellcheck disable=SC1091
	source .env
	set +a
fi

export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-3307}"
export DB_NAME="${DB_NAME:-${MYSQL_DATABASE:-coffee_order}}"
export DB_USERNAME="${DB_USERNAME:-root}"
export DB_PASSWORD="${DB_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
export REDIS_HOST="${REDIS_HOST:-localhost}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"

if [ -z "$DB_PASSWORD" ]; then
	echo "DB_PASSWORD(또는 .env의 MYSQL_ROOT_PASSWORD)가 필요하다." >&2
	exit 1
fi

PORT1="${PORT1:-8080}"
PORT2="${PORT2:-8081}"
LOG_DIR="$(mktemp -d)"
PID1=""
PID2=""

cleanup() {
	echo "인스턴스 프로세스 정리..."
	[ -n "$PID1" ] && kill "$PID1" 2>/dev/null || true
	[ -n "$PID2" ] && kill "$PID2" 2>/dev/null || true
	# kill은 신호만 보내고 바로 반환하므로, 종료를 기다리지 않으면 스크립트가 끝난 뒤에도
	# 포트가 잠시 점유된 채로 남아 바로 이어지는 재실행이 bind 실패로 헷갈리게 실패할 수 있다.
	[ -n "$PID1" ] && wait "$PID1" 2>/dev/null
	[ -n "$PID2" ] && wait "$PID2" 2>/dev/null
	true
}
trap cleanup EXIT

wait_for_menus() {
	local port="$1"
	local deadline=$((SECONDS + 60))
	while [ "$SECONDS" -lt "$deadline" ]; do
		# 헬스체크만이 아니라 DataSeeder가 심은 메뉴가 실제로 응답에 들어있는지까지 확인한다.
		# ApplicationRunner(DataSeeder)는 내장 톰캣이 이미 요청을 받기 시작한 뒤에 실행되므로,
		# 200만 보고 넘어가면 시딩 전의 빈 배열을 "준비됨"으로 오판할 수 있다.
		if curl -sf "http://localhost:${port}/api/menus" 2>/dev/null | grep -q '"id"'; then
			return 0
		fi
		sleep 1
	done
	echo "인스턴스(port ${port})가 준비되지 않았다. 로그: ${LOG_DIR}/app${port}.log" >&2
	return 1
}

echo "1) docker compose 인프라(mysql/redis/kafka) 기동..."
docker compose up -d mysql redis kafka

echo "2) jar 빌드..."
./gradlew bootJar -q

JAR_FILE="$(ls -t build/libs/*.jar | grep -v plain | head -n1)"
echo "   jar: ${JAR_FILE}"

echo "3) 인스턴스1(port ${PORT1}) 기동..."
SERVER_PORT="$PORT1" java -jar "$JAR_FILE" > "${LOG_DIR}/app${PORT1}.log" 2>&1 &
PID1=$!
wait_for_menus "$PORT1"
echo "   인스턴스1 준비 완료(시딩 확인됨)"

# 인스턴스1이 시딩까지 끝낸 뒤에 인스턴스2를 띄워, 두 인스턴스가 동시에
# count()==0을 보고 중복 시드를 넣는 경쟁을 줄인다(DataSeeder는 존재 검증 없이
# count()==0일 때만 insert하므로 완전한 배제는 아니고 완화 조치다).
echo "4) 인스턴스2(port ${PORT2}) 기동..."
SERVER_PORT="$PORT2" java -jar "$JAR_FILE" > "${LOG_DIR}/app${PORT2}.log" 2>&1 &
PID2=$!
wait_for_menus "$PORT2"
echo "   인스턴스2 준비 완료"

echo "5) verifyMultiInstance 실행..."
set +e
./gradlew verifyMultiInstance -Dinstance1.port="$PORT1" -Dinstance2.port="$PORT2"
STATUS=$?
set -e

echo "인스턴스 로그 디렉터리: ${LOG_DIR}"
exit "$STATUS"
