package com.phoenix.nattester.service;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

public class ServerService extends Service {
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerService.class);
	public static final int srvPort=23457;
	
	private IServerServiceCallback callback=null;
	private DatagramSocket socket;
	
	private ServerListener listener;
	private ServerSender sender;
	
	// Implement public interface for the service
	private final IServerService.Stub binder = new IServerService.Stub() {
		@Override
		public int sendMessage(String aIP, int dstPort, byte[] aMessage) throws RemoteException {
			Message2Send m2s;
			try {
				m2s = new Message2Send(InetAddress.getByName(aIP), dstPort, aMessage);
				sender.addMessage(m2s);
				return 1;
			} catch (UnknownHostException e) {
				LOGGER.error("Unable to enqueue packet to send");
				return -1;
			}
		}

		@Override
		public synchronized void setCallback(IServerServiceCallback callback) throws RemoteException {
			LOGGER.debug("service: about to update callback: " + callback);
			ServerService.this.callback = callback;
		}

		@Override
		public void startServer() throws RemoteException {
			// init socket as listening
			try {
				LOGGER.debug("Starting server...");
				if (socket!=null){
					LOGGER.error("Socket is not null, probably running");
				}
				
				// open socket, bind it and set timeout on it
				socket = new DatagramSocket(srvPort);
				socket.setReuseAddress(true);
                socket.setSoTimeout(300);
				
				// prepare sender and listener threads
				sender = new ServerSender(srvPort, socket);
				listener = new ServerListener(srvPort, socket);
				
				LOGGER.debug("Callback in service: " + ServerService.this.callback);
				sender.setCallback(ServerService.this.callback);
				listener.setCallback(ServerService.this.callback);
				
				sender.start();
				listener.start();
			} catch (SocketException e) {
				LOGGER.error("Problem during starting server socket", e);
			}
		}

		@Override
		public void stopServer() throws RemoteException {
			LOGGER.debug("Stopping server...");
			
		}
	};
	
	
	@Override
	public IBinder onBind(Intent intent) {
		LOGGER.debug("Service is bound: " + intent);
		if (ServerService.class.getName().equals(intent.getAction())) {
			LOGGER.debug("Bound by intent " + intent);
		    return binder;
		  } else {
		    return null;
		  }
	}

	@Override
	public void onCreate() {
		super.onCreate();
		LOGGER.debug("Service is starting...");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		LOGGER.debug("Service is stopping...");
		
		try {
			if (listener!=null){
				LOGGER.debug("Shutting down listener");
				listener.setRunning(false);
				
				try {
					Thread.sleep(1000);
					listener.interrupt();
				}catch(Exception e){
					
				}
				listener=null;
			}
			
			if (sender!=null){
				LOGGER.debug("Shutting down sender");
				sender.setRunning(false);
				
				try {
					Thread.sleep(1000);
					sender.interrupt();
				}catch(Exception e){
					
				}
				sender=null;
			}
			
			if (socket!=null){
				if (socket.isBound()){
					LOGGER.debug("Disconnecting socket");
					socket.close();
				}
				
				socket=null;
			}
		} catch(Exception e){
			LOGGER.error("Exception during ending server socket", e);
		}
	}

}
