#include <queue>
#include <mutex>
#include <string>
#include <iostream>
#include <streambuf>
#include <pthread.h>
#ifdef __WIN32__
#	include <winsock2.h>
#else
#	include <sys/socket.h>
#endif

using namespace std;

/*
 * This uses two threads, one for each socket/queue.
 * The buffer stream is used for the chunked incoming data.
 */

class SafeQueue {
	private:
		queue<char*> q;
		pthread_mutex_t m;
	public:
		SafeQueue();
		~SafeQueue() {
			pthread_mutex_destroy(&m);
		}
		void enqueue(char* c) {
			pthread_mutex_lock(&m);
			q.push(c);
			pthread_mutex_unlock(&m);
		}
		char* dequeue() {
			pthread_mutex_lock(&m);
			char* c = q.front();
			q.pop();
			return c;
			pthread_mutex_unlock(&m);
  		}
};

SafeQueue::SafeQueue() {
	pthread_mutex_init(&m, NULL);
}

SafeQueue input, output;

class BufferInputStream : public basic_streambuf<char> {
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
		int underflow() {
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

void *Pull(void *threadid) {
	long tid = (long) threadid;
	cout << "THREAD: " << tid << endl;
	pthread_exit(NULL);
}

int Connect(string ip) {
	int flag = 1;
	int sock = socket(AF_INET, SOCK_STREAM, 0);
	setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, (char*) &flag, sizeof(int));
	
	struct sockaddr_in address;
	address.sin_family = AF_INET;
	address.sin_port = htons(80);
	address.sin_addr.s_addr = inet_addr(ip.c_str());

	cout << ip << endl;
	connect(sock, (struct sockaddr*) &address, sizeof (address));
	return sock;
}

main() {
#ifdef __WIN32__
	WORD versionWanted = MAKEWORD(2, 2);
	WSADATA wsaData;
	WSAStartup(versionWanted, &wsaData);
#endif
	pthread_t thread;
	pthread_create(&thread, NULL, Pull, (void *) 0);

	string name = "bitcoinbankbook.com";
	string data = "GET /pull HTTP/1.1\r\nHost: " + name + "\r\n\r\n";

	struct hostent *host = gethostbyname(name.c_str());
	string ip(inet_ntoa(*(struct in_addr*)(host -> h_addr_list[0])));

	int sock = Connect(ip);
	
	int result = 0;
#ifdef __WIN32__
	result = send(sock, data.c_str(), data.length(), 0);
#else
	result = write(sock, data.c_str(), data.length());
#endif
	cout << "OUT: " << result << endl;
	
	BufferInputStream buffer(sock);
	istream stream(&buffer);
	
	string line;
	boolean hex = false;
	
	for(int i = 0; i < 11; i++) {
		getline(stream, line, '\r');
		cout << i << line << (hex ? "T" : "F") << flush;
		if(!hex) {
			char* message = new char[line.size() + 1];
			copy(line.begin(), line.end(), message);
			message[line.size()] = '\0';
			input.enqueue(message);
		}
		hex = !hex;
	}
	
	//char c;
	//stream.get(c);
/*
	char buffer[1024];
#ifdef __WIN32__
	result = recv(sock, buffer, 1024, 0);
#else
	result = read(sock, buffer, 1024);
#endif
*/
#ifdef __WIN32__
	closesocket(sock);
	WSACleanup();
#else
	close(sock);
#endif
	return 0;
}