package com.phoenix.nattester.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.RemoteException;



public class ServerSender extends Thread{
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerSender.class);
	
	private int localPort;
	private boolean running=true;
	private DatagramSocket socket;
	private IServerServiceCallback callback=null;
	
	// messages to send
	private ConcurrentLinkedQueue<Message2Send> messageQueue = new ConcurrentLinkedQueue<Message2Send>();
	
	public ServerSender(int localPort, DatagramSocket socket){
		this.setName("ServerSender: " + localPort);
		this.messageQueue.clear();
		
		this.localPort = localPort;
		this.socket = socket;
	}
	
	public void addMessage(Message2Send msg){
		this.messageQueue.add(msg);
	}
	
	public void clearQueue(){
		this.messageQueue.clear();
	}
	
	@Override
	public void run() {
		// just obtain local address and bind to defined socket
		if (socket==null) {
			LOGGER.error("Passed null socket, cannot bind to it");
			return;
		}
		
		LOGGER.debug("ServerSender is running...");
		while(this.running){
			// at first take a little nap
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				this.running=false;
			}
			
			if (this.messageQueue.isEmpty()) continue;
			
			while(this.messageQueue.isEmpty()==false){
				LOGGER.debug("We have [" + this.messageQueue.size() + "] packets in queue to send");
				
				Message2Send m2s = this.messageQueue.poll();
				Message2Send m2sMaster = m2s;
				if (m2s==null) continue;
				
				Iterator<Message2Send> it = (m2sMaster.blockMessages == null  || m2sMaster.blockMessages.isEmpty()) ? null : m2sMaster.blockMessages.iterator(); 
				do {
					// send message
					try {
						LOGGER.debug("ServerSender is about to send datagram packet: " + m2s.toString());
						DatagramPacket sendPacket = new DatagramPacket(m2s.aMessage, m2s.aMessage.length, m2s.ip, m2s.dstPort);
						socket.send(sendPacket);
						
						// speed optim...
						Thread.sleep(100);
						//if (callback!=null) callback.messageSent(1);
					} catch(Exception e){
						if (callback!=null)
							try {
								callback.messageSent(-1);
							} catch (RemoteException e1) {
								LOGGER.error("Cannot inform callback of unsuccessfull message attempt.", e);
							}
						LOGGER.error("Was not able to send message.", e);
					}
					
					if (it==null) m2s=null;
					else if(it.hasNext()==false) m2s=null;
					else {
						m2s=it.next();
					}
					
				} while(m2s!=null);
				
				it = null;
				m2sMaster.blockMessages=null;
			}
		}
		
		LOGGER.debug("ServerSender is shutting down...");
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
		if (running==false){
			LOGGER.debug("Truncating message queue 2send");
			this.messageQueue.clear();
		}
	}
	public IServerServiceCallback getCallback() {
		return callback;
	}
	public void setCallback(IServerServiceCallback callback) {
		LOGGER.debug("Callback set to sender");
		this.callback = callback;
	}
	public DatagramSocket getSocket() {
		return socket;
	}
	public void setSocket(DatagramSocket socket) {
		this.socket = socket;
	}
}