package se.rupy.http;

import java.util.HashMap;

/**
 * Convenience class to avoid casting and parsing everywhere.
 * 
 * @author marc
 */
public class Hash extends HashMap {
	private boolean secure;
	
	public Hash(boolean secure) {
		this.secure = secure;
	}
	
	public long big(String key) {
		return big(key, 0);
	}
	
	public long big(String key, long fail) {
		Object value = super.get(key);

		if (value == null) {
			return fail;
		} else if(value instanceof Long) {
			return ((Long) value).longValue();
		} else if (value instanceof Integer) {
			return ((Integer) value).intValue();
		} else if (value instanceof Short) {
			return ((Short) value).shortValue();
		} else if (value instanceof Byte) {
			return ((Byte) value).byteValue();
		} else if(value instanceof String) {
			String text = (String) value;
			if(text.length() == 0) {
				return fail;
			}
			return Long.parseLong(text);
		}

		throw new ClassCastException();
	}
	
	public int medium(String key) {
		return medium(key, 0);
	}
	
	public int medium(String key, int fail) {
		Object value = super.get(key);

		if (value == null) {
			return fail;
		} else if (value instanceof Integer) {
			return ((Integer) value).intValue();
		} else if (value instanceof Short) {
			return ((Short) value).shortValue();
		} else if (value instanceof Byte) {
			return ((Byte) value).byteValue();
		} else if (value instanceof String) {
			String text = (String) value;
			if(text.length() == 0) {
				return fail;
			}
			return Integer.parseInt(text);
		}

		throw new ClassCastException();
	}
	
	public short small(String key) {
		return small(key, (short) 0);
	}
	
	public short small(String key, short fail) {
		Object value = super.get(key);

		if (value == null) {
			return fail;
		} else if (value instanceof Short) {
			return ((Short) value).shortValue();
		} else if (value instanceof Byte) {
			return ((Byte) value).byteValue();
		} else if (value instanceof String) {
			String text = (String) value;
			if(text.length() == 0) {
				return fail;
			}
			return Short.parseShort(text);
		}

		throw new ClassCastException();
	}

	public byte tiny(String key) {
		return tiny(key, (byte) 0);
	}
	
	public byte tiny(String key, byte fail) {
		Object value = super.get(key);

		if (value == null) {
			return fail;
		} else if (value instanceof Byte) {
			return ((Byte) value).byteValue();
		} else if (value instanceof String) {
			String text = (String) value;
			if(text.length() == 0) {
				return fail;
			}
			return Byte.parseByte(text);
		}

		throw new ClassCastException();
	}
	
	/**
	 * Returns the boolean value, with a twist though 
	 * since a parameter is true if it's key is present.
	 * @param key
	 * @param exist return true if parameter exists?
	 * @return if the parameter is true or exists.
	 */
	public boolean bit(String key, boolean exist) {
		Object value = super.get(key);

		if (value == null) {
			return false;
		} else if (value instanceof Boolean) {
			return ((Boolean) value).booleanValue();
		} else if (value instanceof String) {
			if(exist) return true;
			String s = (String) value;
			if(s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("yes")) return true;
			return false;
		}

		throw new ClassCastException();
	}

	public String string(String key) {
		String value = (String) super.get(key);

		if (value == null) {
			return "";
		}

		return value;
	}
	
	public String string(String key, String fail) {
		String value = (String) super.get(key);

		if (value == null) {
			return fail;
		}

		return value;
	}
	
	public void put(String key, long value) {
		super.put(key, new Long(value));
	}
	
	public void put(String key, int value) {
		super.put(key, new Integer(value));
	}
	
	public void put(String key, short value) {
		super.put(key, new Short(value));
	}
	
	public void put(String key, byte value) {
		super.put(key, new Byte(value));
	}
	
	public void put(String key, boolean value) {
		super.put(key, new Boolean(value));
	}
	
	protected Object secure(Object key, Object value) {
		return super.put(key, value);
	}
	
	public Object put(Object key, Object value) {
		if(secure && key instanceof String && ((String) key).equals("host"))
			return null;
		
		return super.put(key, value);
	}
	
	/**
	 * @return HashMap.toString()
	 */
	public String contents() {
		return super.toString();
	}
}
