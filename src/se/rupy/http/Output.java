package se.rupy.http;

import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * Handles the outgoing response data.
 */
public abstract class Output extends OutputStream implements Event.Block {
	public final static String EOL = "\r\n";
	private final static byte[] server = ("Server: rupy/1.3" + EOL).getBytes();
	private final static byte[] close = ("Connection: close" + EOL).getBytes();
	private final static byte[] alive = ("Connection: keep-alive" + EOL).getBytes();
	private final static byte[] chunked = ("Transfer-Encoding: chunked" + EOL).getBytes();
	private final static byte[] stream = ("Content-Type: text/event-stream" + EOL).getBytes(); 

	private byte[] one = new byte[1];
	protected int length, size;
	protected long total;
	protected Reply reply;
	protected boolean init, push, fixed, done;

	Output(Reply reply) throws IOException {
		this.reply = reply;
		size = reply.event().daemon().size;
	}

	/**
	 * Used for comet applications to be able to prune 
	 * disconnected clients.
	 * @return If the push has been completed.
	 */
	public boolean complete() {
		return !push && done;
	}

	protected int length() {
		return length;
	}

	protected boolean push() {
		return push;
	}

	public void println(Object o) throws IOException {
		write((o.toString() + EOL).getBytes("UTF-8"));
	}

	public void println(long l) throws IOException {
		write((String.valueOf(l) + EOL).getBytes("UTF-8"));
	}

	public void println(boolean b) throws IOException {
		write((String.valueOf(b) + EOL).getBytes("UTF-8"));
	}

	public void print(Object o) throws IOException {
		write(o.toString().getBytes("UTF-8"));
	}

	public void print(long l) throws IOException {
		write(String.valueOf(l).getBytes("UTF-8"));
	}

	public void print(boolean b) throws IOException {
		write(String.valueOf(b).getBytes("UTF-8"));
	}

	protected void policy() throws IOException {
		wrote("<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\"/></cross-domain-policy>\0".getBytes());
		flush();
	}
	
	protected synchronized void init(long length) throws IOException {
		if (init) {
			if (Event.LOG) {
				reply.event().log("already inited", Event.DEBUG);
			}
			return;
		} else {
			if (Event.LOG) {
				reply.event().log("init " + reply.event().query().version() + " " + length,
						Event.DEBUG);
			}
		}

		done = false;

		reply.event().interest(Event.WRITE);

		init = true;

		if(length > 0) {
			fixed = true;
			headers(length);
		} else {
			if (zero()) {
				headers(0);
			} else {
				headers(-1);
			}
		}
	}

	protected void end() throws IOException {
		if (Event.LOG) {
			if(reply.event().daemon().debug) {
				reply.event().log("end", Event.DEBUG);
			}
		}

		done = true;
		flush();

		if (Event.LOG) {
			if (reply.event().daemon().verbose && length > 0) {
				reply.event().log("reply " + length, Event.VERBOSE);
			}
		}

		reply.event().interest(Event.READ);

		fixed = false;
		init = false;
		length = 0;
	}

