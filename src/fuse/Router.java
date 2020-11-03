package fuse;

import java.io.File;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import se.rupy.http.Async;
import se.rupy.http.Daemon;
import se.rupy.http.Event;
import se.rupy.http.Output;
import se.rupy.http.Root;
import se.rupy.http.Service;

public class Router implements Node {
	public static boolean debug = true;
	public static String hash = "sha-256";

	public static String data = "root.rupy.se";
    public static String what = "195.67.191.192";

    // change these to localhost:8000 if
    // you are developing with HTML5 on localhost
	public static String fuse = "fuse.rupy.se";//"localhost:8000"; // So .js cross domain connects to the right node; does not work with IE with XDR.
	public static String path = "fuse.rupy.se";//"localhost:8000"; // So the modular .js will load from the right domain.

	public static int time = 30;
	
	public static ConcurrentLinkedDeque score = new ConcurrentLinkedDeque();

	ConcurrentHashMap users = new ConcurrentHashMap();
	ConcurrentHashMap names = new ConcurrentHashMap();
	ConcurrentHashMap games = new ConcurrentHashMap();
	ConcurrentHashMap salts = new ConcurrentHashMap();

	static ConcurrentHashMap stats = new ConcurrentHashMap();
	static Daemon daemon;
	static Node node;

	private static String head() {
		//System.out.println(data);
		return "Head:less\r\nHost:" + data; // Head:less\r\n
	}

	public void call(Daemon daemon, Node node) throws Exception {
		this.daemon = daemon;
		this.node = node;
	}

	private synchronized String session() throws Exception {
		String salt = Event.random(4);

		while(users.get(salt) != null)
			salt = Event.random(4);

		return salt;
	}

	private User session(Event event, String name, String salt) throws Exception {
		User user = new User(name, salt);
		users.put(salt, user);
		names.put(name, user);

		// This uses host.rupy.se specific MaxMind GeoLiteCity.dat
        try {
            JSONObject country = new JSONObject((String) daemon.send(null, "{\"type\":\"country\",\"ip\":\"" + event.remote() + "\"}"));
            if (!country.getString("code").equals("--"))
                user.flag = country.getString("code").toLowerCase();
        }
        catch(Exception e) {}
		// End

		return user;
	}

	public void broadcast(String message, boolean finish) throws Exception {
		throw new Exception("Nope");
	}

	public String push(String salt, String data, boolean wake) throws Exception {
		throw new Exception("Nope");
	}

    public String push(int salt, String data, boolean wake) throws Exception {
        throw new Exception("Nope");
    }

	public int wakeup(String name) { return 0; }

	static SimpleDateFormat date = new SimpleDateFormat("HH:mm:ss.SSS");

	public void salt(Event event, final String name) throws Exception {
		//System.err.println("salt " + event.index());

		Async.Work work = new Async.Work(event) {
			public void send(Async.Call call) throws Exception {
				call.get("/salt", head());
			}

			public void read(String host, String body) throws Exception {
				if(debug)
					System.err.println(body);
				if(users.containsKey(body)) {
					salt(event, name);
				}
				else {
					session(event, name, body);
					event.query().put("done", "salt|done|" + body);
					int wakeup = event.reply().wakeup(true);
					if(debug)
						System.err.println(wakeup);
				}
			}

			public void fail(String host, Exception e) throws Exception {
				e.printStackTrace();
				event.query().put("done", "salt|fail|unknown problem");
				event.reply().wakeup(true);
			}
		};

		event.daemon().client().send(what, work, time);
	}

	public String push(final Event event, String data) throws Event, Exception {
		final String[] split = data.split("\\|");

		if(!split[0].equals("send") && !split[0].equals("move")) {
			if(debug)
				System.err.println(" -> '" + data + "'");
		}

		if(split[0].equals("ping")) {
			return "ping|done";
		}

		if(split[0].equals("time")) {
			return "time|done|" + System.currentTimeMillis(); // TODO: this needs timezone!
		}
		
		if(split[0].equals("user")) {
			final boolean name = split.length > 1 && split[1].length() > 0;
			final boolean pass = split.length > 2 && split[2].length() > 0;
			final boolean mail = split.length > 3 && split[3].length() > 0;

			if(name) {
				if(split[1].length() < 3)
					return "user|fail|name too short";

				if(split[1].length() > 12)
					return "user|fail|name too long";

				if(name && !split[1].matches("[a-zA-Z0-9.\\-]+"))
					return "user|fail|name invalid";

				if(name && split[1].matches("[0-9]+"))
					return "user|fail|name alpha missing"; // [0-9]+ reserved for <id>
			}

			if(pass && split[2].length() < 3)
				return "user|fail|pass too short";

			if(mail && split[3].indexOf("@") < 1 && !split[3].matches("[a-zA-Z0-9.@\\-\\+]+"))
				return "user|fail|mail invalid";

			Async.Work work = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					String json = "{";
					String sort = "";

					boolean add = false;

					if(name) {
						json += "\"name\":\"" + split[1].toLowerCase() + "\"";
						sort += ",name";

						add = true;
					}

					if(pass) {
						if(add)
							json += ",";

						json += "\"pass\":\"" + split[2] + "\"";

						add = true;
					}

					if(mail) {
						if(add)
							json += ",";

						json += "\"mail\":\"" + split[3].toLowerCase() + "\"";
						sort += ",mail";
					}

					json += "}";

					byte[] post = ("json=" + json + "&sort=key" + sort + "&create").getBytes("utf-8");

					call.post("/node", head(), post);
				}

				public void read(String host, String body) throws Exception {
					if(debug)
						System.err.println(body);

					if(body.indexOf("Validation") > 0) {
						String message = body.substring(body.indexOf("[") + 1, body.indexOf("]"));

						if(debug)
							System.err.println("Validation " + message);

						if(message.startsWith("name"))
							event.query().put("fail", "user|fail|name contains bad characters");

						if(message.startsWith("mail"))
							event.query().put("fail", "user|fail|mail contains bad characters");
					}
					else if(body.indexOf("Collision") > 0) {
						String message = body.substring(body.indexOf("[") + 1, body.indexOf("]"));

						if(debug)
							System.err.println("Collision " + message);

						if(message.startsWith("name"))
							event.query().put("fail", "user|fail|name already registered");

						if(message.startsWith("mail"))
							event.query().put("fail", "user|fail|mail already registered");
					}
					else {
						JSONObject json = new JSONObject(body);
						String key = json.getString("key");

						User user = session(event, name ? split[1] : "" + Root.hash(key), session());
						user.auth(json);

						salts.put("" + user.id, user.salt);
						
						event.query().put("done", "user|done|" + user.salt + "|" + key + "|" + Root.hash(key));
					}

					event.reply().wakeup(true);
				}

				public void fail(String host, Exception e) throws Exception {
					e.printStackTrace();
					event.query().put("fail", "user|fail|unknown problem");
					event.reply().wakeup(true);
				}
			};

