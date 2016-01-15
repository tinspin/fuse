package se.rupy.http;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.*;
import java.nio.*;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import java.text.*;
import java.util.*;
import java.nio.channels.*;

import se.rupy.http.Daemon.Lock;

/**
 * Asynchronous HTTP request/response, this virtually represents a client
 * socket, but in the case where the server is behind a proxy we cannot depend
 * on that fact since sockets will be reused by multiple different external
 * clients.<br>
 * <br>
 * The hierarchy of the code is as follows:
<tt><br><br>
&nbsp;&nbsp;&nbsp;<a href="http://rupy.se/doc/se/rupy/http/Event.html" class="not">Event</a>&nbsp;-+--&nbsp;<a href="http://rupy.se/doc/se/rupy/http/Query.html" class="not">Query</a>&nbsp;&lt;--&nbsp;<a href="http://rupy.se/doc/se/rupy/http/Input.html" class="not">Input</a>&nbsp;&lt;---&nbsp;+-----------+<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;X&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;Browser&nbsp;&nbsp;|<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;+-&gt;&nbsp;<a href="http://rupy.se/doc/se/rupy/http/Reply.html" class="not">Reply</a>&nbsp;--&gt;&nbsp;<a href="http://rupy.se/doc/se/rupy/http/Output.html" class="not">Output</a>&nbsp;--&gt;&nbsp;+-----------+<br>
<br></tt>
 * Where X marks the potential Comet pause point.
 */
public class Event extends Throwable implements Chain.Link {
	// waste of time it seems. hotspot does this optimisation for me! :)
	protected final static boolean LOG = true;

	static int READ = 1 << 0;
	static int WRITE = 1 << 2;

	static int VERBOSE = 1 << 0;
	static int DEBUG = 1 << 1;

	static SecureRandom random = new SecureRandom();
	
	private static char[] BASE_58 = { 
	'1','2','3','4','5','6','7','8','9','A',
	'B','C','D','E','F','G','H','J','K','L',
	'M','N','P','Q','R','S','T','U','V','W',
	'X','Y','Z','a','b','c','d','e','f','g',
	'h','i','j','k','m','n','o','p','q','r',
	's','t','u','v','w','x','y','z' };
	
	static Mime MIME;

	static {
		MIME = new Mime();
		READ = SelectionKey.OP_READ;
		WRITE = SelectionKey.OP_WRITE;
	}

	private SocketChannel channel;
	private SelectionKey key;

	private Query query;
	private Reply reply;
	private Session session;

	private Daemon daemon;
	private Worker worker;

	private int index, interest;
	private String remote, host;
	protected boolean wakeup = false;
	private boolean close;
	private long touch;

	static ThreadMXBean bean;
	
	static {
		bean = ManagementFactory.getThreadMXBean();
		bean.setThreadContentionMonitoringEnabled(true);
		//System.out.println(bean.isThreadCpuTimeSupported() + " " + bean.isThreadCpuTimeEnabled() + " " + bean.isThreadContentionMonitoringSupported() + " " + bean.isThreadContentionMonitoringEnabled() + " " + bean.isCurrentThreadCpuTimeSupported());
	}
	
	/*
	 * Since variable chunk length on HTTP requests implementations
	 * are sparse I needed a way to remove all of the irrelevant 
	 * headers for comet incoming requests, so I present latest 
	 * addition to the HTTP spec.
	 * 
	 * Head: less
	 */
	protected boolean headless;

	protected Event(Daemon daemon, SelectionKey key, int index) throws IOException {
		touch();

		channel = ((ServerSocketChannel) key.channel()).accept();
		channel.configureBlocking(false);

		this.daemon = daemon;
		this.index = index;

		query = new Query(this);
		reply = new Reply(this);

		key = channel.register(key.selector(), READ, this);
		key.selector().wakeup();

		this.key = key;
	}

	protected String host() {
		return host;
	}
	
	protected int interest() {
		return interest;
	}

	protected void interest(int interest) {
		this.interest = interest;
	}

	public Daemon daemon() {
		return daemon;
	}

	public Query query() {
		return query;
	}

	public Reply reply() {
		return reply;
	}

