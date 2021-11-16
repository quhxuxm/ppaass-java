#Prepare base env
sudo apt update
sudo apt upgrade -y
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
#sudo mkdir /opt/java
sudo apt install openjdk-17-jdk -y
sudo apt install openjdk-17-jre -y
sudo apt install unzip -y

sudo rm apache-maven-3.8.3-bin.zip
sudo wget https://dlcdn.apache.org/maven/maven-3/3.8.3/binaries/apache-maven-3.8.3-bin.zip
sudo unzip apache-maven-3.8.3-bin.zip -d /opt/maven/
sudo update-alternatives --install /usr/bin/mvn mvn /opt/maven/apache-maven-3.8.3/bin/mvn 100
sudo update-alternatives --config mvn

# sudo wget https://download.java.net/java/GA/jdk15.0.2/0d1cfde4252546c6931946de8db48ee2/7/GPL/openjdk-15.0.2_linux-x64_bin.tar.gz
# sudo tar -zxf openjdk-15.0.2_linux-x64_bin.tar.gz -C /opt/java/
# sudo update-alternatives --install /usr/bin/java java /opt/java/jdk-15.0.2/bin/java 100
# sudo update-alternatives --config java

#Create swap file
sudo swapoff /swapfile
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
sudo free -h
#echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Start install ppaass
sudo ps -ef | grep proxy-1.0-SNAPSHOT.jar | grep -v grep | awk '{print $2}' | xargs kill
sudo rm -rf /tmp/build-java
sudo rm -rf /tmp/sourcecode-java

# Build
sudo mkdir /tmp/sourcecode-java
sudo mkdir /tmp/build-java

# Pull ppaass-protocol
cd /tmp/sourcecode-java
sudo git clone https://github.com/quhxuxm/ppaass-protocol.git ppaass-protocol
cd /tmp/sourcecode-java/ppaass-protocol
sudo git pull

sudo mvn clean install

# Pull ppaass
cd /tmp/sourcecode-java
sudo git clone https://github.com/quhxuxm/ppaass-java.git ppaass-java
cd /tmp/sourcecode-java/ppaass-java
sudo git pull
sudo mvn clean package

# ps -ef | grep gradle | grep -v grep | awk '{print $2}' | xargs kill -9
sudo cp /tmp/sourcecode-java/ppaass-java/distribution/target/ppaass-1.0-SNAPSHOT.zip /tmp/build-java
cd /tmp/build-java
sudo chmod 777 ppaass-1.0-SNAPSHOT.zip
sudo unzip ppaass-1.0-SNAPSHOT.zip
cd /tmp/build-java/proxy
sudo chmod 777 *.sh

#Start with the low configuration by default
sudo nohup ./start.sh >run.log 2>&1 &
#Start with the high configuration
#sudo nohup ./start-high.sh >run.log 2>&1 &

sudo ps -ef|grep java|grep proxy-1.0-SNAPSHOT.jar
