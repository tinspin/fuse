package se.rupy.http;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.json.JSONException;

import se.rupy.http.Async;
import se.rupy.http.Daemon;
import se.rupy.http.Deploy;
import se.rupy.http.Event;
import se.rupy.http.Output;
import se.rupy.http.Query;
import se.rupy.http.Service;

/**
 * Secure Unique Identity Cluster.
 * 
 * This solution is "wasteful"; but with 16-digit Base-58 keys and 
 * long ids, collisions should be rare, at least over the network.
 * 
 * @author Marc
 */
public class Root extends Service {
	static int LENGTH = 16;
	public static String local;
	static String secret;
	//static String home;
	static String[] ip = {"89.221.241.32", "89.221.241.33", "92.63.174.125", "2.248.42.217", "2.248.42.217", "2.248.42.217", "2.248.42.217"};
	static String[] host = {"one", "two", "tre", "fyr", "fem", "six", "sju"}; // oct, nio, tio, elv
	static String[] node_type = {"data", "user", "task", "type"};
	static String[] link_type = {"data", "user", "task", "date"};

	//static Deploy.Archive archive;
	static URLDecoder decoder = new URLDecoder();

	// example data for tutorial
	final String key = "yN5SFvPCL2eshThB";
	final String search = "full%20text%20search";

	public Root(Properties prop) {
		int i = 0;
		Iterator it = prop.keySet().iterator();
		ip = new String[prop.size() - 1];
		host = new String[prop.size() - 1];
		while(it.hasNext()) {
			String key = (String) it.next();
			String value = (String) prop.get(key);
			//System.out.println(key + " " + value);
			if(key.equals("key")) {
				secret = value;
			}
			else {
				host[i] = key;
				ip[i++] = value;
			}
		}
	}
	
	static String type(String[] type, String name, int selected) {
		StringBuilder select = new StringBuilder("<select id=\"type\" name=\"" + name + "\">");

		for(int i = 0; i < type.length; i++) {
			select.append("<option" + (i == selected ? " selected" : "") + ">" + type[i] + "</option>");
		}

		select.append("</select>");
		return select.toString();
	}

	static int type(String[] type, String name) {
		for(int i = 0; i < type.length; i++) {
			if(type[i].equals(name))
				return i;
		}

		return -1;
	}
	
	public String path() { return "/root"; }

	public void create(Daemon daemon) throws Exception {
		local = System.getProperty("host", "none");
	}

	/**
	 * A cross functional Root home folder detection for your deploy.
	 * 
	 * Usage example from logging in a user:
	 * 
	 * <pre>
	public static class Login extends Service {
		public String path() { return "/login"; }
		public void filter(Event event) throws Event, Exception {
			event.query().parse();
			
			String mail = event.string("mail");
			String pass = event.string("pass");

			if(mail.length() > 0 && pass.length() > 0) {
				File file = new File(<b>Root.home()</b> + "/node/user/mail" + <b>Root.path(mail)</b>);

				if(!file.exists()) {
					System.out.println(file);
					error(event, "Anv&auml;ndare ej funnen.");
				}

				JSONObject user = new JSONObject(<b>Root.file(file)</b>);

				if(Deploy.hash(pass, "SHA").equals(user.getString("pass"))) {
					event.session().put("user", user);
				}
				else {
					error(event, "Fel l&ouml;senord.");
				}
			}

			event.reply().header("Location", "user");
			event.reply().code("302 Found");
		}
	}</pre>
	 * @return
	 * @throws Exception
	 */
	public static String home() throws Exception {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		
		if(loader instanceof Deploy.Archive) {
			Deploy.Archive archive = (Deploy.Archive) loader;
			return "app/" + archive.host() + "/root";
		}
		else {
			throw new Exception("Home could not be found.");
		}
	}
	
