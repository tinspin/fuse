package se.rupy.http;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONArray;
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
// TODO: debug the problem when all nodes has an object except the node that tries to create it
//       might be a problem on http.Root only since the exceptions are wrapped...
public class Root extends Service {
	static int LENGTH = 16;
	public static String local;
	public static String home;
	static String[] ip = {"89.221.241.32", "89.221.241.33", "92.63.174.125", "2.248.42.217", "2.248.42.217", "2.248.42.217"};
	static String[] host = {"one", "two", "tre", "fem", "six", "sju"}; // oct, nio, tio, elv
	static String[] node_type = {"data", "node", "user", "task", "type"};
	static String[] link_type = {"data", "node", "user", "task", "date"};
	static String[] meta_type = {"data", "node", "user", "task"};

	//static String root = "/";
	static Deploy.Archive archive;
	static URLDecoder decoder = new URLDecoder();

	// example data for tutorial
	final String key = "SWhK6hk5jhQuJJaJ";
	final String search = "full%20text%20search";

	// <!-- start http.Root
	static String secret;
	static String domain;
	static String root = "/root";

	public Root(String domain, Properties prop) {
		this.domain = domain;
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

	public static String[] getIp() {
		try {
			secure();
			return ip;
		}
		catch(IOException e) {
			return null;
		}
	}

	public static String[] getHost() {
		try {
			secure();
			return host;
		}
		catch(IOException e) {
			return null;
		}
	}

	static void secure() throws IOException {
		//TODO: Replace with classloader test.

		File pass = new File("app/" + domain + "/passport");

		if(!pass.exists()) {
			pass.createNewFile();
		}

		pass.canRead();
	}
	
	static String secret() throws Exception {
		if(secret == null) {
			//secret = file("app/" + archive.host() + "/root/secret");
			secret = file("secret");
		}

		return secret;
	}

	public void create(Daemon daemon) throws Exception {
		local = System.getProperty("host", "none");
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
			throw new Exception("Home could not be found. (" + loader + ")");
		}
	}
	
	// --> http.Root end
	