	public Session session() {
		return session;
	}

	public String remote() {
		return remote;
	}

	public boolean close() {
		return close;
	}

	public Worker worker() {
		return worker;
	}

	public int index() {
		return index;
	}

	protected void close(boolean close) {
		this.close = close;
	}

	protected void worker(Worker worker) {
		this.worker = worker;
	}

	protected SocketChannel channel() {
		return channel;
	}

	protected void log(Object o) {
		log(o, Event.DEBUG);
	}

	protected void log(Object o, int level) {
		if(o instanceof Exception && daemon.debug) {
			daemon.out.print("[" + (worker == null ? "*" : "" + worker.index())
					+ "-" + index + "] ");
			((Exception) o).printStackTrace(daemon.out);
		} else if(daemon.debug || daemon.verbose && level == Event.VERBOSE)
			daemon.out.println("["
					+ (worker == null ? "*" : worker.index() + "|" + worker.id() + "|" + Thread.currentThread().getId()) + "-"
					+ index + "] " + o);
	}

	/**
	 * @return same as {@link Query}.
	 */
	public long big(String key) {
		return query.big(key);
	}

	/**
	 * @return same as {@link Query}.
	 */
	public int medium(String key) {
		return query.medium(key);
	}

	/**
	 * @return same as {@link Query}.
	 */
	public short small(String key) {
		return query.small(key);
	}

	/**
	 * @return same as {@link Query}.
	 */
	public byte tiny(String key) {
		return query.tiny(key);
	}

	/**
	 * @return same as {@link Query}.
	 */
	public boolean bit(String key) {
		return query.bit(key, true);
	}

	/**
	 * @return same as {@link Query}.
	 */
	public String string(String key) {
		return query.string(key);
	}

	/**
	 * @return same as {@link Query#input()}.
	 */
	public Input input() {
		return query.input();
	}

	/**
	 * @return same as {@link Reply#output()}.
	 * @throws IOException
	 */
	public Output output() throws IOException {
		return reply.output();
	}

	protected void read() throws IOException {
		touch();

		if(!query.headers()) {
			disconnect(null);
		}

		if(query.policy()) {
			reply.policy();
			disconnect(null);
			return;
		}
		
		remote = address();

		if(query.version() == null || !query.version().equalsIgnoreCase("HTTP/1.1")) {
			reply.code("505 Not Supported");
		}
		else if(!query.header().containsKey("host")) {
			reply.code("400 Bad Request");
		}
		else {
			if(host == null)
				host = query.header("host");
			
			if(!service(daemon.chain(this), false)) {
				if(daemon.host && query.path().startsWith("/root/")) {
					reply.code("403 Forbidden");
					reply.output().print(
							"<pre>'" + query.path() + "' is forbidden.</pre>");
				}
				else if(!content()) {
					//System.out.println(daemon.host + " " + 
					//		query.path().startsWith("/root") + " " + 
					//		query.path().startsWith("/node") + " " + 
					//		query.path().startsWith("/link"));
					if(daemon.root && (
							query.path().startsWith("/root") || 
							query.path().startsWith("/node") || 
							query.path().startsWith("/link") || 
							query.path().startsWith("/meta") || 
							query.path().startsWith("/tree") || 
							query.path().startsWith("/salt")) && 
							!host.equals("root.rupy.se")) {
						if(!service(daemon.root(), false)) {
							reply.code("404 Not Found");
							reply.output().print(
									"<pre>'" + encode(query.path()) + "' was not found.</pre>");
						}
					}
					else if(!service(daemon.chain(this, "null"), false)) {
						reply.code("404 Not Found");
						reply.output().print(
								"<pre>'" + encode(query.path()) + "' was not found.</pre>");
					}
				}
			}
		}

		finish();
	}

	/**
	 * HTML encodes value to avoid XSS attacks.
	 * & = &amp;
	 * < = &lt;
	 * > = &gt;
	 * " = &quot;
	 * ' = &#x27;
	 * / = &#x2F;
	 */
	public static String encode(String value) {
		value = value.replace("&", "&amp;");
		value = value.replace("<", "&lt;");
		value = value.replace(">", "&gt;");
		value = value.replace("\"", "&quot;");
		value = value.replace("'", "&#x27;");
		value = value.replace("/", "&#x2F;");
		return value;
	}
	
