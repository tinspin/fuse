#include <iostream>
#include <stdio.h>
#ifdef __WIN32__
# include <winsock2.h>
#else
# include <sys/socket.h>
#endif

main()
{
#ifdef __WIN32__
	WORD versionWanted = MAKEWORD(1, 1);
	WSADATA wsaData;
	WSAStartup(versionWanted, &wsaData);
#endif

	char data[100]="GET / HTTP/1.1\r\nHost: localhost\r\n\r\n";
	int socket_ptr;
	struct sockaddr_in address;
	socket_ptr = socket(AF_INET, SOCK_STREAM, 0);

	address.sin_family = AF_INET;
	address.sin_port = htons(8000);
	address.sin_addr.s_addr = inet_addr("127.0.0.1");

	connect(socket_ptr, (struct sockaddr *) &address, sizeof (address));
	
	int result = 0;
	
#ifdef __WIN32__
	result = send(socket_ptr, data, strlen(data), 0);
#else
	result = write(socket_ptr, data, strlen(data));
#endif

	printf("out: %d\n", result);

	char recvbuf[1024];
	result = recv(socket_ptr, recvbuf, 1024, 0);
	printf("in: %d\n", result);
	
#ifdef __WIN32__
	closesocket(socket_ptr);
    WSACleanup();
#else
	close(socket_ptr);
#endif
	
	std::cout << "Hello World!";
	return 0;
}