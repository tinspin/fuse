package se.rupy.http;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONObject;

import se.rupy.http.*;

public class User extends Service {
	private String host() throws Exception {
		return Root.host();
	}

	private String head() throws Exception {
		return "Head:less\r\nHost:" + host();
	}

	public static void redirect(Event event) throws IOException, Event {
		String referer = event.query().header("referer");
		redirect(event, referer == null ? "/" : referer, true);
	}

	public static void redirect(Event event, String path) throws IOException, Event {
		redirect(event, path, false);
	}

	public static void redirect(Event event, String path, boolean forward) throws IOException, Event {
		if(forward) {
			HashMap query = (HashMap) event.query().clone();
			event.session().put("post", query);
		}

		event.reply().header("Location", path);
		event.reply().code("302 Found");
		Output out = event.output();
		out.finish();
		out.flush();
		throw event;
	}

	public static void refill(Event event) {
		HashMap post = (HashMap) event.session().get("post");

		if(post != null) {
			Iterator it = post.keySet().iterator();

			while(it.hasNext()) {
				Object key = it.next();
				Object value = post.get(key);

				event.query().put(key, value);
			}
		}
	}

	public String path() { return "/user"; }

	private void script(Event event) throws Exception {
		Output out = event.output();
		String salt = event.session().string("salt");
		String algo = event.query().string("algo", "sha-256");

		if(algo.equals("sha-256"))
			out.println("<script src=\"sha256.js\"></script>");
		else
			out.println("<script src=\"md5.js\"></script>");
		out.println("<script>");
		out.println("  function join(e) {");
		out.println("    e = e || window.event;");
		out.println("    var unicode = e.charCode ? e.charCode : e.keyCode ? e.keyCode : 0;");
		out.println("    if(unicode == 13) {");
		out.println("      hash('join');");
		out.println("    }");
		out.println("  }");
		out.println("  function sign(e) {");
		out.println("    e = e || window.event;");
		out.println("    var unicode = e.charCode ? e.charCode : e.keyCode ? e.keyCode : 0;");
		out.println("    if(unicode == 13) {");
		out.println("      hash('sign');");
		out.println("    }");
		out.println("  }");
		out.println("  var digits = /^\\d+$/;");
		out.println("  function hash(type) {");
		out.println("    var name = document.getElementById('name');");
		out.println("    var pass = document.getElementById('pass');");
		out.println("    var salt = document.getElementById('salt');");
		out.println("    if(pass.value.length > 0) {");
		out.println("      if(type == 'join') {");
		if(algo.equals("sha-256"))
			out.println("        pass.value = CryptoJS.SHA256(pass.value + name.value.toLowerCase());");
		else
			out.println("        pass.value = md5(pass.value + name.value.toLowerCase());");
		out.println("      } else {");
		out.println("        salt.value = '" + salt + "';");
		out.println("        if(!digits.test(name.value))");
		if(algo.equals("sha-256")) {
			out.println("          pass.value = CryptoJS.SHA256(pass.value + name.value.toLowerCase());");
			out.println("        pass.value = CryptoJS.SHA256(pass.value + salt.value);");
		}
		else {
			out.println("          pass.value = md5(pass.value + name.value.toLowerCase());");
			out.println("        pass.value = md5(pass.value + salt.value);");
		}
		out.println("      }");
		out.println("      document.forms['user'].submit();");
		out.println("    }");
		out.println("  }");
		out.println("</script>");
		out.println("<style>");
		out.println("  a:link, a:hover, a:active, a:visited { color: #6699ff; font-style: italic; }");
		out.println("  div { font-family: monospace; }");
		out.println("  input { font-family: monospace; }");
		out.println("</style>");
	}