	static String type(String[] type, String name, String selected) {
		StringBuilder select = new StringBuilder("<select id=\"type\" name=\"" + name + "\">");

		for(int i = 0; i < type.length; i++) {
			select.append("<option" + (type[i].equals(selected) ? " selected" : "") + ">" + type[i] + "</option>");
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

	public String path() { return root; }
/*
	public void create(Daemon daemon) throws Exception {
		local = InetAddress.getLocalHost().getHostName();
		archive = (Deploy.Archive) Thread.currentThread().getContextClassLoader();
		home = "app/" + archive.host() + "/root";
	}

	private static String host() {
		return "root.rupy.se";
	}

	public static String home() {
		return "app/" + host() + "/root";
	}
*/
	public void filter(Event event) throws Event, Exception {
		Output out = event.output();

		if(event.query().method() == Query.POST) {
			ByteArrayOutputStream data = new ByteArrayOutputStream();
			Deploy.pipe(event.input(), data);
			String file = decrypt(data.toByteArray());
			String path = event.query().header("path");

			boolean make = path.startsWith("/make");
			boolean link = path.startsWith("/link");
			boolean meta = path.startsWith("/meta");

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
				else if(meta)
					meta(new JSONObject(file), null);
				else
					store(new JSONObject(file), type, sort, make, ((String) event.query().header("host")).equals("test.rupy.se"));

				out.print(1);
			}
			catch(SortFail e) {
				out.print(e);
			}
			catch(KeyFail e) {
				out.print(0);
			}
			catch(Exception e) {
				e.printStackTrace();
				out.print(e + "[" + local + "]");
			}
		}
		else {
			out.println("<html><head><title>Root Cloud Store</title></head>");
			out.println("<body><pre>ROOT replicates JSON objects async<font color=\"red\"><sup>*</sup></font> over HTTP<br>across a cluster with guaranteed uniqueness<br>and DNS roundrobin for 100% read uptime.<br>");
			out.println("<i>Usage:</i>");
			out.println("         <font color=\"grey\"><i><b>user</b></i></font>   <font color=\"grey\"><i><b>node</b></i></font>   <font color=\"grey\"><i><b>link</b></i></font>   <font color=\"grey\"><i><b>meta</b></i></font>");
			out.println("       ┌──────┬──────┬──────┬──────┐");
			out.println(" <font color=\"red\"><i>make</i></font>  │ <a href=\"/user?url=binarytask.com\">join</a> │ <a href=\"/node?make\">form</a> │ <a href=\"/link\">bind</a> │ <a href=\"/meta\">bond</a> │  <font color=\"grey\"><i>\"insert\"</i></font>");
			out.println(" <font color=\"green\"><i>find</i></font>  │ <a href=\"http://binarytask.com\">auth</a> │ <a href=\"/node/user/key/" + key + "\">load</a> │ <a href=\"/link/user/data/" + key + "\">list</a> │ <a href=\"/meta/user/data/hej\">roll</a> │  <font color=\"grey\"><i>\"select\"</i></font>");
			out.println(" <font color=\"blue\"><i>edit</i></font>  │      │ <a href=\"/node\">save</a> │      │ <a href=\"/meta?edit\">yoke</a> │  <font color=\"grey\"><i>\"update\"</i></font>");
			out.println(" <i>trim</i>  │      │ drop │      │ <a href=\"/meta?tear=true\">tear</a> │  <font color=\"grey\"><i>\"delete\"</i></font>");
			out.println("       └──────┴──────┴──────┴──────┘");
			out.println("");
			out.println("       ┌──────┬──────┬──────┬──────┐");
			out.println(" open  │ <a href=\"/node/data/text/" + search + "\">text</a> │ <a href=\"/node/data/date/14/11/25/example\">path</a> │ <a href=\"/link/user/data/" + hash(key) + "\">list</a> │ <a href=\"/tree\">tree</a> │");
			out.println("       └──────┴──────┴──────┴──────┘");
			out.println("");
			out.println("<font color=\"red\"><sup>*</sup></font><i>Source:</i> <a href=\"http://root.rupy.se/code\">Async</a>, <a href=\"http://root.rupy.se/code?path=/User.java\">User</a>, <a href=\"http://root.rupy.se/code?path=/Root.java\">Root</a>");
			out.println("Host: " + Root.local);
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

				post.post(root, "Host:" + Root.host[i] + "." + host() + "\r\nHead:less\r\nPath:" + path, data);
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
		while(exists(json, type, sort, event, create, ((String) event.query().header("host")).equals("test.rupy.se")) == 0) {
			json = create(event, json);
		}
		return json;
	}

	private static JSONObject create(Event event, JSONObject json) throws Exception {
		boolean test = ((String) event.query().header("host")).equals("test.rupy.se");
		String key = Event.random(test ? 2 : LENGTH);

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
			throw new KeyFail("Root [" + type + "/" + key + "] not found!");
	}

	private static int exists(JSONObject json, String type, String sort, Event event, boolean create, boolean test) throws Exception {
		//if(json.has("root"))
		//	throw new RootFail("contains key 'root'.");

		String key = json.getString("key");
		long id = hash(key);

		if(id < 4) // Reserved for developer
			return 0;

		boolean key_exist = Files.exists(Paths.get(home() + "/node/" + type + "/key" + path(key)));
		boolean id_exist = Files.exists(Paths.get(home() + "/node/" + type + "/id" + path(id)));

		//if(event == null) // TODO: This is to test remote collisions, set LENGTH to 2 and uncomment this.

		boolean skip = false;

		if(test && event != null)
			skip = true;

		if(!skip && create && (key_exist || id_exist))
			return 0;

		if(!test)
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
				if(((state.equals("") || state.equals("0")) && !out_key.equals(key)) || state.equals("-2")) {
					event.query().put(host[i], "2");
					event.query().put("out_" + host[i] + "_key", key);
					send(i, key, encrypted, event, event.query().bit("trace", true));
				}
			}
		}

		return 1;
	}

	public static class KeyFail extends Exception {
		public KeyFail(String message) { super(message); }
	}

