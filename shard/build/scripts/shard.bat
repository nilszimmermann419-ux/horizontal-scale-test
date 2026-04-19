@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  shard startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and SHARD_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\shard-1.0.0-SNAPSHOT.jar;%APP_HOME%\lib\plugin-loader-1.0.0-SNAPSHOT.jar;%APP_HOME%\lib\api-1.0.0-SNAPSHOT.jar;%APP_HOME%\lib\shared-1.0.0-SNAPSHOT.jar;%APP_HOME%\lib\proto-1.0.0-SNAPSHOT.jar;%APP_HOME%\lib\kotlin-stdlib-1.9.22.jar;%APP_HOME%\lib\minestom-2025.10.04-1.21.8.jar;%APP_HOME%\lib\grpc-netty-1.60.0.jar;%APP_HOME%\lib\grpc-protobuf-1.60.0.jar;%APP_HOME%\lib\grpc-stub-1.60.0.jar;%APP_HOME%\lib\lettuce-core-6.3.0.RELEASE.jar;%APP_HOME%\lib\adventure-nbt-4.24.0.jar;%APP_HOME%\lib\adventure-text-serializer-gson-4.24.0.jar;%APP_HOME%\lib\adventure-text-serializer-plain-4.24.0.jar;%APP_HOME%\lib\adventure-text-serializer-ansi-4.24.0.jar;%APP_HOME%\lib\adventure-text-logger-slf4j-4.24.0.jar;%APP_HOME%\lib\adventure-key-4.24.0.jar;%APP_HOME%\lib\adventure-text-serializer-json-4.24.0.jar;%APP_HOME%\lib\adventure-text-serializer-commons-4.24.0.jar;%APP_HOME%\lib\adventure-text-serializer-legacy-4.24.0.jar;%APP_HOME%\lib\adventure-api-4.24.0.jar;%APP_HOME%\lib\annotations-26.0.1.jar;%APP_HOME%\lib\jackson-core-2.16.0.jar;%APP_HOME%\lib\jackson-annotations-2.16.0.jar;%APP_HOME%\lib\jackson-dataformat-yaml-2.16.0.jar;%APP_HOME%\lib\jackson-databind-2.16.0.jar;%APP_HOME%\lib\logback-classic-1.4.14.jar;%APP_HOME%\lib\slf4j-api-2.0.16.jar;%APP_HOME%\lib\micrometer-registry-prometheus-1.12.0.jar;%APP_HOME%\lib\micrometer-core-1.12.0.jar;%APP_HOME%\lib\javax.annotation-api-1.3.2.jar;%APP_HOME%\lib\proto-google-common-protos-2.22.0.jar;%APP_HOME%\lib\protobuf-java-3.25.1.jar;%APP_HOME%\lib\data-1.21.8-rv1.jar;%APP_HOME%\lib\flare-2.0.1.jar;%APP_HOME%\lib\flare-fastutil-2.0.1.jar;%APP_HOME%\lib\jctools-core-4.0.5.jar;%APP_HOME%\lib\fastutil-8.5.15.jar;%APP_HOME%\lib\grpc-core-1.60.0.jar;%APP_HOME%\lib\gson-2.11.0.jar;%APP_HOME%\lib\netty-codec-http2-4.1.100.Final.jar;%APP_HOME%\lib\netty-handler-proxy-4.1.100.Final.jar;%APP_HOME%\lib\grpc-protobuf-lite-1.60.0.jar;%APP_HOME%\lib\grpc-context-1.60.0.jar;%APP_HOME%\lib\grpc-api-1.60.0.jar;%APP_HOME%\lib\grpc-util-1.60.0.jar;%APP_HOME%\lib\guava-32.0.1-android.jar;%APP_HOME%\lib\error_prone_annotations-2.27.0.jar;%APP_HOME%\lib\perfmark-api-0.26.0.jar;%APP_HOME%\lib\netty-codec-http-4.1.100.Final.jar;%APP_HOME%\lib\netty-handler-4.1.101.Final.jar;%APP_HOME%\lib\netty-transport-native-unix-common-4.1.101.Final.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\netty-codec-socks-4.1.100.Final.jar;%APP_HOME%\lib\netty-codec-4.1.101.Final.jar;%APP_HOME%\lib\netty-transport-4.1.101.Final.jar;%APP_HOME%\lib\netty-buffer-4.1.101.Final.jar;%APP_HOME%\lib\netty-resolver-4.1.101.Final.jar;%APP_HOME%\lib\netty-common-4.1.101.Final.jar;%APP_HOME%\lib\reactor-core-3.6.0.jar;%APP_HOME%\lib\snakeyaml-2.2.jar;%APP_HOME%\lib\logback-core-1.4.14.jar;%APP_HOME%\lib\micrometer-observation-1.12.0.jar;%APP_HOME%\lib\micrometer-commons-1.12.0.jar;%APP_HOME%\lib\HdrHistogram-2.1.12.jar;%APP_HOME%\lib\LatencyUtils-2.0.3.jar;%APP_HOME%\lib\simpleclient_common-0.16.0.jar;%APP_HOME%\lib\examination-string-1.3.0.jar;%APP_HOME%\lib\examination-api-1.3.0.jar;%APP_HOME%\lib\ansi-1.1.1.jar;%APP_HOME%\lib\annotations-4.1.1.4.jar;%APP_HOME%\lib\animal-sniffer-annotations-1.23.jar;%APP_HOME%\lib\failureaccess-1.0.1.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\checker-qual-3.33.0.jar;%APP_HOME%\lib\j2objc-annotations-2.8.jar;%APP_HOME%\lib\reactive-streams-1.0.4.jar;%APP_HOME%\lib\simpleclient-0.16.0.jar;%APP_HOME%\lib\option-1.1.0.jar;%APP_HOME%\lib\simpleclient_tracer_otel-0.16.0.jar;%APP_HOME%\lib\simpleclient_tracer_otel_agent-0.16.0.jar;%APP_HOME%\lib\simpleclient_tracer_common-0.16.0.jar


@rem Execute shard
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %SHARD_OPTS%  -classpath "%CLASSPATH%" com.shardedmc.shard.ShardServer %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable SHARD_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%SHARD_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
