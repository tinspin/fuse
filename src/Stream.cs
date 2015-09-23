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
public class Stream {
	public string host = "fuse.rupy.se";
	public int port = 80;
	
	private Queue<string> queue;
	private Socket pull, push;
	private bool connected;
	
	private class State {
		public Socket socket = null;
		public const int size = 32768;
		public byte[] data = new byte[size];
	}

	public Stream() {
		bool policy = true;
		
		//policy = Security.PrefetchSocketPolicy(host, port); // policy ###
		
		if(!policy)
			throw new Exception("Policy (" + host + ":" + port + ") failed.");

		IPAddress address = Dns.Resolve(host).AddressList[0];
		IPEndPoint remote = new IPEndPoint(address, port);
		
		//Console.WriteLine("Address: " + address + ".");
		
		push = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
		push.NoDelay = true;
		push.Connect(remote);
	}
	
	public void Connect(string name) {
		queue = new Queue<string>();
	
		IPAddress address = Dns.Resolve(host).AddressList[0];
		IPEndPoint remote = new IPEndPoint(address, port);
	
		pull = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
		pull.NoDelay = true;
		pull.Connect(remote);
		
		String data = "GET /pull?name=" + name + " HTTP/1.1\r\n"
				    + "Host: " + host + "\r\n"
					+ "Head: less\r\n\r\n"; // enables TCP no delay
					
		pull.Send(Encoding.ASCII.GetBytes(data));
		
		State state = new State();
		state.socket = pull;
		
		pull.BeginReceive(state.data, 0, State.size, 0, new AsyncCallback(Callback), state);
		
		connected = true;
	}
	
	public string Send(String name, String message) {
		byte[] data = new byte[1024];
		String text = "POST /push HTTP/1.1\r\n"
				    + "Host: " + host + "\r\n"
					+ "Head: less\r\n\r\n" // enables TCP no delay
					+ "name=" + name + "&message=" + message;
		
		push.Send(Encoding.ASCII.GetBytes(text));
		int read = push.Receive(data);
		text = Encoding.ASCII.GetString(data, 0, read);
		
		//Console.WriteLine("Read: " + read + ".");
		//Console.WriteLine("Text: " + text + ".");
		
		string[] split = text.Split(new string[] { "\r\n" }, StringSplitOptions.None);
		return split[2];
	}
	
