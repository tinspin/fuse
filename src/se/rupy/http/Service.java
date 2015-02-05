package se.rupy.http;

/**
 * The service filter is like a servlet which describes its own identifier. You
 * have to be careful how you write your hot-deployable code, see the wiki for
 * more information. The service becomes a singleton instance on the server, so
 * use of static methods is recommended.
 * 
 * @author marc
 */
public abstract class Service implements Chain.Link {
	/**
	 * When the session is created, read the incoming cookie key and/or set 
	 * the key you wish to be stored, the domain and the expire date.
	 */
	public final static int CREATE = 1;

	/**
	 * Normal timeout, the client simply was inactive for too long. Timeout 
	 * is specified when you start the daemon.
	 */
	public final static int TIMEOUT = 2;

	/**
	 * The client actively disconnected it's last TCP socket. This will not work
	 * correctly if the server is placed behind a proxy.
	 */
	public final static int DISCONNECT = 3;

	/**
	 * Where in the filter chain is this service? Default position is first
	 * (index 0).
	 * 
	 * @return the index of the service in it's filter chain.
	 */
	public int index() {
		return 0;
	}

	/**
	 * The identifier that should trigger this service. Or return null 
	 * if you want to filter all 404 queries. For example "/admin".
	 * You can specify a service that should filter multiple identifiers by
	 * separating them with a ':' character. For example: if you want to
	 * identify a user before multiple services, return "/update:/query" here
	 * and set the index to 0 on the identity service and set index to 1 on the
	 * update and query filters. Then you use the following code to redirect to
	 * the login page for example:
	 * 
	 * <pre>
	 * event.daemon().chain(event, "/login").filter(event);
	 * throw event; // stop the chain</pre>or
	 * 
	 * <pre>
	 * event.reply().header(&quot;Location&quot;, &quot;/login&quot;);
	 * event.reply().code(&quot;302 Found&quot;);
	 * throw event; // stop the chain
	 * </pre>
	 * 
	 * @return the path (URI) to the service(s).
	 */
	public abstract String path();

	/**
	 * Initiate service dependencies. This is called when you hot-deploy the
	 * application / start the server. Important: This will be called for every 
	 * chain instance of the service. So if you have multiple paths this will be 
	 * called once for every path. Use static lock if you use this to start some 
	 * singleton thread.
	 * @param daemon so you can setup yourself as a listener.
	 * @throws Exception
	 */
	public void create(Daemon daemon) throws Exception {
	}

	/**
	 * Free service dependencies. This is called when you hot-deploy the
	 * application.
	 * 
	 * @throws Exception
	 */
	public void destroy() throws Exception {
	}

	/**
	 * If sessions are used and a session invokes a service, that service will
	 * then be notified before the session is added, so that you may assign your
	 * own session cookie id; or removed, so that you may act upon both session
	 * timeout and physical TCP disconnects.
	 * 
	 * On host cluster this gets invoked with a <i>null</i> context class loader.
	 * 
	 * @param session
	 * @param type
	 * @throws Exception
	 */
	public void session(Session session, int type) throws Exception {
	}

	/**
	 * The service method, equivalent of HttpServlet.service().
	 * 
	 * @param event
	 * @throws Event
	 *             if you want to break the filter chain
	 * @throws Exception
	 */
	public abstract void filter(Event event) throws Event, Exception;
}
