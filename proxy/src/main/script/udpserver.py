import socket

host = "192.168.31.11"
port = 888

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind((host, port))

i = 0

while True:
    message, client_address = sock.recvfrom(65535)
    print('connection from', client_address)
    print('received "%s"' % message)
    print('sending data back to the client [%s]' % i)
    sock.sendto(bytes("server echo: [%s]" % i, "utf-8"), client_address)
    print('finish', client_address)
    i = i + 1
