FROM ubuntu
MAINTAINER hao.qu@igt.com

ENV ENV_NAME ppaass-proxy
# Set proxy server
RUN echo "http_proxy=10.175.4.220:10080" >> /etc/enviorment
RUN echo "socks_proxy=10.175.4.220:10080" >> /etc/enviorment
RUN echo "proxy=10.175.4.220:10080" >> /etc/yum.conf

RUN apt update -y
RUN apt upgrade -y
RUN apt-get install openjdk-14-jdk -y
RUN apt-get install maven -y
RUN apt clean all

RUN mkdir -p /ppaass

COPY init.sh /ppaass

RUN groupadd -g 501 ppaass
RUN useradd -u 501 -g 501 -m ppaass
RUN chmod 750 /home/ppaass
RUN echo "ppaass:password" | chpasswd

WORKDIR /ppaass
RUN chmod 777 /ppaass/init.sh
RUN /ppaass/init.sh

CMD ["/bin/bash"]

