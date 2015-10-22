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

public class Fuse {
	public string host = "fuse.rupy.se";
	public int port = 80;

	private Queue<string> queue;
	private Socket pull, push;
	private bool connected;

	private IPEndPoint remote;

	private class State {
		public Socket socket = null;
		public const int size = 32768;
		public byte[] data = new byte[size];
	}

	public Fuse() {
		bool policy = true;

		//policy = Security.PrefetchSocketPolicy(host, port); // policy ###

		if(!policy)
			throw new Exception("Policy (" + host + ":" + port + ") failed.");

		IPAddress address = Dns.GetHostEntry(host).AddressList[0];
		remote = new IPEndPoint(address, port);

		push = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
		push.NoDelay = true;
		push.Connect(remote);
	}

	public void Pull(string name) {
		queue = new Queue<string>();

		pull = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
		pull.NoDelay = true;
		pull.Connect(remote);

		String data = "GET /pull?name=" + name + " HTTP/1.1\r\n"
				+ "Host: " + host + "\r\n"
				+ "Head: less\r\n\r\n"; // enables TCP no delay

		pull.Send(Encoding.UTF8.GetBytes(data));

		State state = new State();
		state.socket = pull;

		pull.BeginReceive(state.data, 0, State.size, 0, new AsyncCallback(Callback), state);

		connected = true;
	}

	public string Push(String name, String data) {
		byte[] body = new byte[1024];

		String text = "GET /push?name=" + name + "&data=" + data + " HTTP/1.1\r\n"
				+ "Host: " + host + "\r\n"
				+ "Head: less\r\n\r\n"; // enables TCP no delay

		push.Send(Encoding.UTF8.GetBytes(text));
		int read = push.Receive(body);
		text = Encoding.UTF8.GetString(body, 0, read);

		string[] split = text.Split(new string[] { "\r\n\r\n" }, StringSplitOptions.None);
		return split[1];
	}

	public string[] Pull() {
		if(!connected)
			return null;

		lock(queue) {
			if(queue.Count > 0) {
				string[] messages = new string[queue.Count];

				for(int i = 0; i < messages.Length; i++) {
					messages[i] = queue.Dequeue();
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

				// TODO: Fix this to work with lines split over many chunks!

				if(!split[0].StartsWith("HTTP")) {
					string[] messages = split[1].Split('\n');

					lock(queue) {
						for(int i = 0; i < messages.Length; i++) {
							if(messages[i].Length > 0) {
								queue.Enqueue(messages[i]);
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
			string name = "fuse";
			Fuse fuse = new Fuse();

			// if no key is stored try

			//string key = fuse.User(name);

			//   then store name and key
			// otherwise
			//   get name and key

			string key = "F9hG7K7Jwe1SmtiQ";

			bool success = false;

			if(key != null) {
				success = fuse.Open(name, key);
			}

			if(success) {
				// this will allow you to Fuse.Pull();
				// from MonoBehaviour.Update();
				fuse.Pull(name); 

				// remove in unity ###
				Thread.Sleep(100);
				Alpha alpha = new Alpha(fuse);
				Thread thread = new Thread(new ThreadStart(alpha.Beta));
				thread.Start();
				Thread.Sleep(500);
				// remove

				fuse.Chat(name, "hello");

				Thread.Sleep(500);

				Console.WriteLine("host " + fuse.Room(name, "race", 4));

				Thread.Sleep(500);

				string[] list = fuse.ListRoom(name);

				if(list != null) {
					Console.WriteLine("list " + list.Length);

					for(int i = 0; i < list.Length; i++) {
						string[] room = list[i].Split('+');

						Console.WriteLine(room[0] + " " + room[1] + " (" + room[2] + ")");
					}
				}
				
				Thread.Sleep(500);

				fuse.Chat(name, "hello");
			}

			Console.WriteLine("login " + success);
		}
		catch(Exception e) {
			Console.WriteLine(e.ToString());
		}
	}

	public string User(string name) {
		string[] user = Push(name, "user").Split('|');

		if(user[1].Equals("fail")) {
			if(user[2].IndexOf("bad") > 0) {
				// limit characters to alpha numeric.
			}
			else if(user[2].IndexOf("already") > 0) {
				// prompt for other name.
			}

			Console.WriteLine("user " + user[2]);
			return null;
		}

		return user[2];
	}

	public bool Open(string name, string key) {
		string salt = Push(name, "salt").Split('|')[2];
		string hash = MD5(key + salt);
		string[] open = Push(name, "open|" + salt + "|" + hash).Split('|');

		if(open[1].Equals("fail")) {
			Console.WriteLine("open " + open[2]);
			return false;
		}

		return true;
	}

	public bool Room(string name, String type, int size) {
		return BoolPush(name, "room|" + type + "|" + size);
	}

	public string[] ListRoom(string name) {
		string list = Push(name, "list|room");

		if(list.StartsWith("list|fail")) {
			Console.WriteLine(name + " " + list);
			return null;
		}

		if(list.Length > 15)
			return list.Substring(15).Split('|'); // from 'list|room|done|'
		else
			return null;
	}

	public bool Join(string name, string which) {
		return BoolPush(name, "join|" + which);
	}

	public bool Exit(string name) {
		return BoolPush(name, "exit");
	}

	public void Lock(string name, string text) {
		EasyPush(name, "lock");
	}

	public void Chat(string name, string text) {
		EasyPush(name, "chat|" + text);
	}

	public void Data(string name, string data) {
		EasyPush(name, "data|" + data);
	}

	public bool BoolPush(string name, string data) {
		string[] push = EasyPush(name, data);

		if(push == null) {
			return false;
		}

		return true;
	}

	public string[] EasyPush(string name, string data) {
		string[] push = Push(name, data).Split('|');
		
		if(push[1].Equals("fail")) {
			Console.WriteLine(name + " " + data + " " + push[2]);
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
			string[] received = fuse.Pull();

			if(received != null) {
				for(int i = 0; i < received.Length; i++) {
					Console.WriteLine("received " + received[i]);
				}
			}
		}
	}
};