	private void print(Event event, String feedback) throws Event, Exception {
		Output out = event.output();
		String name = event.string("name");
		String mail = event.string("mail");
		String fail = event.string("fail");
		String bare = event.query().string("bare", "");
		String host = event.query().header("host");
		String url = event.query().string("url", host);

		if(bare.length() == 0) {
			out.println("<!doctype html>");
			out.println("<html>");
			out.println("<head>");
			out.println("<meta name=\"viewport\" content=\"width=300, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\">");
		}

		script(event);
		
		if(bare.length() == 0) {
			out.println("</head>");
			out.println("<body>");
		}

		out.println("<div><table width=\"100\">");

		if(fail != null) {
			out.println("<tr><td colspan=\"2\"><i><font color=\"#ff3300\">" + fail + "</font></i></td></tr>");
		}

		out.println("<tr>");
		out.println("<form action=\"user\" method=\"post\" name=\"user\"><input type=\"hidden\" name=\"salt\" id=\"salt\" value=\"\"><input type=\"hidden\" name=\"url\" value=\"" + url + "\">");
		out.println("<td><i>name</i>&nbsp;</td><td><input type=\"text\" style=\"width: 100px;\" name=\"name\" id=\"name\" value=\"" + name + "\"></td></tr>");
		out.println("<tr><td><i>pass</i></font>&nbsp;</td><td><input type=\"password\" style=\"width: 100px;\" name=\"pass\" id=\"pass\" onkeypress=\"sign(event);\"></td></tr>");
		out.println("<tr><td><font color=\"#00cc33\"><i>mail*</i></font></td><td><input type=\"text\" style=\"width: 100px;\" name=\"mail\" value=\"" + mail + "\" onkeypress=\"join(event);\"></td></tr>");
		out.println("<tr><td></td><td><a href=\"javascript:hash('sign');\">login</a>&nbsp;<a href=\"javascript:hash('join');\">register</a></td></tr>");
		out.println("<tr><td></td><td><font color=\"#ff9900\"><i>*optional</i></font></td></tr>");
		out.println("</form>");
		out.println("</table></div>");

		if(bare.length() == 0) {
			out.println("</body>");
			out.println("</html>");
		}
	}

