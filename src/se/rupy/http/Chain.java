package se.rupy.http;

import java.lang.reflect.Field;
import java.security.AccessControlException;
import java.security.AccessController;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import java.util.*;

/**
 * This is the {@link Event} filter chain implementation, it has dynamic size with positional integrity.
 */
public class Chain extends LinkedList {
	private int next;

	/*
	 * Dynamic size list with positional integrity. If anyone has a better
	 * solution to this please tell me!
	 */
	protected Link put(Link link) {
		for(int i = 0; i < size(); i++) {
			Link tmp = (Link) super.get(i);

			if (link.index() == tmp.index()) {
				return (Link) set(i, link);
			}
			else if (link.index() < tmp.index()) {
				add(i, link);
				return null;
			}
		}

		add(link);

		return null;
	}

	public void filter(final Event event) throws Event, Exception {
		filter(event, true, false);
	}

	protected void filter(final Event event, boolean write, boolean root) throws Event, Exception {
		for (int i = 0; i < size(); i++) {
			final Service service = (Service) get(i);

			if (event.daemon().timeout > 0 && !event.headless) {
				event.session(service, event);
			}

			long cpu = Event.bean.getThreadCpuTime(Thread.currentThread().getId());

			if(event.daemon().host && !root) {
				try {
					final Deploy.Archive archive = event.daemon().archive(event.query().header("host"), true);

					//System.out.println(archive + " " + event.query().header("host"));

					try {
						Thread.currentThread().setContextClassLoader(archive);
					}
					catch(AccessControlException e) {
						// recursive chaining fails here, no worries! ;)
					}

					Object o = AccessController.doPrivileged(new PrivilegedExceptionAction() {
						public Object run() throws Exception {
							try {
								service.filter(event);
								return null;
							}
							catch(Event event) {
								return event;
							}
							catch(Exception e) {
								throw new PrivilegedActionException(e);
							}
							catch(Throwable t) {
								t.printStackTrace();
								return null;
							}
						}
					}, archive.access());

					if(o != null) {
						throw (Event) o;
					}
				}
				catch(PrivilegedActionException e) {
					if(e.getCause() != null) {
						throw (Exception) e.getCause();
					}
					else {
						throw e;
					}
				}
			}
			else {
				if(root) {
					final Deploy.Archive archive = event.daemon().archive(event.query().header("host"), true);

					//System.out.println(event.query().header("host") + " " + archive);
					
					try {
						Thread.currentThread().setContextClassLoader(archive);
					}
					catch(AccessControlException e) {
						// TODO: Seems this can fail sometimes without recursive!
						/*
						java.lang.Exception: Home could not be found.
				        at se.rupy.http.Root.home(Root.java:155)
				        at se.rupy.http.Root.exists(Root.java:358)
				        at se.rupy.http.Root.sync(Root.java:320)
				        at se.rupy.http.Root$Node.async(Root.java:1368)
				        at se.rupy.http.Root$Node.filter(Root.java:1355)
				        at se.rupy.http.Chain.filter(Chain.java:112)
				        at se.rupy.http.Event.service(Event.java:409)
				        at se.rupy.http.Event.read(Event.java:270)
				        at se.rupy.http.Worker.run(Worker.java:195)
				        at java.lang.Thread.run(Thread.java:744
				        */
						// recursive chaining fails here, no worries! ;)
						System.out.println(e.getMessage());
					}
				}

				service.filter(event);
			}

			String path = event.query().path();
			Daemon.Metric metric = (Daemon.Metric) service.metric.get(path);

			if(metric == null) {
				metric = new Daemon.Metric();
				service.metric.put(path, metric);
			}

			if(i == 0) {
				if(!write)
					metric.req.in++;
				metric.req.out++;
			}
			metric.cpu += Event.bean.getThreadCpuTime(Thread.currentThread().getId()) - cpu;
			metric.net.in += event.query().input.total;
			metric.net.out += event.reply().output.total;

			//System.out.println("add " + metric.hashCode() + " " + metric);

			event.query().input.total = 0;
			event.reply().output.total = 0;
		}
	}

	protected void exit(final Session session, final int type) throws Exception {
		for (int i = 0; i < size(); i++) {
			final Service service = (Service) get(i);

			if(session.daemon().host) {
				try {
					Thread.currentThread().setContextClassLoader(null);
				}
				catch(AccessControlException e) {
					// exit fails here, no worries! ;)
				}
				
				AccessController.doPrivileged(new PrivilegedExceptionAction() {
					public Object run() throws Exception {
						try {
							service.session(session, type);
						}
						catch(Throwable t) {
							t.printStackTrace();
						}
						return null;
					}
				}, session.daemon().control);
			}
			else {
				service.session(session, type);
			}
		}
	}

	protected void reset() {
		next = 0;
	}

	protected Link next() {
		if (next >= size()) {
			next = 0;
			return null;
		}

		return (Link) get(next++);
	}

	interface Link {
		public int index();
	}

	public String toString() {
		StringBuilder buffer = new StringBuilder();
		Iterator it = iterator();

		buffer.append('[');

		while(it.hasNext()) {
			Object object = it.next();
			String name = object.getClass().getName();

			if(name.equals("se.rupy.http.Event")) {
				buffer.append(object);
			}
			else {
				int dollar = name.lastIndexOf('$');
				int dot = name.lastIndexOf('.');
				if(dollar > 0) {
					name = name.substring(dollar + 1);
				}
				else if(dot > 0) {
					name = name.substring(dot + 1);
				}				
				buffer.append(name);
			}

			if(it.hasNext()) {
				buffer.append(", ");
			}
		}

		buffer.append(']');

		return buffer.toString();
	}
}
