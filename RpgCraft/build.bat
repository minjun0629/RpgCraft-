@echo off
chcp 65001 >nul
rem RpgCraft 빌드 스크립트 (Windows) - JDK 17 이상(21 포함) + Maven 또는 Gradle 필요
rem 사용법: build.bat        -> Java 21 (기본)
rem         build.bat 17     -> Java 17 서버용
setlocal
cd /d "%~dp0"
set RELEASE=%1
if "%RELEASE%"=="" set RELEASE=21

where java >nul 2>nul
if errorlevel 1 (
  echo [오류] java 를 찾을 수 없습니다. JDK 17 또는 21 을 설치하고 PATH 에 추가하세요.
  pause & exit /b 1
)
java -version
echo 바이트코드: Java %RELEASE%

if not exist dist mkdir dist
where mvn >nul 2>nul
if not errorlevel 1 (
  call mvn -q -B package -Djava.release=%RELEASE%
  if errorlevel 1 ( echo [오류] 빌드 실패 & pause & exit /b 1 )
  for %%f in (target\RpgCraft-*.jar) do copy /Y "%%f" dist\ >nul
  goto done
)
where gradle >nul 2>nul
if not errorlevel 1 (
  call gradle build -q -PjavaRelease=%RELEASE%
  if errorlevel 1 ( echo [오류] 빌드 실패 & pause & exit /b 1 )
  for %%f in (build\libs\RpgCraft-*.jar) do copy /Y "%%f" dist\ >nul
  goto done
)
echo [오류] Maven(mvn) 또는 Gradle 이 필요합니다. (Java 21 에서 Gradle 은 8.5 이상)
pause & exit /b 1

:done
echo.
echo 완료: dist\RpgCraft.jar  -^>  서버의 plugins 폴더에 넣고 서버를 재시작하세요.
pause
