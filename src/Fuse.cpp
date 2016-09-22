#include <queue>
#include <string>
#include <cstring>
#include <sstream>
#include <iostream>
#include <streambuf>
#include <algorithm>
#include <iterator>
#include <stdio.h>
#ifdef WIN32
#include <windows.h>
#include <winsock2.h>
#else
#include <pthread.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#endif

using namespace std;

/* TODO: Fuse class
 * TODO: Queue sync
 */

/*
 * Updated to C++, zedwood.com 2012
 * Based on Olivier Gay's version
 * See Modified BSD License below: 
 *
 * FIPS 180-2 SHA-224/256/384/512 implementation
 * Issue date:  04/30/2005
 * http://www.ouah.org/ogay/sha2/
 *
 * Copyright (C) 2005, 2007 Olivier Gay <olivier.gay@a3.epfl.ch>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the project nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE PROJECT AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE PROJECT OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

class SHA256
{
protected:
    typedef unsigned char uint8;
    typedef unsigned int uint32;
    typedef unsigned long long uint64;

    const static uint32 sha256_k[];
    static const unsigned int SHA224_256_BLOCK_SIZE = (512/8);
public:
    void init();
    void update(const unsigned char *message, unsigned int len);
    void final(unsigned char *digest);
    static const unsigned int DIGEST_SIZE = ( 256 / 8);

protected:
    void transform(const unsigned char *message, unsigned int block_nb);
    unsigned int m_tot_len;
    unsigned int m_len;
    unsigned char m_block[2*SHA224_256_BLOCK_SIZE];
    uint32 m_h[8];
};

#define SHA2_SHFR(x, n)    (x >> n)
#define SHA2_ROTR(x, n)   ((x >> n) | (x << ((sizeof(x) << 3) - n)))
#define SHA2_ROTL(x, n)   ((x << n) | (x >> ((sizeof(x) << 3) - n)))
#define SHA2_CH(x, y, z)  ((x & y) ^ (~x & z))
#define SHA2_MAJ(x, y, z) ((x & y) ^ (x & z) ^ (y & z))
#define SHA256_F1(x) (SHA2_ROTR(x,  2) ^ SHA2_ROTR(x, 13) ^ SHA2_ROTR(x, 22))
#define SHA256_F2(x) (SHA2_ROTR(x,  6) ^ SHA2_ROTR(x, 11) ^ SHA2_ROTR(x, 25))
#define SHA256_F3(x) (SHA2_ROTR(x,  7) ^ SHA2_ROTR(x, 18) ^ SHA2_SHFR(x,  3))
#define SHA256_F4(x) (SHA2_ROTR(x, 17) ^ SHA2_ROTR(x, 19) ^ SHA2_SHFR(x, 10))
#define SHA2_UNPACK32(x, str)                 \
{                                             \
    *((str) + 3) = (uint8) ((x)      );       \
    *((str) + 2) = (uint8) ((x) >>  8);       \
    *((str) + 1) = (uint8) ((x) >> 16);       \
    *((str) + 0) = (uint8) ((x) >> 24);       \
}
#define SHA2_PACK32(str, x)                   \
{                                             \
    *(x) =   ((uint32) *((str) + 3)      )    \
           | ((uint32) *((str) + 2) <<  8)    \
           | ((uint32) *((str) + 1) << 16)    \
           | ((uint32) *((str) + 0) << 24);   \
}
 
const unsigned int SHA256::sha256_k[64] = //UL = uint32
            {0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
             0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
             0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
             0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
             0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
             0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
             0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
             0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
             0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
             0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
             0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
             0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
             0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
             0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
             0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
             0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

void SHA256::transform(const unsigned char *message, unsigned int block_nb)
{
    uint32 w[64];
    uint32 wv[8];
    uint32 t1, t2;
    const unsigned char *sub_block;
    int i;
    int j;
    for (i = 0; i < (int) block_nb; i++) {
        sub_block = message + (i << 6);
        for (j = 0; j < 16; j++) {
            SHA2_PACK32(&sub_block[j << 2], &w[j]);
        }
        for (j = 16; j < 64; j++) {
            w[j] =  SHA256_F4(w[j -  2]) + w[j -  7] + SHA256_F3(w[j - 15]) + w[j - 16];
        }
        for (j = 0; j < 8; j++) {
            wv[j] = m_h[j];
        }
        for (j = 0; j < 64; j++) {
            t1 = wv[7] + SHA256_F2(wv[4]) + SHA2_CH(wv[4], wv[5], wv[6])
                + sha256_k[j] + w[j];
            t2 = SHA256_F1(wv[0]) + SHA2_MAJ(wv[0], wv[1], wv[2]);
            wv[7] = wv[6];
            wv[6] = wv[5];
            wv[5] = wv[4];
            wv[4] = wv[3] + t1;
            wv[3] = wv[2];
            wv[2] = wv[1];
            wv[1] = wv[0];
            wv[0] = t1 + t2;
        }
        for (j = 0; j < 8; j++) {
            m_h[j] += wv[j];
        }
    }
}

void SHA256::init()
{
    m_h[0] = 0x6a09e667;
    m_h[1] = 0xbb67ae85;
    m_h[2] = 0x3c6ef372;
    m_h[3] = 0xa54ff53a;
    m_h[4] = 0x510e527f;
    m_h[5] = 0x9b05688c;
    m_h[6] = 0x1f83d9ab;
    m_h[7] = 0x5be0cd19;
    m_len = 0;
    m_tot_len = 0;
}

void SHA256::update(const unsigned char *message, unsigned int len)
{
    unsigned int block_nb;
    unsigned int new_len, rem_len, tmp_len;
    const unsigned char *shifted_message;
    tmp_len = SHA224_256_BLOCK_SIZE - m_len;
    rem_len = len < tmp_len ? len : tmp_len;
    memcpy(&m_block[m_len], message, rem_len);
    if (m_len + len < SHA224_256_BLOCK_SIZE) {
        m_len += len;
        return;
    }
    new_len = len - rem_len;
    block_nb = new_len / SHA224_256_BLOCK_SIZE;
    shifted_message = message + rem_len;
    transform(m_block, 1);
    transform(shifted_message, block_nb);
    rem_len = new_len % SHA224_256_BLOCK_SIZE;
    memcpy(m_block, &shifted_message[block_nb << 6], rem_len);
    m_len = rem_len;
    m_tot_len += (block_nb + 1) << 6;
}

void SHA256::final(unsigned char *digest)
{
    unsigned int block_nb;
    unsigned int pm_len;
    unsigned int len_b;
    int i;
    block_nb = (1 + ((SHA224_256_BLOCK_SIZE - 9)
                     < (m_len % SHA224_256_BLOCK_SIZE)));
    len_b = (m_tot_len + m_len) << 3;
    pm_len = block_nb << 6;
    memset(m_block + m_len, 0, pm_len - m_len);
    m_block[m_len] = 0x80;
    SHA2_UNPACK32(len_b, m_block + pm_len - 4);
    transform(m_block, block_nb);
    for (i = 0 ; i < 8; i++) {
        SHA2_UNPACK32(m_h[i], &digest[i << 2]);
    }
}

string sha256(string input)
{
    unsigned char digest[SHA256::DIGEST_SIZE];
    memset(digest,0,SHA256::DIGEST_SIZE);

    SHA256 ctx = SHA256();
    ctx.init();
    ctx.update( (unsigned char*)input.c_str(), input.length());
    ctx.final(digest);

    char buf[2*SHA256::DIGEST_SIZE+1];
    buf[2*SHA256::DIGEST_SIZE] = 0;
    for (int i = 0; i < SHA256::DIGEST_SIZE; i++)
        sprintf(buf+i*2, "%02x", digest[i]);
    return string(buf);
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
		bool empty() {
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
			#ifdef WIN32
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

#ifdef WIN32
HANDLE sema = CreateSemaphore(NULL, 0, 10, NULL);
CRITICAL_SECTION crit;
#else
pthread_mutex_t qlock = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t plock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
#endif

Queue input, output;
string host, ip, salt = "", game;
int push, pull;
bool alive = true, first = true;

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
	#ifdef WIN32
	EnterCriticalSection(&crit);
	#else
	pthread_mutex_lock(&plock);
	#endif
	
	if(salt.length() > 3)
		data = data.substr(0, 4) + '|' + salt + data.substr(4, data.length() - 4);

	cout << "-> " << data << endl;

	string text = "GET /push?data=" + data + " HTTP/1.1\r\nHost: " + host + "\r\n";
	
	if(first) {
		text += "Head: less\r\n\r\n"; // enables TCP no delay
		first = false;
	}
	else
		text += "\r\n";
		
	//cout << "push " << text << endl;
	
	char buffer[1024];

	#ifdef WIN32
	int sent = send(push, text.c_str(), text.length(), 0);
	//cout << "sent " << sent << endl;
	int _read = recv(push, buffer, 1024, 0);
	//cout << "read " << read << endl;
	#else
	int sent = write(push, text.c_str(), text.length());
	int _read = read(push, buffer, 1024);
	#endif
	
	// reconnect if zero bytes read!
	if(_read < 1) {
		push = Connect(ip);

		text = "GET /push?data=" + data + " HTTP/1.1\r\nHost: " + host + "\r\nHead: less\r\n\r\n"; // enables TCP no delay

		#ifdef WIN32
		sent = send(push, text.c_str(), text.length(), 0);
		_read = recv(push, buffer, 1024, 0);
		#else
		sent = write(push, text.c_str(), text.length());
		_read = read(push, buffer, 1024);
		#endif
	}
	
	text = string(buffer, _read);
	
	//cout << "push " << text << endl;
	
	int header = text.find("Content-Length:");
	int EOL = text.find("\r\n", header);
	int content = text.find("\r\n\r\n");
	int length = 0;
	istringstream(text.substr(header + 15, EOL - (header + 15))) >> length;
	int count = _read - (content + 4);
	
	//cout << "push " << header << " " << EOL << " " << content << " " << length << " " << count << endl;
	
	int total = count;

	if(content + 4 + count > text.length()) // UTF-8
		total = text.length() - (content + 4);
	
	text = text.substr(content + 4, total);
	
	if(length != count) {
		stringstream ss;
		ss << text;
		do {
			#ifdef WIN32
			_read = recv(push, buffer, 1024, 0);
			#else
			_read = read(push, buffer, 1024);
			#endif
			text = string(buffer, _read);
			ss << text;
			count += _read;
		}
		while(count < length);

		text = ss.str();
	}

	#ifdef WIN32
	LeaveCriticalSection(&crit);
	#else
	pthread_mutex_unlock(&plock);
	#endif

	return text;
}

vector<string> Split(const string &s, char c) {
	vector<string> v;
	stringstream ss(s);
	string item;
	while(getline(ss, item, c)) {
		v.push_back(item);
	}
	return v;
}

vector<string> Split(const string &s) {
	return Split(s, '|');
}

vector<string> EasyPush(string data) {
	vector<string> push = Split(Push(data));

	if(push.at(1).compare("fail")) {
		//throw new exception(push.at(2));
	}

	return push;
}

bool BoolPush(string data) {
	vector<string> push = EasyPush(data);

	if(push.size() == 0) {
		return false;
	}

	return true;
}

bool Game(string game) {
	return BoolPush("game|" + game);
}

void Pull() {
	string data = "GET /pull?salt=" + salt + " HTTP/1.1\r\nHost: " + host + "\r\nHead: less\r\n\r\n";
	
	#ifdef WIN32
	send(pull, data.c_str(), data.length(), 0);
	#else
	write(pull, data.c_str(), data.length());
	#endif
	
	BufferInputStream buffer(pull);
	istream stream(&buffer);
	
	string line;
	bool hex = true;
	bool first = true;
	bool append = false;
	stringstream ss;
	string message;
	
	while(alive) {
		getline(stream, line, '\r');
		line.erase(0, 1);
		if(append) {
			if(!hex) {
				ss << line;
				while(getline(ss, message, '\n')) {
					if(ss.eof()) {
						ss.clear();
						ss.str("");
						ss << message;
						break;
					}
					else if(message.length() > 0) {
						if(first) {
							first = false;
							Game(game);
						}
						cout << "pull " << message << endl;
						// NOTE: input queue is not needed in a fully threaded c++ environment
						//input.enqueue(message);
					}
				}
				if(ss.eof()) {
					ss.clear();
					ss.str("");
				}
			}
			hex = !hex;
		}
		else if(line.length() == 0) {
			append = true;
		}
	}
}

#ifdef WIN32
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
		if(message.length() > 0) {
			vector<string> done = Split(Push(message), '|');
			if(!done.at(1).compare("fail")) {
				// error
			}
		}
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
		if(message.length() > 0) {
			vector<string> done = Split(Push(message), '|');
			if(!done.at(1).compare("fail")) {
				// error
			}
		}
	}
	pthread_exit(NULL);
}
#endif

void Async(string message) {
	output.enqueue(message);
	#ifdef WIN32
	ReleaseSemaphore(sema, 1, NULL);
	#else
	pthread_mutex_lock(&qlock);
	pthread_cond_signal(&cond);
	pthread_mutex_unlock(&qlock);
	#endif
}

void DoPull() {
	#ifdef WIN32
	CreateThread(NULL, 0, PullAsync, NULL, 0, NULL);
	#else
	pthread_t t;
	pthread_create(&t, NULL, PullAsync, (void *) 0);
	#endif
}

void Start(string h, string g) {
	#ifdef WIN32
	WORD versionWanted = MAKEWORD(2, 2);
	WSADATA wsaData;
	WSAStartup(versionWanted, &wsaData);
	InitializeCriticalSection(&crit);
	#endif
	
	host = h;
	game = g;
		
	hostent * record = gethostbyname(host.c_str());
	in_addr * address = (in_addr * ) record->h_addr;
	ip = inet_ntoa(* address);

	push = Connect(ip);

	#ifdef WIN32
	CreateThread(NULL, 0, PushAsync, NULL, 0, NULL);
	#else
	pthread_t t;
	pthread_create(&t, NULL, PushAsync, (void *) 0);
	#endif
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
	EasyUser(name, sha256(pass + s), mail);
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
	
	//copy(salt.begin(), salt.end(), ostream_iterator<string>(cout, " "));
		
	if(salt.at(1).compare("fail")) {
		//throw new exception(salt.at(2));
	}

	vector<string> sign = Split(Push("sign|" + salt.at(2) + "|" + sha256(hide + salt.at(2))));

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
	return Sign(name, sha256(pass + s));
}

string to(int i) {
	ostringstream ss;
	ss << i;
	return ss.str();
}

bool Room(string type, int size) {
	return BoolPush("room|" + type + "|" + to(size));
}

int main() {
	string key = "TvaaS3cqJhQyK6sn";
	
	Start("fuse.rupy.se", "race");
	
	salt = SignNameKey("fuse", key);
	
	DoPull();
	
	#ifdef WIN32
	Sleep(60000);
	#else
	sleep(60000);
	#endif

	alive = false;
	
	return 0;
}