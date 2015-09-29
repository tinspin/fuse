package stream;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import se.rupy.http.Async;
import se.rupy.http.Daemon;
import se.rupy.http.Deploy;
import se.rupy.http.Event;
import se.rupy.http.Root;
import stream.Node;

public class Game implements Node {
	ConcurrentHashMap salts = new ConcurrentHashMap();
	ConcurrentHashMap rooms = new ConcurrentHashMap();
	ConcurrentHashMap users = new ConcurrentHashMap();
	
	static Room lobby = new Room(null, "lobby", 1024);
	static Daemon daemon;
	static Node node;

	public void call(Daemon daemon, Node node) throws Exception {
		this.daemon = daemon;
		this.node = node;
	}

	public String push(final Event event, final String name, String message) throws Exception {
		System.err.println(name + " " + message);

		if(name.length() == 0)
			return "main|fail|name missing";
		
		String[] split = message.split("\\|");
		
		if(message.startsWith("join")) {
			Async.Work user = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					call.post("/node", "Host:" + event.query().header("host"), ("json={\"name\":\"" + name + "\"}&sort=key,name&create").getBytes("utf-8"));
				}

				public void read(String host, String body) throws Exception {
					System.out.println(body);

					if(body.indexOf("Validation") > 0) {
						String message = body.substring(body.indexOf("[") + 1, body.indexOf("]"));

						System.out.println("Validation " + message);
						
						if(message.startsWith("name"))
							event.query().put("fail", "join|fail|" + message.substring(message.indexOf("=") + 1) + " contains bad characters");
					}
					else if(body.indexOf("Collision") > 0) {
						String message = body.substring(body.indexOf("[") + 1, body.indexOf("]"));

						System.out.println("Collision " + message);
						
						if(message.startsWith("name"))
							event.query().put("fail", "join|fail|" + message.substring(message.indexOf("=") + 1) + " already registered");
					}
					else {
						JSONObject user = new JSONObject(body);

						String key = user.getString("key");

						event.query().put("done", "join|done|" + key);
					}
					
					event.reply().wakeup();
				}

				public void fail(String host, Exception e) throws Exception {
					e.printStackTrace();
				}
			};

			event.daemon().client().send("localhost", user, 30);

