/*
 * This file is part of JSTUN. 
 * 
 * Copyright (c) 2005 Thomas King <king@t-king.de> - All rights
 * reserved.
 * 
 * This software is licensed under either the GNU Public License (GPL),
 * or the Apache 2.0 license. Copies of both license agreements are
 * included in this distribution.
 */

package de.javawi.jstun.test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.phoenix.nattester.MessageInterface;
import com.phoenix.nattester.TaskCancelInfo;

import de.javawi.jstun.attribute.ChangeRequest;
import de.javawi.jstun.attribute.ErrorCode;
import de.javawi.jstun.attribute.MappedAddress;
import de.javawi.jstun.attribute.MessageAttribute;
import de.javawi.jstun.attribute.MessageAttributeException;
import de.javawi.jstun.attribute.MessageAttributeParsingException;
import de.javawi.jstun.attribute.ResponseAddress;
import de.javawi.jstun.attribute.SourceAddress;
import de.javawi.jstun.header.MessageHeader;
import de.javawi.jstun.header.MessageHeaderParsingException;
import de.javawi.jstun.util.UtilityException;

public class ExternalPortBindingLifetimeTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExternalPortBindingLifetimeTest.class);
	String stunServer;
	int port;
	int secport;
	int timeout = 750; //ms
	MappedAddress ma;
	SourceAddress sa;
	Timer timer;
	DatagramSocket initialSocket;
	MessageInterface callback = null;
	TaskCancelInfo cancelInfo = null;
	boolean blocking=true;
	
	// start value for binary search - should be carefully choosen
	int upperBinarySearchLifetime = 245000; // ms
	int lowerBinarySearchLifetime = 0;
	int binarySearchLifetime = ( upperBinarySearchLifetime + lowerBinarySearchLifetime ) / 2;
	
	// lifetime value
	int lifetime = -1; // -1 means undefined.
	boolean completed = false;
	
	int lastExternalPort = -1; // -1 means undefined
		
	public ExternalPortBindingLifetimeTest(String stunServer, int port, int secport) {
		super();
		this.stunServer = stunServer;
		this.port = port;
		this.secport = secport;
		timer = new Timer(true);
	}
	
	public void test() throws UtilityException, SocketException, UnknownHostException, IOException, MessageAttributeParsingException, MessageAttributeException, MessageHeaderParsingException {
		initialSocket = new DatagramSocket();
		initialSocket.connect(InetAddress.getByName(stunServer), port);
		initialSocket.setSoTimeout(timeout);
		
		if (bindingCommunicationInitialSocket()) {
			return;
		}
		BindingLifetimeTask task = new BindingLifetimeTask();
		timer.schedule(task, binarySearchLifetime);
		
		LOGGER.debug("Timer scheduled initially: " + binarySearchLifetime + ".");
		guiLog("Timer scheduled initially: " + binarySearchLifetime + ".");
		
		// make it blocking here
		try {
			while(this.completed==false && blocking && this.wasCancelled()==false){
				Thread.sleep(1000);
			}
		} catch (InterruptedException e) {
			LOGGER.warn("Blocking is interrupted", e);
		}
	}
	
	public void test2() throws UtilityException, SocketException, UnknownHostException, IOException, MessageAttributeParsingException, MessageAttributeException, MessageHeaderParsingException {
		initialSocket = new DatagramSocket();
		initialSocket.connect(InetAddress.getByName(stunServer), port);
		initialSocket.setSoTimeout(timeout);
		
		if (bindingCommunicationInitialSocket()) {
			return;
		}
		SimpleBindingLifetimeTask task = new SimpleBindingLifetimeTask();
		timer.schedule(task, binarySearchLifetime);
		
		LOGGER.debug("Timer scheduled initially: " + binarySearchLifetime + ".");
		guiLog("Timer scheduled initially: " + binarySearchLifetime + ".");
		
		// make it blocking here
		try {
			while(this.completed==false && blocking && this.wasCancelled()==false){
				Thread.sleep(1000);
			}
		} catch (InterruptedException e) {
			LOGGER.warn("Blocking is interrupted", e);
		}
	}
	
	private boolean bindingCommunicationInitialSocket() throws UtilityException, IOException, MessageHeaderParsingException, MessageAttributeParsingException {
		MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
		sendMH.generateTransactionID();
		ChangeRequest changeRequest = new ChangeRequest();
		sendMH.addMessageAttribute(changeRequest);
		byte[] data = sendMH.getBytes();
		
		DatagramPacket send = new DatagramPacket(data, data.length, InetAddress.getByName(stunServer), port);
		initialSocket.send(send);
		initialSocket.send(send);
		LOGGER.debug("Binding Request sent [initialSocket]");
		this.guiLog("Binding Request sent [initialSocket]: BindingRequest");
	
		MessageHeader receiveMH = new MessageHeader();
		int retryCount=10;
		while(retryCount>0){
			try{
				while (!(receiveMH.equalTransactionID(sendMH))) {
					DatagramPacket receive = new DatagramPacket(new byte[512], 512);
					initialSocket.receive(receive);
					receiveMH = MessageHeader.parseHeader(receive.getData());
					receiveMH.parseAttributes(receive.getData());
					
					retryCount=0;
					break;
				}
			} catch(Exception e){
				LOGGER.info("Exception when reading socket; retryCount=" + retryCount, e);
				retryCount-=1;
				
				if (retryCount==0){
					LOGGER.info("Finishing, problem with socket");
					this.completed=true;
					return true;
				}
			}
		}
			
		ma = (MappedAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.MappedAddress);
		sa = (SourceAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.SourceAddress);
		ErrorCode ec = (ErrorCode) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ErrorCode);
		if (ec != null) {
			LOGGER.debug("Message header contains an Errorcode message attribute.");
			this.guiLog("Message header contains an Errorcode message attribute.");
			return true;
		}
		if (ma == null) {
			LOGGER.debug("Response does not contain a Mapped Address message attribute.");
			this.guiLog("Response does not contain a Mapped Address message attribute.");
			return true;
		}
		
		guiLog("[initialSocket] mappedAddress=" + ma.toString() + "; sourceAddress=" + sa.toString());
		return false;
	}
	
	public int getLifetime() {
		return lifetime;
	}
	
	public boolean isCompleted() {
		return completed;
	}
	
	public void setUpperBinarySearchLifetime(int upperBinarySearchLifetime) {
		this.upperBinarySearchLifetime = upperBinarySearchLifetime;
		binarySearchLifetime = (upperBinarySearchLifetime + lowerBinarySearchLifetime) / 2;
	}
	
	class BindingLifetimeTask extends TimerTask {
		
		public BindingLifetimeTask() {
			super();
		}
		
		public void run() {
			try {
				lifetimeQuery();
			} catch (Exception e) {
				LOGGER.debug("Unhandled Exception. BindLifetimeTasks stopped.");
				e.printStackTrace();
			}
		}
		
		public void lifetimeQuery() throws UtilityException, MessageAttributeException, MessageHeaderParsingException, MessageAttributeParsingException, IOException {
			try {
				DatagramSocket socket = new DatagramSocket();
				socket.connect(InetAddress.getByName(stunServer), secport); // has to use different port here - not to refresh mapping for testing one
				socket.setSoTimeout(timeout);
			
				MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
				sendMH.generateTransactionID();
				ChangeRequest changeRequest = new ChangeRequest();
				changeRequest.setChangePort(); // force STUN server to use port to send reply
				
				// tell to the STUN server to send response on this given socket
				ResponseAddress responseAddress = new ResponseAddress();
				responseAddress.setAddress(ma.getAddress());
				responseAddress.setPort(ma.getPort());
				
				sendMH.addMessageAttribute(changeRequest);
				sendMH.addMessageAttribute(responseAddress);
				byte[] data = sendMH.getBytes();
			
				DatagramPacket send = new DatagramPacket(data, data.length, InetAddress.getByName(stunServer), secport);
				socket.send(send);
				socket.send(send);
				
				LOGGER.debug("Binding Request sent.");
				guiLog("Binding Request sent; responseAddress:port=" + ma.toString() + "; sa=" + sa.toString());
		
				MessageHeader receiveMH = new MessageHeader();
				while (!(receiveMH.equalTransactionID(sendMH))) {
					DatagramPacket receive = new DatagramPacket(new byte[512], 512);
					initialSocket.receive(receive);
					receiveMH = MessageHeader.parseHeader(receive.getData());
					receiveMH.parseAttributes(receive.getData());
				}
				ErrorCode ec = (ErrorCode) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ErrorCode);
				if (ec != null) {
					LOGGER.debug("Message header contains errorcode message attribute.");
					return;
				}
				LOGGER.debug("Binding Response received.");
				guiLog("Binding Response received - port is alive");
				
				if (upperBinarySearchLifetime == (lowerBinarySearchLifetime + 1)) {
					LOGGER.debug("BindingLifetimeTest completed. UDP binding lifetime: " + binarySearchLifetime + ".");
					guiLog("BindingLifetimeTest completed. UDP binding lifetime: " + binarySearchLifetime + ".");
					
					completed = true;
					return;
				}
				lifetime = binarySearchLifetime;
				LOGGER.debug("Lifetime update: " + lifetime + ".");
				guiLog("Lifetime update: " + lifetime + ".");
				
				lowerBinarySearchLifetime = binarySearchLifetime;
				binarySearchLifetime = (upperBinarySearchLifetime + lowerBinarySearchLifetime) / 2;
				if (binarySearchLifetime > 0 && wasCancelled()==false) {
					BindingLifetimeTask task = new BindingLifetimeTask();
					timer.schedule(task, binarySearchLifetime);
					
					LOGGER.debug("Timer scheduled: " + binarySearchLifetime + ".");
					guiLog("Lifetime update: " + lifetime + ".\n");
				} else {
					completed = true;
				}
			} catch (SocketTimeoutException ste) {
				LOGGER.debug("Read operation at query socket timeout.");
				guiLog("Read operation at query socket timeout.");
				
				if (upperBinarySearchLifetime == (lowerBinarySearchLifetime + 1)) {
					LOGGER.debug("BindingLifetimeTest completed. UDP binding lifetime: " + binarySearchLifetime + ".");
					guiLog("BindingLifetimeTest completed. UDP binding lifetime: " + binarySearchLifetime + ".");
					
					completed = true;
					return;
				}
				
				upperBinarySearchLifetime = binarySearchLifetime;
				binarySearchLifetime = (upperBinarySearchLifetime + lowerBinarySearchLifetime) / 2;
				if (binarySearchLifetime > 0 && wasCancelled()==false) {
					if (bindingCommunicationInitialSocket()) {
						return;
					}
					BindingLifetimeTask task = new BindingLifetimeTask();
					timer.schedule(task, binarySearchLifetime);
					
					LOGGER.debug("Timer scheduled: " + binarySearchLifetime + ".");
					guiLog("Timer scheduled: " + binarySearchLifetime + ".\n");
				} else {
					completed = true;
				}
			}
		}
	}

	class SimpleBindingLifetimeTask extends TimerTask {
		MappedAddress last_ma = new MappedAddress();
		
		public SimpleBindingLifetimeTask() {
			super();
		}
		
		public void run() {
			try {
				lifetimeQuery();
			} catch (Exception e) {
				LOGGER.debug("Unhandled Exception. BindLifetimeTasks stopped.");
				e.printStackTrace();
			}
		}
		
		public void lifetimeQuery() throws UtilityException, MessageAttributeException, MessageHeaderParsingException, MessageAttributeParsingException, IOException {
			// copy previous mapped address to last_ma
			last_ma.setAddress(ma.getAddress());
			last_ma.setPort(ma.getPort());
			// do the check again
			if (bindingCommunicationInitialSocket()) {
				return;
			}
			// watch out! previous call changed ma
			guiLog("lastport=" + last_ma.getPort() + "; currentPort=" + ma.getPort());
			if (upperBinarySearchLifetime == (lowerBinarySearchLifetime + 1)) {
				LOGGER.debug("BindingLifetimeTest completed. UDP binding lifetime: " + binarySearchLifetime + ".");
				guiLog("BindingLifetimeTest completed. UDP binding lifetime: " + binarySearchLifetime + ".");
				
				completed = true;
				return;
			}
			
			if (last_ma.getPort() != ma.getPort()){
				upperBinarySearchLifetime = binarySearchLifetime;
			} else {
				guiLog("Binding Response received - port is alive");
				lifetime = binarySearchLifetime;
				LOGGER.debug("Lifetime update: " + lifetime + ".");
				guiLog("Lifetime update: " + lifetime + ".");
				
				lowerBinarySearchLifetime = binarySearchLifetime;
			}
			
			binarySearchLifetime = (upperBinarySearchLifetime + lowerBinarySearchLifetime) / 2;
			if (binarySearchLifetime > 0 && wasCancelled()==false) {
				SimpleBindingLifetimeTask task = new SimpleBindingLifetimeTask();
				timer.schedule(task, binarySearchLifetime);
				
				LOGGER.debug("Timer scheduled: " + binarySearchLifetime + ".");
				guiLog("Lifetime update: " + lifetime + ".\n");
			} else {
				completed = true;
			}
		}
	}
	
	
	
	public MessageInterface getCallback() {
		return callback;
	}

	public void setCallback(MessageInterface callback) {
		this.callback = callback;
	}
	
	public TaskCancelInfo getCancelInfo() {
		return cancelInfo;
	}

	public void setCancelInfo(TaskCancelInfo cancelInfo) {
		this.cancelInfo = cancelInfo;
	}
	
	public int getUpperBinarySearchLifetime() {
		return upperBinarySearchLifetime;
	}

	public boolean isBlocking() {
		return blocking;
	}

	public void setBlocking(boolean blocking) {
		this.blocking = blocking;
	}

	private void guiLog(String message){
		if (this.callback==null)return;
		callback.addMessage(message);
	}
	
	private boolean wasCancelled(){
		if (this.cancelInfo==null) return false;
		return this.cancelInfo.wasCancelled();
	}
	
	public synchronized void cancel(){
		this.completed=true;
		timer.cancel();
	}
}