			event.daemon().client().send(what, work, time);
			throw event;
		}

		if(split[0].equals("salt")) {
			if(split.length < 2)
				return "salt|fail|name not found";

			final String name = split[1].toLowerCase();

			salt(event, name);

			throw event;
		}

		if(split.length < 2)
			return "main|fail|salt not found";

		final User user = (User) users.get(split[1]);

		if(user == null || !user.salt.equals(split[1])) {
			if(debug) {
				System.err.println(split[1]);
				System.err.println(user);
			}
			return "main|fail|salt not found";
		}

		if(split[0].equals("sign")) {
			final String hash = split[2].toLowerCase();

			Async.Work work = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					String body = "name=" + user.name + "&pass=" + hash + "&salt=" + user.salt + "&host=" + Router.data + "&algo=" + Router.hash;
					call.post("/user", head(), body.getBytes());
				}

				public void read(String host, String body) throws Exception {
					if(debug)
						System.err.println(body);
					try {
						JSONObject json = new JSONObject(body);
						user.auth(json);

						salts.put("" + user.id, user.salt);
						
						event.query().put("done", "sign|done|" + user.name);
					}
					catch(Exception e) {
						event.query().put("fail", "sign|fail|" + body);
					}
					int wakeup = event.reply().wakeup(true);
					if(debug)
						System.err.println(wakeup);
				}

				public void fail(String host, Exception e) throws Exception {
					e.printStackTrace();
					event.query().put("fail", "sign|fail|unknown problem");
					event.reply().wakeup(true);
				}
			};

			event.daemon().client().send(what, work, time);
			throw event;
		}

		if(!user.sign)
			return "main|fail|user not authorized";

		if(split[0].equals("game")) {
			if(debug)
				System.err.println("push " + Router.date.format(new Date()) + " " + user.salt + " " + user.name);

			if(!split[2].matches("[a-zA-Z]+"))
				return "game|fail|name invalid";

			Game game = (Game) games.get(split[2].toLowerCase());

			if(game == null) {
				game = new Game(split[2].toLowerCase());
				games.put(split[2], game);
			}

			user.game = game;
			user.move(null, game, true);

			// add this user and users in other games to each other

			Iterator it = users.values().iterator();

			while(it.hasNext()) {
				User u = (User) it.next();

				if(user.game != null && u.game != null && user.game.name != u.game.name && user.name != u.name) {
					node.push(u.salt, "here|root|" + user.name, true);
					node.push(user.salt, "here|root|" + u.name, false);
				}
			}

			node.wakeup(user.salt);

			// add this user to users in same game but in rooms

			it = user.game.rooms.values().iterator();

			while(it.hasNext()) {
				Room r = (Room) it.next();
				r.send(user, "here|stem|" + user.name, true);
			}

			return "game|done";
		}

		if(user.game == null)
			return "main|fail|no game";

		if(split[0].equals("name") || split[0].equals("nick") || split[0].equals("pass") || split[0].equals("mail")) {
			File file = null;
			final String rule = split[0];
			boolean id = split[2].matches("[0-9]+");

			if(id) {
				file = new File(Root.home() + "/node/user/id" + Root.path(Long.parseLong(split[2])));

				if(file == null || !file.exists()) {
					return rule + "|fail|not found";
				}

				JSONObject json = new JSONObject(Root.file(file));

				try {
					return rule + "|done|" + json.getString(rule);
				}
				catch(JSONException e) {
					return rule + "|fail|not found";
				}
			}
			else {
				if(rule.equals("mail") && split[2].indexOf("@") < 1 && !split[2].matches("[a-zA-Z0-9.@\\-\\+]+"))
					return "mail|fail|mail invalid";

				if((rule.equals("name") || rule.equals("nick")) && !split[2].matches("[a-zA-Z0-9.\\-]+"))
					return rule + "|fail|" + rule + " invalid";

				if(rule.equals("name") && split[2].matches("[0-9]+"))
					return "name|fail|name alpha missing"; // [0-9]+ reserved for <id>

				if(rule.equals("name"))
					user.json.put("name", split[2].toLowerCase());
				else if(rule.equals("nick"))
					user.json.put("nick", split[2]);
				else if(rule.equals("pass"))
					user.json.put("pass", split[2]);
				else
					user.json.put("mail", split[2]);

				final String json = user.json.toString();

				Async.Work work = new Async.Work(event) {
					public void send(Async.Call call) throws Exception {
						String sort = "";

						if(rule.equals("name"))
							sort = "&sort=name";

						byte[] post = ("json=" + json + sort).getBytes("utf-8");

						call.post("/node", head(), post);
					}

					public void read(String host, String body) throws Exception {
						if(debug)
							System.err.println(body);

						if(body.indexOf("Collision") > 0) {
							String message = body.substring(body.indexOf("[") + 1, body.indexOf("]"));

							if(debug)
								System.err.println("Collision " + message);

							if(message.startsWith("name"))
								event.query().put("fail", "name|fail|taken");
						}
						else
							event.query().put("done", rule + "|done");

						event.reply().wakeup(true);
					}

					public void fail(String host, Exception e) throws Exception {
						e.printStackTrace();
						event.query().put("fail", rule + "|fail|unknown problem");
						event.reply().wakeup(true);
					}
				};

				event.daemon().client().send(what, work, time);
				throw event;
			}
		}
		else if(split[0].equals("away")) {
			if(!user.room.away() && user.room.play)
				user.room.send(user, "hold", true);

			user.away = true;
			user.room.send(user, "away|" + user.name);

			return "away|done";
		}
		else if(split[0].equals("back")) {
			user.away = false;
			user.room.send(user, "back|" + user.name);

			if(!user.room.away() && user.room.play)
				user.room.send(user, "free", true);

			return "back|done";
		}
		else if(split[0].equals("ally")) {
			final User poll = (User) names.get(split[2]);
			String info = split.length > 3 ? "|" + split[3] : "";
			
			if(poll == null) {
				return "ally|fail|user " + split[2] + " not online";
			}

			if(user.ally(poll)) {
				Async.Work work = new Async.Work(event) {
					public void send(Async.Call call) throws Exception {
						call.post("/meta", head(), 
								("pkey=" + user.json.getString("key") + "&ckey=" + poll.json.getString("key") + 
										"&ptype=user&ctype=user&json={}&tear=true").getBytes("utf-8"));
					}

					public void read(String host, String body) throws Exception {
						if(debug)
							System.err.println("fuse ally tear read " + body);
						if(body.equals("1")) {
							user.remove(poll.name);
							poll.remove(user.name);
						}
						event.query().put("done", "ally|done");
						event.reply().wakeup(true);
					}

					public void fail(String host, Exception e) throws Exception {
						if(debug)
							System.err.println("fuse ally tear fail " + e);
						event.query().put("fail", "ally|fail|unknown problem");
						event.reply().wakeup(true);
					}
				};

				event.daemon().client().send(what, work, time);
				throw event;
			}

			boolean game = user.room instanceof Game;

			if(!game)
				return "ally|fail|user busy";

			node.push(poll.salt, "poll|ally|" + user.name + info, true);

			poll.poll = user.name;
			user.poll = poll.name;
			user.type = poll.type = "ally";

			return "ally|done|poll";
		}
		else if(split[0].equals("peer")) {
			user.peer(event, split[2]);
			return "peer|done";
		}
		else if(split[0].equals("room")) {
			if(user.room.user != null)
				return "room|fail|not in lobby";

			String type = split[2];

			if(!type.matches("[a-zA-Z]+"))
				return "room|fail|type invalid";

			int size = Integer.parseInt(split[3]);

			Room room = new Room(user, type, size);

			user.game.rooms.put(user.name, room);
			user.game.send(user, "room|" + room);
			user.move(user.game, room, true);

			return "room|done";
		}
		else if(split[0].equals("list")) {
			final String list = split[2];
			String type = null;
			
			if(split.length > 3)
				type = split[3];
			
			if(list.equals("room")) {
				if(type == null) {
					StringBuilder builder = new StringBuilder("list|done|room|");
					Iterator it = user.game.rooms.values().iterator();

					while(it.hasNext()) {
						Room room = (Room) it.next();
						builder.append(room);

						if(it.hasNext()) {
							builder.append(";");
						}
					}

					return builder.toString();
				}
				else {
					StringBuilder builder = new StringBuilder("list|done|room|item|");
					Iterator it = user.room.items.values().iterator();
					boolean first = true;

					while(it.hasNext()) {
						Item item = (Item) it.next();

						if(first) {
							first = false;
						}
						else {
							builder.append(";");
						}

						builder.append(item);
					}

					return builder.toString();
				}
			}

			if(list.equals("user")) {
				if(split.length > 3)
					type = split[3];
				else
					return "list|fail|type missing";

				if(type.equals("soft")) {
					if(user.soft != null)
						return user.list(type, user.soft.getJSONArray("list"));
					else
						return split[0] + "|done|user|soft|";
				}

				final String key = user.json.getString("key");
				final String t = type;

				Async.Work work = new Async.Work(event) {
					public void send(Async.Call call) throws Exception {
						call.get("/meta/user/" + t + "/" + user.name + "?sort=1", head());
					}

					public void read(String host, String body) throws Exception {
						if(debug)
							System.err.println(body);
						try {
							JSONArray l = null;

							if(t.equals("hard")) {
								user.hard = (JSONObject) new JSONObject(body);
								l = user.hard.getJSONArray("list");
							}
							else {
								user.item = (JSONObject) new JSONObject(body);
								l = user.item.getJSONArray("list");
							}

							event.query().put("done", user.list(t, l));
						}
						catch(Exception e) {
							event.query().put("fail", "list|fail|not found");
						}

						event.reply().wakeup(true);
					}

					public void fail(String host, Exception e) throws Exception {
						if(debug)
							System.err.println("list user " + e);
						event.query().put("fail", "list|fail|unknown problem");
						event.reply().wakeup(true);
					}
				};

				event.daemon().client().send(what, work, time);
				throw event;
			}

			return "list|fail|wrong type (" + list + ")";
		}
		else if(split[0].equals("join")) {
			Room room = (Room) user.game.rooms.get(split[2]);
			String info = split.length > 3 ? "|" + split[3] : "";

			if(room == null) {
				boolean game = user.room instanceof Game;

				if(!game && user.room.users.size() == user.room.size && !user.room.play)
					return "join|fail|is full";

				User poll = (User) names.get(split[2]);
System.out.println(poll + " " + names);
				boolean poll_game = poll.room instanceof Game;

				if(poll != null && user.game.name.equals(poll.game.name)) {
					if(!game || !poll_game)
						return "join|fail|user busy";

					node.push(poll.salt, "poll|join|" + user.name + "|" + info, true);

					poll.poll = user.name;
					user.poll = poll.name;
					user.type = poll.type = "join";

					return "join|done|poll";
				}
			}

			if(room == null)
				return "join|fail|not found";

			if(user.room.user != null && user.room.user.name.equals(room.user.name))
				return "join|fail|already here";

			if(room.users.size() == room.size && !room.play)
				return "join|fail|is full";

			user.move(user.room, room, true);

			if(room.users.size() == room.size)
				user.game.send(user, "lock|" + user.room.user.name);

			return "join|done|room";
		}
		else if(split[0].equals("poll")) {
			String type = user.type;

			if(debug)
				System.err.println(split[2] + " " + names);

			final User poll = (User) names.get(split[2]);
			boolean accept = split[3].toLowerCase().equals("true");

			if(poll == null)
				return "poll|fail|user not online";

			if(user.poll == null || !user.poll.equals(poll.name))
				return "poll|fail|wrong user";

			if(accept) {
				if(type.equals("join")) {
					boolean game = poll.room instanceof Game;
					Room room = null;

					if(!game)
						room = poll.room;
					else {
						room = new Room(poll, "duel", 2);
						user.game.rooms.put(poll.name, room);
						poll.move(poll.room, room, false);
					}

					user.move(user.game, room, true);

					if(game)
						user.game.send(user, "room|" + room);
				}
				else if(type.equals("ally")) {
					Async.Work work = new Async.Work(event) {
						public void send(Async.Call call) throws Exception {
							call.post("/meta", head(), 
									("pkey=" + user.json.getString("key") + "&ckey=" + poll.json.getString("key") + 
											"&ptype=user&ctype=user&json={}&echo=true").getBytes("utf-8"));
						}

						public void read(String host, String body) throws Exception {
							if(debug)
								System.err.println("fuse ally read " + body);
							if(body.equals("1")) {
								user.add(poll.name);
								poll.add(user.name);
							}
							event.query().put("done", "ally|done");
							event.reply().wakeup(true);
						}

						public void fail(String host, Exception e) throws Exception {
							if(debug)
								System.err.println("fuse ally fail " + e);
							event.query().put("fail", "ally|fail|unknown problem");
							event.reply().wakeup(true);
						}
					};

					event.daemon().client().send(what, work, time);
					throw event;
				}
				else
					return "poll|fail|type not found";
			}

			user.poll = null;
			poll.poll = null;
			user.type = poll.type = null;

			return "poll|done|" + type;
		}
		else if(split[0].equals("play")) {
			String seed = split[2];

			if(user.room.away())
				return "play|fail|someone is away";

			if(user.room.play)
				return "play|fail|already playing";

			if(user.room.user == null)
				return "play|fail|in lobby";

			if(user.room.users.size() < 2 && !user.room.type.equals("item"))
				return "play|fail|only one player";

			if(user.room.user == user)
				user.room.send(user, "play|" + seed, true);
			else
				return "play|fail|not creator";

			user.game.send(user, "view|" + user.room.user.name);

			return "play|done";
		}
		else if(split[0].equals("over")) {
			if(!user.room.play)
				return "over|fail|not playing";

			if(split.length > 2)
				user.room.send(user, "over|" + user.name + '|' + split[2], true);
			else
				user.room.send(user, "over|" + user.name, true);

			user.lost++;

			//Iterator it = user.room.users.values().iterator();
            Iterator it = user.room.users.iterator();

			while(it.hasNext()) {
				User other = (User) it.next();

				if(user.salt != other.salt) {
					StringBuilder builder = new StringBuilder();

					//if(user.flag != null)
					//	builder.append("<img style=\"display: inline;\" width=\"18\" height=\"18\" src=\"http://host.rupy.se/flag/" + user.flag + ".svg\">");
//builder.append("<div class=\"parent\"><img class=\"animal\" width=\"32\" height=\"32\" src=\"svg/animal/penguin.svg\"><img class=\"flag\" width=\"14\" height=\"14\" src=\"http://host.rupy.se/flag/" + user.flag + ".svg\"></div>&nbsp;");
					builder.append("<font color=\"#ff3300\">" + user.name + "(" + user.lost + ")</font> <font color=\"#ffffff\">vs.</font> ");

					//if(other.flag != null)
					//	builder.append("<img style=\"display: inline;\" width=\"18\" height=\"18\" src=\"http://host.rupy.se/flag/" + other.flag + ".svg\">");
//builder.append("<div class=\"parent\"><img class=\"animal\" width=\"32\" height=\"32\" src=\"svg/animal/cat.svg\"><img class=\"flag\" width=\"14\" height=\"14\" src=\"http://host.rupy.se/flag/" + other.flag + ".svg\"></div>&nbsp;");
					builder.append("<font color=\"#00cc33\">" + other.name + "</font>");

					score.add(builder.toString());

					if(score.size() > 3) {
						score.poll();
					}
				}
			}

			return "over|done";
		}
		else if(split[0].equals("exit")) {
			//if(user.room.user == null)
			//	return "exit|fail|in lobby";

			Room room = user.room;
			boolean full = room.users.size() == room.size;
			Room drop = user.move(user.room, user.game, false);

			if(drop != null) {
				user.game.rooms.remove(drop.user.name);
				user.game.send(user, "drop|" + drop.user.name);
			}
			else if(full)
				user.game.send(user, "open|" + room.user.name);

			user.game.wakeup();

			return "exit|done";
		}
		else if(split[0].equals("drop")) {
			final String name = split[2];
			int many = Integer.parseInt(split[3]);

			JSONObject json = user.data(user.item, name);

			if(json == null)
				return "drop|fail|not found";

			int count = json.optInt("count");

			if(count < many)
				return "drop|fail|not enough";

			Item temp = new Item(name, many);
			temp.position(user.position);
			final Item item = user.room.add(temp);
			json.put("count", count - many);
			final String save = json.toString();

			Async.Work work = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					call.post("/meta", head(), 
							("pkey=" + user.json.getString("key") + "&ckey=" + name + 
									"&ptype=user&ctype=item&json=" + save).getBytes("utf-8"));
				}

				public void read(String host, String body) throws Exception {
					if(debug)
						System.err.println(split[0] + " " + body);
					user.room.send(user, "item|" + item, true);
					event.query().put("done", split[0] + "|done|" + item.salt);
					event.reply().wakeup(true);
				}

				public void fail(String host, Exception e) throws Exception {
					if(debug)
						System.err.println(split[0] + " " + e);
					event.query().put("fail", split[0] + "|fail|unknown problem");
					event.reply().wakeup(true);
				}
			};

			event.daemon().client().send(what, work, time);
			throw event;
		}
		else if(split[0].equals("pick")) {
			String salt = split[2];

			final Item item = user.room.item(salt);

			if(item == null)
				return "pick|fail|not found";

			JSONObject json = user.data(user.item, item.name);

			if(json == null) {
				json = new JSONObject("{count: " + item.count + "}");
				JSONArray array = user.item.getJSONArray("list");
				JSONObject object = new JSONObject();
				object.put(item.name, json);
				array.put(object);
			}
			else
				json.put("count", json.optInt("count") + item.count);

			final String save = json.toString();

			Async.Work work = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					call.post("/meta", head(), 
							("pkey=" + user.json.getString("key") + "&ckey=" + item.name + 
									"&ptype=user&ctype=item&json=" + save).getBytes("utf-8"));
				}

				public void read(String host, String body) throws Exception {
					if(debug)
						System.err.println(split[0] + " " + body);
					user.room.send(user, "pick|" + user.name + "|" + item.salt, true);
					event.query().put("done", split[0] + "|done|" + item.salt);
					event.reply().wakeup(true);
				}

				public void fail(String host, Exception e) throws Exception {
					if(debug)
						System.err.println(split[0] + " " + e);
					event.query().put("fail", split[0] + "|fail|unknown problem");
					event.reply().wakeup(true);
				}
			};

			event.daemon().client().send(what, work, time);
			throw event;
		}
		else if(split[0].equals("save") || split[0].equals("tear")) {
			final boolean tear = split[0].equals("tear");

			if(split[2].length() < 3) {
				return split[0] + "|fail|name too short";
			}

			if(split[2].length() > 64) {
				return split[0] + "|fail|name too long";
			}

			final String name = split[2];
			final JSONObject json = tear ? null : new JSONObject(split[3]);
			final String type = tear ? split[3] : split.length > 4 ? split[4] : "hard";

			if(type.equals("soft")) {
				JSONObject soft = user.data(user.soft, name);

				if(soft != null) {
					user.soft = user.data(user.soft, name, json);
				}
				else if(!tear) {
					if(user.soft == null)
						user.soft = new JSONObject("{\"total\": 0, \"list\": []}");
					JSONArray list = user.soft.getJSONArray("list");
					list.put(new JSONObject("{\"" + name + "\": " + json + "}"));
				}

				return split[0] + "|done";
			}

			Async.Work work = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					call.post("/meta", head(), 
							("pkey=" + user.json.getString("key") + "&ckey=" + name + 
									"&ptype=user&ctype=" + type + (tear ? "&tear=true" : "&json=" + json)).getBytes("utf-8"));
				}

				public void read(String host, String body) throws Exception {
					if(debug)
						System.err.println(split[0] + " " + body);
					JSONObject save = type.equals("hard") ? user.hard : user.item;
					JSONObject data = user.data(save, name);
					if(data != null)
						save = user.data(save, name, json);
					event.query().put("done", split[0] + "|done");
					event.reply().wakeup(true);
				}

				public void fail(String host, Exception e) throws Exception {
					if(debug)
						System.err.println(split[0] + " " + e);
					event.query().put("fail", split[0] + "|fail|unknown problem");
					event.reply().wakeup(true);
				}
			};

			event.daemon().client().send(what, work, time);
			throw event;
		}
		else if(split[0].equals("load") || split[0].equals("hard") || split[0].equals("item") || split[0].equals("soft")) {
			boolean load = split[0].equals("load");

			final String base = load ? user.name : split[2];
            //final String base = split[2]; // jonas

			final String name = load ? split[2] : split[3];
			final String type = load && split.length > 3 ? split[3] : split[0];

			System.out.println(data);
			System.out.println(load + " " + base + " " + name + " " + type);
			
			if(type.equals("soft")) {
				String salt = null;
				
				if(!split[0].equals("load")) {
					salt = (String) salts.get(split[2]);
				
					if(salt == null) {
						return split[0] + "|fail|id not found";
					}
				}
				else {
					salt = split[1];
				}

				User target = (User) users.get(salt);
				
				if(target == null) {
					return split[0] + "|fail|salt not found";
				}

				JSONObject soft = target.data(target.soft, name);
				
				if(soft != null)
					return split[0] + "|done|" + soft;
				else
					return split[0] + "|fail|not found";
			}

			Async.Work work = new Async.Work(event) {
				public void send(Async.Call call) throws Exception {
					call.get("/meta/user/" + type + "/" + base + "/" + URLEncoder.encode(name, "UTF-8"), head());
				}

				public void read(String host, String body) throws Exception {
					try {
						JSONObject json = new JSONObject(body);
						event.query().put("done", split[0] + "|done|" + body);
					}
					catch(Exception e) {
						event.query().put("fail", split[0] + "|fail|" + body);
					}

					event.reply().wakeup(true);
				}

				public void fail(String host, Exception e) throws Exception {
					e.printStackTrace();
					event.query().put("fail", split[0] + "|fail|unknown problem");
					event.reply().wakeup(true);
				}
			};

			event.daemon().client().send(what, work, time);
			throw event;
		}
		else if(split[0].equals("chat")) {
			String tree = split[2];
			boolean game = user.room instanceof Game;

			if(split.length > 3) {
				if(tree.equals("root"))
					broadcast(user, "chat|root|" + user.name + "|" + split[3], true);

				if(tree.equals("root") || tree.equals("stem"))
					user.game.send(user, "chat|stem|" + user.name + "|" + split[3], true);

				if((tree.equals("root") || tree.equals("stem") || tree.equals("leaf")) && !game)
					user.room.send(user, "chat|leaf|" + user.name + "|" + split[3], true);
			}

			return "chat|done";
		}
		else if(split[0].equals("send")) {
			user.room.send(user, "send|" + user.name + "|" + split[2]);
			return "send|done";
		}
		else if(split[0].equals("show")) {
			user.room.send(user, "show|" + user.name + "|" + split[2], true);

			String[] show = split[2].split(",");

			if(show[0].equals("ball")) {
				Item item = (Item) user.game.items.get(show[1]);

				if(item.position == null)
					item.position = new Position();
				
				item.position.x = Float.parseFloat(show[2]);
				item.position.y = Float.parseFloat(show[3]);
				item.position.z = Float.parseFloat(show[4]);

				if(item.velocity == null)
					item.velocity = new Velocity();

				item.velocity.x = Float.parseFloat(show[5]);
				item.velocity.y = Float.parseFloat(show[6]);
				item.velocity.z = Float.parseFloat(show[7]);
			}

			return "show|done";
		}
		else if(split[0].equals("move")) {
			user.room.send(user, "move|" + user.name + "|" + split[2]);
			
			String[] all = split[2].split(";");
			String[] pos = all[0].split(",");
			
			if(user.position == null)
				user.position = new Position();
			
			user.position.x = Float.parseFloat(pos[0]);
			user.position.y = Float.parseFloat(pos[1]);
			user.position.z = Float.parseFloat(pos[2]);
			
			String[] rot = all[1].split(",");
			
			if(user.rotation == null)
				user.rotation = new Rotation();
			
			user.rotation.x = Float.parseFloat(rot[0]);
			user.rotation.y = Float.parseFloat(rot[1]);
			user.rotation.z = Float.parseFloat(rot[2]);
			user.rotation.w = Float.parseFloat(rot[3]);
			
			user.action = all[2];
			
			return "move|done";	
		}

		return "main|fail|rule not found";
	}

	public static class Part {
		String salt;
		String name;
		Position position;
		Rotation rotation;
		Velocity velocity;
	}

	public static class Position {
		float x, y, z;
		
		public String toString() {
			return x + "," + y + "," + z;
		}
	}
	
	public static class Rotation {
		float x, y, z, w;
		
		public String toString() {
			return x + "," + y + "," + z + "," + w;
		}
	}
	
	public static class Velocity {
		float x, y, z;
		
		public String toString() {
			return x + "," + y + "," + z;
		}
	}
	
	public static class Item extends Part {
		int id;
		int count;
		String data;
		
		public Item(String name, String data) {
			this.name = name;
			this.data = data;
		}

		public Item(String name, int data) {
			this.name = name;
			this.count = data;
			this.data = "" + data;
		}
		
		public void position(Position pos) {
			if(pos == null)
				pos = new Position();
			
			if(position == null)
				position = new Position();
			
			position.x = pos.x;
			position.y = pos.y;
			position.z = pos.z;
		}
		
		public void rotation(Rotation rot) {
			if(rot == null)
				rot = new Rotation();
			
			if(rotation == null)
				rotation = new Rotation();
			
			rotation.x = rot.x;
			rotation.y = rot.y;
			rotation.z = rot.z;
			rotation.w = rot.w;
		}
		
		public void velocity(Velocity vel) {
			if(vel == null)
				vel = new Velocity();
			
			if(velocity == null)
				velocity = new Velocity();
			
			velocity.x = vel.x;
			velocity.y = vel.y;
			velocity.z = vel.z;
		}

		public Item clone() {
			Item item = new Item(this.name, this.count);
			item.position = position;
			if(rotation != null)
				item.rotation = rotation;
			if(velocity != null)
				item.velocity = velocity;
			return item;
		}
		
		public String toString() {
			if(velocity != null && rotation != null)
				return salt + "," + name + "," + data + "," + position + "," + rotation + "," + velocity; // 13
			else if(rotation != null)
				return salt + "," + name + "," + data + "," + position + "," + rotation; // 10
			else if(velocity != null)
				return salt + "," + name + "," + data + "," + position + "," + velocity; // 9
			else
				return salt + "," + name + "," + data + "," + position; // 6
		}
	}

	public static class Game extends Room {
		ConcurrentHashMap rooms = new ConcurrentHashMap();
		String name;

		public Game(String name) {
			super((User) null, "game", 1024);
			this.name = name;
		}
	}

	public static class User extends Part {
		String[] ip;
		String action; // what is the user doing?
		JSONObject json, hard, item, soft;
		LinkedList ally = new LinkedList();
		String nick, flag, poll, type;
		boolean sign, away;
		Game game;
		Room room;
		int lost, intsalt;
		long id;

		User(String name, String salt) throws Exception {
			this.name = name;
			this.salt = salt;
			this.intsalt = salt(salt);
            // 0 is the lowest ASCII with 48.
            // z is the highest with 122.
			// Lowest "0000" for cache-friendly forward pointer is 808.464.432
            // Highest "zzzz" is 2.054.847.098 which does not overflow.
            // Limit for 4 character base-58 is 58^4 = 11.316.496

            // System.out.println(salt("0000"));
            // System.out.println(salt("zzzz"));
        }

		public static int salt(String salt) {
            return salt.charAt(0) << 24 |
                  (salt.charAt(1) & 0xFF) << 16 |
                  (salt.charAt(2) & 0xFF) << 8 |
                  (salt.charAt(3) & 0xFF);
        }

        public static String salt(int salt) {
            return "" + (byte) (salt >> 24) +
                        (byte) (salt >> 16) +
                        (byte) (salt >> 8) +
                        (byte) salt;
        }

		private void auth(JSONObject json) throws Exception {
			this.json = json;
			this.id = Root.hash(json.getString("key"));
			this.sign = true;

			try {
				ally();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		JSONObject data(JSONObject type, String name, JSONObject json) throws Exception {
			if(type == null)
				return type;

			JSONArray list = type.getJSONArray("list");

			if(json == null) {
				JSONArray a = new JSONArray();
				
				for(int i = 0; i < list.length(); i++) {
					JSONObject item = list.getJSONObject(i);
					String[] names = JSONObject.getNames(item);

					if(!names[0].equals(name)) {
						a.put(new JSONObject("{\"" + names[0] + "\": " + item.getJSONObject(names[0]) + "}"));
					}
				}
				
				type.put("list", a);
			}
			else {
				for(int i = 0; i < list.length(); i++) {
					JSONObject item = list.getJSONObject(i);
					String[] names = JSONObject.getNames(item);

					if(names[0].equals(name)) {
						list.put(i, new JSONObject("{\"" + name + "\": " + json + "}"));
					}
				}
			}
			
			return type;
		}

		JSONObject data(JSONObject type, String name) throws Exception {
			if(type == null)
				return null;

			JSONArray list = type.getJSONArray("list");

			for(int i = 0; i < list.length(); i++) {
				JSONObject item = list.getJSONObject(i);
				String[] names = JSONObject.getNames(item);

				if(names[0].equals(name)) {
					return item.getJSONObject(names[0]);
				}
			}

			return null;
		}

		String list(String type, JSONArray list) throws Exception {
			StringBuilder builder = new StringBuilder("list|done|user|" + type + "|");

			for(int i = 0; i < list.length(); i++) {
				JSONObject json = list.getJSONObject(i);
				String[] name = JSONObject.getNames(json);
				JSONObject item = json.getJSONObject(name[0]);

				if(type.equals("item"))
					builder.append(name[0] + "," + item.optInt("count"));
				else
					builder.append(name[0] + "," + item.toString().length());

				if(i < list.length() - 1)
					builder.append(";");
			}

			return builder.toString();
		}

		private void ally() throws Exception {
			Async.Work work = new Async.Work(null) {
				public void send(Async.Call call) throws Exception {
					call.get("/meta/user/user/" + json.getString("key"), head());
				}

				public void read(String host, String body) throws Exception {
					try {
						JSONObject json = new JSONObject(body);
						JSONArray list = json.getJSONArray("list");

						for(int i = 0; i < list.length(); i++) {
							String name = JSONObject.getNames(list.getJSONObject(i))[0];
							ally.addFirst(name);
						}
					}
					catch(JSONException e) {}
				}

				public void fail(String host, Exception e) throws Exception {
					e.printStackTrace();
				}
			};

			daemon.client().send(what, work, time);
		}

		void peer(Event event, String ip) {
			this.ip = new String[2];
			this.ip[0] = ip;
			this.ip[1] = event.remote();
		}

		void add(String ally) {
			this.ally.add(ally);
		}

		void remove(String ally) {
			this.ally.remove(ally);
		}

		boolean ally(User user) {
			return ally.contains(user.name);
		}

		String peer(User other) {
			if(ip != null)
				if(other.ip != null && ip[1].equals(other.ip[1]))
					return "|" + ip[0];
				else
					return "|" + ip[1];

			return "";
		}

		Room move(Room from, Room to, boolean wakeup) throws Exception {
			Room drop = null;
			boolean to_game = to instanceof Game;
			boolean from_game = from instanceof Game;

			if(to != null) {
				this.room = to;

				to.send(this, "here|" + (to_game ? "stem" : "leaf") + "|" + name);
				to.add(this);
			}

			if(from != null) {
				from.remove(this);

				if(from.user != null && from.user.name.equals(name)) {
					from.send(this, "stop|" + name);
					from.clear();

					drop = from;
				}
				else
					from.send(this, "gone|" + (from_game ? "stem": "leaf") + "|" + name);
			}

			if(wakeup) {
				if(to != null)
					to.wakeup();

				if(from != null)
					from.wakeup();
			}

			lost = 0;

			return drop;
		}

		public String toString() {
			return name + "(" + salt + ")";
		}
	}

	public static class Room {
        CopyOnWriteArrayList users = new CopyOnWriteArrayList();
        ConcurrentHashMap items = new ConcurrentHashMap();
        int[] salts; // To try and avoid cache misses in the loop for move packets.

		boolean play;
		String name, type;
		User user;
		int size;

		Room(String type, int size) {
			this((User) null, type, size);
		}

		Room(User user, String type, int size) {
			this.user = user;
			this.type = type;
			this.size = size;
			salts = new int[size];
			for(int i = 0; i < salts.length; i++)
			    salts[i] = -1;
		}
		
		Room(String name, String type, int size) {
			this.name = name;
			this.type = type;
			this.size = size;
            salts = new int[size];
            for(int i = 0; i < salts.length; i++)
                salts[i] = -1;
		}
		
		synchronized Item add(Item item) throws Exception {
			String salt = Event.random(4);

			while(items.get(salt) != null)
				salt = Event.random(4);

			item.salt = salt;

			items.put(salt, item);

			return item;
		}

		private synchronized Item item(String salt) {
			return (Item) items.remove(salt);
		}

		boolean away() {
            Iterator it = users.iterator();

			while(it.hasNext()) {
				User user = (User) it.next();

				if(user.away)
					return true;
			}

			return false;
		}

		public void wakeup() throws Exception {
            Iterator it = users.iterator();

			while(it.hasNext()) {
				User u = (User) it.next();
				node.wakeup(u.salt);
			}
		}

		void send(User from, String data) throws Exception {
			send(from, data, false);
		}

		void send(User from, String data, boolean all) throws Exception {
            /* Cache-misses!
			 * Compacting from the end.
			 * TODO: Add forward pointer.
			 */
            if(data.startsWith("move") || data.startsWith("send")) {
                for(int i = 0; i < salts.length; i++) {
                    if(salts[i] == -1) return;
                    if(salts[i] > 0 && salts[i] != from.intsalt)
                        node.push(salts[i], data, true);
                }

                return;
            }

			if(data.startsWith("play"))
				play = true;

			if(data.startsWith("over"))
				play = false;

			if(data.startsWith("here"))
				if(debug)
					System.err.println(users);

			boolean wakeup = false;

            Iterator it = users.iterator();

			while(it.hasNext()) {
				User user = (User) it.next();

				try {
					boolean user_game = user.room instanceof Game;
					boolean from_game = from == null ? false : from.room instanceof Game;

					// send every user in room to joining user

					if(data.startsWith("here") && !from.name.equals(user.name)) {
						node.push(from.salt, "here|" + (user_game || from_game ? "stem" : "leaf") + "|" + user.name + user.peer(from), false);

						if(from.ally(user))
							node.push(from.salt, "ally|" + user.name, false);

						if(user.away)
							node.push(from.salt, "away|" + user.name, false);

						if(user.action != null)
							node.push(from.salt, "move|" + user.name + "|" + user.position + ";" + user.rotation + ";" + user.action, false);
					}

					// send every user in room to leaving user

					if(data.startsWith("gone") && !from.name.equals(user.name)) {
						node.push(from.salt, "gone|" + (user_game || from_game ? "stem" : "leaf") + "|" + user.name + user.peer(from), false);
					}

					// send message from user to room

					if(all || (from == null || !from.name.equals(user.name))) {
						if(data.startsWith("here")) {
							node.push(user.salt, data + from.peer(user), false);

							if(user.ally(from))
								node.push(user.salt, "ally|" + from.name, false);
						}
						else if(data.startsWith("gone") && from.room.user != null) {
							node.push(user.salt, data + "|" + from.room.user.name, false);
						}
						else if(data.startsWith("drop")) {
							node.push(user.salt, data, false);
						}
						else {
							node.push(user.salt, data, true);
						}
					}

					// eject everyone

					if(data.startsWith("stop")) {
						user.move(null, user.game, false);
					}
				}
				catch(Exception e) {
					e.printStackTrace(); // user timeout?
				}
			}

			// broadcast stop

			if(data.startsWith("exit")) {
				user.game.send(from, "stop|" + user.name);
			}
		}

		void clear() {
			users.clear();
		}

        void add(User user) {
            users.add(user);

            synchronized(salts) {
                for (int i = 0; i < salts.length; i++) {
                    if (salts[i] < 1) {
                        salts[i] = User.salt(user.salt);
                        return;
                    }
                }
            }
		}

        void remove(User user) {
            users.remove(user);

            synchronized(salts) {
                for (int i = 0; i < salts.length; i++) {
                    if (salts[i] == user.intsalt) {
                        if (i < salts.length - 1 && salts[i + 1] == -1)
                            salts[i] = -1;
                        else {
                            // TODO: Add forward pointer.
                            salts[i] = 0;
                        }
                        return;
                    }
                }
            }
		}

		public String toString() {
			return (user == null ? name == null ? "lobby" : name : user.name) + "," + type + "," + users.size() + "," + size;
		}
	}

	static void add(Event event, String data, boolean error) {
		String rule = data.substring(0, 4);
		boolean fail = data.substring(5, 9).equals("fail");

		Stat stat = (Stat) stats.get(rule);

		if(stat == null) {
			stat = new Stat();
			stats.put(rule, stat);
		}

		long time = System.currentTimeMillis() - event.big("time");

		if(time > 50) {
			if(debug)
				System.err.println(rule + " " + time);
		}

		if(error)
			stat.error++;

		if(fail)
			stat.fail++;

		stat.count++;
		stat.total += time;
		stat.min = (int) (time < stat.min ? time : stat.min);
		stat.max = (int) (time > stat.max ? time : stat.max);
	}

	public static class Warn extends Service {
		public String path() { return "/warn"; }
		public void filter(Event event) throws Event, Exception {
			event.query().parse();
			String text = event.string("text");
			String secret = event.string("secret");

			if(secret.equals("1234")) {
				node.broadcast("warn|none|" + text, false);
				event.output().print("OK");
			}
			else
				event.output().print("KO");
		}
	}

	public static class Stat {
		long total;
		int count;
		int error;
		int fail;
		int min = Integer.MAX_VALUE;
		int max;
	}

	public static class Data extends Service {
		static DecimalFormat decimal;

		static {
			decimal = (DecimalFormat) DecimalFormat.getInstance();
			decimal.applyPattern("#.#");
		}

		public String path() { return "/data"; }
		public void filter(Event event) throws Event, Exception {
			Iterator it = stats.keySet().iterator();
			Output out = event.output();

			event.query().parse();

			if(event.bit("clear")) {
				stats = new ConcurrentHashMap();
				out.println("Stats cleared!");
				throw event;
			}

			out.println("<pre>");
			out.println("<table>");
			out.println("<tr><td>rule&nbsp;</td><td>avg.&nbsp;</td><td>min.&nbsp;</td><td>max.&nbsp;</td><td></td><td>num.&nbsp;</td><td>fail&nbsp;</td><td>err.&nbsp;</td></tr>");
			out.println("<tr><td colspan=\"8\" bgcolor=\"#000000\"></td></tr>");

			while(it.hasNext()) {
				String name = (String) it.next();
				Stat stat = (Stat) stats.get(name);

				float avg = (float) stat.total / (float) stat.count;

				out.println("<tr><td>" + name + "&nbsp;&nbsp;&nbsp;</td><td>" + decimal.format(avg) + "&nbsp;&nbsp;&nbsp;</td><td>" + stat.min + "&nbsp;&nbsp;&nbsp;</td><td>" + stat.max + "&nbsp;&nbsp;&nbsp;</td><td>x</td><td>" + stat.count + "&nbsp;&nbsp;&nbsp;</td><td>" + stat.fail + "&nbsp;&nbsp;&nbsp;</td><td>" + stat.error + "&nbsp;&nbsp;&nbsp;</td></tr>");
			}

			out.println("</table>");
			out.println("</pre>");
		}
	}

	public void broadcast(User user, String message, boolean ignore) throws Exception {
		Iterator it = users.values().iterator();

		while(it.hasNext()) {
			User u = (User) it.next();
			String m = message;
			boolean add = true;

			if(ignore)
				add = (u.game == null || user.game == null || !u.game.name.equals(user.game.name));

			if(ignore && !add && u.room.user != null && user.room.user == null) { // fix for stem -> leaf
				add = !u.game.name.equals(u.room.user.name);

				if(add && m.startsWith("chat")) {
					String[] split = m.split("\\|");
					m = "chat|stem|" + split[2] + "|" + split[3];
				}
			}

			if(add)
				node.push(u.salt, m, true);
		}
	}

	static protected String stack(Thread thread) {
		StackTraceElement[] stack = thread.getStackTrace();
		StringBuilder builder = new StringBuilder();

		for(int i = 0; i < stack.length; i++) {
			builder.append(stack[i]);

			if(i < stack.length - 1) {
				builder.append("\r\n");
			}
		}

		return builder.toString();
	}

	public synchronized void remove(String salt, int place) throws Exception {
		User user = (User) users.get(salt);

		if(debug)
			System.err.println("quit " + place + " " + user + " " + salt); // + " " + stack(Thread.currentThread()));
		
		if(place == 6 && user == null)
			throw new Exception("Prune salt " + salt);
		
		users.remove(salt);

		if(user != null && user.salt != null && user.game != null) {
			Room room = user.move(user.room, null, false);
			user.game.rooms.remove(user.name);

			if(place != 1) {
				user.game.send(user, "quit|" + user.name);
			}

			names.remove(user.name);
			broadcast(user, "gone|root|" + user.name, true);
		}
	}

	public void exit() {
		//daemon.remove(this);
	}
}