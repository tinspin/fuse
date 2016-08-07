#include <queue>
#include <string>
#include <sstream>
#include <iostream>
#include <streambuf>
#include <algorithm>
#include <iterator>
#include <stdio.h>
#ifdef __WIN32__
#include <windows.h>
#include <winsock2.h>
#else
#include <pthread.h>
#include <sys/socket.h>
#endif

using namespace std;

/* TODO: Push loop.
 * TODO: Queue sync?
 */

/* SHA-256
 */

#define DBL_INT_ADD(a,b,c) if (a > 0xffffffff - (c)) ++b; a += c;
#define ROTLEFT(a,b) (((a) << (b)) | ((a) >> (32-(b))))
#define ROTRIGHT(a,b) (((a) >> (b)) | ((a) << (32-(b))))
#define CH(x,y,z) (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x,y,z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define EP0(x) (ROTRIGHT(x,2) ^ ROTRIGHT(x,13) ^ ROTRIGHT(x,22))
#define EP1(x) (ROTRIGHT(x,6) ^ ROTRIGHT(x,11) ^ ROTRIGHT(x,25))
#define SIG0(x) (ROTRIGHT(x,7) ^ ROTRIGHT(x,18) ^ ((x) >> 3))
#define SIG1(x) (ROTRIGHT(x,17) ^ ROTRIGHT(x,19) ^ ((x) >> 10))

typedef struct {
	unsigned char data[64];
	unsigned int datalen;
	unsigned int bitlen[2];
	unsigned int state[8];
} CTX;

