# wget https://github.com/quhxuxm/ppaass/raw/master/distribute/target/ppaass-1.0-SNAPSHOT.zip
ps -ef | grep proxy-1.0-SNAPSHOT.jar | grep -v grep | awk '{print $2}' | xargs kill
cd /ppaass
rm -rf /ppaass/build
rm -rf /ppaass/sourcecode
# Build
mkdir /ppaass/sourcecode
mkdir /ppaass/build
cd /ppaass/sourcecode
git clone https://github.com/quhxuxm/ppaass.git ppaass
cd /ppaass/sourcecode/ppaass
git pull
mvn clean package
# ps -ef | grep gradle | grep -v grep | awk '{print $2}' | xargs kill -9
cp /ppaass/sourcecode/ppaass/distribution/target/ppaass-1.0-SNAPSHOT.zip /ppaass/build
cd /ppaass/build
chmod 777 ppaass-1.0-SNAPSHOT.zip
unzip ppaass-1.0-SNAPSHOT.zip
cd /ppaass/build/proxy
chmod 777 *.sh
nohup ./start.sh >run.log 2>&1 &
ps -ef|grep java|grep proxy-1.0-SNAPSHOT.jar
