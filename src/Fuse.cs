//using UnityEngine; // ### 1
using System;
using System.Net;
using System.Net.Sockets;
using System.Collections;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Threading;
using System.Text;

/* A real-time comet stream plugin for unity.
 * For unity search for the 5x ### and change.
 * For usage scroll down to Main() method.
 */
public class Fuse { // : MonoBehaviour { // ### 2
	public static Fuse instance;
	public string host = "fuse.rupy.se";
	public int port = 80;

	private readonly object sync = new System.Object();
	private Thread thread;
	private Queue<string> input, output;
	private Socket pull, push;
	private bool connected, first = true;
	private string salt;

	private IPEndPoint remote;

	private class State {
		public Socket socket = null;
		public const int size = 1024;
		public byte[] data = new byte[size];
	}
	
	public static void Log(string message) {
		//Debug.Log(message); // uncomment ### 3
		Console.WriteLine(message); // comment ### 3
	}
	
	public Fuse() {
		//Start();
	}
	
	void Awake() {
		// ### 4
		// Also to debug multiplayer it helps if you check:
		// Edit -> Project Settings -> Player -> Resolution & Presentation -> Run In Background
		//Application.runInBackground = true;
		//DontDestroyOnLoad(gameObject);
	}
	
	public void Host(string host) {
		this.host = host;
		bool policy = true;

		//policy = Security.PrefetchSocketPolicy(host, port); // not needed for most cases ### 5
		
		if(!policy)
			throw new Exception("Policy (" + host + ":" + port + ") failed.");
		
		IPAddress address = Dns.GetHostEntry(host).AddressList[0];
		remote = new IPEndPoint(address, port);
		
		Log(host + " " + address);
		
		Connect();
	}

	public static int Ping(string host) {
		Fuse fuse = new Fuse();
		fuse.Host(host);
		fuse.Connect();
		int time = Environment.TickCount;
		fuse.Push("ping");
		return Environment.TickCount - time;
	}

	void Connect() {
		first = true;
		push = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
		push.NoDelay = true;
		push.Connect(remote);
	}

	void Start() {
		Log("Start");

		instance = this;
		
		input = new Queue<string>();
		output = new Queue<string>();
		
		thread = new Thread(PushAsync);
		thread.Start();
	}

	public void Pull(string salt) {
		this.salt = salt;
		
		pull = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
		pull.NoDelay = true;
		pull.Connect(remote);

		String text = "GET /pull?salt=" + salt + " HTTP/1.1\r\nHost: " + host + "\r\nHead: less\r\n\r\n";

		pull.Send(Encoding.UTF8.GetBytes(text));

		State state = new State();
		state.socket = pull;

		pull.BeginReceive(state.data, 0, State.size, 0, new AsyncCallback(Callback), state);

		connected = true;
	}

	private void PushAsync() {
		while(true) {
			try {
				String message = null;

				lock(output) {
					if(output.Count > 0)
						message = output.Dequeue();
				}

				while(message != null) {
					Push(message);

					lock(output) {
						if(output.Count > 0)
							message = output.Dequeue();
						else
							message = null;
					}
				}
			}
			finally {
				lock(thread)
					Monitor.Wait(thread);
			}
		}
	}

	public void Async(string data) {
		lock(output)
			output.Enqueue(data);
		lock(thread)
			Monitor.Pulse(thread);
	}

	public string Push(string data) {
		lock(sync) {
			byte[] body = new byte[1024];

			if(salt != null)
				data = data.Substring(0, 4) + '|' + salt + data.Substring(4, data.Length - 4);

			String text = "GET /push?data=" + data + " HTTP/1.1\r\nHost: " + host + "\r\n";

			if(first) {
				text += "Head: less\r\n\r\n"; // enables TCP no delay
				first = false;
			}
			else
				text += "\r\n";

			push.Send(Encoding.UTF8.GetBytes(text));
			int read = push.Receive(body);
			text = Encoding.UTF8.GetString(body, 0, read);

			if(text.Length == 0) {
				Connect();

				text = "GET /push?data=" + data + " HTTP/1.1\r\nHost: " + host + "\r\n";
			
				if(first) {
					text += "Head: less\r\n\r\n"; // enables TCP no delay
					first = false;
				}
				else
					text += "\r\n";

				push.Send(Encoding.UTF8.GetBytes(text));
				read = push.Receive(body);
				text = Encoding.UTF8.GetString(body, 0, read);
			}

			string[] split = text.Split(new string[] { "\r\n\r\n" }, StringSplitOptions.None);
			return split[1];
		}
	}

