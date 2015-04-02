package se.rupy.http;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.*;
import java.nio.*;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import java.text.*;
import java.util.*;

import java.nio.channels.*;

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
 * 
 * @author marc
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
	private String remote;
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
			if(!service(daemon.chain(this))) {
				if(daemon.host && query.path().startsWith("/root/")) {
					reply.code("403 Forbidden");
					reply.output().print(
							"<pre>'" + query.path() + "' is forbidden.</pre>");
				}
				else if(!content()) {
					if(!service(daemon.chain(this, "null"))) {
						reply.code("404 Not Found");
						reply.output().print(
								"<pre>'" + query.path() + "' was not found.</pre>");
					}
				}
			}
		}

		finish();
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
			try {
				long cpu = bean.getThreadCpuTime(Thread.currentThread().getId());

				Deploy.pipe(stream.input(), reply.output(stream.length()));
				
				stream.cpu += bean.getThreadCpuTime(Thread.currentThread().getId()) - cpu;
				stream.net.read += query.input.length;
				stream.net.write += reply.output.total;
			}
			finally {
				stream.close();
			}

			if(Event.LOG) {
				log("content " + type, VERBOSE);
			}
		} else {
			reply.code("304 Not Modified");
		}

		return true;
	}

	protected boolean service(Chain chain) throws IOException {
		if(chain == null)
			return false;

		try {
			chain.filter(this);
		} catch (Failure f) {
			throw f;
		} catch (Event e) {
			// Break the filter chain.
		} catch (Exception e) {
			if(Event.LOG) {
				log(e);
			}

			daemon.error(this, e);

			StringWriter trace = new StringWriter();
			PrintWriter print = new PrintWriter(trace);
			e.printStackTrace(print);
			
			reply.code("500 Internal Server Error");
			reply.output().print("<pre>" + trace.toString() + "</pre>");
			
			if(reply.push()) {
				reply.output().finish();
				reply.output().flush();
			}
		}

		return true;
	}

	protected void write() throws IOException {
		touch();
		service(daemon.chain(this));
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

	protected final void session(final Service service, Event event) throws Exception {
		String key = cookie(query.header("cookie"), "key");

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
			final Deploy.Archive archive = daemon.archive(query().header("host"));
			try {
				Thread.currentThread().setContextClassLoader(archive);
			}
			catch(AccessControlException e) {
				// recursive chaining fails here, no worries! ;)
			}
			Integer i = (Integer) AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws Exception {
					return new Integer(service.index());
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

		while (builder.length() < length) {
			builder.append(BASE_58[Math.abs(random.nextInt() % BASE_58.length)]);
		}

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