	public static class SortFail extends Exception {
		public SortFail(String message) { super(message); }
	}

	public static class LinkFail extends Exception {
		public LinkFail(String message) { super(message); }
	}

	public static class MetaFail extends Exception {
		public MetaFail(String message) { super(message); }
	}

	//public static class RootFail extends Exception {
	//	public RootFail(String message) { super(message); }
	//}

	private static void store(JSONObject json, String type, String sort, boolean create, boolean test) throws Exception {
		if(exists(json, type, sort, null, create, test) == 0)
			throw new KeyFail("Node [" + local + "] collision");

		String path = home() + "/node/" + type + "/id" + path(hash(json.getString("key")));

		new File(path.substring(0, path.lastIndexOf("/"))).mkdirs();

		File file = new File(path);

		if(create)
			file.createNewFile();

		write(file, json);

		sort(json, path, type, sort, create);
	}

	private static void write(File file, JSONObject json) throws Exception {
		BufferedWriter output = new BufferedWriter(new FileWriter(file));
		output.write(json.toString());
		output.close();
	}

	private static void sort(JSONObject json, String path, String type, String sort, boolean create) throws Exception {
		String home = home() + "/node/" + type;
		long id = hash(json.getString("key"));
		String[] name = sort.split(",");

		for(int i = 0; i < name.length; i++) {
			String key = name[i].trim();
			String value = json.getString(key);
			boolean full = false;

			if(value.contains(" ") || key.equals("text"))
				full = true;
			else if(value.matches("[0-9]+") || !value.matches("[a-zA-Z0-9/.@\\-\\+]+") || value.toLowerCase().matches("root"))
				throw new SortFail("Validation [" + key + "=" + json.getString(key) + "]");

			if(full) {
				String[] words = value.toLowerCase().split("\\s+");

				for(int j = 0; j < words.length; j++) {
					String word = words[j];

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
						file.close();
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
					throw new SortFail("Collision [" + key + "=" + json.getString(key) + "]");
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
					send_link(i, data, event);
				}
			}
		}
	}

	private static void meta(JSONObject json, final Event event) throws Exception {
		JSONObject parent = json.getJSONObject("parent");
		JSONObject child = json.getJSONObject("child");
		JSONObject node = json.getJSONObject("json");
		boolean echo = json.optBoolean("echo");
		boolean tear = json.optBoolean("tear");
		String path = json.getString("path");
		String ptype = parent.getString("type");
		String pkey = parent.getString("key");
		String ctype = child.getString("type");
		String ckey = child.getString("key");

		if(event == null) {
			meta(ptype, pkey, ctype, ckey, path, node, echo, tear);
		}
		else {
			byte[] data = Root.encrypt(json.toString());

			for(int i = 0; i < host.length; i++) {
				if(!host[i].equals(local)) {
					send_meta(i, data, event);
				}
			}
		}
	}

	/*
	 * Link parent and child.
	 * Root relations is self referencing lists of node types, so you can get last 10 registered users f.ex.
	 * Atomic relation is node-to-node, both one-to-one and one-to-many, allowing for regular give me all articles written by user f.ex.
	 * These lists have no edit or delete, see meta!
	 */
	private static void link(String ptype, String pkey, String ctype, String ckey) throws Exception {
		String path = home() + "/link/";

		long pid = hash(pkey);
		long cid = ctype.equals("date") ? Long.parseLong(ckey) : hash(ckey);

		if(!ctype.endsWith("date") && !new File(home() + "/node/" + ctype + "/id" + path(cid)).exists())
			throw new LinkFail("Child node doesn't exist.");

		if(!new File(home() + "/node/" + ptype + "/id" + path(pid)).exists())
			throw new LinkFail("Parent node doesn't exist.");

		path += ptype + "/" + ctype + "/" + path(pkey);

		new File(path.substring(0, path.lastIndexOf("/"))).mkdirs();

		RandomAccessFile file = new RandomAccessFile(path, "rw");

		write(file, cid);

		file.close();
	}