	public string[] Read() {
		if(!connected)
			return null;

		int length = 0;

		lock(input) {
			length = input.Count;
		}
		
		if(length > 0) {
			string[] messages = new string[input.Count];

			for(int i = 0; i < messages.Length; i++) {
				lock(input) {
					messages[i] = input.Dequeue();
				}
			}

			return messages;
		}

		return null;
	}

	private void Callback(IAsyncResult ar) {
		try {
			State state = (State) ar.AsyncState;
			int read = state.socket.EndReceive(ar);

			if(read > 0) {
				string text = Encoding.UTF8.GetString(state.data, 0, read);
				string[] split = text.Split(new string[] { "\r\n" }, StringSplitOptions.None);

				if(!split[0].StartsWith("HTTP")) {
					for(int j = 1; j < split.Length; j += 2) {
						string[] messages = split[j].Split('\n');

						for(int i = 0; i < messages.Length; i++) {
							if(messages[i].Length > 0) {
								lock(input) {
									input.Enqueue(messages[i]);
								}
							}
						}
					}
				}

				state.socket.BeginReceive(state.data, 0, State.size, 0, new AsyncCallback(Callback), state);
			}
		} catch (Exception e) {
			Log(e.ToString());
		}
	}

	// ------------- PROTOCOL  -------------

	/* Anonymous user.
	 * You need to:
	 * - store both key and id.
	 * - set and get name (unique) or nick 
	 * manually if you need lobby.
	 */
	public void User() {
		string[] user = EasyUser("", "", "");
		string key = user[3];
		string id = user[4];
		// TODO: store both key and id
	}
	
	// Returns key to be stored.
	public string User(string name) {
		return EasyUser(name, "", "")[3];
	}
	
	public void User(string name, string pass) {
		User(name, pass, "");
	}

	public void User(string name, string pass, string mail) {
		EasyUser(name, Hash(pass + name.ToLower()), mail);
	}

	private string[] EasyUser(string name, string hash, string mail) {
		string[] user = Push("user|" + name + "|" + hash + "|" + mail).Split('|');

		if(user[1].Equals("fail")) {
			throw new Exception(user[2]);
		}

		this.salt = user[2];
		return user;
	}

	public string SignIdKey(string id, string key) {
		return Sign(id, key);
	}

	public string SignNameKey(string name, string key) {
		return Sign(name, key);
	}

	public string SignNamePass(string name, string pass) {
		return Sign(name, Hash(pass + name.ToLower()));
	}
	
	private string Sign(string user, string hide) {
		string[] salt = Push("salt|" + user).Split('|');
		
		if(salt[1].Equals("fail")) {
			throw new Exception(salt[2]);
		}
		
		string[] sign = Push("sign|" + salt[2] + "|" + Hash(hide + salt[2])).Split('|');

		if(sign[1].Equals("fail")) {
			throw new Exception(sign[2]);
		}

		return salt[2];
	}

	public bool Game(string game) {
		return BoolPush("game|" + game);
	}

	public bool Room(String type, int size) {
		return BoolPush("room|" + type + "|" + size);
	}

	public string[] ListRoom() {
		string list = Push("list|room");

		if(list.StartsWith("list|fail")) {
			Log(list);
			return null;
		}

		if(list.Length > 15)
			return list.Substring(15).Split(';'); // from 'list|done|room|'
		else
			return null;
	}

	public bool Join(string room) {
		return BoolPush("join|" + room);
	}
	
	public bool Join(string user, string info) {
		return BoolPush("join|" + user + "|" + info);
	}
	
	public bool Exit(string user) {
		return BoolPush("exit|" + user);
	}
	
	public bool Poll(string user, string accept) {
		return BoolPush("poll|" + user + "|" + accept);
	}

	public void Play(string seed) {
		Async("play|" + seed);
	}
	
	public void Over(string data) {
		Async("over|" + data);
	}
	
	/* use coroutines for everything where 
	   you need to act on the response while 
	   in the main thread */
	
	public void Save(string name, string json) {
		Save(name, json, "hard");
	}
	
	public void Save(string name, string json, string type) {
		EasyPush("save|" + Uri.EscapeDataString(name) + "|" + Uri.EscapeDataString(json) + "|" + type);
	}
	
	public string Load(string name) {
		return Load(name, "data");
	}
	
	public string Load(string name, string type) {
		return EasyPush("load|" + Uri.EscapeDataString(name) + "|" + type)[2];
	}
	
	// Delete data
	public void Tear(string name, string type) {
		EasyPush("save|" + Uri.EscapeDataString(name) + "|" + type);
	}
	
