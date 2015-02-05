package se.rupy.http;

import java.util.*;

/**
 * If you save a class that is hot-deployed here it will throw a
 * ClassCastException if you re-deploy the application. We advise to only store
 * bootclasspath loaded classes in the session.
 * 
 * @author Marc
 */
public class Session extends Hash {
	private Daemon daemon;
	private Chain service;
	private Chain event;
	private boolean set;
	private String key, domain, host;
	private long date, expires;

	protected Session(Daemon daemon, String host) {
		super(false);
		this.daemon = daemon;

		service = new Chain();
		event = new Chain();

		touch();
	}

	protected void add(Service service) {
		if (!this.service.contains(service)) {
			this.service.add(service);
		}
	}

	protected void add(Event event) {
		if (!this.event.contains(event)) {
			this.event.add(event);
		}
	}

	public Chain event() {
		return event;
	}
	
	protected Daemon daemon() {
		return daemon;
	}
	
	protected void remove() throws Exception {
		remove(null);
	}

	protected synchronized boolean remove(Event event) throws Exception {
		if (event == null) {
			this.event.clear();
			service.exit(this, Service.TIMEOUT);
			return true;
		} else {
			boolean found = this.event.remove(event);
			if (this.event.isEmpty() && found) {
				service.exit(this, Service.DISCONNECT);
				return true;
			}
		}

		return false;
	}

	/**
	 * Has the session cookie been set?
	 * 
	 * @return has the cookie BEEN set.
	 */
	public boolean set() {
		return set;
	}

	/**
	 * Has the session cookie been set? false means no and therefore set it,
	 * true means yea; so don't set it. A little backwards but you'll get used
	 * to it! ;)
	 * 
	 * @param set
	 */
	public void set(boolean set) {
		this.set = set;
	}

	public long expires() {
		return expires;
	}

	protected void expires(long expires) {
		this.expires = expires;
		set = false;
	}

	public String key() {
		return key;
	}

	protected void key(String key) {
		this.key = key;
		set = false;
	}

	public String domain() {
		return domain;
	}

	/**
	 * Set the key you wish to store in the clients cookie here, together with
	 * the expire date. You can only set one at the time and it will be for the
	 * path=/;
	 * 
	 * @param key
	 * @param expires
	 */
	public void key(String key, String domain, long expires) {
		if (key == null)
			return;

		//synchronized (daemon.session()) {
			daemon.session().remove(this.key);
			this.key = key;
			daemon.session().put(key, this);
		//}

		this.domain = domain;
		this.expires = expires;

		set = false;
	}

	public long date() {
		return date;
	}

	protected void touch() {
		date = System.currentTimeMillis();
	}
	
	public String toString() {
		return key;
	}
}