	/**
	 * Escape JSON.
	 */
	public static String escape(String value) {
		value = value.replace("\"", "\\\"");
		value = value.replace("\\", "\\\\");
		value = value.replace("\b", "\\b");
		value = value.replace("\f", "\\f");
		value = value.replace("\n", "\\n");
		value = value.replace("\r", "\\r");
		value = value.replace("\t", "\\t");
		return value;
	}
	
	protected String address() {
		String remote = query.header("x-forwarded-for");

		if(remote == null) {
			InetSocketAddress address = (InetSocketAddress) channel.socket()
					.getRemoteSocketAddress();

			if(address != null)
				remote = address.getAddress().getHostAddress();
		}

		if(Event.LOG) {
			log("remote " + remote, VERBOSE);
		}

		return remote;
	}

	protected boolean content() throws IOException {
		Deploy.Stream stream = daemon.content(query);

		if(stream == null)
			return false;

		String type = MIME.content(query.path(), "application/octet-stream");

		reply.type(type);
		reply.modified(stream.date());

		if(query.modified() == 0 || query.modified() < reply.modified()) {
			Daemon.Metric metric = null;
			
			if(daemon.host) {
				Deploy.Archive archive = daemon.archive(query.header("host"), false);
				metric = (Daemon.Metric) archive.files().get(query.path());
				
				if(metric == null) {
					metric = new Daemon.Metric();
					archive.files().put(query.path(), metric); // TODO: Threadlock. Edit: NM, this was raspberry pi with corrupt SD card.
				}
			}
			
			long cpu = bean.getThreadCpuTime(Thread.currentThread().getId());
			
			/* Zero-Copy file stream.
			 * This does not seem to improve speed or CPU usage when testing manually.
			 * TODO: Test with Siege.
			 
			Output out = reply.output(stream.length());
			out.flush();
			
			File file = ((Deploy.Big) stream).file;
			
			long length, sent = 0;
			
			length = file.length();
			
			FileChannel fc = new FileInputStream(file).getChannel();
			
			try {
				while(sent < length) {
					sent += fc.transferTo(sent, 1024, channel);
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			finally {
				fc.close();
			}
			*/
			///*
			try {
				Deploy.pipe(stream.input(), reply.output(stream.length()));
			}
			finally {
				stream.close();
			}
			//*/
			if(daemon.host) {
				metric.req.in++;
				metric.req.out++;
				metric.cpu += bean.getThreadCpuTime(Thread.currentThread().getId()) - cpu;
				metric.net.in += query.input.total;
				metric.net.out += reply.output.total; // + file.length();
				query.input.total = 0;
				reply.output.total = 0;
			}
			
			if(Event.LOG) {
				log("content " + type, VERBOSE);
			}
		} else {
			reply.code("304 Not Modified");
		}

		return true;
	}

	protected boolean service(Daemon.Lock chain, boolean write) throws IOException {
		if(chain == null)
			return false;

		try {
			chain.chain.filter(this, write, chain.root);
			
			// Fixes FUSE "opened output without flush" cascade.
			if(reply().output.init)
				reply().output.flush();
		} catch (Failure f) {
			throw f;
		} catch (Event e) {
			// Break the filter chain.
		} catch (Exception e) {
			if(daemon.host && 
			   e instanceof PrivilegedActionException && 
			   e.getCause() != null && 
			   e.getCause() instanceof Exception) {
				e = (Exception) e.getCause();
			}
			
			if(Event.LOG) {
				log(e);
			}

			daemon.error(this, e);

			StringWriter trace = new StringWriter();
			PrintWriter print = new PrintWriter(trace);
			e.printStackTrace(print);
			
			reply.code("500 Internal Server Error");
			reply.output().print("<pre>{" + System.getProperty("host", "???") + "} " + encode(trace.toString()) + "</pre>");
			
			if(reply.push()) {
				reply.output().finish();
				reply.output().flush();
			}
		}

		return true;
	}

