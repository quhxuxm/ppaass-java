
import sys, socket, socks

host = sys.argv[1]
port = int(sys.argv[2])

sock = socks.socksocket(socket.AF_INET, socket.SOCK_DGRAM)
sock.set_proxy(socks.SOCKS5, "127.0.0.1", 10080)
sock.connect((host, port))

while True:
    message = 'This is the message.  It will be repeated.'
    print >>sys.stderr, 'sending "%s"' % message
    sock.send(message)
    data = sock.recv(65535)
    print >>sys.stderr, 'received "%s"' % data

sock.close()