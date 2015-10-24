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
	private string name, salt;

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

	public string Name() {
		return name;
	}
	
	public void Name(string name) {
		this.name = name;
	}

	public void Pull() {
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

	public string Push(string data) {
		byte[] body = new byte[1024];

		if(salt != null)
			data = data.Substring(0, 4) + '|' + salt + data.Substring(4, data.Length - 4);

		String text = "GET /push?name=" + name + "&data=" + data + " HTTP/1.1\r\n"
				+ "Host: " + host + "\r\n"
				+ "Head: less\r\n\r\n"; // enables TCP no delay

		push.Send(Encoding.UTF8.GetBytes(text));
		int read = push.Receive(body);
		text = Encoding.UTF8.GetString(body, 0, read);

		string[] split = text.Split(new string[] { "\r\n\r\n" }, StringSplitOptions.None);
		return split[1];
	}

	public string[] Read() {
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
			Fuse fuse = new Fuse();
			fuse.Name("fuse");
			
			// if no key is stored try

			//string key = fuse.User();

			//   then store name and key
			// otherwise
			//   get name and key

			string key = "F9hG7K7Jwe1SmtiQ";

			bool success = false;

			if(key != null) {
				success = fuse.Open(key);
			}

			if(success) {
				// this will allow you to Fuse.Read();
				// from MonoBehaviour.Update();
				fuse.Pull();
				Console.WriteLine("Game: " + fuse.Game("klossar"));

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
						Console.WriteLine("      " + room[0] + " " + room[1] + " (" + room[2] + ")");
					}
				}
				
				Thread.Sleep(500);

				fuse.Chat("hello");
			}

			Console.WriteLine("Open: " + success);
		}
		catch(Exception e) {
			Console.WriteLine(e.ToString());
		}
	}

	public string User() {
		string[] user = Push("user").Split('|');

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

		this.salt = user[4];
		return user[2];
	}

	public bool Open(string key) {
		string salt = Push("salt").Split('|')[2];
		string hash = MD5(key + salt);
		string[] open = Push("open|" + salt + "|" + hash).Split('|');

		if(open[1].Equals("fail")) {
			Console.WriteLine("open " + open[2]);
			return false;
		}

		this.salt = salt;
		return true;
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
			Console.WriteLine(name + " " + list);
			return null;
		}

		if(list.Length > 15)
			return list.Substring(15).Split('|'); // from 'list|done|room|'
		else
			return null;
	}

	public bool Join(string which) {
		return BoolPush("join|" + which);
	}

	public bool Exit() {
		return BoolPush("exit");
	}

	public void Lock(string text) {
		EasyPush("lock");
	}

	public void Chat(string text) {
		EasyPush("chat|" + text);
	}

	public void Send(string data) {
		EasyPush("send|" + data);
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