	public void filter(Event event) throws Event, Exception {
		event.query().parse();

		String algo = event.query().string("algo", "sha-256");

		if(event.push()) {
			String name = event.query().string("success");
			String fail = event.query().string("fail");
			String host = event.query().header("host");
			String url = event.query().string("url", host);

			JSONObject user = (JSONObject) event.query().get("user");
			
			if(user != null) {
				String pass = event.string("pass");
				String salt = event.string("salt");
				String hash = Deploy.hash(Deploy.hash(user.getString("pass") + user.getString("name"), algo) + salt, algo);

				if(hash.equals(pass)) {
					user.remove("pass");
					event.output().print(user);
				}
				else {
					event.query().put("fail", "pass didn't match");
					redirect(event);
				}
			}
			else if(name.length() > 0) {
				redirect(event, "http://" + url + "?name=" + name);
			}
			else if(fail.length() > 0) {
				Output out = event.output();
				out.println("<meta http-equiv=\"refresh\" content=\"0;URL=http://" + url + "?fail=" + URLEncoder.encode(fail, "UTF-8") + "\">");
				out.finish();
				out.flush();
				throw event;
			}
			else if(event.bit("redirect")) {
				Output out = event.output();
				out.println("<meta http-equiv=\"refresh\" content=\"0;URL=http://" + url + "\">");
				out.finish();
				out.flush();
				throw event;
			}
		}
		else {
			if(event.query().method() == Query.GET) {
				refill(event);
				
				String salt = event.session().string("salt");

				if(salt.length() == 0) {
					event.hold();
					
					Async.Work work = new Async.Work(event) {
						public void send(Async.Call call) throws Exception {
							call.get("/salt", head());
						}

						public void read(String host, String body) throws Exception {
							event.query().put("redirect", "true");
							event.session().put("salt", body);
							event.reply().wakeup(true, true);
						}

						public void fail(String host, Exception e) throws Exception {
							e.printStackTrace();
							event.reply().wakeup(true, true);
						}
					};

					event.daemon().client().send("localhost", work, 30);
					throw event;
				}
				else {
					Output out = event.output();
					print(event, null);
					out.finish();
					out.flush();
				}
			}
			else if(event.query().method() == Query.POST) {
				final String name = event.string("name").toLowerCase();
				final String salt = event.string("salt");
				final String pass = event.string("pass");
				String host = event.string("host");

				if(name.length() < 3) {
					event.query().put("fail", "name too short");
					redirect(event);
				}
				
				if(salt.length() > 0) {
					if(event.session() != null)
						event.session().put("salt", null);
					
					if(host.equals(Root.host())) {
						if(Root.Salt.salt.containsKey(salt)) {
							Root.Salt.salt.remove(salt);
						}
						else {
							event.reply().code("400 Bad Request");
							event.output().print("salt not found");
							throw event;
						}
						
						File file = null;
						
						if(name.matches("[0-9]+")) {
							file = new File(Root.home() + "/node/user/id" + Root.path(Long.parseLong(name)));
						}
						else {
							file = new File(Root.home() + "/node/user/name" + Root.path(name));
						}

						if(!file.exists()) {
							event.output().print("name not found");
							throw event;
						}
						
						JSONObject object = new JSONObject(Root.file(file));
						
						String secret = object.optString("pass");
						boolean key = false;
						
						if(secret.length() == 0) {
							secret = object.optString("key");
							key = true;
						}
						
						String hash = Deploy.hash(secret + salt, algo);
						
						if(hash.equals(pass)) {
							object.remove("pass");
							event.output().print(object);
						}
						else if(key == false) {
							secret = object.optString("key");
							hash = Deploy.hash(secret + salt, algo);
							
							if(hash.equals(pass)) {
								object.remove("pass");
								event.output().print(object);
							}
							else {
								event.output().print("wrong pass");
							}
						}
						else {
							event.output().print("wrong pass");
						}

						throw event;
					}
					else {
						Async.Work work = new Async.Work(event) {
							public void send(Async.Call call) throws Exception {
								String body = "name=" + name + "&pass=" + pass + "&salt=" + salt + "&host=" + host();
								call.post("/user", head(), body.getBytes());
							}

							public void read(String host, String body) throws Exception {
								try {
									JSONObject user = new JSONObject(body);
									event.session().put("user", user);
									event.query().put("success", user.getString("name"));
								}
								catch(Exception e) {
									event.query().put("fail", body);
								}

								event.reply().wakeup(true, true);
							}

							public void fail(String host, Exception e) throws Exception {
								e.printStackTrace();
								event.query().put("fail", "something snapped");
								event.reply().wakeup(true, true);
							}
						};

						event.daemon().client().send("localhost", work, 30);
						throw event;
					}
				}
				else {
					String mail = event.string("mail").toLowerCase();

					if(name.length() > 12) {
						event.query().put("fail", "name too long");
						redirect(event);
					}

					if(mail.length() > 0 && mail.indexOf("@") == -1) {
						event.query().put("fail", "mail @ missing");
						redirect(event);
					}

					String user = "{\"name\":\"" + name + "\",\"pass\":\"" + pass + "\"";
					String list = "key,name";

					if(mail.length() > 0) {
						user += ",\"mail\":\"" + mail + "\"";
						list += ",mail";
					}

					user += "}";

					final String json = user;
					final String sort = list;

					Async.Work work = new Async.Work(event) {
						public void send(Async.Call call) throws Exception {
							call.post("/node", head(), ("json=" + json + "&sort=" + sort + "&create").getBytes("utf-8"));
						}

						public void read(String host, String body) throws Exception {
							boolean invalid = body.indexOf("Validation") > 0;
							boolean collide = body.indexOf("Collision") > 0;

							if(invalid || collide) {
								String message = body.substring(body.indexOf("[") + 1, body.indexOf("]"));
								event.query().put("fail", message.substring(0, 4) + " " + 
										(invalid ? "contains bad characters" : "") + " " + 
										(collide ? "already registered" : ""));
							}
							else {
								JSONObject user = new JSONObject(body);
								event.session().put("user", user);
								event.query().put("success", user.getString("name"));
							}

							event.reply().wakeup(true, true);
						}

						public void fail(String host, Exception e) throws Exception {
							e.printStackTrace();
							event.query().put("fail", e.toString() + "[" + Root.local + "]");
							event.reply().wakeup(true, true);
						}
					};

					event.daemon().client().send("localhost", work, 30);
					throw event;
				}
			}
		}
	}
}