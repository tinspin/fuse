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
		Start();
	}
	
	void Awake() {
		// ### 4
		// Also to debug multiplayer it helps if you check:
		// Edit -> Project Settings -> Player -> Resolution & Presentation -> Run In Background
		//Application.runInBackground = true;
		//DontDestroyOnLoad(gameObject);
	}
	
	void Start() {
		instance = this;
		bool policy = true;

		//policy = Security.PrefetchSocketPolicy(host, port); // not needed for most cases ### 5

		if(!policy)
			throw new Exception("Policy (" + host + ":" + port + ") failed.");

		IPAddress address = Dns.GetHostEntry(host).AddressList[0];
		remote = new IPEndPoint(address, port);

		input = new Queue<string>();
		output = new Queue<string>();

		thread = new Thread(PushAsync);
		thread.Start();

		push = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
		push.NoDelay = true;
		push.Connect(remote);
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
				lock(output) {
					while(output.Count > 0) {
						Push(output.Dequeue());
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
		byte[] body = new byte[1024];

		if(salt != null)
			data = data.Substring(0, 4) + '|' + salt + data.Substring(4, data.Length - 4);

		String text = "GET /push?data=" + Uri.EscapeDataString(data) + " HTTP/1.1\r\nHost: " + host + "\r\n";

		if(first) {
			text += "Head: less\r\n\r\n"; // enables TCP no delay
			first = false;
		}
		else
			text += "\r\n";

		push.Send(Encoding.UTF8.GetBytes(text));
		int read = push.Receive(body);
		text = Encoding.UTF8.GetString(body, 0, read);

		string[] split = text.Split(new string[] { "\r\n\r\n" }, StringSplitOptions.None);
		return split[1];
	}

	public string[] Read() {
		if(!connected)
			return null;

		lock(input) {
			if(input.Count > 0) {
				string[] messages = new string[input.Count];

				for(int i = 0; i < messages.Length; i++) {
					messages[i] = input.Dequeue();
				}

				return messages;
			}
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
					string[] messages = split[1].Split('\n');

					lock(input) {
						for(int i = 0; i < messages.Length; i++) {
							if(messages[i].Length > 0) {
								input.Enqueue(messages[i]);
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
		User(name, "", pass);
	}

	public void User(string name, string mail, string pass) {
		EasyUser(name, mail, MD5(pass + name.ToLower()));
	}

	private string[] EasyUser(string name, string mail, string hash) {
		string[] user = Push("user|" + name + "|" + mail + "|" + hash).Split('|');

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
		return Sign(name, MD5(pass + name.ToLower()));
	}
	
	private string Sign(string user, string hide) {
		string[] salt = Push("salt|" + user).Split('|');
		
		if(salt[1].Equals("fail")) {
			throw new Exception(salt[2]);
		}
		
		string[] sign = Push("sign|" + salt[2] + "|" + MD5(hide + salt[2])).Split('|');

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
			return list.Substring(15).Split('|'); // from 'list|done|room|'
		else
			return null;
	}

	public bool Join(string room) {
		return BoolPush("join|" + room);
	}
	
	public bool Join(string room, string info) {
		return BoolPush("join|" + room + "|" + info);
	}
	
	public bool Poll(string user, string info) {
		return BoolPush("poll|" + user + "|" + info);
	}

	public void Play(string seed) {
		Async("play|" + seed);
	}
	
	public void Over(string data) {
		Async("over|" + data);
	}

	public void Chat(string text) {
		Async("chat|" + text);
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

	public static string MD5(string input) {
		MD5 md5 = System.Security.Cryptography.MD5.Create();
		byte[] bytes = Encoding.UTF8.GetBytes(input);
		byte[] hash = md5.ComputeHash(bytes);
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
			Fuse fuse = new Fuse();
			string key;

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

			key = "F9hG7K7Jwe1SmtiQ";
			string salt = null;

			if(key != null) {
				try {
					salt = fuse.SignNameKey("fuse", key);
				}
				catch(Exception e) {
					Log(e.Message);
					return;
				}
			}

			Log("Sign: " + salt);

			if(salt != null) {
				fuse.Pull(salt);
				fuse.Game("kloss");

				Thread.Sleep(100);
				
				Thread thread = new Thread(FakeLoop);
				thread.Start();
				
				Thread.Sleep(500);

				fuse.Chat("hello");

				Thread.Sleep(500);

				Log("Room: " + fuse.Room("race", 4));

				Thread.Sleep(500);

				string[] list = fuse.ListRoom();

				if(list != null) {
					Log("List: " + list.Length);

					for(int i = 0; i < list.Length; i++) {
						string[] room = list[i].Split('+');
						Log("      " + room[0] + " " + room[1] + " " + room[2]);
					}
				}
				
				Thread.Sleep(500);

				fuse.Chat("hello");
				
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
