import sys, socket

host = sys.argv[1]
port = int(sys.argv[2])

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind((host, port))

while True:
    message, client_address = sock.recvfrom(65535)
    print >>sys.stderr, 'connection from', client_address
    print >>sys.stderr, 'received "%s"' % message
    print >>sys.stderr, 'sending data back to the client'
    sock.sendto(message+"(server echo)", client_address)
    print >>sys.stderr, 'finish', client_address