	public string[] Receive() {
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
				string text = Encoding.ASCII.GetString(state.data, 0, read);
				string[] split = text.Split(new string[] { "\r\n" }, StringSplitOptions.None);

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
	
	/* Protocol:           --> double arrow = broadcast
	 *                      -> single arrow = single send
	 *
	 * Send()                  Return;
	 * user                 -> user|<key>
	 *                      -> fail|<name> contains bad characters
	 *                      -> fail|<name> already registered
	 * salt                 -> salt|<salt>
	 * auth|<salt>|<hash>   -> auth|Success
	 *                      -> fail|User not found
	 *                      -> fail|Wrong hash
	 * room|<type>|<size>   -> room|Success // make and join room
	 *                      -> fail|User not in lobby
	 * list                 -> list|<name>|<type>|<size>|<name>|<type>|<size>|...
	 * join|<name>          -> join|Success
	 *                     --> join|<name> // in new room
	 *                     --> exit|<name> // in lobby
	 *                      -> fail|Room not found
	 *                      -> fail|Room is locked
	 *                      -> fail|Room is full
	 * exit                 -> exit|Success
	 *                     --> exit|<name> // in old room OR
	 *                     --> drop|<name> // in old room when maker leaves 
	 *                                        then room is dropped and everyone 
	 *                                        put back in lobby
	 *                     --> join|<name> // in lobby
	 *                      -> fail|User in lobby
	 * lock                 -> lock|Success
	 *                     --> lock|<name> // to everyone in room, can be used 
	 *                                        to start the game
	 *                      -> fail|User not room host
	 * chat|<text>          -> noop
	 *                     --> chat|<name>|<text>
	 * data|<data>          -> noop
	 *                     --> data|<name>|<data>
	 */
	
	// ------------- EXAMPLE USAGE -------------
	
	public static void Main() {
		try {
			string name = "two";
			Stream stream = new Stream();
			
			// if no key is stored try
			
			//string key = stream.User(name);
			
			//   then store name and key
			// otherwise
			//   get name and key
			
			string key = "SFwPWQLZcBAES7BZ";
			
			bool success = false;
		
			if(key != null) {
				success = stream.Auth(name, key);
			}
			
			if(success) {
				// this will allow you to Stream.Receive();
				// from MonoBehaviour.Update();
				stream.Connect(name); 
		
				// remove in unity ###
				Thread.Sleep(100);
				Alpha alpha = new Alpha(stream);
				Thread thread = new Thread(new ThreadStart(alpha.Beta));
				thread.Start();
				Thread.Sleep(500);
				// remove
		
				stream.Chat(name, "hello");
				
				Thread.Sleep(500);
				
				Console.WriteLine("Room: " + stream.Room(name, "race", 4));
				
				Thread.Sleep(500);
				
				string[] list = stream.List(name);
				
				Console.WriteLine("List: " + list.Length / 3);
				
				for(int i = 0; i < list.Length; i+=3) {
					Console.WriteLine(list[i] + " " + list[i + 1] + " (" + list[i + 2] + ")");
				}
				
				Thread.Sleep(500);
				
				stream.Chat(name, "hello");
			}
			
			Console.WriteLine("Login: " + success + ".");
		}
		catch(Exception e) {
			Console.WriteLine(e.ToString());
		}
	}

	public string User(string name) {
		string[] user = Send(name, "user").Split('|');

		if(user[0].Equals("fail")) {
			if(user[1].IndexOf("bad") > 0) {
				// limit characters to alpha numeric.
			}
			else if(user[1].IndexOf("already") > 0) {
				// prompt for other name.
			}
			
			Console.WriteLine("User fail: " + user[1] + ".");
			return null;
		}
		
		return user[1];
	}
	
	public bool Auth(string name, string key) {
		string salt = Send(name, "salt").Split('|')[1];
		string hash = MD5(key + salt);
		string[] auth = Send(name, "auth|" + salt + "|" + hash).Split('|');
		
		if(auth[0].Equals("fail")) {
			Console.WriteLine("Auth fail: " + auth[1] + ".");
			return false;
		}
		
		return true;
	}
	
	public bool Room(string name, String type, int size) {
		string[] room = Send(name, "room|" + type + "|" + size).Split('|');
		
		if(room[0].Equals("fail")) {
			Console.WriteLine("Room fail: " + room[1] + ".");
			return false;
		}
		
		return true;
	}
	
	public string[] List(string name) {
		string list = Send(name, "list");
		
		if(list.StartsWith("fail")) {
			Console.WriteLine("List fail: " + list + ".");
			return null;
		}
		
		return list.Substring(list.IndexOf('|') + 1).Split('|');
	}
	
	public bool Join(string name) {
		string[] join = Send(name, "join").Split('|');
		
		if(join[0].Equals("fail")) {
			Console.WriteLine("Join fail: " + join[1] + ".");
			return false;
		}
		
		return true;
	}
	
	public bool Exit(string name) {
		string[] exit = Send(name, "exit").Split('|');
		
		if(exit[0].Equals("fail")) {
			Console.WriteLine("Exit fail: " + exit[1] + ".");
			return false;
		}
		
		return true;
	}
	
	public void Lock(string name, string text) {
		Send(name, "lock");
	}
	
	public void Chat(string name, string text) {
		Send(name, "chat|" + text);
	}
	
	public void Data(string name, string data) {
		Send(name, "data|" + data);
	}
	
	public static string MD5(string input) {
		MD5 md5 = System.Security.Cryptography.MD5.Create();
		byte[] bytes = Encoding.ASCII.GetBytes(input);
		byte[] hash = md5.ComputeHash(bytes);
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < hash.Length; i++) {
			sb.Append(hash[i].ToString("X2"));
		}
		return sb.ToString();
	}
}

// this is my emulation of MonoBehaviour.Update();

public class Alpha {
	private Stream stream;
	public Alpha(Stream stream) { this.stream = stream; }
	public void Beta() {
		while(true) {
			string[] received = stream.Receive();
			
			if(received != null) {
				for(int i = 0; i < received.Length; i++) {
					Console.WriteLine("Received: " + received[i] + ".");
				}
			}
		}
	}
};