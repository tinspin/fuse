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
	
	private Queue find(String name) {
		Iterator it = list.values().iterator();

		while(it.hasNext()) {
			Queue queue = (Queue) it.next();

			if(queue.name.equals(name)) {
				return queue;
			}
		}

		return null;
	}

	public String path() {
		return "/push:/pull";
	}

	public void remove(Queue queue, int place) throws Exception {
		if(place > 1)
			node.remove(queue.name);
	}

	public void remove(String name) throws Exception {
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

	public String push(Event event, String name, String data) throws Exception {
		Queue queue = find(name);

		if(queue != null) {
			queue.add(data);

			int wakeup = queue.event.reply().wakeup();

			if(wakeup == Reply.CLOSED || wakeup == Reply.COMPLETE) {
				//if(debug)
				//	System.err.println(queue.event.index() + " remove 1");
				remove(queue, 1);
				list.remove(queue.event);
			}
		}

		return null;
	}

	public void broadcast(String name, String data) throws Exception {
		Iterator it = list.values().iterator();

		while(it.hasNext()) {
			Queue queue = (Queue) it.next();

			if(name == null || !queue.name.equals(name)) {
				queue.add(data);

				int wakeup = queue.event.reply().wakeup();

				if(wakeup == Reply.CLOSED || wakeup == Reply.COMPLETE) {
					//if(debug)
					//	System.err.println(queue.event.index() + " remove 5");
					remove(queue, 5);
					list.remove(queue.event);
				}
			}
		}
	}

	private void purge(boolean finish) throws Exception {
		Iterator it = list.values().iterator();

		while(it.hasNext()) {
			Queue queue = (Queue) it.next();

			queue.add("noop");

			if(finish) {
				//if(debug)
				//	System.err.println(queue.event.index() + " finish 1");
				queue.finish = true;
			}

			int wakeup = queue.event.reply().wakeup();

			if(wakeup == Reply.CLOSED || wakeup == Reply.COMPLETE) {
				//if(debug)
				//	System.err.println(queue.event.index() + " remove 2");
				remove(queue, 2);
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
				String name = event.string("name");
				String data = event.string("data");
				byte[] body = null;
				
				//System.out.println(event.query().parameters());
				
				try {
					String response = node.push(event, name, data);

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
			Queue queue = (Queue) list.get(event);

			try {
				Output out = event.output();

				if(queue == null || queue.finish) {
					//if(debug)
					//	System.err.println(event.index() + " finish 2");
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
							out.write((data + "\n").getBytes());
						}

						data = (String) queue.poll();
					}

					out.flush();
				}
			}
			catch(Exception e) {
				//if(debug)
				//	System.err.println(event.index() + " remove 3");
				remove(queue, 3);
				list.remove(event);
				throw e;
			}
		}
		else {
			event.query().parse();
			boolean ie = event.bit("ie");
			String name = event.string("name");
			Queue queue = find(name);

			if(queue != null) {
				//if(debug)
				//	System.err.println(event.index() + " remove 4");
				remove(queue, 4);
				list.remove(queue.event);
			}

			list.put(event, new Queue(name, event));

			String accept = event.query().header("accept");

			boolean stream = accept != null && accept.indexOf("text/event-stream") > -1;
			
			if(stream) {
				event.reply().type("text/event-stream");
			}

			//System.out.println(event.query().header());
			
			String origin = event.query().header("origin");
			
			if(origin != null)
				event.reply().header("Access-Control-Allow-Origin", origin);
			
			event.hold();
			
			Output out = event.output();

			String padding = ie ? this.padding.toString() : "";
			
			//System.out.println("ie " + ie + " " + padding.length() + " " + stream);
			
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
			event.reply().header("Location", "game.html");
			event.reply().code("302 Found");
		}
	}
	
	/*
	 * The queue contains the event and the messages 
	 * to be sent to the client.
	 */
	static class Queue extends ConcurrentLinkedQueue {
		public String name;
		private Event event;
		private boolean finish;

		public Queue(String name, Event event) {
			this.name = name;
			this.event = event;
		}
	}
}