	private static String host() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		
		if(loader instanceof Deploy.Archive) {
			Deploy.Archive archive = (Deploy.Archive) loader;
			return archive.host();
		}
		else {
			return "";
		}
	}
	
	public void filter(Event event) throws Event, Exception {
		Output out = event.output();

		if(event.query().method() == Query.POST) {
			ByteArrayOutputStream data = new ByteArrayOutputStream();
			Deploy.pipe(event.input(), data);
			String file = decrypt(data.toByteArray());
			String path = event.query().header("path");

			boolean create = path.startsWith("/make");
			boolean link = path.startsWith("/link");

			String type = "user";
			String sort = "key";

			int question = path.indexOf("?");

			if(question > 0) {
				StringTokenizer amp = new StringTokenizer(path.substring(question + 1), "&");

				while(amp.hasMoreTokens()) {
					String equ = amp.nextToken();
					int pos = equ.indexOf('=');

					String key = null;
					String value = "false";

					if(pos == -1) {
						pos = equ.length();
						key = equ.substring(0, pos);
					}
					else {
						key = equ.substring(0, pos);
						value = equ.length() > pos + 1 ? decoder.decode(equ.substring(pos + 1), "UTF-8") : "";
					}

					if(key.equals("type"))
						type = value;
					if(key.equals("sort"))
						sort = value;
				}
			}

			try {
				if(link)
					link(new JSONObject(file), null);
				else
					store(new JSONObject(file), type, sort, create);

				out.print(1);
			}
			catch(SortException e) {
				out.print(e);
			}
			catch(KeyException e) {
				out.print(0);
			}
			catch(Exception e) {
				e.printStackTrace();
				out.print(e + "[" + local + "]");
			}
		}
		else {
			out.println("<html><head><title>Root Cloud Store</title></head>");
			out.println("<body><pre><img src=\"favicon.ico\"> replicates JSON objects async<font color=\"red\"><sup>*</sup></font> over HTTP<br>across a cluster with guaranteed uniqueness<br>and DNS roundrobin for 100% read uptime.<br>");
			out.println("<i>Usage:</i>");
			out.println("         <font color=\"grey\"><i><b>user</b></i></font>   <font color=\"grey\"><i><b>node</b></i></font>   <font color=\"grey\"><i><b>link</b></i></font>");
			out.println("       ┌──────┬──────┬──────┐");
			out.println(" <font color=\"red\"><i>make</i></font>  │ <a href=\"/user?url=binarytask.com\">join</a> │ <a href=\"/node?make\">form</a> │ <a href=\"/link\">bind</a> │  <font color=\"grey\"><i>\"insert\"</i></font>");
			out.println(" <font color=\"green\"><i>find</i></font>  │ <a href=\"http://binarytask.com\">auth</a> │ <a href=\"/node/user/key/" + key + "\">load</a> │ <a href=\"/link/user/data/" + key + "\">list</a> │  <font color=\"grey\"><i>\"select\"</i></font>");
			out.println(" <font color=\"blue\"><i>edit</i></font>  │      │ <a href=\"/node\">save</a> │      │  <font color=\"grey\"><i>\"update\"</i></font>");
			out.println(" <i>trim</i>  │      │ drop │ tear │  <font color=\"grey\"><i>\"delete\"</i></font>");
			out.println("       └──────┴──────┴──────┘");
			out.println("");
			out.println("                <font color=\"grey\"><i><b>sort</b></i></font>");
			out.println("       ┌──────┬──────┬──────┐");
			out.println(" open  │ <a href=\"/node/data/text/" + search + "\">text</a> │ <a href=\"/node/data/date/14/11/25/example\">path</a> │ <a href=\"/link/user/data/" + hash(key) + "\">list</a> │");
			out.println("       └──────┴──────┴──────┘");
			out.println("");
			out.println("<font color=\"red\"><sup>*</sup></font><i>Source:</i> <a href=\"http://root.rupy.se/code\">Async</a>, <a href=\"http://root.rupy.se/code?path=/User.java\">User</a>, <a href=\"http://root.rupy.se/code?path=/Root.java\">Root</a>");
			out.println("</pre>");
			out.println("</body>");
			out.println("</html>");
		}
	}

	private static void send(final int i, final String key, final byte[] data, Event event, final boolean trace) throws Exception {
		if(trace) {
			Output out = event.output();
			out.println("> " + host[i] + " (" + key + ")");
			out.flush();
		}

		Async.Work work = new Async.Work(event) {
			public void send(Async.Call post) throws Exception {
				String path = null;
				Query query = event.query();

				String param = "?type=" + query.string("type", "user") + "&sort=" + query.string("sort", "key");

				if(event.query().bit("create", true))
					path = "/make" + param;
				else
					path = event.query().path() + param;

				post.post("/root", "Host:" + Root.host[i] + "." + host() + "\r\nPath:" + path, data);
			}

			public void read(String host, String body) throws Exception {
				if(!event.query().string(Root.host[i]).equals("-2"))
					event.query().put(Root.host[i], body);

				event.query().put("in_" + Root.host[i] + "_key", key);

				if(trace)
					event.query().put("wakeup", Root.host[i]);

				event.reply().wakeup(true);
			}

			public void fail(String host, Exception e) throws Exception {
				if(e instanceof Async.Timeout) {
					event.daemon().client().send(host, this, 60);
				}
				else {
					event.query().put(Root.host[i], e.toString());

					if(trace)
						event.query().put("wakeup", Root.host[i]);

					e.printStackTrace();
					
					event.reply().wakeup(true);
				}
			}
		};

		event.daemon().client().send(ip[i], work, 60);
	}

	/**
	 * Create and synchronize a unique node key across the cluster.
	 * The id is generated by hashing the key with {@link #hash(String)}.
	 */
	public static JSONObject sync(Event event, String type, String sort, boolean create) throws Exception {
		JSONObject json = json(event);
		if(create) {
			json = create(event, json);
		}
		while(exists(json, type, sort, event, create) == 0) {
			json = create(event, json);
		}
		return json;
	}

	private static JSONObject create(Event event, JSONObject json) throws JSONException {
		String key = Event.random(LENGTH);

		if(event.query().bit("create", true)) { // From /node but with create flag.
			json.put("key", key);
		}
		else { // From /make.
			return new JSONObject("{\"key\":\"" + key + "\"}");
		}

		return json;
	}

	/**
	 * Fetch the id for a key.
	 */
	public static long id(String key, String type) throws Exception {
		JSONObject o = new JSONObject(file(home() + "/node/" + type + "/id" + path(hash(key))));

		if(o.getString("key").equals(key))
			return hash(key);
		else
			throw new KeyException("Root [" + type + "/" + key + "] not found!");
	}

	private static int exists(JSONObject json, String type, String sort, Event event, boolean create) throws Exception {
		String key = json.getString("key");
		long id = hash(key);

		if(id < 4) // Reserved for developer
			return 0;

		boolean key_exist = Files.exists(Paths.get(home() + "/node/" + type + "/key" + path(key)));
		boolean id_exist = Files.exists(Paths.get(home() + "/node/" + type + "/id" + path(id)));

		//if(event == null) // TODO: This is to test remote collisions, set LENGTH to 2 and uncomment this.
		if(create && (key_exist || id_exist))
			return 0;

		sort(json, null, type, sort, create); // TODO: To test remote sort collision comment this out.

		if(event == null)
			return 1;

		if(!create && event.query().path().startsWith("/node") && 
				!key_exist && !id_exist)
			throw new Exception("Node does not exist! [type=" + type + "]");

		byte[] encrypted = encrypt(json.toString());

		for(int i = 0; i < host.length; i++) {
			String state = event.string(host[i]);
			String out_key = event.string("out_" + host[i] + "_key");

			if(!host[i].equals(local)) {
				//System.out.println(host[i] + " " + local);
				if(((state.equals("") || state.equals("0")) && !out_key.equals(key)) || state.equals("-2")) {
					event.query().put(host[i], "2");
					event.query().put("out_" + host[i] + "_key", key);
					send(i, key, encrypted, event, event.query().bit("trace", true));
				}
			}
		}

		return 1;
	}

	public static class KeyException extends Exception {
		public KeyException(String message) { super(message); }
	}

	public static class SortException extends Exception {
		public SortException(String message) { super(message); }
	}

	public static class LinkException extends Exception {
		public LinkException(String message) { super(message); }
	}

	private static void store(JSONObject json, String type, String sort, boolean create) throws Exception {
		if(exists(json, type, sort, null, create) == 0)
			throw new KeyException("Node [" + local + "] collision");

		String path = Root.home() + "/node/" + type + "/id" + path(hash(json.getString("key")));

		//System.out.println(path);
		
		new File(path.substring(0, path.lastIndexOf("/"))).mkdirs();

		File file = new File(path);

		if(create)
			file.createNewFile();

		BufferedWriter output = new BufferedWriter(new FileWriter(file));
		output.write(json.toString());
		output.close();

		sort(json, path, type, sort, create);
	}

	private static void sort(JSONObject json, String path, String type, String sort, boolean create) throws Exception {
		String home = Root.home() + "/node/" + type;
		long id = hash(json.getString("key"));
		String[] name = sort.split(",");

		for(int i = 0; i < name.length; i++) {
			String key = name[i].trim();
			String value = json.getString(key);
			boolean full = false;

			if(value.contains(" ") || key.equals("text"))
				full = true;
			else if(value.matches("[0-9]+") || !value.matches("[a-zA-Z0-9/.@\\+]+"))
				throw new SortException("Validation [" + key + "=" + json.getString(key) + "]");

			if(full) {
				String[] words = value.toLowerCase().split("\\s+");

				for(int j = 0; j < words.length; j++) {
					String word = words[j];

					// remove punctuation
					// remove punctuation
					while(word.endsWith(".") 
					   || word.endsWith(":") 
					   || word.endsWith(",") 
					   || word.endsWith(";") 
					   || word.endsWith("!") 
					   || word.endsWith("?"))
						word = word.substring(0, word.length() - 1);

					if(word.matches("[\\p{L}]+")) { // UTF-8 character
						sort = home + "/" + key + "/" + word;
						new File(sort.substring(0, sort.lastIndexOf("/"))).mkdirs();
						RandomAccessFile file = new RandomAccessFile(sort, "rw");
						write(file, id);
					}
				}
			}
			else {
				if(value.indexOf("/") > 0)
					sort = home + "/" + key + "/" + value;
				else
					sort = home + "/" + key + path(value);

				boolean exists = new File(sort).exists();

				if(exists && create) {
					throw new SortException("Collision [" + key + "=" + json.getString(key) + "]");
				}

				if(!exists && path != null) {
					new File(sort.substring(0, sort.lastIndexOf("/"))).mkdirs();
					Files.createLink(Paths.get(sort), Paths.get(path));
				}
			}
		}
	}

	private static void link(JSONObject json, final Event event) throws Exception {
		JSONObject parent = json.getJSONObject("parent");
		JSONObject child = json.getJSONObject("child");

		String ptype = parent.getString("type");
		String pkey = parent.getString("key");
		String ctype = child.getString("type");
		String ckey = child.getString("key");

		if(event == null) {
			link(ptype, pkey, ctype, ckey);
		}
		else {
			byte[] data = Root.encrypt(json.toString());

			for(int i = 0; i < host.length; i++) {
				if(!host[i].equals(local)) {
					send(i, data, event);
				}
			}
		}
	}

	/*
	 * Link parent and child.
	 * Root relations is self referencing lists of node types, so you can get last 10 registered users f.ex.
	 * Atomic relation is node-to-node, both one-to-one and one-to-many, allowing for regular give me all articles written by user f.ex.
	 * TODO: one-to-many-to-many... etc.
	 */
	private static void link(String ptype, String pkey, String ctype, String ckey) throws Exception {
		String path = Root.home() + "/link/";

		long pid = hash(pkey);
		long cid = ctype.equals("date") ? Long.parseLong(ckey) : hash(ckey);

		if(!ctype.endsWith("date") && !new File(Root.home() + "/node/" + ctype + "/id" + path(cid)).exists())
			throw new LinkException("Child node doesn't exist.");

		if(pkey.length() == 0 && ptype.equals(ctype)) { // Root relation.
			path += ptype + "/root";
		}
		else { // Atomic relation
			if(!new File(Root.home() + "/node/" + ptype + "/id" + path(pid)).exists())
				throw new LinkException("Parent node doesn't exist.");

			path += ptype + "/" + ctype + "/" + path(pkey);
		}

		new File(path.substring(0, path.lastIndexOf("/"))).mkdirs();

		RandomAccessFile file = new RandomAccessFile(path, "rw");

		write(file, cid);
	}

	/*
	 * Finds the references to same nodes in full text index files.
	 * 
	 * This should search from the end until the size asked for is found 
	 * with infinite "next" pagination memory seek position instead of size.
	 */
	private static List first_compare(RandomAccessFile one, RandomAccessFile two) throws Exception {
		if(one.length() > 0 && two.length() > 0) {
			LinkedList list = new LinkedList();

			byte[] one_data = new byte[4096];
			byte[] two_data = new byte[4096];

			// loop one
			for(int i = 0; i < one.length() / one_data.length + 1; i++) {
				one.seek(i * one_data.length);
				int one_read = one.read(one_data);

				if(one_read > 0) {
					ByteBuffer one_buffer = ByteBuffer.wrap(one_data, 0, one_read);

					// for each one id
					for(int j = 0; j < one_read / 8; j++) {
						long one_id = one_buffer.getLong();

						// loop two
						for(int k = 0; k < two.length() / two_data.length + 1; k++) {
							two.seek(k * two_data.length);
							int two_read = two.read(two_data);

							if(two_read > 0) {
								ByteBuffer two_buffer = ByteBuffer.wrap(two_data, 0, two_read);

								// for each two id
								for(int l = 0; l < two_read / 8; l++) {
									long two_id = two_buffer.getLong();

									// match
									if(one_id == two_id) {
										list.add(new Long(one_id));
									}
								}
							}
						}
					}
				}
			}

			return list;
		}

		return null;
	}

	private synchronized static List remove(RandomAccessFile file, List list) throws Exception {
		// check for non duplicate

		LinkedList both = new LinkedList();

		if(file.length() > 0) {
			byte[] data = new byte[4096];

			for(int i = 0; i < file.length() / data.length + 1; i++) {
				file.seek(i * data.length);
				int read = file.read(data);

				ByteBuffer buffer = ByteBuffer.wrap(data, 0, read);

				for(int j = 0; j < read / 8; j++) {
					Long id = new Long(buffer.getLong());

					if(list.contains(id)) {
						both.add(id);
					}
				}
			}
		}

		return both;
	}

	private synchronized static void write(RandomAccessFile file, long id) throws Exception {
		/* check for duplicate
		 *  
		 * here we should probably check for zero long values since we need to delete references by zeroing them.
		 * but we want to write the latest entry last for chronology.
		 */
		if(file.length() > 0) {
			byte[] data = new byte[4096];

			for(int i = 0; i < file.length() / data.length + 1; i++) {
				file.seek(i * data.length);
				int read = file.read(data);

				ByteBuffer buffer = ByteBuffer.wrap(data, 0, read);

				for(int j = 0; j < read / 8; j++) {
					if(id == buffer.getLong()) {
						return;
					}
				}
			}
		}

		// write

		file.seek(file.length());
		file.writeLong(id);
		file.close();
	}

	private static void send(final int i, final byte[] data, Event event) throws Exception {
		final Async.Work work = new Async.Work(event) {
			public void send(Async.Call post) throws Exception {
				post.post("/root", "Host:" + Root.host[i] + "." + host() + "\r\nPath:/link", data);
			}

			public void read(String host, String body) throws Exception {
				int working = 0, complete = 0, failed = 0;
				event.query().put(Root.host[i], body);

				for(int j = 0; j < Root.host.length; j++) {
					if(!Root.host[i].equals(local)) {
						String result = event.string(Root.host[j]);

						if(result.equals("2"))
							working++;
						else if(result.equals("1"))
							complete++;
						else if(result.length() > 0)
							failed++;
					}
				}

				if(complete == Root.host.length - 1) {
					event.query().put("result", "1");
					link((JSONObject) event.query().get("json"), null);
					int state = event.reply().wakeup(true);
				}
				else if(failed > 0) {
					event.query().put("result", body + "[" + local + "]");
					int state = event.reply().wakeup(true);
				}
			}

			public void fail(String host, Exception e) throws Exception {
				if(e instanceof Async.Timeout) {
					event.daemon().client().send(host, this, 60);
				}
				else {
					e.printStackTrace();
					event.query().put("result", e.toString());
					event.reply().wakeup(true);
				}
			}
		};

		event.daemon().client().send(ip[i], work, 60);
	}

	public static long hash(String key) throws Exception {
		long h = 2166136261L;

		for(int i = 0; i < key.length(); i++) {
			h = (h ^ key.charAt(i)) * 16777619;
		}

		return Math.abs(h);
	}

	public static String path(long id) {
		return path(String.valueOf(id), 3);
	}

	public static String path(String name) {
		return path(name, 2);
	}

	/* Make a path of the first X chars then a file of the remainder.
	 * 58^2=3364 this is how you calculate if you need more or less folders.
	 * 10^3=1000 this is why the id needs one more folder.
	 */
	private static String path(String name, int length) {
		int index = name.indexOf('.');

		if((index > 0 && index <= length) || name.length() <= length) // Unless we can't!
			return "/" + name;

		StringBuilder path = new StringBuilder();

		for(int i = 0; i < name.length(); i++) {
			if(i <= length) {
				path.append("/");
			}

			path.append(name.charAt(i));
		}

		return path.toString();
	}

	/*
	 * Encryption stuff
	 */

	//static String secret;

	static String secret() throws Exception {
		if(secret == null) {
			//secret = file("app/" + archive.host() + "/root/secret");
			secret = file("secret");
		}

		return secret;
	}

	public static String file(String path) throws Exception {
		return file(new File(path));
	}

	public static String file(File file) throws Exception {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		String content = in.readLine();
		in.close();
		return content;
	}

	static byte[] encrypt(String text) throws Exception {
		Cipher aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
		aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret().getBytes(), "AES"));
		return aes.doFinal(text.getBytes());
	}

	static String decrypt(byte[] data) throws Exception {
		Cipher aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
		aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret().getBytes(), "AES"));
		return new String(aes.doFinal(data));
	}

	static JSONObject json(Event event) throws Exception {
		Object json = event.query().get("json");

		if(json == null)
			return null;

		if(json instanceof JSONObject)
			return (JSONObject) json;
		else if(json instanceof String)
			return new JSONObject((String) json);
		else
			throw new Exception("JSON is wrong type!");
	}

	public static class Find extends Service {
		public String path() {
			return null;
		}

		public void filter(Event event) throws Event, Exception {
			event.query().parse();
			int from = event.query().medium("from", 0);
			int size = event.query().medium("size", 10);
			String[] path = event.query().path().split("/");

			/* Example Public Paths:
			 * 
			 * Node:
			 * - /node/user/key/<key>
			 * 
			 * Link:
			 * - /link/user/data/<key>
			 * 
			 * Find:
			 * - /node/data/text/full%20text%20search
			 */

			String full = "";
			String poll = "";
			String type = "";
			String sort = "";
			String last = "";

			try {
				poll = path[1];
				type = path[2];
				sort = path[3];
			}
			catch(Exception e) {
				fail(event, full, poll, type, sort, last);
			}

			for(int i = 4; i < path.length; i++) {
				last += path[i];

				if(i < path.length - 1)
					last += "/";
			}

			if(poll.equals("node") && type.equals("user") && (sort.equals("name") || sort.equals("mail") || sort.equals("id"))) {
				event.reply().code("403 Forbidden");
				event.output().print("<pre>Public node/user/" + sort + " is forbidden.</pre>");
				throw event;
			}
			
			if(poll.equals("link") && type.equals("user") && sort.equals("date") && last.matches("[0-9]+")) {
				event.reply().code("403 Forbidden");
				event.output().print("<pre>Public link/user/date is forbidden.</pre>");
				throw event;
			}

			boolean remove = false; // remove key from node

			try {
				if(poll.equals("link")) { // link list
					full = home() + "/link/" + type + "/root";

					if(!sort.equals("root")) {
						if(last.length() > 0) {
							full = home() + "/link/" + type + "/" + sort + Root.path(last);

							if(last.matches("[0-9]+")) {
								full = home() + "/node/" + type + "/id" + Root.path(last, 3);

								File file = new File(full);

								if(file.exists()) {
									JSONObject json = new JSONObject(Root.file(file));
									full = home() + "/link/" + type + "/" + sort + Root.path(json.getString("key"));
									remove = true;
								}
							}
						}
						else {
							event.reply().code("400 Bad Request");
							event.output().print("<pre>Sort without key.</pre>");
							throw event;
						}
					}
					else {
						sort = type;
					}

					File file = new File(full);

					if(file.exists()) {
						write_last(event, sort, new RandomAccessFile(file, "rw"), type, last, from, size, remove);
					}
					else {
						event.reply().type("application/json; charset=UTF-8");
						event.output().print("[]");
						//fail(event, full, poll, type, sort, last);
					}
				}
				else {
					full = home() + "/node/" + type + "/" + sort + "/" + last;

					String decoded = decoder.decode(last, "UTF-8");

					if(sort.equals("text")) { // full text search
						full = home() + "/node/" + type + "/" + sort + "/";
						remove = true;

						if(last.contains(" ")) {
							String[] word = last.split("\\s+");

							RandomAccessFile one = new RandomAccessFile(full + word[0], "r");
							RandomAccessFile two = new RandomAccessFile(full + word[1], "r");

							List list = first_compare(one, two);

							for(int i = 2; i < word.length; i++) {
								RandomAccessFile next = new RandomAccessFile(full + word[i], "r");

								list = remove(next, list);
							}

							print_list(event, type, list, null, null, list.size(), remove);
						}
						else {
							full = home() + "/node/" + type + "/" + sort + "/";

							write_last(event, type, new RandomAccessFile(full + last, "r"), null, null, from, size, remove);
						}
					}
					else { // node sort index
						if(last.matches("[a-zA-Z0-9.@\\+]+"))
							full = home() + "/node/" + type + "/" + sort + Root.path(last);

						if(last.matches("[0-9]+")) {
							full = home() + "/node/" + type + "/" + sort + Root.path(Long.parseLong(last));
							remove = true;
						}

						if(last.contains("/"))
							remove = true;

						File file = new File(full);

						if(file.exists()) {
							event.reply().type("application/json; charset=UTF-8");
							JSONObject obj = new JSONObject(file(file));
							
							if(remove) {
								obj.put("id", hash(obj.getString("key")));
								obj.remove("key");
							}
							
							byte[] data = obj.toString(4).getBytes("UTF-8");
							Output out = event.reply().output(data.length);
							out.write(data);
						}
						else {
							fail(event, full, poll, type, sort, last);
						}
					}
				}
			}
			catch(Exception e) {
				e.printStackTrace();
				fail(event, full, poll, type, sort, last);
			}
		}

		private void write_last(Event event, String type, RandomAccessFile file, String poll, String last, int from, int size, boolean remove) throws Event, Exception {
			LinkedList list = new LinkedList();
			int length = (int) (file.length() > 8 * size ? 8 * size : file.length());
			long start = length + from * 8;

			if(start > file.length()) {
				start = file.length();
			}
			
			byte[] data = new byte[length];
			file.seek(file.length() - start);
			int read = file.read(data);
			ByteBuffer buffer = ByteBuffer.wrap(data); // TODO: add 0, read?

			for(int i = 0; i < size; i++) {
				if(buffer.remaining() > 0)
					list.addFirst(new Long(buffer.getLong()));
				else
					break;
			}
			
			print_list(event, type, list, poll, last, file.length() / 8, remove);
		}

		private void print_list(Event event, String type, List list, String poll, String last, long total, boolean remove) throws Event, Exception {
			boolean secure = poll != null && last != null && last.matches("[0-9]+") && type.equals("user");
			String key = "";
			
			if(secure) {
				event.query().parse();
				
				String hash = event.string("hash");
				String salt = event.string("salt");
				
				if(Salt.salt.containsKey(salt)) {
					Salt.salt.remove(salt);
				}
				else {
					event.reply().code("400 Bad Request");
					event.output().print("<pre>Salt not found.</pre>");
					throw event;
				}
				
				JSONObject object = new JSONObject(file(home() + "/node/" + poll + "/id/" + Root.path(last, 3)));
				key = object.getString("key");
				
				String match = Deploy.hash(key + salt, "SHA");
				
				if(!hash.equals(match)) {
					event.reply().code("400 Bad Request");
					event.output().print("<pre>Auth did not match.</pre>");
					throw event;
				}
			}
			
			StringBuilder builder = new StringBuilder();
			builder.append("{\"total\":" + total + ", \"list\":[");

			Iterator it = list.iterator();

			while(it.hasNext()) {
				long id = ((Long) it.next()).longValue();

				if(type.equals("date")) {
					builder.append(id);
				}
				else {
					String open = Root.home() + "/node/" + type + "/id" + Root.path(id);

					JSONObject obj = new JSONObject(file(open));
					
					if(remove) {
						obj.put("id", hash(obj.getString("key")));
						obj.remove("key");
					}
					
					//if(type.equals("user"))
					//	obj.remove("pass");
						
					builder.append(obj.toString(4));
				}

				if(it.hasNext())
					builder.append(",");
			}

			builder.append("]}");
			
			String result = builder.toString();
			
			if(secure) {
				event.reply().header("Hash", Deploy.hash(result + key, "SHA"));
			}
			
			event.reply().type("application/json; charset=UTF-8");
			byte[] data = result.getBytes("UTF-8");
			Output out = event.reply().output(data.length);
			out.write(data);
		}

		private void fail(Event event, String path, String poll, String type, String sort, String last) throws Event, Exception {
			JSONObject obj = new JSONObject("{\"path\":\"" + path + "\",\"poll\":\"" + poll + "\",\"type\":\"" + type + "\",\"sort\":\"" + sort + "\",\"last\":\"" + last + "\"}");
			event.reply().code("404 Not Found");
			event.output().print("<pre>Node '" + event.query().path() + "' was not found on host " + local + ".</pre>");
			
			System.out.println(obj.toString(4));
			
			throw event;
		}
	}

	public static class Link extends Service {
		public String path() {
			return "/link";
		}

		public void filter(Event event) throws Event, Exception {
			if(event.push()) {
				for(int i = 0; i < host.length; i++) {
					event.query().put(host[i], "");
				}

				Output out = event.output();
				out.println(event.query().string("result"));
				out.finish();
				out.flush();
			}
			else {
				event.query().parse();
				String ptype = event.string("ptype");
				String ctype = event.string("ctype");
				String pkey = event.string("pkey");
				String ckey = event.string("ckey");

				if(event.query().method() == Query.GET) {
					Output out = event.output();
					out.println("<style> input, select { font-family: monospace; } </style>");
					out.println("<pre>");
					out.println("<form action=\"link\" method=\"post\">");
					out.println("  Parent <input type=\"text\" style=\"width: 100px;\" name=\"pkey\"> " + type(node_type, "ptype", 1) + "<br>");
					out.println("  Child  <input type=\"text\" style=\"width: 100px;\" name=\"ckey\"> " + type(link_type, "ctype", 0) + "<br>");
					out.println("         <input type=\"submit\" value=\"Link\">");
					out.println("</form>");
					out.println("</pre>");
					throw event;
				}
				else {
					JSONObject json = new JSONObject(
							"{\"parent\":{\"key\":\"" + pkey + "\",\"type\":\"" + ptype + "\"}," +
									"\"child\":{\"key\":\"" + ckey + "\",\"type\":\"" + ctype + "\"}}");
					event.query().put("json", json);
					link(json, event);
				}
			}
		}
	}

	public static class Salt extends Service {
		static HashMap salt = new HashMap();
		
		public String path() {
			return "/salt";
		}

		public void filter(Event event) throws Event, Exception {
			String salt = Event.random(8);
			
			while(this.salt.containsKey(salt)) {
				salt = Event.random(8);
			}
			
			this.salt.put(salt, null);
			event.output().print(salt);
		}
	}
	
	public static class Hash extends Service {
		public String path() {
			return "/hash";
		}

		public void filter(Event event) throws Event, Exception {
			event.query().parse();
			String key = event.string("key");
			Output out = event.output();

			if(key.length() != LENGTH) {
				out.println("<style> input { font-family: monospace; } </style>");
				out.println("<pre>");
				out.println("<form action=\"hash\" method=\"get\">");
				out.println("  Key <input type=\"text\" name=\"key\" value=\"" + key + "\"> <input type=\"submit\" value=\"Hash\">");
				out.println("</form>");
				out.println("</pre>");
			}
			else
				out.println("<pre>" + hash(key) + "</pre>");
		}
	}

	public static class Node extends Service {
		public String path() { return "/make:/node"; }
		public void filter(final Event event) throws Event, Exception {
			if(event.push()) {
				JSONObject json = json(event);
				int working = 0, complete = 0, collision = 0, failed = 0;
				long time = event.big("time");
				boolean trace = event.query().bit("trace", true);

				if(trace)
					event.output().println("  < " + event.string("wakeup") + " " + (System.currentTimeMillis() - time) + " ms.");

				for(int i = 0; i < host.length; i++) {
					if(!host[i].equals(local)) {
						String result = event.string(host[i]);
						String key = event.string("in_" + host[i] + "_key");

						if(trace)
							event.output().println("      " + host[i] + " " + result + " " + (result.equals("0") || result.equals("1") ? "(" + key + ") " : ""));

						/* ""   = ready for write
						 * "2"  = writing
						 * "1"  = write completed successfully
						 * "0"  = write conflict
						 * "-1" = write fail (remote error) // deprecated
						 * "-2" = resend
						 */
						if(result.equals("2"))
							working++;
						else if(result.equals("1"))
							complete++;
						else if(result.equals("0")) {
							event.query().put(host[i], "");
							if(json.getString("key").equals(key)) {
								if(trace)
									event.output().println("        collision match");

								collision++;
							}
							else {
								if(trace)
									event.output().println("        collision fail");

								failed++;
							}
						}
						else if(result.length() > 0) {
							if(result.equals("-2"))
								event.query().put(host[i], "");
							if(result.equals("java.nio.channels.ClosedChannelException"))
								event.query().put(host[i], "-2");
							if(result.startsWith("Root$SortException")) {
								event.output().print(result + "[" + local + "]");
								event.output().finish();
								event.output().flush();
								throw event;
								//throw new SortException(result.substring(result.indexOf(": ") + 2));
							}
							if(result.startsWith("java.")) {
								event.output().print(result + "[" + local + "]");
								event.output().finish();
								event.output().flush();
								throw event;
								//throw new Exception("Node [" + host[i] + "] failed: " + result);
							}

							failed++;
						}
					}
				}

				if(trace)
					event.output().flush();

				if(complete == host.length - 1) {
					boolean create = event.query().bit("create", true);

					store(json, event.query().string("type", "user"), 
							event.query().string("sort", "key"), 
							event.query().path().equals("/make") || create);

					for(int i = 0; i < host.length; i++) {
						event.query().put(host[i], "");
					}

					if(!trace)
						event.reply().type("application/json; charset=UTF-8");

					byte[] data = json.toString(4).getBytes("UTF-8");
					Output out = event.reply().output(data.length);

					out.write(data);
					out.finish();
					out.flush();
				}
				else if(failed > 0 || collision > 0) {
					if(collision > 0)
						for(int i = 0; i < host.length; i++) {
							String state = event.string(host[i]);
							if(state.equals("0") || state.equals("1") || state.equals("2")) {
								if(trace) {
									event.output().println("        invalidated " + host[i] + " " + state);
									event.output().flush();
								}

								event.query().put(host[i], "");
							}
						}

					async(event, collision > 0);
				}
			}
			else {
				event.hold();
				event.query().parse(10240);
				event.query().put("time", System.currentTimeMillis());

				boolean trace = event.query().bit("trace", true);
				boolean create = event.query().bit("create", true);

				if(trace)
					event.output().println("<pre>");

				if(event.query().path().equals("/node")) {
					if(event.query().method() == Query.GET) {
						boolean make = event.query().bit("make", true);

						int user = type(node_type, "user");
						
						Output out = event.output();
						out.println("<script>");
						out.println("  function toggle() {");
						out.println("    var make = document.getElementById('make').checked;");
						out.println("    document.getElementById('node').value = (make ? 'Make' : 'Edit');");
						out.println("    var select = document.getElementById('type');");
						out.println("    if(make) {");
						out.println("      select.remove(" + user + ");");
						out.println("    } else {");
						out.println("      var option = document.createElement('option');");
						out.println("      option.text = 'user';");
						out.println("      select.options.add(option, select.options[" + user + "]);");
						out.println("    }");
						out.println("  }");
						out.println("</script>");
						out.println("<style> input, select { font-family: monospace; } </style>");
						out.println("<pre>");
						out.println("<form action=\"node\" method=\"post\">");
						out.println("  <textarea rows=\"10\" cols=\"50\" name=\"json\"></textarea><br>");
						out.print("  Type " + type(node_type, "type", 0));
						out.print(" <input id=\"make\" type=\"checkbox\" name=\"create\" onclick=\"toggle();\"/> Make");
						out.println(" <input type=\"checkbox\" name=\"trace\"> Info<br>");
						out.println("  Comma separated list of JSON keys to index,");
						out.println("  \"text\" key reserved for full text search:<br>");
						out.println("  Sort <input type=\"text\" name=\"sort\" value=\"key\"><br>");
						out.println("       <input id=\"node\" type=\"submit\" value=\"Edit\">");
						out.println("</form>");
						out.println("</pre>");

						if(make)
							out.println("<script>document.getElementById('make').checked = true; toggle();</script>");

						out.finish();
						out.flush();
						throw event;
					}
				}

				try {
					async(event, event.query().path().equals("/make") || create);
				}
				catch(Exception e) {
					e.printStackTrace();
					event.output().print(e + "[" + local + "]");
					event.output().finish();
					event.output().flush();
					throw event;
				}
			}
		}

		private void async(Event event, boolean create) throws Exception {
			JSONObject json = Root.sync(event, event.query().string("type", "user"), event.query().string("sort", "key"), create);
			event.query().put("json", json);
		}
	}
}