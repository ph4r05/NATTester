package com.phoenix.nattester.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerListener extends Thread{
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerListener.class);
	
	private int localPort;
	private boolean running=true;
	private DatagramSocket socket;
	private IServerServiceCallback callback=null;
	
	public ServerListener(int localPort, DatagramSocket socket){
		this.setName("ServerListener: " + localPort);
		this.localPort = localPort;
		this.socket = socket;
	}
	
	@Override
	public void run() {
		// just obtain local address and bind to defined socket
		if (socket==null) {
			LOGGER.error("Passed null socket, cannot bind to it");
			return;
		}
		
		LOGGER.debug("ServerListener is running...");
		try {
            while(this.running) {
            	try {
	                DatagramPacket receive = new DatagramPacket(new byte[256], 256);
	                socket.receive(receive);
	                ReceivedMessage msg = new ReceivedMessage();
	                msg.setMilliReceived(System.currentTimeMillis());
	                msg.setSourceIP(receive.getAddress().toString());
	                msg.setSourcePort(receive.getPort());
	                msg.setMessage(receive.getData());
	                LOGGER.info("Message received! "+msg.toString());
	                
	                if (callback!=null){
	                	try {
	                		callback.messageReceived(msg);
	                	} catch(Exception e){
	                		LOGGER.error("Exception when notying about new message", e);
	                	}
	                } else {
	                	LOGGER.debug("callback is null");
	                }
            	} catch(SocketTimeoutException e){
            		;
            	}
            }
        }
        catch(IOException e) {
            LOGGER.error("IOException in listener", e);
        }
        finally {
        	LOGGER.debug("ServerSender is shutting down...");
        }
	}
	
	public int getLocalPort() {
		return localPort;
	}
	public void setLocalPort(int localPort) {
		this.localPort = localPort;
	}
	public boolean isRunning() {
		return running;
	}
	public void setRunning(boolean running) {
		this.running = running;
	}
	public IServerServiceCallback getCallback() {
		return callback;
	}
	public void setCallback(IServerServiceCallback callback) {
		LOGGER.debug("Callback set to listener");
		this.callback = callback;
	}
	public DatagramSocket getSocket() {
		return socket;
	}
	public void setSocket(DatagramSocket socket) {
		this.socket = socket;
	}
}
