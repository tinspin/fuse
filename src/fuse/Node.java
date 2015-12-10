package fuse;

import se.rupy.http.Daemon;
import se.rupy.http.Event;

public interface Node {
	public void call(Daemon daemon, Node node) throws Exception;
	public String push(Event event, String data) throws Exception;
	public String push(String salt, String data, boolean wake) throws Exception;
	public void broadcast(String message, boolean finish) throws Exception;
	public boolean wakeup(String name);
	public void remove(String salt, int place) throws Exception;
	public void exit();
}