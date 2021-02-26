# wget https://github.com/quhxuxm/ppaass/raw/master/distribute/target/ppaass-1.0-SNAPSHOT.zip
ps -ef | grep proxy-1.0-SNAPSHOT.jar | grep -v grep | awk '{print $2}' | xargs kill
rm -rf /tmp/build
rm -rf /tmp/sourcecode
# Build
mkdir /tmp/sourcecode
mkdir /tmp/build
cd /tmp/sourcecode
git clone https://github.com/quhxuxm/ppaass.git ppaass
cd /tmp/sourcecode/ppaass
git pull
mvn clean package
# ps -ef | grep gradle | grep -v grep | awk '{print $2}' | xargs kill -9
cp /tmp/sourcecode/ppaass/distribution/target/ppaass-1.0-SNAPSHOT.zip /tmp/build
cd /tmp/build
chmod 777 ppaass-1.0-SNAPSHOT.zip
unzip ppaass-1.0-SNAPSHOT.zip
cd /tmp/build/proxy
chmod 777 *.sh
sudo nohup ./start.sh >run.log 2>&1 &
ps -ef|grep java|grep proxy-1.0-SNAPSHOT.jar