unsigned int k[64] = {
	0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
	0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
	0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
	0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
	0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
	0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
	0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
	0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

void SHAtransform(CTX *ctx, unsigned char data[]) {
	unsigned int a, b, c, d, e, f, g, h, i, j, t1, t2, m[64];

	for (i = 0, j = 0; i < 16; ++i, j += 4)
		m[i] = (data[j] << 24) | (data[j + 1] << 16) | (data[j + 2] << 8) | (data[j + 3]);
	for (; i < 64; ++i)
		m[i] = SIG1(m[i - 2]) + m[i - 7] + SIG0(m[i - 15]) + m[i - 16];

	a = ctx->state[0];
	b = ctx->state[1];
	c = ctx->state[2];
	d = ctx->state[3];
	e = ctx->state[4];
	f = ctx->state[5];
	g = ctx->state[6];
	h = ctx->state[7];

	for (i = 0; i < 64; ++i) {
		t1 = h + EP1(e) + CH(e, f, g) + k[i] + m[i];
		t2 = EP0(a) + MAJ(a, b, c);
		h = g;
		g = f;
		f = e;
		e = d + t1;
		d = c;
		c = b;
		b = a;
		a = t1 + t2;
	}

	ctx->state[0] += a;
	ctx->state[1] += b;
	ctx->state[2] += c;
	ctx->state[3] += d;
	ctx->state[4] += e;
	ctx->state[5] += f;
	ctx->state[6] += g;
	ctx->state[7] += h;
}

void SHAinit(CTX *ctx) {
	ctx->datalen = 0;
	ctx->bitlen[0] = 0;
	ctx->bitlen[1] = 0;
	ctx->state[0] = 0x6a09e667;
	ctx->state[1] = 0xbb67ae85;
	ctx->state[2] = 0x3c6ef372;
	ctx->state[3] = 0xa54ff53a;
	ctx->state[4] = 0x510e527f;
	ctx->state[5] = 0x9b05688c;
	ctx->state[6] = 0x1f83d9ab;
	ctx->state[7] = 0x5be0cd19;
}

void SHAupdate(CTX *ctx, unsigned char data[], unsigned int len) {
	for (unsigned int i = 0; i < len; ++i) {
		ctx->data[i] = data[i];
		ctx->datalen++;
		if (ctx->datalen == 64) {
			SHAtransform(ctx, ctx->data);
			DBL_INT_ADD(ctx->bitlen[0], ctx->bitlen[1], 512);
			ctx->datalen = 0;
		}
	}
}

void SHAfinal(CTX *ctx, unsigned char hash[]) {
	unsigned int i = ctx->datalen;

	if (ctx->datalen < 56) {
		ctx->data[i++] = 0x80;

		while (i < 56)
			ctx->data[i++] = 0x00;
	}
	else {
		ctx->data[i++] = 0x80;

		while (i < 64)
			ctx->data[i++] = 0x00;

		SHAtransform(ctx, ctx->data);
		memset(ctx->data, 0, 56);
	}

	DBL_INT_ADD(ctx->bitlen[0], ctx->bitlen[1], ctx->datalen * 8);
	ctx->data[63] = ctx->bitlen[0];
	ctx->data[62] = ctx->bitlen[0] >> 8;
	ctx->data[61] = ctx->bitlen[0] >> 16;
	ctx->data[60] = ctx->bitlen[0] >> 24;
	ctx->data[59] = ctx->bitlen[1];
	ctx->data[58] = ctx->bitlen[1] >> 8;
	ctx->data[57] = ctx->bitlen[1] >> 16;
	ctx->data[56] = ctx->bitlen[1] >> 24;
	SHAtransform(ctx, ctx->data);

	for (i = 0; i < 4; ++i) {
		hash[i] = (ctx->state[0] >> (24 - i * 8)) & 0x000000ff;
		hash[i + 4] = (ctx->state[1] >> (24 - i * 8)) & 0x000000ff;
		hash[i + 8] = (ctx->state[2] >> (24 - i * 8)) & 0x000000ff;
		hash[i + 12] = (ctx->state[3] >> (24 - i * 8)) & 0x000000ff;
		hash[i + 16] = (ctx->state[4] >> (24 - i * 8)) & 0x000000ff;
		hash[i + 20] = (ctx->state[5] >> (24 - i * 8)) & 0x000000ff;
		hash[i + 24] = (ctx->state[6] >> (24 - i * 8)) & 0x000000ff;
		hash[i + 28] = (ctx->state[7] >> (24 - i * 8)) & 0x000000ff;
	}
}

//string hash(char* data) {
string hash(string s) {
	const char *data = s.c_str();
	int strLen = strlen(data);
	CTX ctx;
	unsigned char hash[32];
	string hashStr = "";

	SHAinit(&ctx);
	SHAupdate(&ctx, (unsigned char*) data, strLen);
	SHAfinal(&ctx, hash);

	char c[3];
	for (int i = 0; i < 32; i++) {
		sprintf(c, "%02x", hash[i]);
		hashStr += c;
	}

	return hashStr;
}

class Queue {
	private:
		queue<string> queue;
	public:
		Queue() {}
		~Queue() {}
		void enqueue(string message) {
			queue.push(message);
		}
		boolean empty() {
			return queue.empty();
		}
		string dequeue() {
			string message = queue.front();
			queue.pop();
			return message;
		}
};

class BufferInputStream : public basic_streambuf<char> {
	private:
		static const int SIZE = 1024;
		char buffer[SIZE];
		int socket;
	public:
		BufferInputStream(int socket) {
			this -> socket = socket;
			setg(buffer, buffer, buffer);
		}
		~BufferInputStream() {}
	protected:
		int overflow(int c) { return c; }
		int sync() { return 0; }
		int underflow() {
			if(gptr() < egptr())
				return *gptr();
			#ifdef __WIN32__
			int num = recv(socket, reinterpret_cast<char*>(buffer), SIZE, 0);
			#else
			int num = read(socket, reinterpret_cast<char*>(buffer), SIZE);
			#endif
			if(num <= 0)
				return -1;
			setg(buffer, buffer, buffer + num);
			return *gptr();
		}
};

#ifdef __WIN32__
HANDLE sema = CreateSemaphore(NULL, 0, 10, NULL);
#else
pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
#endif

Queue input, output;
string host, ip, salt = "";
int push, pull;
boolean alive = true, first = true;

int Connect(string ip) {
	int flag = 1;
	int sock = socket(AF_INET, SOCK_STREAM, 0);
	setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, (char*) &flag, sizeof(int));
	
	struct sockaddr_in address;
	address.sin_family = AF_INET;
	address.sin_port = htons(80);
	address.sin_addr.s_addr = inet_addr(ip.c_str());

	connect(sock, (struct sockaddr*) &address, sizeof (address));
	return sock;
}

