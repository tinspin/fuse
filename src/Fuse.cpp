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

	char msg[100]="GET / HTTP/1.1\r\nHost: localhost\r\n\r\n";
	int conn_sock;
	struct sockaddr_in server_addr;
	conn_sock = socket(AF_INET, SOCK_STREAM, 0);

	server_addr.sin_family = AF_INET;
	server_addr.sin_port = htons(8000);
	server_addr.sin_addr.s_addr = inet_addr("127.0.0.1");

	connect(conn_sock, (struct sockaddr *) &server_addr, sizeof (server_addr));
	
#ifdef __WIN32__
	int result = send(conn_sock, msg, strlen(msg), 0);
	printf("yo: %d\n", result);
	shutdown(conn_sock, SD_SEND);
#else
	write(conn_sock, msg, strlen(msg));
	close(conn_sock);
#endif
	
	std::cout << "Hello World!";
	return 0;
}