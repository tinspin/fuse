#include <iostream>
#include <streambuf>
#include <stdio.h>
#include <pthread.h>
#ifdef __WIN32__
# include <winsock2.h>
#else
# include <sys/socket.h>
#endif

/*
 * This uses two threads, one for each socket.
 * The buffer stream is used for the chunked incoming data.
 */

class BufferInputStream : public std::basic_streambuf<char>
{
private:
	static const int SIZE = 128;
	char ibuf[SIZE];
	int sock;
	
public:
	BufferInputStream(int sock);
	~BufferInputStream() {}
	
protected:
	int overflow(int_type c)
	{
		return c;
	}

	int sync()
	{
		return 0;
	}

	int underflow()
	{
		if(gptr() < egptr())
			return *gptr();

		int num;
		if((num = recv(sock, reinterpret_cast<char*>(ibuf), SIZE, 0)) <= 0)
			return EOF;

		setg(ibuf, ibuf, ibuf + num);
		return *gptr();
	}
};

BufferInputStream::BufferInputStream(int sock) {
	this -> sock = sock;
	setg(ibuf, ibuf, ibuf);
}

void *PrintHello(void *threadid)
{
	long tid = (long) threadid;
	std::cout << "Hello World! Thread ID, " << tid << std::endl;
	pthread_exit(NULL);
}

main()
{
#ifdef __WIN32__
	WORD versionWanted = MAKEWORD(1, 1);
	WSADATA wsaData;
	WSAStartup(versionWanted, &wsaData);
#endif

	pthread_t thread;
	pthread_create(&thread, NULL, PrintHello, (void *) 0);

	char data[100] = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n";

	struct sockaddr_in address;
	int sock = socket(AF_INET, SOCK_STREAM, 0);

	address.sin_family = AF_INET;
	address.sin_port = htons(8000);
	address.sin_addr.s_addr = inet_addr("127.0.0.1");

	connect(sock, (struct sockaddr *) &address, sizeof (address));
	
	int result = 0;
	
#ifdef __WIN32__
	result = send(sock, data, strlen(data), 0);
#else
	result = write(sock, data, strlen(data));
#endif

	printf("out: %d\n", result);
	
	BufferInputStream input(sock);
	
	std::string line;
	std::istream stream(&input);
	std::getline(stream, line);
	
	std::cout << line << std::endl;
	//printf("in: %d\n", line);
/*
	char buffer[1024];
	
#ifdef __WIN32__
	result = recv(sock, buffer, 1024, 0);
#else
	result = read(sock, buffer, 1024);
#endif
	
	printf("in: %d\n", result);
*/
#ifdef __WIN32__
	closesocket(sock);
	WSACleanup();
#else
	close(sock);
#endif
	
	std::cout << "Hello World!";
	return 0;
}