import socket
import socks

host = "192.168.31.11"
port = 888

sock = socks.socksocket(socket.AF_INET, socket.SOCK_DGRAM)
sock.set_proxy(socks.SOCKS5, "127.0.0.1", 10081)
sock.connect((host, port))

i = 0

while True:
    message = bytes('This is the message.  It will be repeated: %s' % i, 'utf-8')
    i = i + 1
    print('sending [%s]' % message)
    sock.send(message)
    data, addr = sock.recv(65535)
    print('received [%s]' % data)
    print('from', addr)

sock.close()