			return "hold";
		}

		if(message.startsWith("salt")) {
			String salt = Event.random(8);
			salts.put(salt, "");
			return "salt|done|" + salt;
		}

		if(message.startsWith("user")) {
			String salt = split[1];
			String hash = split[2].toLowerCase();
			
			if(name.length() > 0 && hash.length() > 0) {
				File file = new File(Root.home() + "/node/user/name" + Root.path(name));

				if(!file.exists()) {
					System.out.println(file);
					return "user|fail|user not found.";
				}

				JSONObject json = new JSONObject(Root.file(file));

				if(salts.remove(salt) == null) {
					return "user|fail|salt not found";
				}
				
				String md5 = Deploy.hash(json.getString("key") + salt, "MD5");
				
				//System.out.println(json);
				//System.out.println(hash);
				//System.out.println(md5);
				
				if(hash.equals(md5)) {
					User user = new User(name);
					user.json = json;
					users.put(user.name, user);
					user.move(null, lobby);
					return "user|done";
				}
				else
					return "user|fail|wrong hash";
			}
		}
		
		User user = (User) users.get(name);
		
		if(event.query().header("host").equals("fuse.radiomesh.org") && user == null)
			return "main|fail|user '" + name + "' not authorized";
		else if(name.equals("one")) { // TODO: Remove
			user = new User(name);
			users.put(user.name, user);
			user.move(null, lobby);
		}
		
		if(message.startsWith("peer")) {
			user.peer(event, split[1]);

			return "peer|done";
		}
		
		if(message.startsWith("host")) {
			if(user.room.user != null)
				return "host|fail|user not in lobby";

			String type = split[1];
			int size = Integer.parseInt(split[2]);
			Room room = new Room(user, type, size);
			rooms.put(room.user.name, room);
			user.move(lobby, room);

			return "host|done";
		}
		
		if(message.startsWith("list")) {
			String what = split[1];
			
			if(what.equals("room")) {
				StringBuilder builder = new StringBuilder("list|room|done");
				Iterator it = rooms.values().iterator();
			
				while(it.hasNext()) {
					Room room = (Room) it.next();
					builder.append("|" + room.user.name + "&" + room.type + "&" + room.users.size());
				}
				
				return builder.toString();
			}
			
			if(what.equals("data")) {
				final String type = split[2];
				
				final int from = 0;
				final int size = 5;
				final String key = user.json.getString("key");
				
				Async.Work work = new Async.Work(event) {
					public void send(Async.Call call) throws Exception {
						call.get("/link/user/" + type + "/" + key + "?from=" + from + "&size=" + size, "Host:" + event.query().header("host"));
					}

					public void read(String host, String body) throws Exception {
						StringBuilder builder = new StringBuilder("list|data|done");
						final JSONObject result = (JSONObject) new JSONObject(body);
						JSONArray list = result.getJSONArray("list");
						
						for(int i = 0; i < list.length(); i++) {
							JSONObject item = list.getJSONObject(i);
							long id = Root.hash(item.getString("key"));							
							builder.append("|" + id);
						}
						
						event.query().put("done", builder.toString());
						event.reply().wakeup();
					}

					public void fail(String host, Exception e) throws Exception {
						System.out.println("fuse load " + e);
					}
				};
				
				event.daemon().client().send("localhost", work, 30);
				return "hold";
			}
			
			return "list|fail|can only list 'room' or 'data'";
		}
		
		if(message.startsWith("room")) {
			Room room = (Room) rooms.get(split[1]);
			
			if(room == null)
				return "room|fail|room not found";
			
			if(room.lock)
				return "room|fail|room is locked";
			
			if(room.users.size() == room.size)
				return "room|fail|room is full";
			
			user.move(user.room, room);
			
			return "room|done";
		}
		
		if(message.startsWith("lock")) {
			if(user.room.user == null)
				return "lock|fail|user in lobby";
			
			if(user.room.user == user)
				user.room.send(null, "link");
			
			return "lock|done";
		}
		
		if(message.startsWith("exit")) {
			if(user.room.user == null)
				return "exit|fail|user in lobby";
			
			Room room = user.move(user.room, lobby);
			
			if(room != null) {
				rooms.remove(room.user.name);
			}
			
			return "exit|done";
		}
		
		if(message.startsWith("save")) {
			if(split[2].length() > 512) {
				return "save|fail|data too large";
			}
			
			final String type = split[1];
			final JSONObject json = new JSONObject(split[2]);
			final String key = json.optString("key");
			final String user_key = user.json.getString("key");
			
			Async.Work node = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					String data = "json=" + json.toString() + "&type=" + type + "&sort=key" + (key.length() > 0 ? "" : "&create");
					call.post("/node", "Host:" + event.query().header("host"), data.getBytes("utf-8"));
				}

				public void read(String host, final String body) throws Exception {
					final JSONObject node = (JSONObject) new JSONObject(body);
					
					Async.Work link = new Async.Work(event) {
						public void send(Async.Call call) throws Exception {
							call.post("/link", "Host:" + event.query().header("host"), ("pkey=" + user_key + 
									"&ckey=" + node.getString("key") + 
									"&ptype=user&ctype=" + type).getBytes("utf-8"));
						}

						public void read(String host, String body) throws Exception {
							System.out.println("fuse node " + body);
							String key = node.getString("key");
							event.query().put("done", "save|done|" + Root.hash(key) + "|" + key);
							event.reply().wakeup();
						}

						public void fail(String host, Exception e) throws Exception {
							System.out.println("fuse link " + e);
						}
					};

					event.daemon().client().send(host, link, 30);
				}

				public void fail(String host, Exception e) throws Exception {
					System.out.println("fuse save " + e);
				}
			};

			event.daemon().client().send("localhost", node, 30);
			return "hold";
		}
		
		if(message.startsWith("load")) {
			String type = split[1];
			long id = Long.parseLong(split[2]);
			
			File file = new File(Root.home() + "/node/" + type + "/id" + Root.path(id));

			if(!file.exists()) {
				System.out.println(file);
				event.query().put("fail", "load|fail|data not found");
			}

			return "load|done|" + Root.file(file);
		}
		
		if(message.startsWith("chat")) {
			if(user == null)
				lobby.send("chat|" + name + "|" + split[1]);
			else
				user.room.send("chat|" + name + "|" + split[1]);
			
			return "chat|done";
		}
		
		if(message.startsWith("move")) {
			if(user == null)
				lobby.send("move|" + name + "|" + split[1]);
			else
				user.room.send(user, "move|" + name + "|" + split[1]);
			
			return "move|done";
		}
		
		return "main|fail|type '" + split[0] + "' not found";
	}

	public static class User {
		String[] ip;
		JSONObject json;
		String name;
		Room room;
		
		User(String name) {
			this.name = name;
		}
		
		void peer(Event event, String ip) {
			this.ip = new String[2];
			this.ip[0] = ip;
			this.ip[1] = event.remote();
		}

		String peer(User other) {
			if(ip != null)
				if(other.ip != null && ip[1].equals(other.ip[1]))
					return "|" + ip[0];
				else
					return "|" + ip[1];
			
			return "";
		}
		
		Room move(Room from, Room to) throws Exception {
			Room drop = null;
			
			if(from != null) {
				from.remove(this);
				
				if(from.user != null && from.user.name.equals(name)) {
					from.send("stop|" + name);
					from.clear();
					
					drop = from;
				}
				else
					from.send("gone|" + name);
			}
			
			if(to != null) {
				this.room = to;
			
				to.add(this);
				to.send(this, "here|" + name);
			}
			
			return drop;
		}
		
		public String toString() {
			return name;
		}
	}
	
	public static class Room {
		ConcurrentHashMap users = new ConcurrentHashMap();
		boolean lock;
		String type;
		User user;
		int size;
		
		Room(User user, String type, int size) {
			this.type = type;
			this.user = user;
			this.size = size;
		}
		
		void send(String message) throws Exception {
			send(null, message);
		}
		
		void send(User from, String message) throws Exception {
			Iterator it = users.values().iterator();
			
			if(message.startsWith("lock"))
				lock = true;
			
			while(it.hasNext()) {
				User user = (User) it.next();
				
System.out.println(from + " " + message);
				
				if(message.startsWith("here")) // send every user in room to joining user
					node.push(null, from.name, "here|" + user.name + user.peer(from));
				
				if(from == null || !from.name.equals(user.name)) // send message from user to room
					node.push(null, user.name, message.startsWith("here") ? message + from.peer(user) : message);
				
				if(message.startsWith("stop")) // eject everyone
					user.move(null, lobby);
			}
		}
		
		void clear() {
			users.clear();
		}
		
		void add(User user) {
			users.put(user.name, user);
		}
		
		void remove(User user) {
			users.remove(user.name);
		}
		
		public String toString() {
			return user.name + " " + type + " " + users;
		}
	}
	
	public void broadcast(String name, String message) {

	}

	public void exit() {
		//daemon.remove(this);
	}
}