string Push(string data) {
	if(salt.length() > 3)
		data = data.substr(0, 4) + '|' + salt + data.substr(4, data.length() - 4);

	string text = "GET /push?data=" + data + " HTTP/1.1\r\nHost: " + host + "\r\n";
	
	if(first) {
		text += "Head: less\r\n\r\n"; // enables TCP no delay
		first = false;
	}
	else
		text += "\r\n";
		
	cout << "push " << text << endl;
	
	char buffer[1024];

	#ifdef __WIN32__
	int sent = send(push, text.c_str(), text.length(), 0);
	cout << "sent " << sent << endl;
	int read = recv(push, buffer, 1024, 0);
	cout << "read " << read << endl;
	#else
	int sent = write(push, text.c_str(), text.length());
	int read = read(push, buffer, 1024);
	#endif
	
	// TODO: Reconnect if zero bytes read!
	
	text = string(buffer, read);
	
	cout << "push " << text << endl;
	
	int header = text.find("Content-Length:");
	int EOL = text.find("\r\n", header);
	int content = text.find("\r\n\r\n");
	int length = 0;
	istringstream(text.substr(header + 15, EOL - (header + 15))) >> length;
	int count = read - (content + 4);
	
	cout << "push " << header << " " << EOL << " " << content << " " << length << " " << count << endl;
	
	int total = count;

	if(content + 4 + count > text.length()) // UTF-8
		total = text.length() - (content + 4);
	
	text = text.substr(content + 4, total);
	
	if(length == count) {
		return text;
	}
	else {
		stringstream ss;
		
		do {
			#ifdef __WIN32__
			read = recv(push, buffer, 1024, 0);
			#else
			read = read(push, buffer, 1024);
			#endif
			text = string(buffer, read);
			ss << text;
			count += read;
		}
		while(count < length);

		return ss.str();
	}
}

void Pull() {
	string data = "GET /pull?salt=" + salt + " HTTP/1.1\r\nHost: " + host + "\r\nHead: less\r\n\r\n";
	
	#ifdef __WIN32__
	send(pull, data.c_str(), data.length(), 0);
	#else
	write(pull, data.c_str(), data.length());
	#endif
	
	BufferInputStream buffer(pull);
	istream stream(&buffer);
	
	string line;
	boolean hex = true;
	boolean append = false;
	
	while(alive) {
		getline(stream, line, '\r');
		if(append) {
			if(!hex) {
				string message;
				stringstream ss(line);
				while(getline(ss, message, '\n')) {
					if(message.length() > 0) {
						cout << "pull " << message << endl;
						input.enqueue(message);
					}
				}
			}
			hex = !hex;
		}
		else if(line.length() == 1) {
			append = true;
		}
	}
}

#ifdef __WIN32__
DWORD WINAPI PullAsync(void *data) {
	pull = Connect(ip);
	Pull();
	return 0;
}
DWORD WINAPI PushAsync(void *data) {
	while(alive) {
		if(output.empty())
			WaitForSingleObject(sema, INFINITE);
		string message = output.dequeue();
		cout << message << endl;
		Push(message);
	}
	return 0;
}
#else
void *PullAsync(void *data) {
	pull = Connect(ip);
	Pull();
	pthread_exit(NULL);
}
void *PushAsync(void *data) {
	while(alive) {
		if(output.empty())
			pthread_cond_wait(&cond, &lock);
		string message = output.dequeue();
		Push(message);
	}
	pthread_exit(NULL);
}
#endif

