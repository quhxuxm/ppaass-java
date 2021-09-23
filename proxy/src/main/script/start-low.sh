java -jar -server -XX:+UseParallelGC -Xms512m -Xmx512m  proxy-1.0-SNAPSHOT.jar --spring.config.location=classpath:application.properties,optional:./config/application-low.properties
