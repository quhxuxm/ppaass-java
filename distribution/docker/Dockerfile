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
# ps -ef | grep gradle | grep -v grep | awk '{print $2}' | xargs kill -9
COPY ppaass-1.0-SNAPSHOT.zip /ppaass/build/ppaass-1.0-SNAPSHOT.zip
RUN chmod 777 /ppaass/build/ppaass-1.0-SNAPSHOT.zip
WORKDIR /ppaass/build/
RUN unzip ppaass-1.0-SNAPSHOT.zip
WORKDIR /ppaass/build/proxy
RUN chmod 777 *.sh
CMD ./start.sh


