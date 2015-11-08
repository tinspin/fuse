package fuse;

import java.io.File;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import se.rupy.http.Async;
import se.rupy.http.Daemon;
import se.rupy.http.Deploy;
import se.rupy.http.Event;
import se.rupy.http.Root;

public class Router implements Node {
	ConcurrentHashMap users = new ConcurrentHashMap();
	ConcurrentHashMap games = new ConcurrentHashMap();

	static Daemon daemon;
	static Node node;

	public void call(Daemon daemon, Node node) throws Exception {
		this.daemon = daemon;
		this.node = node;
	}

	private synchronized String session() {
		String salt = Event.random(4);

		while(users.get(salt) != null)
			salt = Event.random(4);

		return salt;
	}

	private User session(String name, String salt, JSONObject json) throws Exception {
		User user = new User(name.length() > 0 ? name : "" + Root.hash(json.getString("key")), salt);
		user.json = json;
		users.put(salt, user);
		return user;
	}

	public String push(String salt, String data, boolean wake) throws Exception {
		throw new Exception("Nope");
	}
	public boolean wakeup(String name) { return false; }

	public String push(final Event event, String data) throws Exception {
		System.err.println("-> " + data);

		final String[] split = data.split("\\|");

		if(data.startsWith("user")) {
			final boolean name = split.length > 1 && split[1].length() > 0;
			final boolean mail = split.length > 2 && split[2].length() > 0;
			final boolean pass = split.length > 3 && split[3].length() > 0;

			if(name) {
				if(split[1].length() < 3)
					return "user|fail|name too short";

				if(split[1].length() > 12)
					return "user|fail|name too long";

				if(name && !split[1].matches("[a-zA-Z0-9.\\-]+"))
					return "user|fail|name invalid";

				if(name && split[1].matches("[0-9]+"))
					return "user|fail|name alpha missing";
			}

			if(mail && split[2].indexOf("@") < 1 && !split[2].matches("[a-zA-Z0-9.@\\-\\+]+"))
				return "user|fail|mail invalid";

			if(pass && split[3].length() < 3)
				return "user|fail|pass too short";

			Async.Work user = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					String json = "{";
					String sort = "";

					boolean add = false;

					if(name) {
						json += "\"name\":\"" + split[1] + "\"";
						sort += ",name";

						add = true;
					}

					if(mail) {
						if(add)
							json += ",";

						json += "\"mail\":\"" + split[2] + "\"";
						sort += ",mail";

						add = true;
					}

					if(pass) {
						if(add)
							json += ",";

						json += "\"pass\":\"" + split[3] + "\"";
					}

					json += "}";

					byte[] post = ("json=" + json + "&sort=key" + sort + "&create").getBytes("utf-8");
					String host = event.query().header("host");

					call.post("/node", "Host:" + host, post);
				}

				public void read(String host, String body) throws Exception {
					System.out.println(body);

					if(body.indexOf("Validation") > 0) {
						String message = body.substring(body.indexOf("[") + 1, body.indexOf("]"));

						System.out.println("Validation " + message);

						if(message.startsWith("name"))
							event.query().put("fail", "user|fail|name contains bad characters");

						if(message.startsWith("mail"))
							event.query().put("fail", "user|fail|mail contains bad characters");
					}
					else if(body.indexOf("Collision") > 0) {
						String message = body.substring(body.indexOf("[") + 1, body.indexOf("]"));

						System.out.println("Collision " + message);

						if(message.startsWith("name"))
							event.query().put("fail", "user|fail|name already registered");

						if(message.startsWith("mail"))
							event.query().put("fail", "user|fail|mail already registered");
					}
					else {
						JSONObject json = new JSONObject(body);
						String key = json.getString("key");

						User user = session(name ? split[1] : "", session(), json);
						user.authorized = true;

						event.query().put("done", "user|done|" + user.salt + "|" + key + "|" + Root.hash(key));
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

		if(data.startsWith("mail")) {
			File file = new File(Root.home() + "/node/user/mail" + Root.path(split[1]));

			if(file == null || !file.exists()) {
				return "mail|fail|not found";
			}

			JSONObject json = new JSONObject(Root.file(file));

			return "mail|done|" + Root.hash(json.getString("key"));
		}

		if(data.startsWith("salt")) {
			String name = split[1];
			String salt = session();

			File file = null;

			if(name.matches("[0-9]+"))
				file = new File(Root.home() + "/node/user/id" + Root.path(Long.parseLong(name)));
			else
				file = new File(Root.home() + "/node/user/name" + Root.path(name));

			if(file == null || !file.exists()) {
				return "salt|fail|not found";
			}

			JSONObject json = new JSONObject(Root.file(file));

			session(name, salt, json);

			return "salt|done|" + salt;
		}

		if(split.length < 2)
			return "main|fail|salt not found";
		
		User user = (User) users.get(split[1]);

		if(user == null || !user.salt.equals(split[1]))
			return "main|fail|salt not found";

		if(data.startsWith("hash")) {
			String hash = split[2].toLowerCase();

			if(user.name.length() > 0 && hash.length() > 0) {
				String key = user.json.has("pass") ? user.json.getString("pass") : user.json.getString("key");
				String md5 = Deploy.hash(key + user.salt, "MD5");

				if(hash.equals(md5)) {
					user.authorized = true;
					return "hash|done|" + user.name;
				}
				else {
					users.remove(user.salt);
					return "hash|fail|wrong " + (user.json.has("pass") ? "pass" : "key");
				}
			}
		}

		if(!user.authorized)
			return "main|fail|user not authorized";

		if(data.startsWith("game")) {
			if(!split[2].matches("[a-zA-Z]+"))
				return "game|fail|name invalid";

			Game game = (Game) games.get(split[2]);

			if(game == null) {
				game = new Game(split[2]);
				games.put(split[2], game);
			}

			game.add(user);
			user.move(null, game);
			user.game = game;

			return "game|done";
		}

		if(user.game == null)
			return "main|fail|no game";

		if(data.startsWith("peer")) {
			user.peer(event, split[2]);
			return "peer|done";
		}

		if(data.startsWith("room")) {
			if(user.room.user != null)
				return "room|fail|not in lobby";

			String type = split[2];

			if(!type.matches("[a-zA-Z]+"))
				return "room|fail|type invalid";

			int size = Integer.parseInt(split[3]);

			Room room = new Room(user, type, size);

			user.game.rooms.put(user.name, room);
			user.game.send(user, "made|" + room);
			user.move(user.game, room);

			return "room|done";
		}

		if(data.startsWith("list")) {
			String what = split[2];

			if(what.equals("room")) {
				StringBuilder builder = new StringBuilder("list|done|room");
				Iterator it = user.game.rooms.values().iterator();

				while(it.hasNext()) {
					Room room = (Room) it.next();
					builder.append("|" + room);
				}

				return builder.toString();
			}

			if(what.equals("data")) {
				final String type = split[3];

				final int from = 0;
				final int size = 5;
				final String key = user.json.getString("key");

				Async.Work work = new Async.Work(event) {
					public void send(Async.Call call) throws Exception {
						call.get("/link/user/" + type + "/" + key + "?from=" + from + 
								"&size=" + size, "Host:" + event.query().header("host"));
					}

					public void read(String host, String body) throws Exception {
						StringBuilder builder = new StringBuilder("list|done|data");
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

			return "list|fail|wrong type";
		}

		if(data.startsWith("join")) {
			Room room = (Room) user.game.rooms.get(split[2]);

			if(room == null)
				return "join|fail|not found";

			if(user.room.user != null && user.room.user.name.equals(room.user.name))
				return "join|fail|already here";

			if(room.users.size() == room.size && !room.play)
				return "join|fail|is full";

			user.move(user.room, room);

			return "join|done";
		}

		if(data.startsWith("play")) {
			String seed = split[2];
			
			if(user.room.user == null)
				return "play|fail|in lobby";

			if(user.room.users.size() < 2)
				return "play|fail|only one player";

			if(user.room.user == user)
				user.room.send(user, "head|" + seed);
			else
				return "play|fail|not creator";

			return "play|done";
		}
		
		if(data.startsWith("over")) {
			if(split.length > 2)
				user.room.send(user, "tail|" + user.name + '|' + split[2]);
			else
				user.room.send(user, "tail|" + user.name);

			return "over|done";
		}

		if(data.startsWith("quit")) {
			if(user.room.user == null)
				return "quit|fail|user in lobby";

			Room room = user.move(user.room, user.game);

			if(room != null) {
				user.game.rooms.remove(room.user.name);
				user.game.send(user, "halt|" + user.name);
			}

			return "quit|done";
		}

		if(data.startsWith("save")) {
			if(split[2].length() > 512) {
				return "save|fail|too large";
			}

			final String type = split[1];
			final JSONObject json = new JSONObject(split[3]);
			final String key = json.optString("key");
			final String user_key = user.json.getString("key");

			Async.Work node = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					String data = "json=" + json.toString() + "&type=" + type + 
							"&sort=key" + (key.length() > 0 ? "" : "&create");
					call.post("/node", "Host:" + event.query().header("host"), data.getBytes("utf-8"));
				}

				public void read(String host, final String body) throws Exception {
					final JSONObject node = (JSONObject) new JSONObject(body);

					Async.Work link = new Async.Work(event) {
						public void send(Async.Call call) throws Exception {
							call.post("/link", "Host:" + event.query().header("host"), 
									("pkey=" + user_key + "&ckey=" + node.getString("key") + 
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

		if(data.startsWith("load")) {
			String type = split[1];
			long id = Long.parseLong(split[3]);

			File file = new File(Root.home() + "/node/" + type + "/id" + Root.path(id));

			if(!file.exists()) {
				System.out.println(file);
				event.query().put("fail", "load|fail|not found");
			}

			return "load|done|" + Root.file(file);
		}

		if(data.startsWith("chat")) {
			user.room.send(user, "text|" + user.name + "|" + split[2]);
			return "chat|done";
		}

		if(data.startsWith("send")) {
			user.room.send(user, "sent|" + user.name + "|" + split[2]);
			return "send|done";
		}

		if(data.startsWith("move")) {
			user.room.send(user, "data|" + user.name + "|" + split[2]);
			return "move|done";
		}

		return "main|fail|type not found";
	}

	public static class User {
		String[] ip;
		JSONObject json;
		String name, salt;
		boolean authorized;
		Game game;
		Room room;

		User(String name, String salt) {
			this.name = name;
			this.salt = salt;
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

			if(to != null) {
				this.room = to;

				to.add(this);
				to.send(this, "here|" + name);
			}

			if(from != null) {
				from.remove(this);

				if(from.user != null && from.user.name.equals(name)) {
					from.send(this, "stop|" + name);
					from.clear();

					drop = from;
				}
				else
					from.send(this, "gone|" + name);
			}

			return drop;
		}

		public String toString() {
			return name + "(" + salt + ")";
		}
	}

	public static class Room {
		ConcurrentHashMap users = new ConcurrentHashMap();

		boolean play;
		String type;
		User user;
		int size;

		Room(String type, int size) {
			this.type = type;
			this.size = size;
		}

		Room(User user, String type, int size) {
			this.type = type;
			this.user = user;
			this.size = size;
		}

		void send(User from, String data) throws Exception {
			Iterator it = users.values().iterator();

			if(data.startsWith("head"))
				play = true;

			System.err.println("<-- " + from + " " + data);

			boolean wakeup = false;

			while(it.hasNext()) {
				User user = (User) it.next();

				try {
					// send every user in room to joining user
					if(data.startsWith("here") && !from.name.equals(user.name)) {
						node.push(from.salt, "here|" + user.name + user.peer(from), false);
						wakeup = true;
					}

					// send every user in room to leaving user
					if(data.startsWith("gone") && !from.name.equals(user.name)) {
						node.push(from.salt, "gone|" + user.name + user.peer(from), false);
						wakeup = true;
					}

					// send message from user to room
					if(data.startsWith("text") || data.startsWith("head") || data.startsWith("tail") || !from.name.equals(user.name)) {
						if(data.startsWith("here"))
							data += from.peer(user);
						
						if(data.startsWith("gone") && from.room.user != null)
							data += "|" + from.room.user.name;
						
						node.push(user.salt, data, true);
					}

					// eject everyone
					if(data.startsWith("stop")) {
						user.move(null, user.game);
					}
				}
				catch(Exception e) {
					e.printStackTrace(); // user timeout?
				}
			}

			if(wakeup)
				node.wakeup(from.salt);

			// broadcast stop
			if(data.startsWith("quit")) {
				user.game.send(from, "stop|" + user.name);
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
			String kase = "join";
			
			if(users.size() == size)
				kase = "lock";
			
			if(play)
				kase = "view";
			
			return (user == null ? "lobby" : user.name) + "+" + type + "+" + users.size() + "/" + size + "+" + kase;
		}
	}

	public static class Game extends Room {
		ConcurrentHashMap rooms = new ConcurrentHashMap();

		String name;

		public Game(String name) {
			super(null, "game", 1024);
			this.name = name;
		}
	}

	public void remove(String salt, int place) throws Exception {
		User user = (User) users.get(salt);

		if(user == null)
			System.err.println("remove; salt '" + salt + "' not found (" + place + ").");
		else if(user.salt != null && user.game != null) {
			Room room = user.move(user.room, null);
			user.game.rooms.remove(user.name);
			if(place != 1) {
				user.game.send(user, "kill|" + user.name);
				System.err.println("remove; user '" + user + "' killed (" + place + ").");
			}
			else {
				System.err.println("remove; user '" + user + "' removed (" + place + ").");
			}
			users.remove(salt);
		}
	}

	public void exit() {
		//daemon.remove(this);
	}
}