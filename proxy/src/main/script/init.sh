#Prepare base env
# sudo apt update
# sudo apt upgrade -y
# sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
# sudo mkdir /opt/java
# sudo apt install maven -y
# sudo apt install unzip -y
# sudo wget https://download.java.net/java/GA/jdk15.0.2/0d1cfde4252546c6931946de8db48ee2/7/GPL/openjdk-15.0.2_linux-x64_bin.tar.gz
# sudo tar -zxf openjdk-15.0.2_linux-x64_bin.tar.gz -C /opt/java/
# sudo update-alternatives --install /usr/bin/java java /opt/java/jdk-15.0.2/bin/java 100
# sudo update-alternatives --config java

#Create swap file
#sudo fallocate -l 4G /swapfile
#sudo chmod 600 /swapfile
#sudo mkswap /swapfile
#sudo swapon --show
#sudo free -h
#echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Start install ppaass
sudo ps -ef | grep proxy-1.0-SNAPSHOT.jar | grep -v grep | awk '{print $2}' | xargs kill
sudo rm -rf /tmp/build
sudo rm -rf /tmp/sourcecode
# Build
sudo mkdir /tmp/sourcecode
sudo mkdir /tmp/build
cd /tmp/sourcecode
sudo git clone https://github.com/quhxuxm/ppaass.git ppaass
cd /tmp/sourcecode/ppaass
sudo git pull
sudo mvn clean package
# ps -ef | grep gradle | grep -v grep | awk '{print $2}' | xargs kill -9
sudo cp /tmp/sourcecode/ppaass/distribution/target/ppaass-1.0-SNAPSHOT.zip /tmp/build
cd /tmp/build
sudo chmod 777 ppaass-1.0-SNAPSHOT.zip
sudo unzip ppaass-1.0-SNAPSHOT.zip
cd /tmp/build/proxy
sudo chmod 777 *.sh

#Start with the low configuration by default
sudo nohup ./start-low.sh >run.log 2>&1 &
#Start with the high configuration
#sudo nohup ./start-high.sh >run.log 2>&1 &

sudo ps -ef|grep java|grep proxy-1.0-SNAPSHOT.jar