	/*
	 * Link parent and child with meta files, potentially with tree structure.
	 */
	private static void meta(String ptype, String pkey, String ctype, String ckey, String path, JSONObject data, boolean echo, boolean tear) throws Exception {
		File parent = new File(home() + "/node/" + ptype + "/key" + path(pkey));
		File child = new File(home() + "/node/" + ctype + "/key" + path(ckey));

		if(!parent.exists())
			throw new MetaFail("Parent node doesn't exist. (" + parent + ")");

		if(!child.exists())
			throw new MetaFail("Child node doesn't exist. (" + child + ")");

		JSONObject p = new JSONObject(file(parent));
		JSONObject c = new JSONObject(file(child));

		String pname = pkey;
		String cname = ckey;

		if(p.has("name"))
			pname = p.getString("name");

		if(c.has("name"))
			cname = c.getString("name");

		String ppname = path(pname);
		String ccname = path(cname);

		pname = "/" + pname;
		cname = "/" + cname;

		String root = home() + "/meta/" + ptype + "/" + ctype + ppname + cname + path;
		String link = home() + "/meta/" + ctype + "/" + ptype + ccname + pname + path;

		if(tear) {
			try {
				new File(root).delete();
				new File(link).delete();
			}
			catch (Exception e) {
				// OK
			}
		}
		else {
			File folder = new File(root.substring(0, root.lastIndexOf("/")));
			folder.mkdirs();
			folder.setLastModified(new Date().getTime());

			BufferedWriter output = new BufferedWriter(new FileWriter(root));
			output.write(data.toString());
			output.close();

			if(echo) {
				new File(link.substring(0, link.lastIndexOf("/"))).mkdirs();
				
				try {
					Files.createLink(Paths.get(link), Paths.get(root));
				}
				catch(FileAlreadyExistsException e) {
					// OK
				}
			}
		}
	}

	// rm -rf
	public static boolean delete(File file) {
		if(file.isDirectory()) {
			String[] files = file.list();

			for(int i = 0; i < files.length; i++) {
				boolean success = delete(new File(file, files[i]));

				if(!success)
					return false;
			}
		}

		return file.delete();
	}

	/*
	 * Finds the references to same nodes in full word index files.
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

	private static void send_link(final int i, final byte[] data, Event event) throws Exception {
		final Async.Work work = new Async.Work(event) {
			public void send(Async.Call post) throws Exception {
				post.post(root, "Host:" + Root.host[i] + "." + host() + "\r\nHead:less\r\nPath:/link", data);
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

	private static void send_meta(final int i, final byte[] data, Event event) throws Exception {
		final Async.Work work = new Async.Work(event) {
			public void send(Async.Call post) throws Exception {
				post.post(root, "Host:" + Root.host[i] + "." + host() + "\r\nHead:less\r\nPath:/meta", data);
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
		MessageDigest md = MessageDigest.getInstance("SHA-512");

		byte[] data = key.getBytes();
		md.update(data, 0, data.length);
		byte[] hash = md.digest();

		long h = 2166136261L;

		for(int i = 0; i < hash.length; i++) {
			h = (h ^ hash[i]) * 16777619;
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
/*
	static String secret;

	static String secret() throws Exception {
		if(secret == null) {
			secret = file("app/" + archive.host() + "/root/secret");
		}

		return secret;
	}
*/
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
		public static int NAME = 1;
		public static int DATE = 2;

		public String path() {
			return null;
		}

