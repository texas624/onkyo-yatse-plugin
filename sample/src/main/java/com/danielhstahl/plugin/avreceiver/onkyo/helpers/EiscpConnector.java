package com.danielhstahl.plugin.avreceiver.onkyo.helpers;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;


/**
 * autodiscover device / aquire connection
 *
 * @author marcelpokrandt
 *
 */
public class EiscpConnector {
	public  final static String MUTE_ON="AMT01";
	public final static String MUTE_OFF="AMT00";
	public final static String MASTER_VOL_DOWN="MVLDOWN";
	public final static String MASTER_VOL_UP="MVLUP";
	public final static String MUTE_QUERY="AMTQSTN";
	public final static String MASTER_VOL_QUERY="MVLQSTN";
	public final static String MASTER_VOL="MVL";
	public final static String SYSTEM_POWER="PWR";
	public final static String MUTE="AMT";
	public final static String SYSTEM_POWER_QUERY="PWRQSTN";
	public final static String SYSTEM_POWER_STANDBY="PWR00";
	public final static String SYSTEM_POWER_ON="PWR01";
	public final static int DEFAULT_EISCP_PORT=60128;
	Socket socket;
	BufferedOutputStream socketOut;
	BufferedInputStream socketIn;
	private String address;

	EiscpConnectorSocketReaderThread eiscpConnectorReaderThread = null;
	Thread eiscpConnectorReaderThreadThread = null;

	boolean closed = false;



	public static EiscpConnector autodiscover() throws Exception {
		String queryDatagramString = "!xECNQSTN";
		byte[] queryDatagram = EiscpProtocolHelper.iscpToEiscpMessage(queryDatagramString);

		int port = DEFAULT_EISCP_PORT;

		DatagramSocket datagramSocket = new DatagramSocket();
		datagramSocket.setBroadcast(true);

		//log.debug("send autodiscover datagram: " + queryDatagramString);// + " -> " + EiscpProtocolHelper.convertToHexString(queryDatagram));
		DatagramPacket p = new DatagramPacket(
				queryDatagram, queryDatagram.length);
		p.setAddress(
				InetAddress.getByAddress(
						new byte[] {
								(byte) 255, (byte) 255, (byte) 255, (byte) 255 }));
		p.setPort(port);
		datagramSocket.send(p);

		do {
			try {
				//log.info("wait for autodiscover answere");
				return receiveAutodiscoverAnswere(datagramSocket);
			} catch (Exception ex) {
				//log.warn(ex.getMessage(), ex);
			}
		} while(true);
	}

	public static EiscpConnector receiveAutodiscoverAnswere(DatagramSocket datagramSocket) throws Exception {
		byte[] buf = new byte[256];

		DatagramPacket pct = new DatagramPacket(buf, buf.length);

		datagramSocket.receive(pct);

		byte[] receivedMessage = new byte[pct.getLength()];
		System.arraycopy(buf, 0, receivedMessage, 0, receivedMessage.length);

		//log.debug("answere from " + pct.getSocketAddress());

		String responseString = EiscpProtocolHelper.interpreteEiscpResponse(receivedMessage);
		String address = pct.getAddress().getHostAddress().toString();
		EiscpConnector conn = new EiscpConnector(address, responseString);
		return conn;
	}


	public EiscpConnector(String address, String autodiscoverResponse) throws UnknownHostException, IOException {
		// TODO parse
		//log.debug("autodiscovered: " + autodiscoverResponse);
		init(address, DEFAULT_EISCP_PORT);
	}

	public EiscpConnector(String address) throws UnknownHostException, IOException {
		init(address, DEFAULT_EISCP_PORT);
	}

	public EiscpConnector(String address, int port) throws UnknownHostException, IOException {
		init(address, port);
	}

	public void addListener(EiscpListener listener) {
		if (eiscpConnectorReaderThreadThread == null) {
			eiscpConnectorReaderThread = new EiscpConnectorSocketReaderThread(this, socketIn);
			eiscpConnectorReaderThreadThread = new Thread(eiscpConnectorReaderThread);
			eiscpConnectorReaderThreadThread.start();
		}

		eiscpConnectorReaderThread.addListener(listener);
	}

	public void removeListener(EiscpListener listener) {
		eiscpConnectorReaderThread.removeListener(listener);
	}

	private void init(String address, int port) throws UnknownHostException, IOException {
		//log.debug("connect to " + address + ":" + port);
		this.address=address;
		socket = new Socket(address, port);
		socketOut = new BufferedOutputStream(socket.getOutputStream());
		socketIn = new BufferedInputStream(socket.getInputStream());

		//log.debug("connected");
	}
	public String getAddress(){
		return address;
	}

	@Override
	protected void finalize() throws Throwable {
		if (! closed) close();
		super.finalize();
	}


	public int available() throws IOException {
		return socketIn.available();
	}

	public byte[] readMessage() throws IOException {
		int a = socketIn.available();
		if (a <= 0) return null;

		byte[] res = new byte[a];
		int readBytes = socketIn.read(res);

		if (readBytes < a) throw new IOException("read too less bytes");
		return res;
	}


	public void sendIscpCommand(String command) throws IOException {
		//log.debug("sendIscpCommand: " + command);
		String message = "!1" + command;

		sendIscpMessage(message);
	}

	/**
	 *
	 * @param message already has prepended !1
	 * @throws IOException
	 */
	public void sendIscpMessage(String message) throws IOException {
		byte[] eiscpMessage = EiscpProtocolHelper.iscpToEiscpMessage(message);
		
		/*log.trace("sendIscpMessage: {} - eISCP message: {} bytes - {}",
					message, 
					eiscpMessage.length, 
					EiscpProtocolHelper.convertToHexString(eiscpMessage));*/

		socketOut.write(eiscpMessage);
		socketOut.flush();
	}

	public void close() {
		//log.debug("-- close");

		if (eiscpConnectorReaderThread != null) {
			eiscpConnectorReaderThread.quit();
		}

		try {
			socketOut.close();
		} catch (Exception ex) {}
		try {
			socketIn.close();
		} catch (Exception ex) {}
		try {
			socket.close();
		} catch (Exception ex) {}

		closed = true;
	}
}