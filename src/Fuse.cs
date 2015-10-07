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
// TODO: UTF-8?!
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

	public void Connect(string name) {
		queue = new Queue<string>();

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

		String text = "GET /push?name=" + name + "&message=" + message + " HTTP/1.1\r\n"
				+ "Host: " + host + "\r\n"
				+ "Head: less\r\n\r\n"; // enables TCP no delay

		push.Send(Encoding.ASCII.GetBytes(text));
		int read = push.Receive(data);
		text = Encoding.ASCII.GetString(data, 0, read);

		string[] split = text.Split(new string[] { "\r\n\r\n" }, StringSplitOptions.None);
		return split[1];
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

	/* Protocol:           --> = broadcast to Receive()
	 *                      -> = direct return on Send()
	 *
	 * In sort of chronological order:
	 * 
	 * <type>                 <echo>
	 *
	 *  -> main|fail|name missing
	 *
	 *                      // register new user
	 *  join                -> join|done|<key>
	 *                      -> join|fail|<name> contains bad characters
	 *                      -> join|fail|<name> already registered
	 *
	 *                      // login old user
	 *  salt                -> salt|done|<salt>
	 *  user|<salt>|<hash>  -> user|done
	 *                      -> user|fail|user not found
	 *                      -> user|fail|salt not found
	 *                      -> user|fail|wrong hash
	 *
	 *                      <> = here you should call Connect(<name>) 
	 *                      with the name you successfully logged in as.
	 *
	 *  -> main|fail|user '<name>' not authorized
	 *
	 *                      // add friend
	 *  ally|<name>         -> ally|done
	 *                      -> ally|fail|user not found
	 *
	 *                      // set status
	 *  away|<boolean>      -> away|done
	 *
	 *                      // enable peer-to-peer
	 *  peer|<192.168...>   -> peer|done                    // send the internal IP
	 *
	 *                      // host room
	 *  host|<type>|<size>  -> host|done
	 *                      -> host|fail|user not in lobby
	 *
	 *                      // list rooms or data
	 *  list|room           -> list|room|done|<name>+<type>+<size>|...
	 *  list|data|<type>    -> list|data|done|<id>|...      // use load to get data
	 *                      -> list|fail|can only list 'room' or 'data'
	 *
	 *                      // join room
	 *  room|<name>         -> room|done
	 *                     --> here|<name>(|<ip>)           // in new room, all to all (ip if peer was set)
	 *                     --> gone|<name>                  // in lobby
	 *                      -> room|fail|room not found
	 *                      -> room|fail|room is locked
	 *                      -> room|fail|room is full
	 *
	 *                      // exit room
	 *  exit                -> exit|done
	 *                     --> here|<name>(|<ip>)           // in lobby, all to all (ip if peer was set)
	 *                     --> gone|<name>                  // in old room OR
	 *                     --> stop|<name>                  // in old room when maker leaves 
	 *                                                         then room is dropped and everyone 
	 *                                                         put back in lobby
	 *                      -> exit|fail|user in lobby
	 *
	 *                      // lock room before the game starts
	 *  lock                -> lock|done
	 *                     --> link|<name>                  // to everyone in room, can be used 
	 *                                                         to start the game
	 *                      -> lock|fail|user not room host
	 *
	 *                      // insert and select data
	 *  save|<type>|<json>  -> save|done|<id>|<key>         // to update data use this key in json
	 *                      -> save|fail|data too large
	 *  load|<type>|<id>    -> load|done|<json>             // use id from list|data|<type>
	 *                      -> load|fail|data not found
	 *
	 *                      // chat anywhere
	 *  chat|<text>         -> chat|done
	 *                     --> text|<name>|<text>
	 *
	 *                      // real-time gameplay packets
	 *  move|<data>         -> move|done
	 *                     --> data|<name>|<data>
	 *                      // <data> = <x>+<y>+<z>|<x>+<y>+<z>+<w>|<action>(|<speed>|...)
	 *                      //          position   |orientation    |key/button
	 *
	 *  -> main|fail|type '<type>' not found
	 *
	 * <soon> // future protocol
	 *
	 *  pull // load cards
	 *  pick // select card
	 *  push // show new cards
	 *
	 * <peer> // peer protocol
	 *
	 *  talk // send voice
	 *  head // send head/body movement
	 *  hand // send hand movements
	 */

	// #########################################
	// YOU CAN DELETE EVERYTHING BELOW THIS LINE
	// #########################################
	//   Don't forget the closing } though! ;)

	// ------------- EXAMPLE USAGE -------------

	public static void Main() {
		try {
			string name = "two";
			Fuse fuse = new Fuse();

			// if no key is stored try

			//string key = fuse.Join(name);

			//   then store name and key
			// otherwise
			//   get name and key

			string key = "SFwPWQLZcBAES7BZ";

			bool success = false;

			if(key != null) {
				success = fuse.User(name, key);
			}

			if(success) {
				// this will allow you to Fuse.Receive();
				// from MonoBehaviour.Update();
				fuse.Connect(name); 

				// remove in unity ###
				Thread.Sleep(100);
				Alpha alpha = new Alpha(fuse);
				Thread thread = new Thread(new ThreadStart(alpha.Beta));
				thread.Start();
				Thread.Sleep(500);
				// remove

				fuse.Chat(name, "hello");

				Thread.Sleep(500);

				Console.WriteLine("host " + fuse.Host(name, "race", 4));

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

	public string Join(string name) {
		string[] join = Send(name, "join").Split('|');

		if(join[1].Equals("fail")) {
			if(join[2].IndexOf("bad") > 0) {
				// limit characters to alpha numeric.
			}
			else if(join[2].IndexOf("already") > 0) {
				// prompt for other name.
			}

			Console.WriteLine("join " + join[2]);
			return null;
		}

		return join[2];
	}

	public bool User(string name, string key) {
		string salt = Send(name, "salt").Split('|')[2];
		string hash = MD5(key + salt);
		string[] user = Send(name, "user|" + salt + "|" + hash).Split('|');

		if(user[1].Equals("fail")) {
			Console.WriteLine("user " + user[2]);
			return false;
		}

		return true;
	}

	public bool Host(string name, String type, int size) {
		return BoolPush(name, "host|" + type + "|" + size);
	}

	public string[] ListRoom(string name) {
		string list = Send(name, "list|room");

		if(list.StartsWith("list|fail")) {
			Console.WriteLine(name + " " + list);
			return null;
		}

		if(list.Length > 15)
			return list.Substring(15).Split('|'); // from 'list|room|done|'
		else
			return null;
	}

	public bool Room(string name, string which) {
		return BoolPush(name, "room|" + which);
	}

	public bool Exit(string name) {
		return BoolPush(name, "exit");
	}

	public void Lock(string name, string text) {
		Push(name, "lock");
	}

	public void Chat(string name, string text) {
		Push(name, "chat|" + text);
	}

	public void Data(string name, string data) {
		Push(name, "data|" + data);
	}

	public bool BoolPush(string name, string message) {
		string[] push = Push(name, message);

		if(push == null) {
			return false;
		}

		return true;
	}

	public string[] Push(string name, string message) {
		string[] push = Send(name, message).Split('|');
		
		if(push[1].Equals("fail")) {
			Console.WriteLine(name + " " + message + " " + push[2]);
			return null;
		}
		
		return push;
	}

	public static string MD5(string input) {
		MD5 md5 = System.Security.Cryptography.MD5.Create();
		byte[] bytes = Encoding.ASCII.GetBytes(input);
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
			string[] received = fuse.Receive();

			if(received != null) {
				for(int i = 0; i < received.Length; i++) {
					Console.WriteLine("received " + received[i]);
				}
			}
		}
	}
};