	protected void write() throws IOException {
		touch();
		service(daemon.chain(this), true);
		finish();
	}

	private void finish() throws IOException {
		String log = daemon.access(this);

		reply.done();
		query.done();

		if(log != null) {
			daemon.access(log, reply.push());
		}
	}

	protected void register() throws IOException {
		if(interest != key.interestOps()) {
			if(Event.LOG) {
				log((interest == READ ? "read" : "write") + " prereg " + interest
						+ " " + key.interestOps() + " " + key.readyOps(), DEBUG);
			}
			
			key = channel.register(key.selector(), interest, this);
			
			if(Event.LOG) {
				log((interest == READ ? "read" : "write") + " postreg " + interest
						+ " " + key.interestOps() + " " + key.readyOps(), DEBUG);
			}
			
			key.selector().wakeup();
			
			//if(Event.LOG) {
			//	log((interest == READ ? "read" : "write") + " wakeup", DEBUG);
			//}
		}
	}

	protected void register(int interest) {
		interest(interest);

		try {
			if(channel.isOpen())
				register();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected int block(Block block) throws Exception {
		long max = System.currentTimeMillis() + daemon.delay;

		while (System.currentTimeMillis() < max) {			
			register();

			int available = block.fill();

			long delay = daemon.delay - (max - System.currentTimeMillis());

			if(available > 0) {
				if(Event.LOG) {
					log("delay " + delay + " " + available, VERBOSE);
				}

				return available;
			}

			if(delay > 100) {
				/*
				 * Increase socket buffer.
				 * For really old client computers and slow 
				 * internet connections we increase the buffer.
				 * This only helps if you, as root, have done:
				 * 
				 * > echo 'net.core.wmem_max=1048576' >> /etc/sysctl.conf
				 * > echo 'net.ipv4.tcp_wmem= 16384 65536 1048576' >> /etc/sysctl.conf
				 * > sysctl -p
				 * 
				 * on your linux server, really. Where 1048576 
				 * should be replaced with the size of your 
				 * largest file.
				 */
				
				int buffer = 0;
				
				if(interest == READ) {
					buffer = channel.socket().getReceiveBufferSize();
				}
				else if(interest == WRITE) {
					buffer = channel.socket().getSendBufferSize();
				}
				
				buffer += daemon.size;
				
				if(interest == READ) {
					channel.socket().setReceiveBufferSize(buffer);
				}
				else if(interest == WRITE) {
					channel.socket().setSendBufferSize(buffer);
				}
			}
			
			Thread.yield();
			worker.snooze(10);
			//key.selector().wakeup();
		}

		String agent = query.header("user-agent");

		throw new Exception("IO timeout. (" + interest + ", " + daemon.delay + ", " + agent + ")");
	}

	/**
	 * Non blocking IO requires some blocking logic to handle ethernet latency.
	 * @author Marc
	 */
	public interface Block {
		/**
		 * This is used by the event to ask the query/reply to pull/push data.
		 * @return how many bytes was sent
		 * @throws IOException
		 */
		public int fill() throws IOException;
	}

	protected void disconnect(Exception e) {
		try {
			if(channel != null) { // && channel.isOpen()) {
				channel.close();
			}

			if(key != null) {
				key.cancel();
			}

			if(session != null) {
				session.remove(this);
			}

			if(daemon.debug) {
				if(Event.LOG) {
					log("disconnect " + e);
				}

				if(e != null) {
					e.printStackTrace();
				}
			}

			daemon.error(this, e);
		} catch (Exception de) {
			de.printStackTrace(daemon.out);
		}
		finally {
			daemon.events.remove(new Integer(index));
		}
	}

	/**
	 * Creates a session variable to enable cross hot-deploy 
	 * sessions on headless real-time comet-stream applications.
	 */
	public void persist() {
		if(session == null) {
			session = new Session(daemon, query().header("host"));
			session.add(this);
		}
	}
	
	protected final void session(final Service service, Event event) throws Exception {
		String key = cookie(query.header("cookie"), "key");

		//System.out.println("KEY " + key + " " + query.header("cookie"));
		
		if(key == null && query.method() == Query.GET) {
			// XSS comet cookie: this means first GETs are parsed!
			// TODO: This should be removed because you can use a P3P header to fix this, go figure!
			// UNDO: Apparently Apple decided to go back in time and mess XSS up!
			query.parse();
			String cookie = query.string("cookie");
			key = cookie.length() > 0 ? cookie : null;
		}

		if(key != null) {
			session = (Session) daemon.session().get(key);

			if(session != null) {
				if(Event.LOG) {
					log("old key " + key, VERBOSE);
				}

				session.add(this);
				session.touch();

				return;
			}
		}

		int index = 0;

		if(daemon.host) {
			final Deploy.Archive archive = daemon.archive(query().header("host"), true);
			try {
				Thread.currentThread().setContextClassLoader(archive);
			}
			catch(AccessControlException e) {
				// recursive chaining fails here, no worries! ;)
			}
			Integer i = (Integer) AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws Exception {
					try {
						return new Integer(service.index());
					}
					catch(Throwable t) {
						t.printStackTrace();
						return new Integer(0);
					}
				}
			}, daemon.control);

			index = i.intValue();
		}
		else {
			index = service.index();
		}

		if(index == 0 && !push()) {
			session = new Session(daemon, event.query().header("host"));
			session.add(service);
			session.add(this);
			session.key(key);

			if(session.key() == null) {
				do {
					key = random(daemon.cookie);
				} while (daemon.session().get(key) != null);

				session.key(key);
			}

			//synchronized (daemon.session()) {
			if(Event.LOG) {
				log("new key " + session.key(), VERBOSE);
			}

			daemon.session().put(session.key(), session);
			//}
		}

		try {
			service.session(session, Service.CREATE);
		} catch (Exception e) {
			e.printStackTrace(daemon.out);
		}
	}

