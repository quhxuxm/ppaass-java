# wget https://github.com/quhxuxm/ppaass/raw/master/distribute/target/ppaass-1.0-SNAPSHOT.zip
ps -ef | grep proxy-1.0-SNAPSHOT.jar | grep -v grep | awk '{print $2}' | xargs kill
rm -rf /home/build
rm -rf /home/sourcecode
# Build
mkdir /home/sourcecode
mkdir /home/build
cd /home/sourcecode
git clone https://github.com/quhxuxm/ppaass.git ppaass
cd /home/sourcecode/ppaass
git pull
mvn clean package
# ps -ef | grep gradle | grep -v grep | awk '{print $2}' | xargs kill -9
cp /home/sourcecode/ppaass/distribution/target/ppaass-1.0-SNAPSHOT.zip /home/build
cd /home/build
chmod 777 ppaass-1.0-SNAPSHOT.zip
unzip ppaass-1.0-SNAPSHOT.zip
cd /home/build/proxy
chmod 777 *.sh
nohup ./start.sh >run.log 2>&1 &
ps -ef|grep java|grep proxy-1.0-SNAPSHOT.jar
