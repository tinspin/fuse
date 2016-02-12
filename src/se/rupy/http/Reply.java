package se.rupy.http;

import java.io.*;
import java.nio.*;
import java.nio.channels.CancelledKeyException;
import java.util.*;

/**
 * HTTP response. Non-blocking asynchronous; if you don't change the code or write output, 
 * the reply will not be sent at the end of the filter method and the client will wait 
 * for a response indefinitely, until a network timeout or a reply wakeup sends a response.
 * The order of execution is {@link #header(String, String)}, {@link #code(String)} and
 * finally {@link #output()}. If you call the {@link #code(String)} or
 * {@link #output()} method, the reply will flush output, so then you won't be
 * able to do an asynchronous reply. To wakeup a dormant asynchronous event use
 * {@link #wakeup()}.
 */
public class Reply {
	final static String HTML = "text/html; charset=UTF-8";
	
	/**
	 * If the reply has a thread writing already. You have to wait until it finishes. this 
	 * should not happen on a 1-to-1 wakeup, but when many threads are writing to one reply 
	 * you can queue replies with {@link #wakeup(boolean)} instead to avoid conflicts.
	 */
	public static int WORKING = -1;
	
	/**
	 * The reply was successfully awakened.
	 */
	public static int OK = 0;
	
	/**
	 * If the reply has been completed. This means the {@link Event} is no longer available 
	 * for wakeup and should probably be removed from the list.
	 */
	public static int COMPLETE = 1;
	
	/**
	 * If the reply has been closed, try increasing timeout variable. This means the {@link 
	 * Event} is no longer available for wakeup and should probably be removed from the list.
	 */
	public static int CLOSED = 2;
	
	private String type = HTML;
	private HashMap headers;
	private Event event;
	private long modified;
	private String code;

	Output output;
	
	protected Reply(Event event) throws IOException {
		this.event = event;
		output = new Output.Chunked(this);
		reset();
	}

	protected void done() throws IOException {
		if(Event.LOG) {
			event.log("done " + output.push() + " " + Thread.currentThread().getId(), Event.DEBUG);
		}
		
		if(!output.push()) {
			output.end();

			if(headers != null) {
				headers.clear();
			}

			reset();
		}
	}

	protected void reset() {
		modified = 0;
		type = "text/html; charset=UTF-8";
		code = "200 OK";
	}
	
	protected Event event() {
		return event;
	}

	protected HashMap headers() {
		return headers;
	}

	public String code() {
		return code;
	}

	protected int length() {
		return output.length();
	}
	
	protected boolean push() {
		return output.push();
	}
	
	/**
	 * Important: call {@link #header(String, String)} before you call this. If
	 * you manually set a code, the reply will flush even if empty. So do not
	 * call this if you wan't to reply asynchronously. For example if you want
	 * to redirect a browser.
	 * 
	 * <pre>
	 * public void filter(Event event) throw Event {
	 *     event.reply().header(&quot;Location&quot;, &quot;/login&quot;);
	 *     event.reply().code(&quot;302 Found&quot;);
	 *     throw event; // stop the chain
	 * }
	 * </pre>
	 * 
	 * @param code
	 */
	public void code(String code) throws IOException {
		if(Event.LOG) {
			event.log("code", Event.DEBUG);
		}
		
		this.code = code;
		output.init(0);
	}

	public String type() {
		return type;
	}

	/**
	 * This has to be called before {@link #output()} to have effect.
	 * @param type
	 */
	public void type(String type) {
		this.type = type;
	}

	/**
	 * This has to be called before {@link #output()} to have effect.
	 * @param name
	 * @param value
	 */
	public void header(String name, String value) {
		if(headers == null) {
			headers = new HashMap();
		}

		headers.put(name, value);
	}

	protected long modified() {
		return modified;
	}

	protected void modified(long modified) {
		this.modified = modified;
	}

	/**
	 * Calls {@link #output(0)}.
	 * @return the output stream.
	 * @throws IOException
	 */
	public Output output() throws IOException {
		return output(0);
	}
	
	/**
	 * Important: call {@link #header(String, String)} and {@link #code(String)}
	 * first, in that order, this method is the point of no return for delivery
	 * of a request. It enables OP_WRITE and writes the headers immediately.
	 * 
	 * @param length if you want to write fixed length data
	 * @return the output stream.
	 * @throws IOException
	 */
	public Output output(long length) throws IOException {
		if(Event.LOG) {
			event.log("output " + length, Event.DEBUG);
		}
		
		output.init(length);
		return output;
	}
	
	protected void policy() throws IOException {
		output.policy();
	}

	public synchronized int wakeup() {
		return wakeup(false, false);
	}
	
	public synchronized int wakeup(boolean wakeup) {
		return wakeup(wakeup, false);
	}
	
	/**
	 * To send data asynchronously, call this and the event will be re-filtered.
	 * Just make sure you didn't already flush the reply and that you are ready to
	 * catch the event when it recycles in {@link Service#filter(Event)}!
	 * 
	 * @param wakeup Automatically wakeup the worker on this event if WORKING.
	 * @param queue Automatically queue this event if WORKING.
	 * @return The status of the wakeup call. {@link Reply#OK}, {@link Reply#COMPLETE}, {@link Reply#CLOSED} or {@link Reply#WORKING}
	 */
	public synchronized int wakeup(boolean wakeup, boolean queue) {
		if(!wakeup && output.complete())
			return COMPLETE;
		
		if(!event.channel().isOpen())
			return CLOSED;
		
		int wake = event.daemon().match(event, null);
		
		if(wake == 0)
			return OK;
		
		if(wakeup)
			event.wakeup = true;
		
		if(queue)
			event.daemon().queue(event);
		
		return WORKING;
	}
	
	public String toString() {
		return "  type: " + type + Output.EOL + 
				"  headers: " + headers + Output.EOL + 
				"  modified: " + modified + Output.EOL + 
				"  code: " + code + Output.EOL + 
				output;
	}
}
