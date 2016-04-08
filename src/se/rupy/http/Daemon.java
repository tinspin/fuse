package se.rupy.http;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.*;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.text.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.channels.*;
import java.nio.file.LinkPermission;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A tiny HTTP daemon. The whole server is non-static so that you can launch
 * multiple contained HTTP servers in one application on different ports.<br>
 * <br>
 * See the configurations: {@link #Daemon()}
 */

public class Daemon implements Runnable {
	protected String domain = "host.rupy.se";
	private int selected, valid, accept, readwrite; // panel stats
	private TreeMap archive, service;
	private Heart heart;
	private Selector selector;
	private String name, bind;

	Chain workers, queue;
	Properties properties;
	PrintStream out, access, error;
	AccessControlContext control;
	ConcurrentHashMap events, session;
	Deploy.Archive deployer;
	int threads, timeout, cookie, delay, size, port, cache, async_timeout;
	boolean verbose, debug, host, alive, panel, root;
	Async client;

	/* Rupy 1.3 introduces measuring of metrics.
	 * Also see ram on the archive.
	 */
	static class Metric {
		static String header = "<tr><td></td><td>CPU</td><td>RAM</td><td colspan=\"2\">R&R</td><td colspan=\"2\">SSD</td><td colspan=\"2\">NET</td></tr>" +
				"<tr><td></td><td>&mu;s</td><td></td><td>&darr;</td><td>&uarr;</td><td>&darr;</td><td>&uarr;</td><td>&darr;</td><td>&uarr;</td></tr>";

		protected long cpu; // CPU time
		protected Data req = new Data(); // requests and async response chunks
		protected Data ssd = new Data(); // disk operations
		protected Data net = new Data(); // server network traffic
		protected Data cli = new Data(); // client network traffic
		protected long ram; // how much memory allocated; used by {@link #Daemon.metric(String name)} and the heartbeat.
		protected long rom; // how many bytes of class definitions stored.

		class Data {
			long in;
			long out;

			void add(Data data) {
				in += data.in;
				out += data.out;
			}

			public String toString() {
				return in + "</td><td>" + out;
			}
		}

		void add(Metric metric) {
			cpu += metric.cpu;
			ram += metric.ram;

			req.add(metric.req);
			ssd.add(metric.ssd);
			net.add(metric.net);
			cli.add(metric.cli);
		}

		public String toString() {
			return "</td><td>" + cpu/1000 + "</td><td>" + ram + "</td><td>" + req + "</td><td>" + ssd + "</td><td>" + net + "</td>";
		}
	}

	public String domain() {
		return domain;
	}
	
	static Instrumentation inst;

	public static void premain(String agentArgs, Instrumentation inst) {
		Daemon.inst = inst;
	}

	protected static long size(Object o) {
		if(inst == null)
			return 0;

		return size(o, new HashMap(), 0);
	}

	private static String indent(int depth) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < depth; i++)
			builder.append("  ");
		return builder.toString();
	}

	private static long size(Object o, HashMap map, int depth) {
		if(inst == null || map.containsKey(o) || o instanceof Daemon)
			return 0;

		map.put(o, null);

		long size = inst.getObjectSize(o);

		//System.out.println(indent(depth) + " > " + o + " " + size);

		if(o instanceof Object[]) {
			Object[] obj = (Object[]) o;

			for (int i = 0; i < obj.length; i++) {
				size += size(obj, map, depth + 1);
			}
		}
		else {
			Field[] fields = o.getClass().getDeclaredFields();

			for(int i = 0; i < fields.length; i++) {
				Field field = fields[i];
				field.setAccessible(true);

				try {
					Object obj = field.get(o);

					if(!primitive(field.getType())) {
						size += size(obj, map, depth + 1);
					}
				}
				catch(Exception e) {}
			}
		}

		return size;
	}

	private static boolean primitive(Class clazz) {
		if(clazz == java.lang.Boolean.TYPE)
			return true;

		if(clazz == java.lang.Character.TYPE)
			return true;

		if(clazz == java.lang.Byte.TYPE)
			return true;

		if(clazz == java.lang.Short.TYPE)
			return true;

		if(clazz == java.lang.Integer.TYPE)
			return true;

		if(clazz == java.lang.Long.TYPE)
			return true;

		if(clazz == java.lang.Float.TYPE)
			return true;

		if(clazz == java.lang.Double.TYPE)
			return true;

		if(clazz == java.lang.Void.TYPE)
			return true;

		return false;
	}

	/**
	 * Don't forget to call {@link #start()}.
	 */
	public Daemon() {
		this(new Properties());
	}

	/**
	 * Don't forget to call {@link #start()}. You can also use {@link Daemon#init(String[])} which will parse 
	 * the parameters for you. The parameters below should be in the properties argument. The parenthesis 
	 * contains the default value.<br><br>
	 * These are also used from the command line, so for example 'java -cp http.jar se.rupy.http.Daemon 
	 * -pass !@#$ -log -port 80' would enable remote deploy with password '!@#$', run on port 80 (requires 
	 * root on linux) and turn on logging.
	 * <table cellpadding="10">
	 * <tr><td valign="top"><b>pass</b> ()
	 * </td><td>
	 *            the pass used to deploy services with {@link Deploy} via HTTP POST, 
	 *            default is '' which disables hot-deploy; pass 'secret' only allows 
	 *            deploys from 127.0.0.1.
	 * </td></tr>
	 * <tr><td valign="top"><b>port</b> (8000)
	 * </td><td>
	 *            which TCP port.
	 * </td></tr>
	 * <tr><td valign="top"><b>threads</b> (5)
	 * </td><td>
	 *            how many worker threads, the daemon also starts one selector 
	 *            and one heartbeat thread.
	 * </td></tr>
	 * <tr><td valign="top"><b>timeout</b> (300)
	 * </td><td>
	 *            session timeout in seconds or 0 to disable sessions 
	 * </td></tr>
	 * <tr><td valign="top"><b>cookie</b> (4)</td><td>
	 *            session key character length; default and minimum is 4, > 10 can 
	 *            be considered secure.
	 * </td></tr>
	 * <tr><td valign="top"><b>delay</b> (5000)
	 * </td><td>
	 *            milliseconds before started event gets dropped due to inactivity. 
	 *            This is also the dead socket worker cleanup variable, so if 
	 *            a worker has a socket that hasn't been active for longer than 
	 *            this; the worker will be released and the socket deemed as dead.
	 * </td></tr>
	 * <tr><td valign="top"><b>size</b> (1024)</td><td>
	 *            IO buffer size in bytes, should be proportional to the data sizes 
	 *            received/sent by the server currently this is input/output- 
	 *            buffer, chunk-buffer, post-body-max and header-max lengths! :P
	 * </td></tr>
	 * <tr><td valign="top"><b>live</b> (false)
	 * </td><td>
	 *            is this rupy running live.
	 * </td></tr>
	 * <tr><td valign="top"><b>cache</b> (86400) <i>requires</i> <b>live</b>
	 * </td><td valign="top">
	 *            seconds to hard cache static files.
	 * </td></tr>
	 * <tr><td valign="top"><b>verbose</b> (false)
	 * </td><td valign="top">
	 *            to log information about these startup parameters, high-level 
	 *            info for each request and deployed services overview.
	 * </td></tr>
	 * <tr><td valign="top"><b>debug</b> (false)
	 * </td><td>
	 *            to log low-level NIO info for each request and class 
	 *            loading info.
	 * </td></tr>
	 * <tr><td valign="top"><b>log</b> (false)
	 * </td><td>
	 *            simple log of access and error in /log.
	 * </td></tr>
	 * <tr><td valign="top"><b>bind</b> ()
	 * </td><td>
	 *            to bind to a specific ip.
	 * </td></tr>
	 * <tr><td valign="top"><b>async_timeout</b> (1000)
	 * </td><td>
	 *            timeout for the async client in milliseconds.
	 * </td></tr>
	 * <tr><td valign="top"><b>host</b> (false)
	 * </td><td>
	 *            to enable virtual hosting, you need to name the deployment 
	 *            jars [host].jar, for example: <i>host.rupy.se.jar</i>. 
	 *            Also if you want to deploy root domain, just deploy www.[host]; 
	 *            so for example <i>www.rupy.se.jar</i> will trigger <i>http://rupy.se</i>. 
	 *            To authenticate deployments you should use a properties file 
	 *            called <i>passport</i> in the rupy root where you store [host]=[pass].<br><br>
	 *            if your host is a <a href="http://en.wikipedia.org/wiki/Platform_as_a_service">PaaS</a> 
	 *            on <i>one machine</i>; add -Djava.security.manager -Djava.security.policy=policy 
	 *            to the rupy java process, add the passport file to your control domain 
	 *            app folder instead (for example app/host.rupy.se/passport; hide it 
	 *            from downloading with the code below) and create a symbolic link to 
	 *            that in the rupy root.
<tt><br><br>
&nbsp;&nbsp;&nbsp;&nbsp;public static class Secure extends Service {<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;public String path() { return "/passport"; }<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;public void filter(Event event) throws Event, Exception {<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;event.output().print("Nice try!");<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;}<br>
&nbsp;&nbsp;&nbsp;&nbsp;}</tt>
	 * </td></tr>
	 * <tr><td valign="top"><b>domain</b> (host.rupy.se) <i>requires</i> <b>host</b>
	 * </td><td>
	 *            if you are hosting a <a href="http://en.wikipedia.org/wiki/Platform_as_a_service">PaaS</a> 
	 *            <i>across a cluster</i>, you have to hook your control domain app up with 
	 *            {@link Daemon#set(Listener listener)}. And reply "OK" if the "auth" message authenticates with {@link Deploy#hash(File file, String pass, String cookie)}:
<tt><br><br>
&nbsp;&nbsp;&nbsp;&nbsp;{"type": "auth", "file": "[host].jar", "pass": "[pass]", "cookie": "[salt]", "cluster": [true/false]}<br>
<br></tt>
	 *            Then you can propagate the deploy with {@link Deploy#deploy(String host, File file, String pass)} 
	 *            if "cluster" is "false" in a separate thread. But for that to work you also need to answer this 
	 *            message with "OK" for your known individual cluster hosts:
<tt><br><br>
&nbsp;&nbsp;&nbsp;&nbsp;{"type": "host", "file": "[name]"}<br>
<br></tt>
	 *            Where [name] is <i>your.domain.name<b>.jar</b></i>. For example I have two 
	 *            hosts: <i>one.rupy.se</i> and <i>two.rupy.se</i> that belong under <i>host.rupy.se</i> 
	 *            so I need to return "OK" if any of these two specific domains try to deploy.
	 * </td></tr>
	 * <tr><td valign="top"><b>multi</b> (false)
	 * </td><td>
	 *            UDP multicast to all cluster nodes for real-time sync. But for this to work you also need to answer this 
	 *            message with "OK" for your known individual cluster ips:
<tt><br><br>
&nbsp;&nbsp;&nbsp;&nbsp;{"type": "packet", "from": "[ip]"}<br>
<br></tt>
	 * </td></tr>
	 * <tr><td valign="top"><b>root</b> (false)
	 * </td><td>
	 *            Root is a distributed index of JSON objects and graph of their relationships in the filesystem. 
	 *            It supports full text search but has rough naming conventions and no order guarantee, it's a work in progress.
	 * </td></tr>
	 * </table>
	 */
	public Daemon(Properties properties) {
		this.properties = properties;

		threads = Integer.parseInt(properties.getProperty("threads", "5"));
		cookie = Integer.parseInt(properties.getProperty("cookie", "4"));
		port = Integer.parseInt(properties.getProperty("port", "8000"));
		timeout = Integer.parseInt(properties.getProperty("timeout", "300")) * 1000;
		delay = Integer.parseInt(properties.getProperty("delay", "5000"));
		size = Integer.parseInt(properties.getProperty("size", "1024"));
		cache = Integer.parseInt(properties.getProperty("cache", "86400"));
		async_timeout = Integer.parseInt(properties.getProperty("async_timeout", "1000"));

		verbose = properties.getProperty("verbose", "false").toLowerCase()
				.equals("true");
		debug = properties.getProperty("debug", "false").toLowerCase().equals(
				"true");
		host = properties.getProperty("host", "false").toLowerCase().equals(
				"true");
		panel = properties.getProperty("panel", "false").toLowerCase().equals(
				"true");
		root = properties.getProperty("root", "false").toLowerCase().equals(
				"true");
		boolean multi = properties.getProperty("multi", "false").toLowerCase().equals(
				"true");
		boolean dns = properties.getProperty("dns", "false").toLowerCase().equals(
				"true");

		bind = properties.getProperty("bind", null);

		if(multi) {
			try {
				setup();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		if(dns) {
			try {
				dns_setup();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		if(host) {
			domain = properties.getProperty("domain", "host.rupy.se");
			deployer = new Deploy.Archive(this);
			
			PermissionCollection permissions = new Permissions();
			permissions.add(new PropertyPermission("host", "read"));
			permissions.add(new SocketPermission("*", "resolve,connect"));
			
			control = new AccessControlContext(new ProtectionDomain[] {
					new ProtectionDomain(null, permissions)});
		}

		//if(!verbose) {
		//	debug = false;
		//}

		archive = new TreeMap();
		service = new TreeMap();
		session = new ConcurrentHashMap();
		events = new ConcurrentHashMap();

		workers = new Chain();
		queue = new Chain();

		try {
			out = new PrintStream(System.err, true, "UTF-8");

			if(properties.getProperty("log") != null || properties.getProperty("test", "false").toLowerCase().equals(
					"true")) {
				log();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Async client() throws Exception {
		return client;
	}

	/**
	 * Disabled for hosted mode.
	 * @param id
	 * @return the event for async wakeup.
	 */
	public Event event(int id) {
		if(!host) {
			return (Event) events.get(new Integer(id));
		}

		return null;
	}

	/**
	 * @return hostname
	 */
	public String name() {
		if(name == null) {
			try {
				return InetAddress.getLocalHost().getHostName();
			}
			catch(Exception e) {
				return "unavailable";
			}
		}

		return name;
	}

	public Properties properties() {
		return properties;
	}

	protected void log() throws IOException {
		File file = new File("log");

		if(!file.exists()) {
			file.mkdir();
		}

		access = new PrintStream(new FileOutputStream(new File("log/access.txt")), true, "UTF-8");
		error = new PrintStream(new FileOutputStream(new File("log/error.txt")), true, "UTF-8");
	}

	protected void error(final Event e, final Throwable t) throws IOException {
		//t.printStackTrace();

		if(error != null && t != null && !(t instanceof Failure.Close)) {
			if(err != null) {
				try {
					Boolean log = (Boolean) AccessController.doPrivileged(new PrivilegedExceptionAction() {
						public Object run() throws Exception {
							try {
								return new Boolean(err.log(e, t));
							}
							catch(Throwable t) {
								t.printStackTrace();
								return true;
							}
						}
					}, control);

					if(!log.booleanValue())
						return;
				}
				catch(PrivilegedActionException pae) {
					pae.printStackTrace();
				}
			}

			Calendar date = Calendar.getInstance();
			StringBuilder b = new StringBuilder();
			Worker worker = e.worker();
			
			if(worker == null)
				b.append(new SimpleDateFormat("yy-MM-dd HH:mm:ss.SSS").format(date.getTime()));
			else
				b.append(worker.log().format(date.getTime()));
			b.append(' ');
			b.append(e.remote());
			b.append(' ');
			b.append(e.query().path());

			String parameters = e.query().parameters();

			if(parameters != null) {
				b.append(' ');
				b.append(parameters);
			}

			b.append(Output.EOL);

			error.write(b.toString().getBytes("UTF-8"));

			t.printStackTrace(error);
		}
	}

	protected String access(Event event) throws IOException {
		if(access != null && !event.reply().push() && !event.headless) {
			Calendar date = Calendar.getInstance();
			StringBuilder b = new StringBuilder();
			Worker worker = event.worker();
			
			if(worker == null)
				b.append(new SimpleDateFormat("yy-MM-dd HH:mm:ss.SSS").format(date.getTime()));
			else
				b.append(worker.log().format(date.getTime()));
			b.append(' ');
			b.append(event.remote());
			b.append(' ');
			b.append(event.query().path());
			b.append(' ');
			b.append(event.reply().code());

			int length = event.reply().length();

			if(length > 0) {
				b.append(' ');
				b.append(length);
			}

			return b.toString();
		}

		return null;
	}

	protected void access(String row, boolean push) throws IOException {
		if(access != null) {
			StringBuilder b = new StringBuilder();

			b.append(row);

			if(push) {
				b.append(' ');
				b.append('>');
			}

			b.append(Output.EOL);

			access.write(b.toString().getBytes("UTF-8"));
		}
	}

	/**
	 * Starts the selector, heartbeat and worker threads.
	 */
	public void start() {
		try {
			heart = new Heart();

			int threads = Integer.parseInt(properties.getProperty("threads",
					"5"));

			for (int i = 0; i < threads; i++) {
				Worker worker = new Worker(this, i);
				workers.add(worker);

				//System.err.println(worker.index() + "|" + worker.id());
			}

			client = new Async(this, async_timeout, false);
			client.start(threads);

			alive = true;

			Thread thread = new Thread(this, "Daemon-Poll");
			id = thread.getId();
			thread.start();
		} catch (Exception e) {
			e.printStackTrace(out);
		}
	}

	static long id;

	/**
	 * Stops the selector, heartbeat and worker threads.
	 */
	public void stop() {
		Iterator it = workers.iterator();

		while(it.hasNext()) {
			Worker worker = (Worker) it.next();
			worker.stop();
		}

		workers.clear();
		alive = false;
		heart.stop();

		selector.wakeup();
	}

	protected ConcurrentHashMap session() {
		return session;
	}

	public int online() {
		return session.size();
	}

	protected Selector selector() {
		return selector;
	}

	protected void chain(final Deploy.Archive archive) throws Exception {
		Deploy.Archive old = (Deploy.Archive) this.archive.get(archive.name());

		if(old != null) {
			Iterator it = old.service().values().iterator();

			while(it.hasNext()) {
				final Service service = (Service) it.next();

				try {
					if(host) {
						Thread.currentThread().setContextClassLoader(archive);
						AccessController.doPrivileged(new PrivilegedExceptionAction() {
							public Object run() throws Exception {
								try {
									service.destroy();
								}
								catch(Throwable t) {
									t.printStackTrace();
								}
								return null;
							}
						}, archive.access());
					}
					else {
						service.destroy();
					}
				} catch (Exception e) {
					e.printStackTrace(out);
				}
			}
		}

		Iterator it = archive.service().values().iterator();

		while(it.hasNext()) {
			Service service = (Service) it.next();
			add(archive.chain(), service, archive);
		}

		this.archive.put(archive.name(), archive);
	}

	/* Sums up all metrics for an archive.
	 * For host to create bill.
	 */
	public Daemon.Metric metric(String name) {
		Metric metric = new Metric();
		Deploy.Archive archive = (Deploy.Archive) this.archive.get(name);
		Iterator it = archive.service().values().iterator();

		while(it.hasNext()) {
			Service service = (Service) it.next();
			Iterator it2 = service.metric.values().iterator();

			while(it2.hasNext()) {
				Metric m = (Metric) it2.next();
				metric.add(m);
			}
		}

		metric.rom = archive.rom;

		return metric;
	}

	public Deploy.Archive archive(String name, boolean deployer) {
		if(!name.endsWith(".jar")) {
			name += ".jar";
		}

		if(host) {
			if(deployer) {
				if(name.equals(domain + ".jar")) {
					return this.deployer;
				}
			}

			try {
				String message = "{\"type\": \"host\", \"file\": \"" + name + "\"}";
				String ok = (String) send(null, message);

				if(ok.equals("OK")) {
					if(deployer) {
						return this.deployer;
					}
					else {
						return (Deploy.Archive) this.archive.get(domain + ".jar");
					}
				}
			}
			catch(Exception e) {
				// TODO: Here chaining fails...
				e.printStackTrace();
			}

			Deploy.Archive archive = (Deploy.Archive) this.archive.get(name);

			if(archive == null) {
				archive = (Deploy.Archive) this.archive.get("www." + name);

				if(archive == null) {
					String base = name.substring(name.indexOf('.') + 1, name.length());

					//System.out.println(base);

					archive = (Deploy.Archive) this.archive.get(base);

					if(archive == null) {
						archive = (Deploy.Archive) this.archive.get("www." + base);
					}
				}
			}

			//System.out.println(archive);

			return archive;
		}
		else {
			return (Deploy.Archive) this.archive.get(name);
		}
	}

	private Listener listener;
	private Chain listeners; // ClusterListeners

	private ErrorListener err;
	private DNSListener dns;
	private Com com;

	/**
	 * Send Object to JVM listener. We recommend you only send bootclasspath loaded 
	 * classes here otherwise hotdeploy will fail.
	 * 
	 * @param event if applicable attach event
	 * @param message to send
	 * @return reply message
	 * @throws Exception
	 */
	public Object send(Event event, Object message) throws Exception {
		if(listener == null) {
			return message;
		}

		return listener.receive(event, message);
	}

	/**
	 * Intra JVM many-to-one listener. Used on cluster for domain 
	 * controller, use multicast on cluster instead.
	 * @return true if successful.
	 * @param listener
	 * @return success
	 */
	public boolean set(Listener listener) {
		try {
			secure();
			this.listener = listener;
			return true;
		}
		catch(IOException e) {
			// if passport could not be created
		}

		return false;
	}

	/**
	 * Set the host controller as DNS server.
	 * @param dns
	 * @return success
	 */
	public boolean set(DNSListener dns) {
		try {
			secure();
			this.dns = dns;
			return true;
		}
		catch(IOException e) {
			// if passport could not be created
		}

		return false;
	}

	/*
	 * So only the controller can be added as listener since we use this feature to authenticate deployments and DNS.
	 */
	void secure() throws IOException {
		if(host) {
			//TODO: Replace with classloader test.

			File pass = new File("app/" + domain + "/passport");

			if(!pass.exists()) {
				pass.createNewFile();
			}

			pass.canRead();
		}
	}

	/**
	 * Cross class-loader communication interface. So that a class deployed 
	 * in one archive can send messages to a class deployed in another.
	 */
	public interface Listener {
		/**
		 * @param event if applicable attach event
		 * @param message most likely a json string
		 * @return the reply message to the sender.
		 * @throws Exception
		 */
		public Object receive(Event event, Object message) throws Exception;
	}

	/**
	 * Receives COM port data.
	 */
	public interface Listen {
		public void read(byte[] data, int length) throws Exception;
	}

	/**
	 * COM port.
	 */
	public static abstract class Com {
		public Listen listen;

		/**
		 * To set the hot-deploy as listener.
		 */
		public void set(Listen listen) {
			this.listen = listen;
		}

		public abstract void write(byte[] data) throws Exception;
	}

	/**
	 * To get the COM port.
	 */
	public Com com() {
		return com;
	}

	/**
	 * To use the COM interface you should boot rupy with your own main method. 
	 * And call {@link Daemon#init(String[])} from there to be able to use this method.
	 * Don't forget to call {@link Daemon#start()} after you used this method.
	 * @param com
	 */
	public void set(Com com) {
		this.com = com;
	}

	/**
	 * Cross cluster-node communication interface. So that applications deployed 
	 * on one node can send messages to instances deployed in other nodes.
	 * @author Marc
	 */
	public interface ClusterListener {
		/**
		 * @param message the message starts with header:
		 * [host].[node], so for example; if I send a message 
		 * from cluster node <i>one</i> ({@link Daemon#name()}) 
		 * with application <i>host.rupy.se</i> the first bytes would be 
		 * 'se.rupy.host.one' followed by payload.
		 * Max length is 1024 bytes!
		 * @throws Exception
		 */
		public void receive(byte[] message) throws Exception;
	}

	/**
	 * So that you can hot-deploy your DNS solution. For some reason, even if you assign 
	 * the right permissions, the receive method on the DatagramSocket will not return if 
	 * it is started from the hot-deployed classloader!?
	 */
	public interface DNSListener {
		/**
		 * @param message The incoming DNS question message.
		 * @return The outgoing DNS answer message.
		 * @throws Exception
		 */
		public byte[] receive(byte[] message) throws Exception;
	}

	/**
	 * Error listener, so you can for example send a warning mail and swallow 
	 * certain exceptions to not be logged.
	 */
	public interface ErrorListener {
		/**
		 * Here you will receive all errors before they are logged.
		 * @param e the responsible
		 * @param t the stack trace
		 * @return true if you wan't this error logged.
		 * @throws Exception
		 */
		public boolean log(Event e, Throwable t);
	}

	/**
	 * Listens for errors.
	 * @param err
	 * @return success
	 */
	public boolean set(ErrorListener err) {
		try {
			secure();
			this.err = err;
			return true;
		}
		catch(IOException e) {
			// if passport could not be created
		}

		return false;
	}
	
	/**
	 * Send inter-cluster-node UDP multicast message.
	 * @param tail your payload.
	 * Max length is 1024 bytes including header: [host].[node]!
	 */
	public void broadcast(byte[] tail) throws Exception {
		if(socket != null) {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();

			if(!(loader instanceof Deploy.Archive)) {
				throw new Exception("You can only broadcast from worker threads!");
			}

			Deploy.Archive archive = (Deploy.Archive) loader;

			byte[] head = (name() + "." + archive.host()).getBytes();

			if(head.length + tail.length > 1024) {
				throw new Exception("Message is too long (" + archive.host() + " " + tail.length + ").");
			}

			byte[] data = new byte[head.length + tail.length];

			System.arraycopy(head, 0, data, 0, head.length);
			System.arraycopy(tail, 0, data, head.length, tail.length);

			socket.send(new DatagramPacket(data, data.length, address, 8888));
		}
	}

	/**
	 * Add multicast listener.
	 * @param listener
	 */
	public void add(ClusterListener listener) {
		if(listeners != null) {
			listeners.add(listener);
		}
	}

	/**
	 * Remove multicast listener.
	 * @param listener
	 */
	public void remove(ClusterListener listener) {
		if(listeners != null) {
			listeners.remove(listener);
		}
	}

	DatagramSocket dns_socket;

	private void dns_setup() throws IOException {
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					byte[] data = new byte[512];
					DatagramPacket packet = new DatagramPacket(data, data.length);
					dns_socket = new DatagramSocket(53);

					while(true) {
						dns_socket.receive(packet);

						if(dns != null) {
							final byte[] request = packet.getData();

							byte[] response = (byte[]) AccessController.doPrivileged(new PrivilegedExceptionAction() {
								public Object run() throws Exception {
									try {
										return dns.receive(request);
									}
									catch(Throwable t) {
										t.printStackTrace();
										return null;
									}
								}
							}, control);

							if(response != null)
								dns_socket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
						}
					}
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		}, "DNS");

		thread.start();
	}

	DatagramSocket socket;
	InetAddress address;

	private void setup() throws IOException {
		listeners = new Chain();
		address = InetAddress.getByName("224.2.2.3");
		socket = new DatagramSocket();

		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					MulticastSocket socket = new MulticastSocket(8888);
					socket.joinGroup(address);

					byte[] empty = new byte[1024];
					byte[] data = new byte[1024];
					DatagramPacket packet = new DatagramPacket(data, data.length);

					while(true) {
						socket.receive(packet);

						synchronized (listeners) {
							Iterator it = listeners.iterator();

							while(it.hasNext()) {
								final ClusterListener listener = (ClusterListener) it.next();
								final byte[] message = data;

								AccessController.doPrivileged(new PrivilegedExceptionAction() {
									public Object run() throws Exception {
										try {
											listener.receive(message);
										}
										catch(Throwable t) {
											t.printStackTrace();
										}

										return null;
									}
								}, control);
							}
						}

						System.arraycopy(empty, 0, data, 0, 1024);
					}
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		}, "UDP-Multicast");

		thread.start();
	}

	/**
	 * You can only add services manually in unhosted mode.
	 * @param service
	 * @throws Exception
	 */
	public void add(Service service) throws Exception {
		if(host)
			throw new Exception("You can't add services manually in hosted mode.");
		else
			add(this.service, service, null);
	}

	protected void add(TreeMap map, final Service service, final Deploy.Archive archive) throws Exception {
		String path = null;

		if(host) {
			Thread.currentThread().setContextClassLoader(archive);
			path = (String) AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws Exception {
					try {
						return service.path();
					}
					catch(Throwable t) {
						t.printStackTrace();
						return "";
					}
				}
			}, control);
		}
		else {
			path = service.path();
		}

		if(path == null) {
			path = "null";
		}

		StringTokenizer paths = new StringTokenizer(path, ":");

		while(paths.hasMoreTokens()) {
			path = paths.nextToken();
			Chain chain = (Chain) map.get(path);

			if(chain == null) {
				chain = new Chain();
				map.put(path, chain);
			}

			final Service old = (Service) chain.put(service);

			if(host) {
				final String p = path;
				Thread.currentThread().setContextClassLoader(archive);
				AccessController.doPrivileged(new PrivilegedExceptionAction() {
					public Object run() throws Exception {
						if(old != null) {
							int index = 0;

							try {
								index = service.index();
							}
							catch(Throwable t) {
								t.printStackTrace();
							}

							throw new Exception(service.getClass().getName()
									+ " with path '" + p + "' and index ["
									+ index + "] is conflicting with "
									+ old.getClass().getName()
									+ " for the same path and index.");
						}

						return null;
					}
				}, control);
			}
			else {
				if(old != null) {
					throw new Exception(service.getClass().getName()
							+ " with path '" + path + "' and index ["
							+ service.index() + "] is conflicting with "
							+ old.getClass().getName()
							+ " for the same path and index.");
				}
			}

			if(verbose)
				out.println(path + padding(path) + chain);

			try {
				if(host) {
					final Daemon daemon = this;
					Thread.currentThread().setContextClassLoader(archive);
					Event e = (Event) AccessController.doPrivileged(new PrivilegedExceptionAction() {
						public Object run() throws Exception {
							try {
								service.create(daemon);
							}
							catch(Throwable t) {
								t.printStackTrace();
							}

							return null;
						}
					}, archive == null ? control : archive.access());
				}
				else {
					service.create(this);
				}
			} catch (Exception e) {
				e.printStackTrace(out);
			}
		}
	}

	protected String padding(String path) {
		StringBuffer buffer = new StringBuffer();

		for(int i = 0; i < 10 - path.length(); i++) {
			buffer.append(' ');
		}

		return buffer.toString();
	}

	protected void verify(final Deploy.Archive archive) throws Exception {
		Iterator it = archive.chain().keySet().iterator();

		while(it.hasNext()) {
			final String path = (String) it.next();
			Chain chain = (Chain) archive.chain().get(path);

			for (int i = 0; i < chain.size(); i++) {
				final Service service = (Service) chain.get(i);

				if(host) {
					final TreeMap a = this.archive;
					final int j = i;
					Thread.currentThread().setContextClassLoader(archive);
					AccessController.doPrivileged(new PrivilegedExceptionAction() {
						public Object run() throws Exception {
							if(j != service.index()) {
								a.remove(archive.name());

								int index = 0;

								try {
									index = service.index();
								}
								catch(Throwable t) {
									t.printStackTrace();
								}

								throw new Exception(service.getClass().getName()
										+ " with path '" + path + "' has index ["
										+ index + "] which is too high.");
							}

							return null;
						}
					}, control);
				}
				else {
					if(i != service.index()) {
						this.archive.remove(archive.name());
						throw new Exception(service.getClass().getName()
								+ " with path '" + path + "' has index ["
								+ service.index() + "] which is too high.");
					}
				}
			}
		}
	}

	protected Deploy.Stream content(Query query) {
		if(host) {
			return content(query.header("host"), query.path());
		}
		else {
			return content(query.path());
		}
	}

	protected Deploy.Stream content(String path) {
		return content("content", path);
	}

	protected Deploy.Stream content(String host, String path) {
		if(!this.host) {
			host = "content";
		}

		File file = new File("app" + File.separator + host + File.separator + path);

		if(file.exists() && !file.isDirectory()) {
			return new Deploy.Big(file);
		}

		if(this.host) {
			file = new File("app" + File.separator + "www." + host + File.separator + path);

			if(file.exists() && !file.isDirectory()) {
				return new Deploy.Big(file);
			}

			String base = host.substring(host.indexOf('.') + 1, host.length());

			//System.out.println(base);

			file = new File("app" + File.separator + base + File.separator + path);

			if(file.exists() && !file.isDirectory()) {
				return new Deploy.Big(file);
			}

			file = new File("app" + File.separator + "www." + base + File.separator + path);

			if(file.exists() && !file.isDirectory()) {
				return new Deploy.Big(file);
			}

			try {
				String message = "{\"type\": \"host\", \"file\": \"" + host + ".jar\"}";
				String ok = (String) send(null, message);

				if(ok.equals("OK")) {
					file = new File("app" + File.separator + domain + path);

					if(file.exists() && !file.isDirectory()) {
						return new Deploy.Big(file);
					}
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			
			file = new File("res" + path);

			if(file.exists() && !file.isDirectory()) {
				return new Deploy.Big(file);
			}
		}

		return null;
	}

	protected void queue(Event event) {
		queue.add(event);
	}
	
	protected Lock chain(Event event) {
		if(host) {
			return chain(event.query().header("host"), event.query().path(), event.push());
		}
		else {
			return chain("content", event.query().path(), event.push());
		}
	}

	public Lock chain(Event event, String path) {
		if(host) {
			return chain(event.query().header("host"), path, event.push());
		}
		else {
			return chain("content", path, event.push());
		}
	}

	public class Lock {
		Chain chain;
		boolean root;

		protected Lock(Chain chain, boolean root) {
			this.chain = chain;
			this.root = root;
		}
		
		public void filter(final Event event) throws Event, Exception {
			chain.filter(event);
		}
	}

	protected Lock chain(String host, String path, boolean wakeup) {
		if(!this.host) {
			host = "content";
		}
		else if(host == null) {
			return null;
		}

		synchronized (this.archive) {
			if(this.host) {
				Deploy.Archive archive = (Deploy.Archive) this.archive.get(host + ".jar");

				if(archive == null) {
					archive = (Deploy.Archive) this.archive.get("www." + host + ".jar");
				}

				if(archive == null) {
					String base = host.substring(host.indexOf('.') + 1, host.length());

					//System.out.println(base);

					archive = (Deploy.Archive) this.archive.get(base + ".jar");

					if(archive == null) {
						archive = (Deploy.Archive) this.archive.get("www." + base + ".jar");
					}

					if(archive == null) {
						try {
							String message = "{\"type\": \"host\", \"file\": \"" + host + ".jar\"}";
							String ok = (String) send(null, message);

							if(ok.equals("OK")) {
								archive = (Deploy.Archive) this.archive.get(domain + ".jar");
							}
						}
						catch(Exception e) {
							e.printStackTrace();
						}
					}
				}

				if(archive != null) {
					Chain chain = (Chain) archive.chain().get(path);

					if(chain != null) {
						return new Lock(chain, false);
					}
				}
			}
			else {
				Iterator it = this.archive.values().iterator();

				while(it.hasNext()) {
					Deploy.Archive archive = (Deploy.Archive) it.next();

					if(archive.host().equals(host)) {
						Chain chain = (Chain) archive.chain().get(path);

						if(chain != null) {
							return new Lock(chain, false);
						}
						/* COM port proxy stuff, breaks sprout talk
						else if(wakeup) {
							chain = (Chain) archive.chain().get("null");

							if(chain != null) {
								return new Lock(chain, false);
							}
						}
						*/
					}
				}
			}
		}

		if(root && path.equals("null"))
			return null;

		synchronized (this.service) {
			Chain chain = (Chain) this.service.get(path);

			if(chain != null) {
				return new Lock(chain, true);
			}
		}

		return null;
	}

	protected Lock root() {
		Chain chain = (Chain) this.service.get("null");

		if(chain != null) {
			return new Lock(chain, true);
		}

		return null;
	}

	private String metric(Deploy.Archive archive, String path) {
		Chain chain = (Chain) archive.chain().get(path);
		Daemon.Metric metric = new Daemon.Metric();

		if(chain != null) {
			Iterator it = chain.iterator();

			while(it.hasNext()) {
				Service service = (Service) it.next();
				Daemon.Metric part = (Daemon.Metric) service.metric.get(path);

				//System.out.println(path + " " + part);

				if(part != null) {
					Daemon.Metric ram = (Daemon.Metric) service.metric.get("RAM");

					if(ram != null)
						metric.add(ram);

					metric.add(part);
				}
			}

			//System.out.println("api " + metric.hashCode() + " " + metric);

			return metric.toString();
		}

		return null;
	}

	public void run() {
		String pass = properties.getProperty("pass", "");
		ServerSocketChannel server = null;

		try {
			selector = Selector.open();
			server = ServerSocketChannel.open();

			if(bind == null)
				server.socket().bind(new InetSocketAddress(port));
			else
				server.socket().bind(new InetSocketAddress(bind, port));

			server.configureBlocking(false);
			server.register(selector, SelectionKey.OP_ACCEPT);

			DecimalFormat decimal = (DecimalFormat) DecimalFormat.getInstance();
			decimal.applyPattern("#.##");

			if(verbose) {
				boolean live = properties.getProperty("live", "false").toLowerCase().equals("true");

				out.println("daemon started\n" + "- pass       \t"
						+ pass + "\n" + "- port       \t" + port + "\n"
						+ "- worker(s)  \t" + threads + " thread"
						+ (threads > 1 ? "s" : "") + "\n" + 
						"- session    \t" + cookie + " characters\n" + 
						"- timeout    \t"
						+ decimal.format((double) timeout / 60000) + " minute"
						+ (timeout / 60000 > 1 ? "s" : "") + "\n"
						+ "- IO timeout \t" + delay + " ms." + "\n"
						+ "- IO buffer  \t" + size + " bytes\n"
						+ "- debug      \t" + debug + "\n"
						+ "- live       \t" + live
						);

				if(live)
					out.println("- cache      \t" + cache);

				out.println("- host       \t" + host);

				if(host)
					out.println("- domain     \t" + domain);
			}

			if(pass != null && pass.length() > 0 || host) {
				Service deploy = null;

				if(host) {
					deploy = new Deploy("app" + File.separator);
				}
				else {
					deploy = new Deploy("app" + File.separator, pass);
				}

				add(this.service, deploy, null);

				File[] app = new File(Deploy.path).listFiles(new Filter());
				File domain = null;

				if(app != null) {
					if(host) {
						domain = new File("app" + File.separator + this.domain + ".jar");
						Deploy.deploy(this, domain, null);
					}

					for (int i = 0; i < app.length; i++) {
						try {
							if(host) {
								if(!app[i].getPath().equals(domain.getPath())) {
									Deploy.deploy(this, app[i], null);
								}
							}
							else {
								Deploy.deploy(this, app[i], null);
							}
						}
						catch(Error e) {
							e.printStackTrace();
						}
					}
				}
			}

			new Thread(heart, "Daemon-Heartbeat").start(); // moved this to get metrics immediately during development.

			/*
			 * Used to debug thread locks and file descriptor leaks.
			 */

			if(panel) {
				Service panel = new Service() {
					public String path() { return "/panel"; }
					public void filter(Event event) throws Event, Exception {
						int width = 50;

						Output out = event.output();
						out.println("<pre>");
						out.println("<table><tr><td align=\"center\">Event</td><td></td><td align=\"center\">Worker</td></tr><tr><td valign=\"top\">");

						Iterator it = events.values().iterator();
						out.println("<table><tr><td width=\"" + width + "\">id</td><td width=\"" + width + "\">init</td><td width=\"" + width + "\">push</td><td width=\"" + width + "\">done</td><td width=\"" + width + "\">last</td><td width=\"" + width + "\">worker</td></tr>");
						out.println("<tr><td colspan=\"6\" bgcolor=\"#000000\"></td></tr>");
						while(it.hasNext()) {
							Event e = (Event) it.next();
							out.println("<tr><td>" + e.index() + "</td><td>" + (e.reply().output.init ? "1" : "0") + "</td><td>" + (e.push() ? "1" : "0") + "</td><td>" + (e.reply().output.done ? "1" : "0") + "</td><td>" + (System.currentTimeMillis() - e.last()) + "</td><td>" + (e.worker() == null ? "" : "" + e.worker().index()) + "</td></tr>");
						}
						out.println("</table>");

						out.println("</td><td></td><td valign=\"top\">");

						out.println("<table><tr><td width=\"" + width + "\">id</td><td width=\"" + width + "\">busy</td><td width=\"" + width + "\">lock</td><td width=\"" + width + "\">event</td></tr>");
						out.println("<tr><td colspan=\"4\" bgcolor=\"#000000\"></td></tr>");
						it = workers.iterator();
						while(it.hasNext()) {
							Worker worker = (Worker) it.next();
							Event e = worker.event();
							out.println("<tr><td>" + worker.index() + "</td><td>" + (worker.busy() ? "1" : "0") + "</td><td>" + worker.lock() + "</td><td>" + (e == null ? "" : "" + e.index()) + "</td></tr>");
						}

						out.println("</table></td></tr><tr><td colspan=\"3\" align=\"center\">selected: " + selected + ", valid: " + valid + ", accept: " + accept + ", readwrite: " + readwrite + "</td></tr></table>");
						out.println("</pre>");
					}
				};

				Service debug = new Service() {
					public String path() { return "/debug"; }
					public void filter(Event event) throws Event, Exception {
						if(client != null)
							event.output().print(client.debug());
						else
							event.output().print("Async client not started yet.");
					}
				};

				Service api = new Service() {
					public String path() { return "/api"; }
					public void filter(Event event) throws Event, Exception {
						event.query().parse();
						boolean files = event.bit("files");
						Iterator it = archive.values().iterator();
						Output out = event.output();
						out.println("<pre>");
						out.println("<table cellspacing=\"0\" cellpadding=\"2\">");

						if(!event.query().header("host").equals(domain))
							out.println(Metric.header);

						while(it.hasNext()) {
							Deploy.Archive archive = (Deploy.Archive) it.next();
							boolean host = !archive.host().equals("content");
							String title = host ? archive.host() : archive.name();
							String name = "localhost";

							if(host) {
								name = event.query().header("host");
							}

							if(name.equals(domain)) {
								if(!title.equals(domain)) {
									out.println("<tr><td>");
									out.println("<a href=\"http://" + title + "/api\">" + title + "</a>");
									out.println("</td></tr>");
								}
							}
							else if(name.equals("localhost") || name.equals(archive.host())) {
								String s = metric(archive, "/");

								if(s == null)
									s = metric(archive, "null");

								if(s == null)
									s = "</td><td colspan=\"8\"></td>";

								if(host)
									out.println("<tr><td><a href=\"http://" + title + "\">" + title + "</a>" + "&nbsp;" + archive.rom + "&nbsp;" + s + "</td></tr>");
								else
									out.println("<tr><td>" + title + "&nbsp;" + archive.rom + "&nbsp;" + s + "</td></tr>");

								Iterator it2 = archive.chain().keySet().iterator();

								while(it2.hasNext()) {
									String path = (String) it2.next();

									if(path.startsWith("/") && path.length() > 1) {
										out.println("<tr><td>&nbsp;&nbsp;<a href=\"" + path + "\">" + path + "</a>&nbsp;" + metric(archive, path) + "</tr>");
									}
								}

								if(files) {
									Iterator it3 = archive.files().keySet().iterator();

									while(it3.hasNext()) {
										String path = (String) it3.next();
										Metric metric = (Metric) archive.files().get(path);
										out.println("<tr><td>&nbsp;&nbsp;<a href=\"" + path + "\">" + path + "</a>&nbsp;" + metric + "</tr>");
									}
								}
							}
						}

						out.println("</table>");
						out.println("</pre>");
					}
				};

				add(this.service, panel, null);
				add(this.service, debug, null);
				add(this.service, api, null);
			}

			if(root) {
				Properties prop = new Properties();
				prop.load(new FileInputStream("root.txt"));
				add(this.service, new Root(domain, prop), null);
				add(this.service, new Root.Node(), null);
				add(this.service, new Root.Link(), null);
				add(this.service, new Root.Find(), null);
				add(this.service, new Root.Meta(), null);
				add(this.service, new Root.Tree(), null);
				add(this.service, new Root.Salt(), null);
				add(this.service, new User(), null);
			}

			if(properties.getProperty("test", "false").toLowerCase().equals("true")) {
				new Test(this, 1);
			}
		} catch (Exception e) {
			e.printStackTrace(out);
			System.exit(1);
		}

		int index = 0;
		Event event = null;
		SelectionKey key = null;

		while(alive) {
			try {
				selector.select();

				Set set = selector.selectedKeys();
				int valid = 0, accept = 0, readwrite = 0, selected = set.size();
				Iterator it = set.iterator();

				while(it.hasNext()) {
					key = (SelectionKey) it.next();
					it.remove();

					if(key.isValid()) {
						valid++;
						if(key.isAcceptable()) {
							accept++;
							event = new Event(this, key, index++);
							events.put(new Integer(event.index()), event);

							if(Event.LOG) {
								event.log("accept ---");
							}
						} else if(key.isReadable() || key.isWritable()) {
							readwrite++;
							key.interestOps(0);

							event = (Event) key.attachment();
							Worker worker = event.worker();

							if(Event.LOG) {
								if(debug) {
									if(key.isReadable())
										event.log("read ---");
									if(key.isWritable())
										event.log("write ---");
								}
							}

							if(key.isReadable() && event.push()) {
								event.disconnect(null);
							} else if(worker == null) {
								match(event, null, false);
							} else {
								worker.wakeup(false);
							}
						}
					}
				}

				this.valid = valid;
				this.accept = accept;
				this.readwrite = readwrite;
				this.selected = selected;
			} catch (Exception e) {
				/*
				 * Here we get mostly ClosedChannelExceptions and
				 * java.io.IOException: 'Too many open files' when the server is
				 * taking a beating. Better to drop connections than to drop the
				 * server.
				 */
				if(event == null) {
					System.out.println(events + " " + key);
				}
				else {
					event.disconnect(e);
				}
			}
		}

		try {
			if(selector != null) {
				selector.close();
			}
			if(server != null) {
				server.close();
			}
		} catch (IOException e) {
			e.printStackTrace(out);
		}
	}

	private synchronized Event next() {
		if(queue.size() > 0) {
			Event event = (Event) queue.poll();

			while(queue.size() > 0 && event != null && event.worker() != null) {
				event = (Event) queue.poll();
			}

			return event;
		}

		return null;
	}

	protected synchronized Worker employ(Event event) {
		workers.reset();
		Worker worker = (Worker) workers.next();

		if(worker == null) {
			queue.add(event);
			return null;
		}

		while(worker.busy()) {
			worker = (Worker) workers.next();

			if(worker == null) {
				queue.add(event);
				return null;
			}
		}

		return worker;
	}

	protected synchronized int match(Event event, Worker worker, boolean auto) {
		if(event.wakeup) {
			event.wakeup = false;
			return -1;
		}
		
		boolean wakeup = true;
		
		if(event != null && worker != null) {
			// The order here matters a lot, see below!
			worker.event(null);
			event.worker(null);
			
			try {
				event.register(Event.READ);
			}
			catch(CancelledKeyException e) {
				event.disconnect(e);
			}
			
			wakeup = false;
			event = null;
		}
		else if(event.worker() != null) {
			if(auto)
				event.wakeup = auto;
			return 1;
		}

		if(worker == null) {
			worker = employ(event);

			if(worker == null) {
				return 2;
			}
		}
		else if(event == null) {
			event = next();

			if(event == null) {
				return 3;
			}
			else if(event.worker() != null) {
				return event.worker() == worker ? 0 : 4;
			}
		}

		if(Event.LOG) {
			if(debug)
				out.println("event " + event.index()
						+ " and worker " + worker.index()
						+ " found each other. (" + queue.size() + ")");
		}

		// The order here matters a lot, see above!
		event.worker(worker);
		worker.event(event);

		if(wakeup) {
			worker.wakeup(true);
		}

		return 0;
	}

	class Filter implements FilenameFilter {
		public boolean accept(File dir, String name) {
			if(name.endsWith(".jar")) {
				return true;
			}

			return false;
		}
	}

	protected void log(PrintStream out) {
		if(out != null) {
			this.out = out;
		}
	}

	protected void log(Object o) {
		if(out != null) {
			out.println(o);
		}
	}

	class Heart implements Runnable {
		boolean alive;

		Heart() {
			alive = true;
		}

		protected void stop() {
			alive = false;
		}

		public void run() {
			int socket = 300000;
			int timer = 60 * 60 + 1;

			if(timeout > 0) {
				socket = timeout;
			}

			while(alive) {
				try {
					Thread.sleep(1000);

					Iterator it = null;

					if(timeout > 0) {
						it = session.values().iterator();

						while(it.hasNext()) {
							Session se = (Session) it.next();

							if(System.currentTimeMillis() - se.date() > timeout) {
								it.remove();
								se.remove();

								if(Event.LOG) {
									if(debug)
										out.println("session timeout "
												+ se.key());
								}
							}
						}
					}

					it = workers.iterator();

					while(it.hasNext()) {
						Worker worker = (Worker) it.next();
						worker.busy();
					}

					it = events.values().iterator();

					while(it.hasNext()) {
						Event event = (Event) it.next();

						if(System.currentTimeMillis() - event.last() > socket) {
							event.disconnect(null);
						}
					}

					if(host && timer > 60 * 60) { // Once per hour
						it = archive.values().iterator();

						while(it.hasNext()) {
							Deploy.Archive archive = (Deploy.Archive) it.next();

							if(archive != null && archive.name() != null) {
								long time = System.currentTimeMillis();
								Iterator it2 = archive.service().values().iterator();

								while(it2.hasNext()) {
									Service service = (Service) it2.next();
									long size = Daemon.size(service);

									Daemon.Metric metric = (Daemon.Metric) service.metric.get("RAM");

									if(metric == null) {
										metric = new Daemon.Metric();
										service.metric.put("RAM", metric);
									}

									metric.ram = size;

									//System.out.println(archive.name() + service.path() + " " + (System.currentTimeMillis() - time) + " " + size);
								}
							}
						}

						timer = 0;
					}

					timer++;
				} catch (Exception e) {
					e.printStackTrace(out);
				}
			}
		}
	}

	/**
	 * To use your own main method if you for example need COM port handling.
	 * Don't forget to call {@link Daemon#start()} after you called {@link Daemon#set(Com)}.
	 * @param args
	 * @return the Daemon
	 */
	public static Daemon init(String[] args) {
		Properties properties = new Properties();

		for (int i = 0; i < args.length; i++) {
			String flag = args[i];
			String value = null;

			if(flag.startsWith("-") && ++i < args.length) {
				value = args[i];

				if(value.startsWith("-")) {
					i--;
					value = null;
				}
			}

			if(value == null) {
				properties.put(flag.substring(1).toLowerCase(), "true");
			} else {
				properties.put(flag.substring(1).toLowerCase(), value);
			}
		}

		if(properties.getProperty("help", "false").toLowerCase()
				.equals("true")) {
			System.out.println("Usage: java -jar http.jar -verbose");
			return null;
		}

		Daemon daemon = new Daemon(properties);

		/*
		 * If this is run as an application we log PID to pid.txt file in root.
		 */

		try {
			String pid = ManagementFactory.getRuntimeMXBean().getName();
			PrintWriter out = new PrintWriter("pid.txt");
			out.println(pid.substring(0, pid.indexOf('@')));
			out.flush();
			out.close();
		}
		catch(Exception e) {}

		return daemon;
	}

	public static void main(String[] args) {
		Daemon daemon = init(args);
		daemon.start();
	}
	
	static {
		javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
			public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
				if(hostname.equals("rupy.se")) {
					return true;
				}
				return false;
			}
		});
		System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
		TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}
					public void checkClientTrusted(
							java.security.cert.X509Certificate[] certs, String authType) {
					}
					public void checkServerTrusted(
							java.security.cert.X509Certificate[] certs, String authType) {
					}
				}
		};

		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {}
	}
}