	protected static String cookie(String cookie, String key) {
		String value = null;

		if(cookie != null) {
			StringTokenizer tokenizer = new StringTokenizer(cookie, " ");

			while (tokenizer.hasMoreTokens()) {
				String part = tokenizer.nextToken();
				int equals = part.indexOf("=");

				if(equals > -1 && part.substring(0, equals).equals(key)) {
					String subpart = part.substring(equals + 1);

					int index = subpart.indexOf(";");

					if(index > 0) {
						value = subpart.substring(0, index);
					} else {
						value = subpart;
					}
				}
			}
		}

		return value;
	}

	public static String random(int length) {
		StringBuilder builder = new StringBuilder();

		//do {
			while (builder.length() < length) {
				builder.append(BASE_58[Math.abs(random.nextInt() % BASE_58.length)]);
			}
		//}
		//while(!builder.toString().matches("[a-zA-Z]+"));
		
		return builder.toString();
	}

	/**
	 * @return true if the Event is being recycled due to a call to
	 *         {@link Reply#wakeup()}.
	 */
	public boolean push() {
		return reply.output.push();
	}

	/**
	 * Touch the worker if you have a http connection that needs to wait.
	 */
	public void touch() {
		touch = System.currentTimeMillis();

		if(worker != null) {
			worker.touch();
		}
	}

	protected long last() {
		return touch;
	}

	/**
	 * Keeps the chunked reply open for asynchronous writes. If you are 
	 * streaming data and you need to send something upon the first request 
	 * you have to call this in order to avoid that the trailing zero length 
	 * chunk is sent to complete the response.
	 * @throws IOException
	 */
	public void hold() throws IOException {
		reply.output.push = true;
	}

	static class Mime extends Properties {
		public Mime() {
			try {
				load(Mime.class.getResourceAsStream("/mime.txt"));
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		String content(String path, String fail) {
			int index = path.lastIndexOf('.') + 1;

			if(index > 0) {
				return getProperty(path.substring(index), fail);
			}

			return fail;
		}
	}

	public String toString() {
		return "event: " + index + Output.EOL + 
				"interest: " + interest + Output.EOL + 
				"remote: " + remote + Output.EOL + 
				"close: " + close + Output.EOL + 
				"touch: " + touch + Output.EOL + 
				query + 
				reply + 
				worker;
	}
}
