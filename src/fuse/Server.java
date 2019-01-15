package fuse;

import java.io.File;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Date;
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
        list.remove(salt);
        list.remove(Router.User.salt(salt));
		node.remove(salt, place);
	}

	public void remove(String salt, boolean silent) throws Exception {
		// OK
	}
	
	public void create(Daemon daemon) throws Exception {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();

		if(loader != null && loader instanceof Deploy.Archive) {
            Deploy.Archive archive = (Deploy.Archive) loader;
            String host = archive.host();
            String top = host.substring(host.indexOf('.'));

            if (!daemon.domain().equals("host.rupy.se")) {
                //System.out.println(host + " " + top);

                if (host.startsWith("live")) {
                    Router.data = "data" + top;
                } else {
                    Router.data = "base" + top;
                }

                // Only when you don't have Latency driven DNS like Route53.
                //Router.fuse = System.getProperty("host") + top;
                Router.fuse = host;
                Router.path = host;
                Router.what = "localhost";
            }
        }

        if(!alive) {
            System.out.println(Router.data + " / " + Router.fuse);

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
		broadcast("warn|boot|Please reconnect!", false);
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
		Queue queue = (Queue) list.get(salt); //find(salt);
		
		if(queue != null) {
			queue.add(data);

			if(!wake)
				return null;

			int wakeup = queue.event.reply().wakeup();
			
			if(wakeup == Reply.CLOSED || wakeup == Reply.COMPLETE) {
				remove(queue.salt, 1);

			}
		}
		else {
			remove(salt, 6);
		}

		return null;
	}

    public String push(int salt, String data, boolean wake) throws Exception {
        Queue queue = (Queue) list.get(salt); //find(salt);

        if(queue != null) {
            queue.add(data);

            if(!wake)
                return null;

            int wakeup = queue.event.reply().wakeup();

            if(wakeup == Reply.CLOSED || wakeup == Reply.COMPLETE) {
                remove(queue.salt, 1);
            }
        }
        else {
            remove(Router.User.salt(salt), 6);
        }

        return null;
    }
	
	public int wakeup(String salt) {
		Queue queue = (Queue) list.get(salt); //find(salt);

		//System.err.println(salt + " " + queue.size());
		
		if(queue != null) {
			return queue.event.reply().wakeup();
		}
		
		return -2;
	}

	private void purge(boolean finish) throws Exception {
		broadcast("noop", finish);
	}
	
	public void broadcast(String message, boolean finish) throws Exception {
		Iterator it = list.values().iterator();

		while(it.hasNext()) {
			Queue queue = (Queue) it.next();
			queue.add(message);

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
					
					if(Router.debug)
						System.err.println(" <- " + data);
					
					if(origin != null)
						event.reply().header("Access-Control-Allow-Origin", origin);
					
					byte[] body = send.getBytes("UTF-8");
					Output out = event.reply().output(body.length);

					out.print(send);
					out.finish();
					out.flush();

					event.query().put("done", null);
					event.query().put("fail", null);
					
					Router.add(event, send, false);
				}
			}
			else {
				event.query().put("time", new Long(System.currentTimeMillis()));
				
				String data = event.string("data");
				byte[] body = null;
				
				try {
					String response = node.push(event, data);

					//if(response.equals("hold"))
					//	throw event;
					
					if(!data.startsWith("send") && !data.startsWith("move"))
						if(Router.debug)
							System.err.println(" <- " + response);
					
					body = response.getBytes("UTF-8");
					Router.add(event, response, false);
				}
				catch(Exception e) {
					e.printStackTrace();
					body = (data.substring(0, 4) + "|fail|" + e.getClass().getSimpleName()).getBytes("UTF-8");
					Router.add(event, data.substring(0, 4) + "|fail", true);
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
			Queue queue = (Queue) list.get(event.string("salt"));

			try {
				Output out = event.output();

				if(queue == null || queue.finish) {
					out.finish();
				}

				if(queue != null && !queue.isEmpty()) {
					String data = (String) queue.poll();
					String accept = queue.event.query().header("accept");
					
					while(data != null) {
						if(!data.equals("noop") && !data.startsWith("move"))
							if(Router.debug)
								System.err.println(queue.salt + " " + data);
						
						if(accept != null && accept.indexOf("text/event-stream") > -1) {
							out.print("data: " + data + "\n\n");
						}
						else {
							out.print(data + "\n");
						}
						
						data = (String) queue.poll();
					}

					out.flush();
				}
			}
			catch(Exception e) {
				remove(queue.salt, 3);
				throw e;
			}
		}
		else {
			event.query().parse();
			boolean ie = event.bit("ie");
			String salt = event.string("salt");
			Queue queue = (Queue) list.get(salt); //find(salt);

			if(queue != null) {
				if(queue.event.remote().equals(event.remote())) {
					remove(queue.salt, 4);
				}
				else {
					System.out.println("### IP fuse hack " + event.remote());
					event.output().print("salt in use from another IP.");
					throw event;
				}
			}

            Queue q = new Queue(salt, event);
			list.put(salt, q);
            list.put(Router.User.salt(salt), q);
			
			if(Router.debug)
				System.err.println("poll " + Router.date.format(new Date()) + " " + salt);
			
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

			//String padding = ie ? this.padding.toString() : "";
			// AVG Anti-Virus buffers the chunks so we need to push a large amount in the beginning for all browser types.
			
			if(stream) {
				out.print("data: noop" + this.padding + "\n\n");
			}
			else {
				out.print("noop" + this.padding + "\n");
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
	
	private final static char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
	
	/* Ok, so if you use javascript to set the source of an image to animate a sprite some browsers will 
	 * spam the server with GET requests. Since I want to use the z-index and preload images I don't use 
	 * canvas and the only solution around this bug is to inline the images with base64.
	 */
	public static class Encode extends Service {
		public String path() { return "/encode"; }
		public void filter(Event event) throws Event, Exception {
			File[] file = new File("app/fuse.rupy.se/gif").listFiles(new Filter());
			Arrays.sort(file);
			Output out = event.output();
			for(int i = 0; i < file.length; i++) {
				String name = file[i].getName().substring(0, file[i].getName().length() - 4);
				String base = encode(read(file[i])); 
				out.println("<img id=\"" + name + "\" src=\"data:image/png;base64," + base + "\"/>");
			}
		}
		
		class Filter implements FilenameFilter {
			public boolean accept(File dir, String name) {
				if(name.endsWith(".png")) {
					return true;
				}
				return false;
			}
		}
		
		public static byte[] read(File file) throws Exception {
			RandomAccessFile f = new RandomAccessFile(file, "r");
			byte[] b = new byte[(int)f.length()];
			f.readFully(b);
			f.close();
			return b;
		}
		
	    public static String encode(byte[] buf){
	        int size = buf.length;
	        char[] ar = new char[((size + 2) / 3) * 4];
	        int a = 0;
	        int i=0;
	        while(i < size){
	            byte b0 = buf[i++];
	            byte b1 = (i < size) ? buf[i++] : 0;
	            byte b2 = (i < size) ? buf[i++] : 0;

	            int mask = 0x3F;
	            ar[a++] = ALPHABET[(b0 >> 2) & mask];
	            ar[a++] = ALPHABET[((b0 << 4) | ((b1 & 0xFF) >> 4)) & mask];
	            ar[a++] = ALPHABET[((b1 << 2) | ((b2 & 0xFF) >> 6)) & mask];
	            ar[a++] = ALPHABET[b2 & mask];
	        }
	        switch(size % 3){
	            case 1: ar[--a] = '=';
	            case 2: ar[--a] = '=';
	        }
	        return new String(ar);
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
		/*
		public String toString() {
			return salt + "(" + event.index() + ")";
		}
		*/
	}
}