	protected void headers(long length) throws IOException {
		//System.out.println("headers " + length);
		//System.out.println(Worker.stack(Thread.currentThread()));
		
		if (Event.LOG) {
			if (reply.event().daemon().verbose) {
				reply.event().log("code " + reply.code(), Event.VERBOSE);
			}
		}

		wrote((reply.event().query().version() + " " + reply.code() + EOL)
				.getBytes());

		// Debug for multiple async cascade...
		//wrote(("Query-Path: " + reply.event().query().path() + EOL).getBytes());
		//wrote(("Event: " + reply.event().index() + EOL).getBytes());
		
		if(reply.event().headless) {
			if(reply.type().equals("text/event-stream")) {
				wrote(stream);
			}
		}
		else {
			wrote(("Date: " + reply.event().worker().date().format(new Date()) + EOL)
					.getBytes());

			if(!zero()) {
				wrote(("Content-Type: " + reply.type() + EOL).getBytes());
			}

			if (reply.modified() > 0) {
				wrote(("Last-Modified: "
						+ reply.event().worker().date().format(new Date(reply.modified())) + EOL)
						.getBytes());
			}

			if (fixed && reply.event().daemon().properties.getProperty("live") != null) {
				wrote(("Cache-Control: max-age=" + reply.event().daemon().cache + EOL)
						.getBytes());
			}

			if (reply.event().session() != null && !reply.event().session().set()) {
				Session session = reply.event().session();
				String cookie = "Set-Cookie: key="
						+ session.key()
						+ ";"
						+ (session.expires() > 0 ? " expires="
								+ reply.event().worker().date().format(new Date(session
										.expires())) + ";" : "")
										+ (session.domain() != null ? " domain=" + session.domain()
												+ ";" : "") + " path=/";

				wrote((cookie + EOL).getBytes());

				reply.event().session().set(true);

				if (Event.LOG) {
					if (reply.event().daemon().verbose) {
						reply.event().log("cookie " + cookie, Event.VERBOSE);
					}
				}
			}

			if (reply.event().close()) {
				wrote(close);
			} else {
				wrote(alive);
			}
			
			wrote(server);
			
			HashMap headers = reply.headers();

			if (headers != null) {
				Iterator it = headers.keySet().iterator();

				while (it.hasNext()) {
					String name = (String) it.next();
					String value = (String) reply.headers().get(name);
					
					wrote((name + ": " + value + EOL).getBytes());
				}
			}
		}
		
		if (length > -1) {
			wrote(("Content-Length: " + length + EOL).getBytes());
		} else {
			wrote(chunked);
		}

		wrote(EOL.getBytes());
	}

	protected void wrote(int b) throws IOException {
		one[0] = (byte) b;
		wrote(one);
	}

	protected void wrote(byte[] b) throws IOException {
		wrote(b, 0, b.length);
	}

	protected void wrote(byte[] b, int off, int len) throws IOException {
		int remaining = 0;

		try {
			ByteBuffer out = reply.event().worker().out();
			remaining = out.remaining();

			total += len;
			
			while (len > remaining) {
				out.put(b, off, remaining);

				internal(false);

				off += remaining;
				len -= remaining;

				remaining = out.remaining();
			}

			if (len > 0) {
				out.put(b, off, len);
			}
		} catch (Failure.Close c) {
			throw c;
		} catch (IOException e) {
			Failure.chain(e);
		} catch (Exception e) {
			throw (IOException) new IOException("You need to increase your socket write buffer!").initCause(e);
		}
	}

	protected void internal(boolean debug) throws Exception {
		ByteBuffer out = reply.event().worker().out();

		if (out.remaining() < size) {
			out.flip();

			while (out.remaining() > 0) {
				int sent = fill();

				if (Event.LOG) {
					if (debug) {
						reply.event().log(
								"sent " + sent + " remaining " + out.remaining(),
								Event.DEBUG);
					}
				}

				if (sent == 0) {
					reply.event().block(this);

					if (Event.LOG) {
						if (debug) {
							reply.event().log("still in buffer " + out.remaining(),
									Event.DEBUG);
						}
					}
				}
			}
		}

		out.clear();
	}
	
	public void flush() throws IOException {
		if (Event.LOG) {
			if(reply.event().daemon().debug) {
				reply.event().log("flush " + length, Event.DEBUG);
			}
		}
		
		try {
			internal(true);
		} catch (Exception e) {
			throw (Failure.Close) new Failure.Close("No flush! (" + reply.event().index() + ")").initCause(e); // Connection dropped by peer
		}
	}

