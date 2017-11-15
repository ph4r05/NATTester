package com.phoenix.nattester.random;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.javawi.jstun.attribute.ChangeRequest;
import de.javawi.jstun.attribute.ErrorCode;
import de.javawi.jstun.attribute.MappedAddress;
import de.javawi.jstun.attribute.MessageAttribute;
import de.javawi.jstun.header.MessageHeader;
import de.javawi.jstun.util.UtilityException;

public class Probe {
	private static final Logger LOGGER = LoggerFactory.getLogger(Probe.class);
	public final static String TAG = "Probe";
	
	/**
	 * Main worker here - performs real connection & mapped port detection
	 * @param p
	 * @return
	 */
	public ProbeTaskReturn probeScan(ProbeTaskParam p){
		ProbeTaskReturn tr = new ProbeTaskReturn(p);
		tr.setInitTime(System.currentTimeMillis());
		
		final int recvRetryCount = 10;
		final int sendRetryCount = 7;
		
		// prepare sending & receiving socket
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(p.getIntPort());
			socket.connect(p.getStunAddressCached(), p.getExtPort()); // has to use different port here - not to refresh mapping for testing one
			socket.setSoTimeout(p.getSocketTimeout());
		} catch (SocketException e1) {
			LOGGER.warn("Socket exception, cannot continue", e1);
			tr.setError(true);
			tr.setErrCode(5);
			tr.setErrReason("Socket exception - init socket");
			return tr;
		}
		
		// build simple STUN binding request
		byte[] data = null;
		MessageHeader sendMH = null;
		if (p.isNoStun()){
			// Debug - collection is on server side, so help with it.
			StringBuilder sb = new StringBuilder();
			sb.append("||t=").append(System.currentTimeMillis())
			  .append(";s=").append(p.getIntPort())
			  .append(";d=").append(p.getExtPort())
			  .append("||");
			data = sb.toString().getBytes();
		} else {
			// STUN request
			try {
				sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
				sendMH.generateTransactionID();
				ChangeRequest changeRequest = new ChangeRequest();
				sendMH.addMessageAttribute(changeRequest);
				data = sendMH.getBytes();
			} catch(UtilityException uo){
				LOGGER.warn("Utility exception in sendMH", uo);
			}
		}
		
		//
		// SEND
		//
		boolean sent=false;
		int sendRetry = sendRetryCount;
		while(sendRetry > 0 && !sent){
			try {
				DatagramPacket send = new DatagramPacket(data, data.length, p.getStunAddressCached(), p.getExtPort());
				socket.send(send);
				
				tr.setSendTime(System.currentTimeMillis());
				tr.setSendRetryCnt(sendRetryCount - sendRetry);
				sent=true;
			} catch(IOException io){
				LOGGER.warn("IOException during send; retryCnt=" + sendRetry, io);
				sendRetry-=1;
				tr.setSendRetryCnt(sendRetryCount - sendRetry);
				
				if (sendRetry==0){
					tr.setError(true);
					tr.setErrCode(7);
					tr.setErrReason("Send retrycount expired");
					
					try {
						data=null;
						socket.close();
					}catch(Exception e){
						LOGGER.debug("Exception in closing socket", e);
					}
					
					return tr;
				}
			}
		}
		
		if (p.isNoRecv() || p.isNoStun()){
			try {
				data=null;
				socket.close();
			}catch(Exception e){
				LOGGER.debug("Exception in closing socket", e);
			}
			
			tr.setError(false);
			return tr;
		}
		
		//
		// RECEIVE
		//
		byte buff[] = new byte[512];
		MessageHeader receiveMH = new MessageHeader();
		int retryCount=recvRetryCount;
		boolean received = false;
		while(retryCount>0 && !received){
			try{
				while (!(receiveMH.equalTransactionID(sendMH))) {
					DatagramPacket receive = new DatagramPacket(buff, 512);
					socket.receive(receive);
					receiveMH = MessageHeader.parseHeader(receive.getData());
					receiveMH.parseAttributes(receive.getData());
					
					received=true;
					tr.setRecvTime(System.currentTimeMillis());
					tr.setRecvRetryCnt(recvRetryCount - retryCount);
					break;
				}
			} catch(Exception e){
				LOGGER.info("Exception when reading socket; retryCount=" + retryCount, e);
				retryCount-=1;
				tr.setRecvRetryCnt(recvRetryCount - retryCount);
				
				if (retryCount==0){
					tr.setError(true);
					tr.setErrCode(1);
					tr.setErrReason("Receive retrycount expired");
					
					try {
						buff=null;
						socket.close();
					}catch(Exception ex){
						LOGGER.debug("Exception in closing socket", ex);
					}
					
					return tr;
				}
			}
		}
		
		MappedAddress ma = (MappedAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.MappedAddress);
		ErrorCode ec = (ErrorCode) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ErrorCode);
		//SourceAddress sa = (SourceAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.SourceAddress);
		if (ec != null) {
			tr.setError(true);
			tr.setErrCode(2);
			tr.setErrReason("stun.reply.ErrorCode is null");
			
			try {
				buff=null;
				socket.close();
			}catch(Exception e){
				LOGGER.debug("Exception in closing socket", e);
			}
			
			return tr;
		}
		if (ma == null) {
			tr.setError(true);
			tr.setErrCode(3);
			tr.setErrReason("stun.reply.MappedAddress is null");
			
			try {
				buff=null;
				socket.close();
			}catch(Exception e){
				LOGGER.debug("Exception in closing socket", e);
			}
			
			return tr;
		}
		
		// everything OK
		tr.setError(false);
		tr.setFinished(true);
		tr.setMappedPort(ma.getPort());
		tr.setMappedAddress(ma.getAddress().toString());		
		
		try {
			buff=null;
			socket.close();
		}catch(Exception e){
			LOGGER.debug("Exception in closing socket", e);
		}
		
		return tr;
	}
}
