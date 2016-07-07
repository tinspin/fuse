#include <queue>
#include <mutex>
#include <string>
#include <sstream>
#include <iostream>
#include <streambuf>
#ifdef __WIN32__
#include <windows.h>
#include <winsock2.h>
#else
#include <pthread.h>
#include <sys/socket.h>
#endif

using namespace std;

/* TODO: Conditional async push.
 * TODO: Windows threads.
 */

class SafeQueue {
	private:
		queue<string> q;
		#ifdef __WIN32__
		CRITICAL_SECTION m;
		#else
		pthread_mutex_t m;
		#endif
	public:
		SafeQueue() {
			#ifdef __WIN32__
			InitializeCriticalSection(&m);
			#else
			pthread_mutex_init(&m, NULL);
			#endif
		}
		~SafeQueue() {
			#ifdef __WIN32__
			DeleteCriticalSection(&m);
			#else
			pthread_mutex_destroy(&m);
			#endif
		}
		void enqueue(string s) {
			#ifdef __WIN32__
			WaitForSingleObject(&m,INFINITE);
			#else
			pthread_mutex_lock(&m);
			#endif
			
			q.push(s);
			
			#ifdef __WIN32__
			ReleaseMutex(&m);
			#else
			pthread_mutex_unlock(&m);
			#endif
		}
		string dequeue() {
			#ifdef __WIN32__
			WaitForSingleObject(&m,INFINITE);
			#else
			pthread_mutex_lock(&m);
			#endif
			
			string s = q.front();
			q.pop();
			return s;
			
			#ifdef __WIN32__
			ReleaseMutex(&m);
			#else
			pthread_mutex_unlock(&m);
			#endif
  		}
};

SafeQueue input, output;

class BufferInputStream : public basic_streambuf<char> {
	private:
		static const int SIZE = 128;
		char ibuf[SIZE];
		int sock;
	public:
		BufferInputStream(int sock){
			this -> sock = sock;
			setg(ibuf, ibuf, ibuf);
		}
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

#ifdef __WIN32__
DWORD WINAPI Pull(void* data) {
	for(int i = 0; i < 15; i++) {
		cout << "THREAD: " << data << endl;
		Sleep(1000);
	}
	return 0;
}
#else
void *Pull(void *data) {
	for(int i = 0; i < 15; i++) {
		cout << "THREAD: " << data << endl;
		sleep(1000);
	}
	pthread_exit(NULL);
}
#endif

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
	
	#ifdef __WIN32__
	HANDLE thread = CreateThread(NULL, 0, Pull, NULL, 0, NULL);
	#else
	pthread_t thread;
	pthread_create(&thread, NULL, Pull, (void *) 0);
	#endif
	
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
			string message;
			stringstream ss(line);
			while(getline(ss, message, '\n')) {
				if(message.length() > 0)
					input.enqueue(message);
			}
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