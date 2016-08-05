#include <queue>
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

/* TODO: Push loop.
 */

class SafeQueue {
	private:
		queue<string> q;
		#ifdef __WIN32__
		CRITICAL_SECTION l;
		#else
		pthread_mutex_t l;
		#endif
	public:
		SafeQueue() {
			#ifdef __WIN32__
			InitializeCriticalSection(&l);
			#else
			pthread_mutex_init(&l, NULL);
			#endif
		}
		~SafeQueue() {
			#ifdef __WIN32__
			DeleteCriticalSection(&l);
			#else
			pthread_mutex_destroy(&l);
			#endif
		}
		void enqueue(string s) {
			#ifdef __WIN32__
			EnterCriticalSection(&l);
			#else
			pthread_mutex_lock(&l);
			#endif
			
			q.push(s);
			
			#ifdef __WIN32__
			LeaveCriticalSection(&l);
			#else
			pthread_mutex_unlock(&l);
			#endif
		}
		boolean empty() {
			return q.empty();
		}
		string dequeue() {
			#ifdef __WIN32__
			EnterCriticalSection(&l);
			#else
			pthread_mutex_lock(&l);
			#endif
			
			string s = q.front();
			q.pop();
			return s;
			
			#ifdef __WIN32__
			LeaveCriticalSection(&l);
			#else
			pthread_mutex_unlock(&l);
			#endif
		}
};

class BufferInputStream : public basic_streambuf<char> {
	private:
		static const int SIZE = 128;
		char ibuf[SIZE];
		int sock;
	public:
		BufferInputStream(int sock) {
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
				return -1;

			cout << "IN: " << num << endl;

			setg(ibuf, ibuf, ibuf + num);
			return *gptr();
		}
};

#ifdef __WIN32__
CRITICAL_SECTION lock;
HANDLE sema = CreateSemaphore(NULL, 0, 1, NULL);
#else
pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
#endif

SafeQueue input, output;
string host, ip;
int push, pull; // sockets
boolean alive = true;

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

void Push() {
	string data = "GET / HTTP/1.1\r\nHost: " + host + "\r\n\r\n";
	
	int result = 0;
	#ifdef __WIN32__
	result = send(push, data.c_str(), data.length(), 0);
	#else
	result = write(push, data.c_str(), data.length());
	#endif
	cout << "OUT: " << result << endl;
	
	char buffer[1024];
	#ifdef __WIN32__
	result = recv(push, buffer, 1024, 0);
	#else
	result = read(push, buffer, 1024);
	#endif
	cout << "IN: " << result << endl;
}

void Pull() {
	string data = "GET /pull HTTP/1.1\r\nHost: " + host + "\r\n\r\n";
	
	int result = 0;
	#ifdef __WIN32__
	result = send(pull, data.c_str(), data.length(), 0);
	#else
	result = write(pull, data.c_str(), data.length());
	#endif
	cout << "OUT: " << result << endl;
	
	BufferInputStream buffer(pull);
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
}

#ifdef __WIN32__
DWORD WINAPI PullAsync(void *data) {
	pull = Connect(ip);
	Pull();
	return 0;
}
#else
void *PullAsync(void *data) {
	pull = Connect(ip);
	Pull();
	pthread_exit(NULL);
}
#endif

#ifdef __WIN32__
DWORD WINAPI PushAsync(void *data) {
	push = Connect(ip);
	while(alive) {
		if(output.empty())
			WaitForSingleObject(sema, INFINITE);
		Push();
	}
	return 0;
}
#else
void *PushAsync(void *data) {
	push = Connect(ip);
	while(alive) {
		if(output.empty())
			pthread_cond_wait(&cond, &lock);
		string message = output.dequeue();
		Push();
	}
	pthread_exit(NULL);
}
#endif

void Async() {
	// TODO: Add message to queue 
	#ifdef __WIN32__
	EnterCriticalSection(&lock);
	ReleaseSemaphore(sema, 1, NULL);
	LeaveCriticalSection(&lock);
	#else
	pthread_mutex_lock(&lock);
	pthread_cond_signal(&cond);
	pthread_mutex_unlock(&lock);
	#endif
}

main() {
	#ifdef __WIN32__
	WORD versionWanted = MAKEWORD(2, 2);
	WSADATA wsaData;
	WSAStartup(versionWanted, &wsaData);
	InitializeCriticalSection(&lock);
	#endif
	
	host = "bitcoinbankbook.com";
	hostent * record = gethostbyname(host.c_str());
	in_addr * address = (in_addr * ) record->h_addr;
	ip = inet_ntoa(* address);

	#ifdef __WIN32__
	HANDLE t1 = CreateThread(NULL, 0, PullAsync, NULL, 0, NULL);
	HANDLE t2 = CreateThread(NULL, 0, PushAsync, NULL, 0, NULL);
	#else
	pthread_t t1;
	pthread_t t2;
	pthread_create(&t1, NULL, PullAsync, (void *) 0);
	pthread_create(&t2, NULL, PushAsync, (void *) 0);
	#endif
	
	#ifdef __WIN32__
	Sleep(5000);
	#else
	sleep(5000);
	#endif
	
	Async();
	
	#ifdef __WIN32__
	Sleep(5000);
	#else
	sleep(5000);
	#endif

	alive = false;

	return 0;
}