		public JSONArray recurse(File file, String full, int from, int size, int level, int deep, final int sort, boolean secure) throws Exception {
			if(deep > -1 && level > deep)
				return null;

			File[] files = file.listFiles();

			if(sort > 0) {
				Arrays.sort(files, new Comparator<File>() {
					public int compare(File a, File b) {
						if(sort == DATE)
							return Long.compare(b.lastModified(), a.lastModified());
						else
							return a.getName().compareTo(b.getName());
					}
				});
			}

			JSONArray arr = new JSONArray();

			if(files.length > 0) {
				int f = 0, s = files.length;

				if(level == 0) {
					f = from;

					if(size > 0)
						s = size;
				}

				int length = f + s;

				if(length > files.length) {
					f -= length - files.length;
					length = files.length;
				}

				if(f < 0) f = 0;

				for(int i = f; i < length; i++) {
					Path path = Paths.get(full + "/" + files[i].getName());

					JSONObject obj = new JSONObject();
					boolean add = false;

					String name = files[i].getName();

					if(!secure && name.length() == 16)
						name = "" + Root.hash(name);

					if(Files.isDirectory(path)) {
						JSONArray child = recurse(path.toFile(), path.toString(), from, size, level + 1, deep, sort, secure);

						if(child != null) {
							add = true;
							obj.put(name, child);
						}
					}
					else {
						add = true;
						obj.put(name, new JSONObject(file(path.toString())));
					}

					if(add)
						arr.put(obj);
				}
			}

			if(arr.length() == 0)
				return null;

			return arr;
		}