	public string Hard(string user, string name) {
		return EasyPush("hard|" + Uri.EscapeDataString(user) + "|" + Uri.EscapeDataString(name))[2];
	}
	
	public string Item(string user, string name) {
		return EasyPush("item|" + Uri.EscapeDataString(user) + "|" + Uri.EscapeDataString(name))[2];
	}
	
	public string Soft(string user, string name) {
		return EasyPush("soft|" + Uri.EscapeDataString(user) + "|" + Uri.EscapeDataString(name))[2];
	}
	
	public string[] ListData() {
		string list = Push("list|data");

		if(list.StartsWith("list|fail")) {
			Log(list);
			return null;
		}

		if(list.Length > 15)
			return list.Substring(15).Split(';'); // from 'list|done|data|'
		else
			return null;
	}
	
	/* tree should be either root, stem or leaf
	 * root -> whole server, excluding others rooms
	 * stem -> game lobby, including your own room
	 * leaf -> your room only
	 * if you prefix text with @name it will send 
	 * to that user if online; no matter where and 
	 * what tree is used.
	 */
	public void Chat(string tree, string text) {
		Async("chat|" + tree + '|' + Uri.EscapeDataString(text));
	}

	public void Send(string data) {
		Async("send|" + data);
	}

	public void Move(string data) {
		Async("move|" + data);
	}

	private bool BoolPush(string data) {
		string[] push = EasyPush(data);

		if(push == null) {
			return false;
		}

		return true;
	}

	private string[] EasyPush(string data) {
		string[] push = Push(data).Split('|');
		
		if(push[1].Equals("fail")) {
			throw new Exception(push[2]);
		}
		
		return push;
	}

	public string Hash(string input) {
		HashAlgorithm algo = (HashAlgorithm) SHA256.Create();
		byte[] bytes = Encoding.UTF8.GetBytes(input);
		byte[] hash = algo.ComputeHash(bytes);
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < hash.Length; i++) {
			sb.Append(hash[i].ToString("X2"));
		}
		return sb.ToString().ToLower();
	}

	// ------------- INCOMING MESSAGES ---------
	
	void Update() {
		string[] received = Read();

		if(received != null) {
			for(int i = 0; i < received.Length; i++) {
				Log("Read: " + received[i]);
			}
		}
	}

	// ------------- EXAMPLE USAGE -------------
	
	public static void Main() {
		try {
			string key = "TvaaS3cqJhQyK6sn";
			string salt = null;
			
			Fuse fuse = new Fuse();
			fuse.Start();
			fuse.Host("fuse.rupy.se");
			
			//Thread.Sleep(100);
			
			// if no key is stored try
/*
			try {
				key = fuse.User("fuse");
				Log("User: " + key);
			}
			catch(Exception e) {
				Log(e.Message);
				return;
			}
*/
			//   then store name and key
			// otherwise
			//   get name and key

			//key = "F9hG7K7Jwe1SmtiQ";
			//key = "yt4QACtL2uzbyUTT";

			//if(key != null) {
				try {
					salt = fuse.SignNameKey("fuse", key);
				}
				catch(Exception e) {
					Log(e.Message);
					return;
				}
			//}

			Log("Sign: " + salt);

			if(salt != null) {
				fuse.Pull(salt);
				
				// Very important to sleep a bit here
				// Use coroutines to send fuse.Game("race");
				// in Unity. Or send it on the first "noop" in Update()!
				Thread.Sleep(100);
				
				fuse.Game("race");

				Thread.Sleep(100);
				
				Thread thread = new Thread(FakeLoop);
				thread.Start();
				
				Thread.Sleep(500);

				fuse.Chat("root", "hello");

				Thread.Sleep(500);

				Log("Room: " + fuse.Room("race", 4));

				Thread.Sleep(500);

				string[] list = fuse.ListRoom();

				if(list != null) {
					Log("List: " + list.Length);

					for(int i = 0; i < list.Length; i++) {
						string[] room = list[i].Split(',');
						Log("      " + room[0] + " " + room[1] + " " + room[2]);
					}
				}
				
				Thread.Sleep(500);

				fuse.Chat("root", "hello");
				
				Thread.Sleep(500);
				
				fuse.Send("white+0+0");
			}
		}
		catch(Exception e) {
			Log(e.ToString());
		}
	}
	
	// ------------- UPDATE EMULATION -------------
	static void FakeLoop() {
		while(true) {
			try {
				instance.Update();
			}
			finally {
				Thread.Sleep(10);
			}
		}
	}
}
