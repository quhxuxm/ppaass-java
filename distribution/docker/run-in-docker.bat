docker build -t="ppaass:v1" ./
docker run --name ppaass -p 80:80 ppaass:v1
