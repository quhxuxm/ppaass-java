set JAVA_HOME=C:\Software\jdk-17
start %JAVA_HOME%\bin\javaw -server -Dcom.sun.management.jmxremote.port=11181 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Djava.rmi.server.hostname=127.0.0.1 -jar agent-1.0-SNAPSHOT.jar --spring.config.location=classpath:application.properties,optional:./application.properties > run.log 2>&1 &
