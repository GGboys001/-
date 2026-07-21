@ECHO OFF
SET APP_HOME=%~dp0
SET WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Missing gradle\wrapper\gradle-wrapper.jar. Open the project in Android Studio and run Gradle sync.
  EXIT /B 1
)
java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