void Async(string message) {
	output.enqueue(message);
	#ifdef __WIN32__
	ReleaseSemaphore(sema, 1, NULL);
	#else
	pthread_mutex_lock(&lock);
	pthread_cond_signal(&cond);
	pthread_mutex_unlock(&lock);
	#endif
}

void DoPull() {
	#ifdef __WIN32__
	CreateThread(NULL, 0, PullAsync, NULL, 0, NULL);
	#else
	pthread_t t;
	pthread_create(&t, NULL, PullAsync, (void *) 0);
	#endif
}

void Start() {
	#ifdef __WIN32__
	WORD versionWanted = MAKEWORD(2, 2);
	WSADATA wsaData;
	WSAStartup(versionWanted, &wsaData);
	#endif
	
	host = "fuse.rupy.se";
	hostent * record = gethostbyname(host.c_str());
	in_addr * address = (in_addr * ) record->h_addr;
	ip = inet_ntoa(* address);

	push = Connect(ip);

	#ifdef __WIN32__
	HANDLE t2 = CreateThread(NULL, 0, PushAsync, NULL, 0, NULL);
	#else
	pthread_t t;
	pthread_create(&t2, NULL, PushAsync, (void *) 0);
	#endif
}

vector<string> Split(const string &s) {
	vector<string> v;
	stringstream ss(s);
	string item;
	while(getline(ss, item, '|')) {
		v.push_back(item);
	}
	return v;
}

vector<string> EasyUser(string name, string hash, string mail) {
	vector<string> user = Split(Push("user|" + name + "|" + hash + "|" + mail));

	if(user.at(1).compare("fail")) {
		//throw new exception(user[2]);
	}

	salt = user.at(2);
	return user;
}

void User(string name, string pass, string mail) {
	string s = name;
	transform(s.begin(), s.end(), s.begin(), ::tolower);
	EasyUser(name, hash(pass + s), mail);
}
	
void User(string name, string pass) {
	User(name, pass, "");
}
	
// Returns key to be stored.
string User(string name) {
	return EasyUser(name, "", "")[3];
}

vector<string> User() {
	return EasyUser("", "", "");
}

string Sign(string user, string hide) {
	vector<string> salt = Split(Push("salt|" + user));
	
	cout << "1" << endl;
	copy(salt.begin(), salt.end(), ostream_iterator<string>(cout, " "));
		
	if(salt.at(1).compare("fail")) {
		//throw new exception(salt.at(2));
	}

	vector<string> sign = Split(Push("sign|" + salt.at(2) + "|" + hash(hide + salt.at(2))));

	if(sign.at(1).compare("fail")) {
		//throw new exception(sign.at(2));
	}

	return salt.at(2);
}

string SignIdKey(string id, string key) {
	return Sign(id, key);
}

string SignNameKey(string name, string key) {
	return Sign(name, key);
}

string SignNamePass(string name, string pass) {
	string s = name;
	transform(s.begin(), s.end(), s.begin(), ::tolower);
	return Sign(name, hash(pass + s));
}

vector<string> EasyPush(string data) {
	vector<string> push = Split(Push(data));
	
	if(push.at(1).compare("fail")) {
		//throw new exception(push.at(2));
	}
		
	return push;
}

boolean BoolPush(string data) {
	vector<string> push = EasyPush(data);

	if(push.size() == 0) {
		return false;
	}

	return true;
}

boolean Game(string game) {
	return BoolPush("game|" + game);
}

string to(int i) {
	ostringstream ss;
	ss << i;
	return ss.str();
}

boolean Room(string type, int size) {
	return BoolPush("room|" + type + "|" + to(size));
}

main() {
	string key = "TvaaS3cqJhQyK6sn";
	
	Start();
	
	salt = SignNameKey("fuse", key);
	
	DoPull();
	
	#ifdef __WIN32__
	Sleep(60000);
	#else
	sleep(60000);
	#endif

	alive = false;
	
	return 0;
}