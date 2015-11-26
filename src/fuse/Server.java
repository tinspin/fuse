package fuse;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import se.rupy.http.*;

public class Server extends Service implements Node, Runnable {
	ConcurrentHashMap list;	// The pull streams.
	Thread thread;			// To purge "noop" messages so proxies don't timeout the connections.
	Node node;				// The routing implementation.

	boolean debug = true;
	boolean alive;

	StringBuilder padding = new StringBuilder();
	
	private Queue find(String salt) {
		Iterator it = list.values().iterator();

		while(it.hasNext()) {
			Queue queue = (Queue) it.next();

			if(queue.salt.equals(salt)) {
				return queue;
			}
		}

		return null;
	}

	public String path() {
		return "/push:/pull";
	}

	public void remove(String salt, int place) throws Exception {
		node.remove(salt, place);
	}

	public void remove(String salt, boolean silent) throws Exception {
		// OK
	}
	
	public void create(Daemon daemon) throws Exception {
		if(!alive) {
			list = new ConcurrentHashMap();
			alive = true;

			thread = new Thread(this);
			thread.start();

			node = new Router();
			node.call(daemon, this);
		}
		
		for(int i = 0; i < 100; i++) {
			padding.append("          ");
		}
	}

	public void run() {
		try {
			while(alive) {
				purge(false);
				Thread.sleep(5000);
			}

			if(debug)
				System.err.println("purge");

			purge(true);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	public void destroy() throws Exception {
		alive = false;
		node.exit();
	}

	public void call(Daemon daemon, Node node) {
		// we'll call you, don't call us!
	}

	public void exit() {}

	public String push(Event event, String data) throws Exception {
		throw new Exception("Nope");
	}
	
	public String push(String salt, String data, boolean wake) throws Exception {
		Queue queue = find(salt);
		
		if(queue != null) {
			queue.add(data);

			if(!wake)
				return null;
			
			int wakeup = queue.event.reply().wakeup();

			if(wakeup == Reply.CLOSED || wakeup == Reply.COMPLETE) {
				remove(queue.salt, 1);
				list.remove(queue.event);
			}
		}
		else {
			remove(salt, 6);
		}

		return null;
	}
	
	public boolean wakeup(String salt) {
		Queue queue = find(salt);

		if(queue != null) {
			return queue.event.reply().wakeup() == Reply.OK;
		}
		
		return false;
	}

	private void purge(boolean finish) throws Exception {
		Iterator it = list.values().iterator();

		while(it.hasNext()) {
			Queue queue = (Queue) it.next();

			queue.add("noop");

			if(finish) {
				queue.finish = true;
			}

			int wakeup = queue.event.reply().wakeup();

			if(wakeup == Reply.CLOSED || wakeup == Reply.COMPLETE) {
				remove(queue.salt, 2);
				it.remove();
			}
		}
	}

	public void filter(Event event) throws Event, Exception {
		if(event.query().path().equals("/push")) {
			event.query().parse();

			if(event.push()) {
				String data = event.query().string("done", null);
				String fail = event.query().string("fail", null);

				if(data != null || fail != null) {
					String send = fail == null ? data : fail;
					String origin = event.query().header("origin");
					
					System.err.println("<- " + data);
					
					if(origin != null)
						event.reply().header("Access-Control-Allow-Origin", origin);
					
					Output out = event.reply().output(send.length());

					out.print(send);
					out.finish();
					out.flush();

					event.query().put("done", null);
					event.query().put("fail", null);
				}
			}
			else {
				String data = event.string("data");
				byte[] body = null;
				
				try {
					String response = node.push(event, data);

					System.err.println("<- " + response);
					
					if(!response.equals("hold")) {
						body = response.getBytes("UTF-8");
					}
				}
				catch(Exception e) {
					e.printStackTrace();
					body = (data.substring(0, 4) + "|fail|" + e.getClass().getSimpleName()).getBytes("UTF-8");
				}
				
				if(body != null) {
					String origin = event.query().header("origin");
					
					if(origin != null)
						event.reply().header("Access-Control-Allow-Origin", origin);
					
					event.reply().output(body.length).write(body);
				}
			}

			throw event;
		}

		if(event.push()) {
			Queue queue = (Queue) list.get(new Integer(event.index()));

			try {
				Output out = event.output();

				if(queue == null || queue.finish) {
					out.finish();
				}

				if(queue != null && !queue.isEmpty()) {
					String data = (String) queue.poll();

					while(data != null) {
						String accept = queue.event.query().header("accept");

						if(accept != null && accept.indexOf("text/event-stream") > -1) {
							out.print("data: " + data + "\n\n");
						}
						else {
							out.print(data + "\n");
						}
						
						out.flush(); // Solves output buffer limit problems
									 // But might be a performance problem
									 // 1024 bytes limit on packets
						
						data = (String) queue.poll();
					}

					out.flush();
				}
			}
			catch(Exception e) {
				remove(queue.salt, 3);
				list.remove(new Integer(event.index()));
				throw e;
			}
		}
		else {
			event.query().parse();
			boolean ie = event.bit("ie");
			String salt = event.string("salt");
			Queue queue = find(salt);

			if(queue != null) {
				if(queue.event.remote().equals(event.remote())) {
					remove(queue.salt, 4);
					list.remove(new Integer(queue.event.index()));
				}
				else {
					System.out.println("### IP fuse hack " + event.remote());
					event.output().print("salt in use from another IP.");
					throw event;
				}
			}

			list.put(new Integer(event.index()), new Queue(salt, event));
			
			String accept = event.query().header("accept");

			boolean stream = accept != null && accept.indexOf("text/event-stream") > -1;
			
			if(stream) {
				event.reply().type("text/event-stream");
			}

			String origin = event.query().header("origin");
			
			if(origin != null)
				event.reply().header("Access-Control-Allow-Origin", origin);
			
			event.hold();
			
			Output out = event.output();

			String padding = ie ? this.padding.toString() : "";
			
			if(stream) {
				out.print("data: noop" + padding + "\n\n");
			}
			else {
				out.print("noop" + padding + "\n");
			}

			out.flush();
		}
	}

	public static class Redirect extends Service {
		public String path() { return "/"; }
		public void filter(Event event) throws Event, Exception {
			event.reply().header("Location", "play.html");
			event.reply().code("302 Found");
		}
	}
	
	/*
	 * The queue contains the event and the messages 
	 * to be sent to the client.
	 */
	static class Queue extends ConcurrentLinkedQueue {
		public String salt;
		private Event event;
		private boolean finish;

		public Queue(String salt, Event event) {
			this.salt = salt;
			this.event = event;
		}
		
		public String toString() {
			return salt + "(" + event.index() + ")";
		}
	}
}