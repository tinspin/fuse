//using UnityEngine; // policy ###
using System;
using System.Net;
using System.Net.Sockets;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Threading;
using System.Text;

/* A real-time comet stream plugin for rupy.
 * For unity seach for ###
 * For usage scroll down to main() method.
 */

// TODO: Fix callback to work with lines split over many chunks.
// DONE: Add push queue wrapper for async outgoing messages.

public class Fuse {
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
	
	public void Salt(string salt) {
		this.salt = salt;
	}
	
	public Fuse() {
		bool policy = true;

		//policy = Security.PrefetchSocketPolicy(host, port); // policy ###

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

	public void Pull() {
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
		lock(output) {
			output.Enqueue(data);
			lock(thread)
				Monitor.Pulse(thread);
		}
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
			Console.WriteLine(e.ToString());
		}
	}

	// #########################################
	// YOU CAN DELETE EVERYTHING BELOW THIS LINE
	// #########################################
	//   Don't forget the closing } though! ;)

	// ------------- EXAMPLE USAGE -------------

	public static void Main() {
		try {
			Fuse fuse = new Fuse();
			string key;

			// if no key is stored try
/*
			try {
				key = fuse.User("fuse");
			}
			catch(Exception e) {
				Console.WriteLine(e);
				return;
			}
*/
			//   then store name and key
			// otherwise
			//   get name and key

			key = "F9hG7K7Jwe1SmtiQ";
			string salt = null;

			if(key != null) {
				salt = fuse.Sign("fuse", key);
			}

			if(salt != null) {
				fuse.Salt(salt);
				Console.WriteLine("Salt: " + fuse.salt);
				// this will allow you to Fuse.Read();
				// from MonoBehaviour.Update();
				fuse.Pull();
				Console.WriteLine("Game: " + fuse.Game("kloss"));

				// remove in unity ###
				Thread.Sleep(100);
				Alpha alpha = new Alpha(fuse);
				Thread thread = new Thread(new ThreadStart(alpha.Beta));
				thread.Start();
				Thread.Sleep(500);
				// remove

				fuse.Chat("hello");

				Thread.Sleep(500);

				Console.WriteLine("Room: " + fuse.Room("race", 4));

				Thread.Sleep(500);

				string[] list = fuse.ListRoom();

				if(list != null) {
					Console.WriteLine("List: " + list.Length);

					for(int i = 0; i < list.Length; i++) {
						string[] room = list[i].Split('+');
						Console.WriteLine("      " + room[0] + " " + room[1] + " " + room[2] + " " + room[3]);
					}
				}
				
				Thread.Sleep(500);

				fuse.Chat("hello");
				
				Thread.Sleep(500);
				
				fuse.Send("white+0+0");
			}

			Console.WriteLine("Open: " + salt);
		}
		catch(Exception e) {
			Console.WriteLine(e.ToString());
		}
	}

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
		EasyUser(name, mail, pass);
	}

	public string[] EasyUser(string name, string mail, string pass) {
		string[] user = Push("user|" + name).Split('|');

		if(user[1].Equals("fail")) {
			throw new Exception(user[2]);
		}

		this.salt = user[2];
		return user;
	}

	public string Sign(string name, string key) {
		// for anonymous user use <id> instead here
		string[] salt = Push("salt|" + name).Split('|');
		
		if(salt[1].Equals("fail")) {
			Console.WriteLine("salt " + salt[2]);
			return null;
		}
		
		string[] sign = Push("sign|" + salt[2] + "|" + MD5(key + salt[2])).Split('|');

		if(sign[1].Equals("fail")) {
			Console.WriteLine("sign " + sign[2]);
			return null;
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
			Console.WriteLine(list);
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

	public bool Exit() {
		return BoolPush("exit");
	}

	public void play(string seed) {
		EasyPush("play|" + seed);
	}

	public void Chat(string text) {
		EasyPush("chat|" + text);
	}

	public void Send(string data) {
		Async("send|" + data);
	}

	public bool BoolPush(string data) {
		string[] push = EasyPush(data);

		if(push == null) {
			return false;
		}

		return true;
	}

	public string[] EasyPush(string data) {
		string[] push = Push(data).Split('|');
		
		if(push[1].Equals("fail")) {
			Console.WriteLine(data + " " + push[2]);
			return null;
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
}

// this is my emulation of MonoBehaviour.Update();

public class Alpha {
	private Fuse fuse;
	public Alpha(Fuse fuse) { this.fuse = fuse; }
	public void Beta() {
		while(true) {
			try {
				string[] received = fuse.Read();

				if(received != null) {
					for(int i = 0; i < received.Length; i++) {
						Console.WriteLine("Read: " + received[i]);
					}
				}
			}
			finally {
				Thread.Sleep(10);
			}
		}
	}
};