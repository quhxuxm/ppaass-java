FROM ubuntu
MAINTAINER hao.qu@igt.com

ENV ENV_NAME ppaass
# Set proxy server
# RUN echo "http_proxy=10.175.4.220:10080" >> /etc/enviorment
# RUN echo "socks_proxy=10.175.4.220:10080" >> /etc/enviorment
# RUN echo "proxy=10.175.4.220:10080" >> /etc/yum.conf

RUN apt update -y
RUN apt upgrade -y
RUN apt-get install openjdk-14-jdk -y
RUN apt-get install unzip -y
RUN apt clean all
RUN mkdir -p /ppaass
WORKDIR /ppaass

RUN rm -rf /ppaass/build
RUN rm -rf /ppaass/sourcecode
# Build
RUN mkdir /ppaass/sourcecode
RUN mkdir /ppaass/build
RUN mkdir /ppaass/build/proxy
# ps -ef | grep gradle | grep -v grep | awk '{print $2}' | xargs kill -9
COPY proxy-1.0-SNAPSHOT.jar /ppaass/build/proxy/proxy-1.0-SNAPSHOT.jar
COPY start.sh /ppaass/build/proxy/start.sh
WORKDIR /ppaass/build/proxy
RUN chmod 777 proxy-1.0-SNAPSHOT.jar
RUN chmod 777 start.sh
CMD ./start.sh
