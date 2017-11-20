package deuterium;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;

public class Main {

	public static final InetSocketAddress DISCOVERY_MULTICAST_GROUP = new InetSocketAddress("239.255.10.200", 50160);
	public static final int DISCOERY_TIMEOUT_MS = 500;
	
	/**
	 * If no arguments, start server and connect to local server.
	 * 
	 * If one argument, try to parse it as server IP.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("deuterium0.0.1 --- " + Arrays.toString(args));

		String serverUrl;
		
		if(args.length == 0) {
			// No arguments, try to find server in local network
			serverUrl = discoverServer();
			
			if(serverUrl == null) {
				// No server was found, start one on this host
				System.out.println("No server discovered in local network. Starting server on this host...");
				
				Server server = new Server();
				new Thread(server).start();
				makeLocalServerDiscoverable();
				
				// Give the server some time to initialize, so the connection won't be denied if the client thread starts before the server
				// This should be properly synchronized instead of just waiting
				try {
					Thread.sleep(400);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} else {
			// If argument was given, assume it is the hostname of the server
			serverUrl = args[0];
		}
		
		
		Client client = new Client(serverUrl);
		new Thread(client).start();
		Shell.run(client.receivedFromServerQueue, client.willSendToServerQueue);
	}

	private static String discoverServer() {
		try {
			MulticastSocket discoverSocket = new MulticastSocket(DISCOVERY_MULTICAST_GROUP.getPort()+1);
			
			discoverSocket.joinGroup(DISCOVERY_MULTICAST_GROUP.getAddress());
			discoverSocket.setSoTimeout(DISCOERY_TIMEOUT_MS);
			
			DatagramPacket packet = new DatagramPacket(new byte[128], 0);
			discoverSocket.receive(packet);
			
			String senderAddr = packet.getAddress().getHostAddress();
			System.out.println("Discovered server at " + senderAddr);
			
			discoverSocket.close();
			
			return senderAddr;
		} catch (SocketTimeoutException e) {
			return null; // Intentionally return null, since no server responded in time
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}

	private static void makeLocalServerDiscoverable() {
		new Thread(() -> {
			try {
				MulticastSocket publishSock = new MulticastSocket(DISCOVERY_MULTICAST_GROUP.getPort()+1);
				
				while(true) {
					publishSock.send(new DatagramPacket(new byte[] { 42, 24 }, 2, DISCOVERY_MULTICAST_GROUP));
					Thread.sleep(DISCOERY_TIMEOUT_MS / 2);
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}).start();
	}
	
}
