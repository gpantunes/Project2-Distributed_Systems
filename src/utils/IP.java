package utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IP {

	public static String hostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "?.?.?.?";
		}
	}

}