	public int fill() throws IOException {
		ByteBuffer out = reply.event().worker().out();

		int remaining = 0;

		if (reply.event().daemon().debug) {
			remaining = out.remaining();
		}

		int sent = 0;

		try {
			sent = reply.event().channel().write(out);
			//reply.event().touch();
		}
		catch(IOException e) {
			throw (Failure.Close) new Failure.Close().initCause(e); // Connection reset by peer
		}

		if (Event.LOG) {
			if (reply.event().daemon().debug) {
				reply.event().log("filled " + sent + " out of " + remaining,
						Event.DEBUG);
			}
		}

		return sent;
	}

	/**
	 * Flush the terminating empty chunk of a asynchronous stream push. An
	 * event becomes an asynchronous stream push if a request is not written
	 * any data to in the first {@link Service#filter(Event)} call.
	 * 
	 * @throws IOException
	 */
	public abstract void finish() throws IOException;

	protected boolean zero() {
		return reply.code().startsWith("302")
				|| reply.code().startsWith("304")
				|| reply.code().startsWith("505");
	}

	static class Chunked extends Output {
		public static int OFFSET = 6;
		private int cursor = OFFSET, count = 0;

		Chunked(Reply reply) throws IOException {
			super(reply);
		}

		public void write(int b) throws IOException {
			reply.event().worker().chunk()[cursor++] = (byte) b;
			count++;

			if (count == size) {
				write();
			}
		}

		public void write(byte[] b) throws IOException {
			write(b, 0, b.length);
		}

		public void write(byte[] b, int off, int len) throws IOException {
			length += len;

			if (fixed) {
				wrote(b, off, len);
				return;
			}

			byte[] chunk = reply.event().worker().chunk();
			int remain = size - count;

			if (len > remain) {
				System.arraycopy(b, off, chunk, cursor, remain);

				count = size;
				write();

				len -= remain;
				off += remain;

				while (len > size) {
					System.arraycopy(b, off, chunk, OFFSET, size);

					len -= size;
					off += size;

					count = size;
					write();
				}

				cursor = OFFSET;
			}
			if (len > 0) {
				System.arraycopy(b, off, chunk, cursor, len);
				count += len;
				cursor += len;
			}
		}

		protected void write() throws IOException {
			byte[] chunk = reply.event().worker().chunk();
			char[] header = Integer.toHexString(count).toCharArray();
			int length = header.length, start = 4 - length, cursor;

			for (cursor = 0; cursor < length; cursor++) {
				chunk[start + cursor] = (byte) header[cursor];
			}

			chunk[start + (cursor++)] = '\r';
			chunk[start + (cursor++)] = '\n';
			chunk[start + (cursor++) + count] = '\r';
			chunk[start + (cursor++) + count] = '\n';

			wrote(chunk, start, cursor + count);

			count = 0;
			this.cursor = OFFSET;
		}

		public void finish() throws IOException {
			if (complete()) {
				throw new IOException("Reply already complete.");
			}

			push = false;
		}

		public void flush() throws IOException {
			if (init) {
				if (zero()) {
					if (Event.LOG) {
						if(reply.event().daemon().debug) {
							reply.event().log("length " + length, Event.DEBUG);
						}
					}
				} else if (!fixed) {
					if (Event.LOG) {
						if(reply.event().daemon().debug) {
							reply.event().log("chunk flush " + count + " " + complete(), Event.DEBUG);
						}
					}

					if (count > 0) {
						write();
					}

					if (complete()) {
						write();
					}
				}
			} else if (!fixed) {
				if (Event.LOG) {
					if(reply.event().daemon().debug) {
						reply.event().log("asynchronous push " + count, Event.DEBUG);
					}
				}

				push = true;
			}

			super.flush();
		}

		public String toString() {
			return "    length: " + length + Output.EOL + 
					"    size: " + size + Output.EOL + 
					"    init: " + init + Output.EOL + 
					"    push: " + push + Output.EOL + 
					"    fixed: " + fixed + Output.EOL + 
					"    done: " + done + Output.EOL + 
					"      cursor: " + cursor + Output.EOL + 
					"      count: " + count + Output.EOL;
		}
	}
}
