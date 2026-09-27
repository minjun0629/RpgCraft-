#!/usr/bin/env sh
# RpgCraft 빌드 스크립트 (macOS / Linux) - JDK 17 이상(21 포함) + Maven 또는 Gradle 필요
# 사용법: ./build.sh            -> Java 21 (기본)
#         ./build.sh 17         -> Java 17 서버용
set -e
cd "$(dirname "$0")"
RELEASE="${1:-21}"

if ! command -v java >/dev/null 2>&1; then
  echo "[오류] java 를 찾을 수 없습니다. JDK 17 또는 21 을 설치하세요."; exit 1
fi
VER=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
echo "JDK 버전: $VER / 바이트코드: Java $RELEASE"
if [ "$VER" -lt 17 ] 2>/dev/null; then echo "[오류] JDK 17 이상이 필요합니다."; exit 1; fi
if [ "$VER" -lt "$RELEASE" ] 2>/dev/null; then echo "[오류] Java $RELEASE 로 빌드하려면 JDK $RELEASE 이상이 필요합니다."; exit 1; fi

mkdir -p dist
if command -v mvn >/dev/null 2>&1; then
  mvn -q -B package -Djava.release="$RELEASE"
  cp target/RpgCraft.jar dist/RpgCraft.jar
elif command -v gradle >/dev/null 2>&1; then
  gradle build -q -PjavaRelease="$RELEASE"
  cp build/libs/RpgCraft-*.jar dist/RpgCraft.jar
else
  echo "[오류] Maven(mvn) 또는 Gradle 이 필요합니다. (Java 21 에서 Gradle 은 8.5 이상)"; exit 1
fi
echo ""
echo "완료: dist/RpgCraft.jar  ->  서버의 plugins 폴더에 넣고 서버를 재시작하세요."