		/* Example Public Paths:
		 * 
		 * Node:
		 * - /node/user/key/<key>
		 * 
		 * Link:
		 * - /link/user/data/<key>
		 * 
		 * Meta:
		 * - /meta/user/data/<key>
		 * 
		 * Find:
		 * - /node/data/text/full%20text%20search
		 */
		public void filter(Event event) throws Event, Exception {
			event.query().parse();

			String[] path = event.query().path().split("/");

			String full = "";
			String rule = "";
			String head = "";
			String tail = "";
			String last = "";

			try {
				rule = path[1];
				head = path[2];
				tail = path[3];
			}
			catch(Exception e) {
				//e.printStackTrace();
				fail(event, full, rule, head, tail, last);
			}

			int from = event.query().medium("from", 0);
			int size = event.query().medium("size", rule.equals("meta") ? -1 : 10);
			int deep = event.query().medium("deep", -1);
			int sort = event.query().medium("sort", 0);

			if(size > 50)
				size = 50;

			for(int i = 4; i < path.length; i++) {
				last += path[i];

				if(i < path.length - 1)
					last += "/";
			}

			if(rule.equals("node") && head.equals("user") && (tail.equals("name") || tail.equals("mail") || tail.equals("id"))) {
				event.reply().code("403 Forbidden");
				event.output().print("<pre>Public node/user/" + tail + " is forbidden.</pre>");
				throw event;
			}

			if(rule.equals("link") && head.equals("user") && tail.equals("date") && last.matches("[0-9]+")) {
				event.reply().code("403 Forbidden");
				event.output().print("<pre>Public link/user/date is forbidden.</pre>");
				throw event;
			}

			boolean remove = false; // remove key from node

			try {
				if(rule.equals("link")) { // link list
					if(last.length() > 0) {
						full = home() + "/link/" + head + "/" + tail + Root.path(last);

						if(last.matches("[0-9]+")) {
							full = home() + "/node/" + head + "/id" + Root.path(last, 3);

							File file = new File(full);

							if(file.exists()) {
								JSONObject json = new JSONObject(Root.file(file));
								full = home() + "/link/" + head + "/" + tail + Root.path(json.getString("key"));
								remove = true;
							}
						}
					}
					else {
						event.reply().code("400 Bad Request");
						event.output().print("<pre>Sort without key.</pre>");
						throw event;
					}

					File file = new File(full);

					if(file.exists()) {
						RandomAccessFile raf = new RandomAccessFile(file, "rw");
						write_last(event, tail, raf, head, last, from, size, remove);
						raf.close();
					}
					else {
						fail(event, full, rule, head, tail, last);
					}
				}
				else if(rule.equals("meta")) {
					if(last.length() == 0)
						fail(event, full, rule, head, tail, last);
					
					full = home() + "/meta/" + head + "/" + tail + Root.path(last);

					File file = new File(full);

					boolean found = file.exists() && file.isDirectory();

					if(!found) {
						String[] tree = last.split("/");

						try {
							if(tree.length == 1) {
								long id = Long.parseLong(last);
								JSONObject json = new JSONObject(file(home() + "/node/" + head + "/id" + Root.path(id)));
								String key = json.getString("key");

								if(json.has("name")) {
									String name = json.getString("name");
									full = home() + "/meta/" + head + "/" + tail + Root.path(name);
								}
								else {
									full = home() + "/meta/" + head + "/" + tail + Root.path(key);
								}
							}
							else if(tree.length > 1) {
								long id = Long.parseLong(tree[1]);
								JSONObject json = new JSONObject(file(home() + "/node/" + tail + "/id" + Root.path(id)));
								String key = json.getString("key");

								if(json.has("name")) {
									String name = json.getString("name");
									full = home() + "/meta/" + head + "/" + tail + Root.path(tree[0]) + "/" + name;
								}
								else {
									full = home() + "/meta/" + head + "/" + tail + Root.path(tree[0]) + "/" + key;
								}
							}
						}
						catch(Exception e) {
							JSONObject json = new JSONObject(file(home() + "/node/" + head + "/key" + Root.path(last)));
							String name = json.getString("name");
							full = home() + "/meta/" + head + "/" + tail + Root.path(name);
						}

						file = new File(full);
					}

					if(file.exists() && file.isDirectory()) {
						boolean secure = last.length() == 16 && last.indexOf("/") == -1 && !last.matches("[0-9]+");

						long time = System.currentTimeMillis();

						JSONArray arr = recurse(file, full, from, size, 0, deep, sort, secure);

						File[] files = file.listFiles();
						int length = 0;

						for(int i = 0; i < files.length; i++) {
							Path p = Paths.get(full + "/" + files[i].getName() + "/root");

							if(files[i].isFile())
								length++;
							else if(files[i].isDirectory() && Files.exists(p))
								length++;
						}

						if(arr != null) {
							StringBuilder builder = new StringBuilder();
							builder.append("{\"total\": " + length + ", \"list\":");
							builder.append(arr.toString(4));
							builder.append("}");

							event.reply().type("application/json; charset=UTF-8");
							byte[] data = builder.toString().getBytes("UTF-8");
							Output out = event.reply().output(data.length);
							out.write(data);
						}
						else {
							fail(event, full, rule, head, tail, last);
						}
					}
					else {
						fail(event, full, rule, head, tail, last);
					}
				}
				else {
					full = home() + "/node/" + head + "/" + tail + "/" + last;

					String decoded = decoder.decode(last, "UTF-8");

					if(tail.equals("text")) { // full word search
						full = home() + "/node/" + head + "/" + tail + "/";
						remove = true;

						if(last.contains(" ")) {
							String[] word = last.split("\\s+");

							RandomAccessFile one = new RandomAccessFile(full + word[0], "r");
							RandomAccessFile two = new RandomAccessFile(full + word[1], "r");

							List list = first_compare(one, two);

							for(int i = 2; i < word.length; i++) {
								RandomAccessFile next = new RandomAccessFile(full + word[i], "r");

								list = remove(next, list);

								next.close();
							}

							print_list(event, head, list, null, null, list.size(), remove);

							one.close();
							two.close();
						}
						else {
							full = home() + "/node/" + head + "/" + tail + "/";

							RandomAccessFile raf = new RandomAccessFile(full + last, "r");

							write_last(event, head, raf, null, null, from, size, remove);

							raf.close();
						}
					}
					else { // node sort index
						if(last.matches("[a-zA-Z0-9.@\\-\\+]+"))
							full = home() + "/node/" + head + "/" + tail + Root.path(last);

						if(last.matches("[0-9]+")) {
							full = home() + "/node/" + head + "/" + tail + Root.path(Long.parseLong(last));
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
							fail(event, full, rule, head, tail, last);
						}
					}
				}
			}
			catch(Exception e) {
				e.printStackTrace();
				fail(event, full, rule, head, tail, last);
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
			builder.append("{\"total\": " + total + ", \"list\":[");

			Iterator it = list.iterator();

			while(it.hasNext()) {
				long id = ((Long) it.next()).longValue();

				if(type.equals("date")) {
					builder.append(id);
				}
				else {
					String open = home() + "/node/" + type + "/id" + Root.path(id);

					try {
						JSONObject obj = new JSONObject(file(open));

						if(remove) {
							obj.put("id", hash(obj.getString("key")));
							obj.remove("key");
						}

						//if(type.equals("user"))
						//	obj.remove("pass");

						builder.append(obj.toString(4));
					}
					catch(Exception e) {
						System.out.println("File " + open + " not found.");
					}
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

		private void fail(Event event, String path, String rule, String head, String tail, String last) throws Event, Exception {
			event.reply().code("404 Not Found");
			event.output().print("<pre>" + toCase(rule) + " '" + event.query().path() + "' was not found on host " + local + ".</pre>");

			JSONObject obj = new JSONObject("{\"path\":\"" + path + "\",\"rule\":\"" + rule + "\",\"head\":\"" + head + "\",\"tail\":\"" + tail + "\",\"last\":\"" + last + "\"}");
			System.out.println(obj.toString(4));

			throw event;
		}

		public final static String toCase(String name) {
			return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
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
					out.println("  Parent <input type=\"text\" style=\"width: 100px;\" name=\"pkey\"> " + type(node_type, "ptype", "user") + "<br>");
					out.println("  Child  <input type=\"text\" style=\"width: 100px;\" name=\"ckey\"> " + type(link_type, "ctype", "data") + "<br>");
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

	/* Use this to build tree structured comments.
	 * Requires a "user" key with the users name in the child node json.
	 */
	public static class Tree extends Service {
		public String path() {
			return "/tree";
		}

		public void filter(Event event) throws Event, Exception {
			if(event.push()) {
				for(int i = 0; i < host.length; i++) {
					event.query().put(host[i], "");
				}

				meta((JSONObject) event.query().get("json"), null);
				
				Output out = event.output();
				out.println(event.query().string("result"));
				out.finish();
				out.flush();
			}
			else {
				event.query().parse();

				long node = event.big("node");
				String type = event.string("type");
				String user = event.string("user");
				boolean tear = event.query().bit("tear", false); // delete
				String path = event.string("path");
				String data = event.string("json");

				if(event.query().method() == Query.GET) {
					Output out = event.output();
					out.println("<style> input, select { font-family: monospace; } </style>");
					out.println("<pre>");
					out.println("<form action=\"meta\" method=\"post\">");
					out.println("  Node <input type=\"text\" style=\"width: 100px;\" name=\"node\"> " + type(node_type, "type", "task") + "<br>");
					out.println("  User <input type=\"text\" style=\"width: 100px;\" name=\"user\"><br>");
					out.println("  <input type=\"checkbox\" name=\"tear\"> Tear (Delete!)<br>");
					out.println("  <textarea rows=\"10\" cols=\"50\" name=\"json\">{}</textarea><br>");
					out.println("  Path <input type=\"text\" name=\"path\" value=\"\"><br>");
					out.println("       <input type=\"submit\" value=\"Meta\">");
					out.println("</form>");
					out.println("</pre>");
					throw event;
				}
				else {
					if(path.length() == 0)
						throw new Exception("Path '" + path + "' is too short.");

					if(path.split("/").length < 2)
						throw new Exception("Path '" + path + "' needs atleast one folder.");

					if(path.endsWith("/"))
						throw new Exception("Path '" + path + "' must not end with /.");

					if(!path.startsWith("/"))
						throw new Exception("Path '" + path + "' must begin with /.");

					JSONObject root = new JSONObject(file(home() + "/node/user/key" + Root.path(user)));

					String end = "/" + root.getString("name") + "/root";
					
					JSONObject file = new JSONObject(file(home() + "/node/" + type + "/id" + Root.path(node)));

					String ckey = file.getString("key");

					if(!path.endsWith(end)) {
						throw new Exception("Path '" + path + "' must end with user name.");
					}

					String name = Root.path(file.getString("user"));
					
					File folder = new File(home() + "/meta/user/" + type + name + "/" + ckey + path.substring(0, path.length() - end.length()));
					
					if(!folder.exists()) {
						throw new Exception("Path '" + path + "' parent folder not found.");
					}
					
					root = new JSONObject(file(home() + "/node/user/name" + name));

					String pkey = root.getString("key");

					JSONObject json = new JSONObject(
							"{\"parent\":{\"key\":\"" + pkey + "\",\"type\":\"user\"}," + 
									"\"child\":{\"key\":\"" + ckey + "\",\"type\":\"" + type + "\"}," + 
									"\"json\":" + data + ",\"tear\":" + tear + ",\"path\":\"" + path + "\"}");
					event.query().put("json", json);
					meta(json, event);
				}
			}
		}
	}

	public static class Meta extends Service {
		public String path() {
			return "/meta";
		}

		public void filter(Event event) throws Event, Exception {
			if(event.push()) {
				for(int i = 0; i < host.length; i++) {
					event.query().put(host[i], "");
				}

				meta((JSONObject) event.query().get("json"), null);
				
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
				boolean echo = event.query().bit("echo", false);
				boolean tear = event.query().bit("tear", false); // delete
				String path = event.string("path");
				String data = event.string("json");

				if(path.length() > 0) {
					if(path.endsWith("/"))
						throw new Exception("Path '" + path + "' must not end with /.");

					if(!path.startsWith("/"))
						throw new Exception("Path '" + path + "' must begin with /.");
				}

				if(event.query().method() == Query.GET) {
					Output out = event.output();
					out.println("<style> input, select { font-family: monospace; } </style>");
					out.println("<pre>");
					out.println("<form action=\"meta\" method=\"post\">");
					out.println("  Parent <input type=\"text\" style=\"width: 100px;\" name=\"pkey\"> " + type(node_type, "ptype", "user") + "<br>");
					out.println("  Child  <input type=\"text\" style=\"width: 100px;\" name=\"ckey\"> " + type(meta_type, "ctype", "data") + "<br>");
					out.println("  <input type=\"checkbox\" name=\"echo\"> Echo<br>");
					out.println("  <input type=\"checkbox\" name=\"tear\"" + (tear ? " checked" : "") + "> Tear (Delete!)<br>");
					out.println("  <textarea rows=\"10\" cols=\"50\" name=\"json\">{}</textarea><br>");
					out.println("  Path <input type=\"text\" name=\"path\" value=\"\"><br>");
					out.println("       <input type=\"submit\" value=\"Meta\">");
					out.println("</form>");
					out.println("</pre>");
					throw event;
				}
				else {
					JSONObject json = new JSONObject(
							"{\"parent\":{\"key\":\"" + pkey + "\",\"type\":\"" + ptype + "\"}," + 
									"\"child\":{\"key\":\"" + ckey + "\",\"type\":\"" + ctype + "\"}," + 
									"\"json\":" + data + ",\"echo\":" + echo + ",\"tear\":" + tear + 
									",\"path\":\"" + path + "\"}");
					event.query().put("json", json);
					meta(json, event);
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

								//failed++;
							}
						}
						else if(result.length() > 0) {
							if(result.equals("-2"))
								event.query().put(host[i], "");
							if(result.equals("java.nio.channels.ClosedChannelException"))
								event.query().put(host[i], "-2");
							if(result.startsWith("Root$SortFail")) {
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
							event.query().path().equals("/make") || create,
							((String) event.query().header("host")).equals("test.rupy.se"));

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

					//async(event, collision > 0);
					async(event);
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
						out.println("  <textarea rows=\"10\" cols=\"50\" name=\"json\">{}</textarea><br>");
						out.print("  Type " + type(node_type, "type", "data"));
						out.print(" <input id=\"make\" type=\"checkbox\" name=\"create\" onclick=\"toggle();\"/> Make");
						out.println("  <input type=\"checkbox\" name=\"trace\"> Info<br>");
						out.println("  Comma separated list of JSON keys to index,");
						out.println("  \"text\" key reserved for full word search:<br>");
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
					//async(event, event.query().path().equals("/make") || create);
					async(event);
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

		private void async(Event event) throws Exception {
			boolean create = event.query().bit("create", true);
			if(event.query().path().equals("/make"))
				create = true;
			JSONObject json = Root.sync(event, event.query().string("type", "user"), event.query().string("sort", "key"), create);
			event.query().put("json", json);
		}
	}
}