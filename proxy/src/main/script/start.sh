java -jar -server -Dio.netty.recycler.maxCapacity=32 -Dio.netty.noResourceLeakDetection=true -XX:+UseG1GC -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:+SegmentedCodeCache  -XX:+ExplicitGCInvokesConcurrent -Xms512m -Xmx512m -XX:MaxDirectMemorySize=512m  proxy-1.0-SNAPSHOT.jar --spring.config.location=classpath:application.properties,optional:./config/application.properties