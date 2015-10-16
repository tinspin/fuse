package fuse;

import se.rupy.http.Daemon;
import se.rupy.http.Event;

public interface Node {
	public void call(Daemon daemon, Node node) throws Exception;
	public String push(Event event, String name, String data) throws Exception;
	public void broadcast(String name, String data) throws Exception;
	public void exit();
}