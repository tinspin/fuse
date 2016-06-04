#include <iostream>
#include <streambuf>
#include <stdio.h>
#include <pthread.h>
#ifdef __WIN32__
# include <winsock2.h>
#else
# include <sys/socket.h>
#endif

using namespace std;

/*
 * This uses two threads, one for each socket/queue.
 * The buffer stream is used for the chunked incoming data.
 */

class BufferInputStream : public basic_streambuf<char>
{
private:
	static const int SIZE = 128;
	char ibuf[SIZE];
	int sock;
	
public:
	BufferInputStream(int sock);
	~BufferInputStream() {}
	
protected:
	int overflow(int c) { return c; }
	int sync() { return 0; }
	int underflow()
	{
		if(gptr() < egptr())
			return *gptr();

#ifdef __WIN32__
		int num = recv(sock, reinterpret_cast<char*>(ibuf), SIZE, 0);
#else
		int num = read(sock, reinterpret_cast<char*>(ibuf), SIZE);
#endif

		if(num <= 0)
			return EOF;

		cout << "IN: " << num << endl;

		setg(ibuf, ibuf, ibuf + num);
		return *gptr();
	}
};

BufferInputStream::BufferInputStream(int sock) {
	this -> sock = sock;
	setg(ibuf, ibuf, ibuf);
}

void *Pull(void *threadid)
{
	long tid = (long) threadid;
	cout << "THREAD: " << tid << endl;
	pthread_exit(NULL);
}

main()
{
#ifdef __WIN32__
	WORD versionWanted = MAKEWORD(2, 2);
	WSADATA wsaData;
	WSAStartup(versionWanted, &wsaData);
#endif

	pthread_t thread;
	pthread_create(&thread, NULL, Pull, (void *) 0);

	char data[100] = "GET /pull HTTP/1.1\r\nHost: bitcoinbankbook.com\r\n\r\n";

	struct hostent *host = gethostbyname("bitcoinbankbook.com");
	struct sockaddr_in address;
	
	int flag = 1;
	int sock = socket(AF_INET, SOCK_STREAM, 0);
	setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, (char*) &flag, sizeof(int));
	
	address.sin_family = AF_INET;
	address.sin_port = htons(80);
	address.sin_addr.s_addr = inet_addr(inet_ntoa(*(struct in_addr*)(host -> h_addr_list[0]))); // inet_addr("127.0.0.1");

	cout << inet_ntoa(*(struct in_addr*)(host -> h_addr_list[0])) << endl;

	connect(sock, (struct sockaddr*) &address, sizeof (address));
	
	int result = 0;
	
#ifdef __WIN32__
	result = send(sock, data, strlen(data), 0);
#else
	result = write(sock, data, strlen(data));
#endif

	printf("OUT: %d\n", result);
	
	BufferInputStream buffer(sock);
	istream stream(&buffer);
		
	string line;
	
	for(int i = 0; i < 50; i++) {
		getline(stream, line, '\r');
		cout << line << flush;
	}
	
	//char c;
	//stream.get(c);
	//printf("in: %d\n", c);
	//printf("in: %d\n", 'H');
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
	
	return 0;
}