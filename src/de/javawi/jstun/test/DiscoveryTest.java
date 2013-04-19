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
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.phoenix.nattester.GuiLogger;
import com.phoenix.nattester.MessageInterface;

import de.javawi.jstun.attribute.ChangeRequest;
import de.javawi.jstun.attribute.ChangedAddress;
import de.javawi.jstun.attribute.ErrorCode;
import de.javawi.jstun.attribute.MappedAddress;
import de.javawi.jstun.attribute.MessageAttribute;
import de.javawi.jstun.attribute.MessageAttributeException;
import de.javawi.jstun.attribute.MessageAttributeParsingException;
import de.javawi.jstun.header.MessageHeader;
import de.javawi.jstun.header.MessageHeaderParsingException;
import de.javawi.jstun.util.UtilityException;
import java.util.ArrayList;
import java.util.List;

public class DiscoveryTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryTest.class);
	
	int timeoutLimit = 5000;
	InetAddress iaddress;
	String stunServer;
	int port;
	int timeoutInitValue = 300; //ms
	MappedAddress ma = null;
	ChangedAddress ca = null;
	boolean nodeNatted = true;
	DatagramSocket socketTest1 = null;
	DiscoveryInfo di = null;
	MessageInterface callback = null;
	GuiLogger guiLogger;
	
	public DiscoveryTest(InetAddress iaddress , String stunServer, int port) {
		super();
		this.iaddress = iaddress;
		this.stunServer = stunServer;
		this.port = port;
	}
		
	public DiscoveryInfo test() throws UtilityException, SocketException, UnknownHostException, IOException, MessageAttributeParsingException, MessageAttributeException, MessageHeaderParsingException{
		ma = null;
		ca = null;
		nodeNatted = true;
		socketTest1 = null;
		di = new DiscoveryInfo(iaddress);

		if (callback!=null) callback.addMessage("Starting test1");
		if (test1()) {
			if (callback!=null) callback.addMessage("Starting test2");
			if (test2()) {
				if (callback!=null) callback.addMessage("Starting test1 again");
				if (test1Redo()) {
					if (callback!=null) callback.addMessage("Starting test3");
					test3();
				}
			}
		}
		
        try {
            socketTest1.close();
        } catch(Exception e){
            ;
        }
        
        if (callback!=null) callback.addMessage("Starting symmetric tests");
        
        // detect symmetric NAT IP/port sensitivity
        testSymmetricPortDelta(3480, 10, 34567, 0, true);
        
        // once again with 1 second pause between scans
        testSymmetricPortDelta(3490, 10, 34567, 1000, true);
        
        // finally only port switching
        testSymmetricPortDelta(3500, 10, 34567, 100, false);
        //testSymmetricPortDelta(3510, 10, 34567, 5000, false);
        
        if (callback!=null) callback.addMessage(di.toString());
		return di;
	}
	
        /**
         * Decide whether note is NATed or not - ip & port must match
         * @return
         * @throws UtilityException
         * @throws SocketException
         * @throws UnknownHostException
         * @throws IOException
         * @throws MessageAttributeParsingException
         * @throws MessageHeaderParsingException 
         */
	public boolean test1() throws UtilityException, SocketException, UnknownHostException, IOException, MessageAttributeParsingException, MessageHeaderParsingException {
		int timeSinceFirstTransmission = 0;
		int timeout = timeoutInitValue;
		while (true) {
			try {
				// Test 1 including response
				socketTest1 = new DatagramSocket(new InetSocketAddress(iaddress, 0));
				socketTest1.setReuseAddress(true);
				socketTest1.connect(InetAddress.getByName(stunServer), port);
				socketTest1.setSoTimeout(timeout);
				
				MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
				sendMH.generateTransactionID();
				
				ChangeRequest changeRequest = new ChangeRequest();
				sendMH.addMessageAttribute(changeRequest);
				
				byte[] data = sendMH.getBytes();
				DatagramPacket send = new DatagramPacket(data, data.length);
				socketTest1.send(send);
				
				LOGGER.debug("Test 1: Binding Request sent to: " + stunServer + ":" + port);
				this.guiLog("Test 1: Binding Request sent to: " + stunServer + ":" + port);
			
				MessageHeader receiveMH = new MessageHeader();
				while (!(receiveMH.equalTransactionID(sendMH))) {
					DatagramPacket receive = new DatagramPacket(new byte[200], 200);
					socketTest1.receive(receive);
					receiveMH = MessageHeader.parseHeader(receive.getData());
					receiveMH.parseAttributes(receive.getData());
				}
				
				ma = (MappedAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.MappedAddress);
				ca = (ChangedAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ChangedAddress);
				ErrorCode ec = (ErrorCode) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ErrorCode);
				if (ec != null) {
					di.setError(ec.getResponseCode(), ec.getReason());
					LOGGER.debug("Message header contains an Errorcode message attribute.");
					return false;
				}
				if ((ma == null) || (ca == null)) {
					di.setError(700, "The server is sending an incomplete response (Mapped Address and Changed Address message attributes are missing). The client should not retry.");
					LOGGER.debug("Response does not contain a Mapped Address or Changed Address message attribute.");
					return false;
				} else {
					di.setPublicIP(ma.getAddress().getInetAddress());
					if ((ma.getPort() == socketTest1.getLocalPort()) && (ma.getAddress().getInetAddress().equals(socketTest1.getLocalAddress()))) {
						LOGGER.debug("Node is not natted.");
						nodeNatted = false;
					} else {
						LOGGER.debug("Node is natted.");
					}
					return true;
				}
			} catch (SocketTimeoutException ste) {
				if (timeSinceFirstTransmission < timeoutLimit) {
					LOGGER.debug("Test 1: Socket timeout while receiving the response.");
					this.guiLog("Test 1: Socket timeout while receiving the response.");
					
					timeSinceFirstTransmission += timeout;
					int timeoutAddValue = (timeSinceFirstTransmission * 2);
					if (timeoutAddValue > 1600) timeoutAddValue = 1600;
					timeout = timeoutAddValue;
				} else {
					// node is not capable of udp communication
					LOGGER.debug("Test 1: Socket timeout while receiving the response. Maximum retry limit exceed. Give up.");
					this.guiLog("Test 1: Socket timeout while receiving the response. Maximum retry limit exceed. Give up.");
					
					di.setBlockedUDP();
					LOGGER.debug("Node is not capable of UDP communication.");
					return false;
				}
			} 
		}
	}
		
        /**
         * Changing IP & port in STUN
         * @return
         * @throws UtilityException
         * @throws SocketException
         * @throws UnknownHostException
         * @throws IOException
         * @throws MessageAttributeParsingException
         * @throws MessageAttributeException
         * @throws MessageHeaderParsingException 
         */
	public boolean test2() throws UtilityException, SocketException, UnknownHostException, IOException, MessageAttributeParsingException, MessageAttributeException, MessageHeaderParsingException {
		int timeSinceFirstTransmission = 0;
		int timeout = timeoutInitValue;
		while (true) {
			try {
				// Test 2 including response
				DatagramSocket sendSocket = new DatagramSocket(new InetSocketAddress(iaddress, 0));
				sendSocket.connect(InetAddress.getByName(stunServer), port);
				sendSocket.setSoTimeout(timeout);
				
				MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
				sendMH.generateTransactionID();
				
				ChangeRequest changeRequest = new ChangeRequest();
				changeRequest.setChangeIP();
				changeRequest.setChangePort();
				sendMH.addMessageAttribute(changeRequest);
					 
				byte[] data = sendMH.getBytes(); 
				DatagramPacket send = new DatagramPacket(data, data.length);
				sendSocket.send(send);
				LOGGER.debug("Test 2: Binding Request sent (CHANGE_IP | CHANGE_PORT) to: " + stunServer + ":"+port);
				this.guiLog("Test 2: Binding Request sent (CHANGE_IP | CHANGE_PORT) to: " + stunServer + ":"+port);
				
				int localPort = sendSocket.getLocalPort();
				InetAddress localAddress = sendSocket.getLocalAddress();
				
				sendSocket.close();
				
				DatagramSocket receiveSocket = new DatagramSocket(localPort, localAddress);
				receiveSocket.connect(ca.getAddress().getInetAddress(), ca.getPort());
				receiveSocket.setSoTimeout(timeout);
                LOGGER.debug("Test 2: Receiving on (CHANGE_IP | CHANGE_PORT) to: " + ca.getAddress().toString() + ":"+ca.getPort());
				
				MessageHeader receiveMH = new MessageHeader();
				while(!(receiveMH.equalTransactionID(sendMH))) {
					DatagramPacket receive = new DatagramPacket(new byte[200], 200);
					receiveSocket.receive(receive);
					receiveMH = MessageHeader.parseHeader(receive.getData());
					receiveMH.parseAttributes(receive.getData());
				}
				ErrorCode ec = (ErrorCode) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ErrorCode);
				if (ec != null) {
					di.setError(ec.getResponseCode(), ec.getReason());
					LOGGER.debug("Message header contains an Errorcode message attribute.");
					return false;
				}
				if (!nodeNatted) {
					di.setOpenAccess();
					LOGGER.debug("Node has open access to the Internet (or, at least the node is behind a full-cone NAT without translation).");
				} else {
					di.setFullCone();
					LOGGER.debug("Node is behind a full-cone NAT.");
				}
				return false;
			} catch (SocketTimeoutException ste) {
				if (timeSinceFirstTransmission < timeoutLimit) {
					//LOGGER.debug("Test 2: Socket timeout while receiving the response.");
					timeSinceFirstTransmission += timeout;
					int timeoutAddValue = (timeSinceFirstTransmission * 2);
					if (timeoutAddValue > 1600) timeoutAddValue = 1600;
					timeout = timeoutAddValue;
				} else {
					LOGGER.debug("Test 2: Socket timeout while receiving the response. Maximum retry limit exceed. Give up.");
					this.guiLog("Test 2: Socket timeout while receiving the response. Maximum retry limit exceed. Give up.");
					
					if (!nodeNatted) {
						di.setSymmetricUDPFirewall();
						LOGGER.debug("Node is behind a symmetric UDP firewall.");
						return false;
					} else {
						// not is natted
						// redo test 1 with address and port as offered in the changed-address message attribute
						return true;
					}
				}
			}
		}
	}
	
        /**
         * Test1 again with address and port provided by STUN in CHANGED-ADDRESS field.
         * Thus connecting to STUN with changed IP and changed port.
         * 
         * Returns true if node is behind symmetric NAT
         * 
         * @return
         * @throws UtilityException
         * @throws SocketException
         * @throws UnknownHostException
         * @throws IOException
         * @throws MessageAttributeParsingException
         * @throws MessageHeaderParsingException 
         */
	public boolean test1Redo() throws UtilityException, SocketException, UnknownHostException, IOException, MessageAttributeParsingException, MessageHeaderParsingException{
		int timeSinceFirstTransmission = 0;
		int timeout = timeoutInitValue;
		while (true) {
			// redo test 1 with address and port as offered in the changed-address message attribute
			try {
				// Test 1 with changed port and address values
				socketTest1.connect(ca.getAddress().getInetAddress(), ca.getPort());
				socketTest1.setSoTimeout(timeout);
				
				MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
				sendMH.generateTransactionID();
				
				ChangeRequest changeRequest = new ChangeRequest();
				sendMH.addMessageAttribute(changeRequest);
				
				byte[] data = sendMH.getBytes();
				DatagramPacket send = new DatagramPacket(data, data.length);
				socketTest1.send(send);
				LOGGER.debug("Test 1 redo with changed address: Binding Request sent to: " + ca.getAddress().toString() + ":"+ca.getPort());
				this.guiLog("Test 1 redo with changed address: Binding Request sent to: " + ca.getAddress().toString() + ":"+ca.getPort());
				
				MessageHeader receiveMH = new MessageHeader();
				while (!(receiveMH.equalTransactionID(sendMH))) {
					DatagramPacket receive = new DatagramPacket(new byte[200], 200);
					socketTest1.receive(receive);
					receiveMH = MessageHeader.parseHeader(receive.getData());
					receiveMH.parseAttributes(receive.getData());
				}
				MappedAddress ma2 = (MappedAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.MappedAddress);
				ErrorCode ec = (ErrorCode) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ErrorCode);
				if (ec != null) {
					di.setError(ec.getResponseCode(), ec.getReason());
					LOGGER.debug("Message header contains an Errorcode message attribute.");
					return false;
				}
				if (ma2 == null) {
					di.setError(700, "The server is sending an incomplete response (Mapped Address message attribute is missing). The client should not retry.");
					LOGGER.debug("Response does not contain a Mapped Address message attribute.");
					return false;
				} else {
					if ((ma.getPort() != ma2.getPort()) || (!(ma.getAddress().getInetAddress().equals(ma2.getAddress().getInetAddress())))) {
						di.setSymmetric();
						LOGGER.debug("Node is behind a symmetric NAT.");
						return false;
					}
				}
				return true;
			} catch (SocketTimeoutException ste2) {
				if (timeSinceFirstTransmission < timeoutLimit) {
					LOGGER.debug("Test 1 redo with changed address: Socket timeout while receiving the response.");
					timeSinceFirstTransmission += timeout;
					int timeoutAddValue = (timeSinceFirstTransmission * 2);
					if (timeoutAddValue > 1600) timeoutAddValue = 1600;
					timeout = timeoutAddValue;
				} else {
					LOGGER.debug("Test 1 redo with changed address: Socket timeout while receiving the response.  Maximum retry limit exceed. Give up.");
					return false;
				}
			}
		}
	}
	
        /**
         * Testing port restriction
         * @throws UtilityException
         * @throws SocketException
         * @throws UnknownHostException
         * @throws IOException
         * @throws MessageAttributeParsingException
         * @throws MessageAttributeException
         * @throws MessageHeaderParsingException 
         */
	public void test3() throws UtilityException, SocketException, UnknownHostException, IOException, MessageAttributeParsingException, MessageAttributeException, MessageHeaderParsingException {
		int timeSinceFirstTransmission = 0;
		int timeout = timeoutInitValue;
		while (true) {
			try {
				// Test 3 including response
				DatagramSocket sendSocket = new DatagramSocket(new InetSocketAddress(iaddress, 0));
				sendSocket.connect(InetAddress.getByName(stunServer), port);
				sendSocket.setSoTimeout(timeout);
				
				MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
				sendMH.generateTransactionID();
				
				ChangeRequest changeRequest = new ChangeRequest();
				changeRequest.setChangePort();
				sendMH.addMessageAttribute(changeRequest);
				
				byte[] data = sendMH.getBytes();
				DatagramPacket send = new DatagramPacket(data, data.length);
				sendSocket.send(send);
				LOGGER.debug("Test 3: Binding Request sent to: " + stunServer + ":"+port);
				this.guiLog("Test 3: Binding Request sent to: " + stunServer + ":"+port);
				
				int localPort = sendSocket.getLocalPort();
				InetAddress localAddress = sendSocket.getLocalAddress();
				
				sendSocket.close();
				
				DatagramSocket receiveSocket = new DatagramSocket(localPort, localAddress);
				receiveSocket.connect(InetAddress.getByName(stunServer), ca.getPort());
				receiveSocket.setSoTimeout(timeout);
                LOGGER.debug("Test 3: Receiving on: " + stunServer + ":"+ca.getPort());
				
				MessageHeader receiveMH = new MessageHeader();
				while (!(receiveMH.equalTransactionID(sendMH))) {
					DatagramPacket receive = new DatagramPacket(new byte[200], 200);
					receiveSocket.receive(receive);
					receiveMH = MessageHeader.parseHeader(receive.getData());
					receiveMH.parseAttributes(receive.getData());
				}
				ErrorCode ec = (ErrorCode) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ErrorCode);
				if (ec != null) {
					di.setError(ec.getResponseCode(), ec.getReason());
					LOGGER.debug("Message header contains an Errorcode message attribute.");
					return;
				}
				if (nodeNatted) {
					di.setRestrictedCone();
					LOGGER.debug("Node is behind a restricted NAT.");
					return;
				}
			} catch (SocketTimeoutException ste) {
				if (timeSinceFirstTransmission < timeoutLimit) {
					LOGGER.debug("Test 3: Socket timeout while receiving the response.");
					timeSinceFirstTransmission += timeout;
					int timeoutAddValue = (timeSinceFirstTransmission * 2);
					if (timeoutAddValue > 1600) timeoutAddValue = 1600;
					timeout = timeoutAddValue;
				} else {
					LOGGER.debug("Test 3: Socket timeout while receiving the response. Maximum retry limit exceed. Give up.");
					di.setPortRestrictedCone();
					LOGGER.debug("Node is behind a port restricted NAT.");
					return;
				}
			}
		}
	}
        
        private class Scan{
            public String serverIP;             /* stun IP address */
            public int serverPort;              /* server STUN port that was used */
            public String natIP;                /* NAT IP address */
            public int natPort;                 /* port observed on nat - my output port */
        }
        
        /**
         * Testing symmetric NAT for address/port sensitivity and port number changing.
         * 
         * @return
         * @throws UtilityException
         * @throws SocketException
         * @throws UnknownHostException
         * @throws IOException
         * @throws MessageAttributeParsingException
         * @throws MessageHeaderParsingException 
         */
	public boolean testSymmetricPortDelta(int newPort, int count, int localPort, long pause, boolean switchIP) throws UtilityException, SocketException, UnknownHostException, IOException, MessageAttributeParsingException, MessageHeaderParsingException {
            int currentPort = newPort;
            int scanIteration = 0;
            String address2connect = stunServer;
            InetAddress address2connectInet = InetAddress.getByName(stunServer);
            String logMsg="";
            
            // how many iterations there will be in real?
            int realCount = switchIP ? 2*count : count;
            
            // number of consecutive scans with same IP address but different ports
            int ipblock = 2;

            // here is stored log from scan - to detect address/port sensitivity
            List<Scan> recs = new ArrayList<Scan>(realCount);
            for (scanIteration = 0; scanIteration < realCount; scanIteration++) {
                int timeSinceFirstTransmission = 0;
                int timeout = timeoutInitValue;
                // this while represents simple packet request reply cycle. (problem may occurr - resending)
                while (true) {
                    try {
                        // build simple STUN request on stun server from my interface and static local port
                        socketTest1 = new DatagramSocket(new InetSocketAddress(iaddress, localPort));
                        socketTest1.setReuseAddress(true);
                        socketTest1.connect(address2connectInet, currentPort);
                        socketTest1.setSoTimeout(timeout);

                        MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
                        sendMH.generateTransactionID();

                        ChangeRequest changeRequest = new ChangeRequest();
                        sendMH.addMessageAttribute(changeRequest);

                        byte[] data = sendMH.getBytes();
                        DatagramPacket send = new DatagramPacket(data, data.length);
                        socketTest1.send(send);
                        LOGGER.debug("Test portDelta: Binding Request sent to: " + address2connect + ":" + currentPort);

                        // wait for our packet
                        MessageHeader receiveMH = new MessageHeader();
                        while (!(receiveMH.equalTransactionID(sendMH))) {
                            DatagramPacket receive = new DatagramPacket(new byte[200], 200);
                            socketTest1.receive(receive);
                            receiveMH = MessageHeader.parseHeader(receive.getData());
                            receiveMH.parseAttributes(receive.getData());
                        }

                        // packet arived, get my address (MAPPED-ADDRESS)
                        ma = (MappedAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.MappedAddress);
                        ca = (ChangedAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ChangedAddress);
                        ErrorCode ec = (ErrorCode) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ErrorCode);
                        if (ec != null) {
                            di.setError(ec.getResponseCode(), ec.getReason());
                            LOGGER.debug("Message header contains an Errorcode message attribute.");
                            return false;
                        }
                        if (ma == null || ca == null) {
                            di.setError(700, "The server is sending an incomplete response (Mapped Address message attributes is missing). The client should not retry.");
                            LOGGER.debug("Response does not contain a Mapped Address or Changed Address message attribute.");
                            return false;
                        } else {
                            // close socket
                            socketTest1.close();
                            
                            // received packet is OK here. store scan record
                            Scan rec = new Scan();
                            rec.natIP = ma.getAddress().toString();
                            rec.natPort = ma.getPort();
                            rec.serverIP = address2connect;
                            rec.serverPort = currentPort;
                            recs.add(rec);
                            
                            logMsg="Test portDelta: received reply [ME:" + localPort + "]"
                                    + "<---|"+ma.getAddress().toString()+":"+ma.getPort()+"|"
                                    + "<---{STUN "+address2connect+":"+currentPort+"}";
                            LOGGER.debug(logMsg);
                            this.guiLog(logMsg);
                            
                            if (switchIP){
                                // In order to detect address sensitivity we have to 
                                // perform at least 2 consecutive check with same IP address
                                if ((scanIteration % ipblock) == (ipblock-1)){
                                    // flipping IP address
                                    address2connect = ca.getAddress().toString();
                                    address2connectInet = ca.getAddress().getInetAddress();
                                    // are we returning from IP2 to IP1 thus ending this port block?
                                    if ((scanIteration % (2*ipblock)) == (2*ipblock-1)){
                                        // increment port by one, starting with fresh new block
                                        currentPort += 1;
                                    } else {
                                        // IP1 -> IP2 transition, reset currentPort
                                        currentPort-=ipblock-1;
                                    }
                                } else {
                                    // for same address we are just incrementing port number
                                    currentPort += 1;
                                }
                                
                            } else {
                                // we are switching only ports, IP is constant
                                currentPort += 1;
                            }
                            
                            // inducing delay here if it is required
                            if (pause>0){
                                try {
                                    Thread.sleep(pause);
                                } catch(Exception e){
                                    
                                }
                            }
                            
                            // stop while cycle -> next scan iteration
                            break;
                        }
                    } catch (SocketTimeoutException ste) {
                        if (timeSinceFirstTransmission < timeoutLimit) {
                            LOGGER.debug("Test portDelta: Socket timeout while receiving the response.");
                            timeSinceFirstTransmission += timeout;
                            int timeoutAddValue = (timeSinceFirstTransmission * 2);
                            if (timeoutAddValue > 1600) {
                                timeoutAddValue = 1600;
                            }
                            timeout = timeoutAddValue;
                        } else {
                            // node is not capable of udp communication
                            LOGGER.debug("Test portDelta: Socket timeout while receiving the response. Maximum retry limit exceed. Give up.");
                            di.setBlockedUDP();
                            return false;
                        }
                    } finally {
                        try {
                            socketTest1.close();
                        } catch(Exception e){
                            
                        }
                    }
                }
            }
            
            // Buffering messages
            StringBuilder sb = new StringBuilder();
            
            // test is over, report results
            sb.append("TestPortDelta is finished; \n");
            sb.append("  parameters: switchIP=" + (switchIP ? "true":"false") 
                    + "; pause=" + pause 
                    + "; count=" + count
                    + "; startPort=" + newPort).append("\n");
            
            // dump results and try to determine address/port sensitivity
            Boolean addressSensitive = null;
            Boolean portSensitive = null;
            
            String lastServerIP = null;
            Integer lastServerPort = null;
            Integer lastNatPort = null;
            
            int deltaSum = 0;
            int deltaCn = 0;
            for(Scan s : recs){
                // Address sensitivity was not already determined.
                // Test it, if we have already some previous records &&
                // previous address was different than this one
                if (addressSensitive==null
                        && lastServerIP!=null && lastServerPort!=null && lastNatPort!=null
                        && lastServerIP.equals(s.serverIP)==false){
                    addressSensitive=lastNatPort.equals(s.natPort)==false;
                }
                
                // Similarly test port sensitivity, previous IP address has to be same as previous
                if (portSensitive==null
                        && lastServerIP!=null && lastServerPort!=null && lastNatPort!=null
                        && lastServerIP.equals(s.serverIP)){
                    portSensitive=lastNatPort.equals(s.natPort)==false;
                }
                
                sb.append("[ME:" + localPort + "]"
                                    + "--->|NAT  "+s.natIP+":"+s.natPort+"|"
                                    + "--->{STUN "+s.serverIP+":"+s.serverPort+"}\n");
                
                if (lastNatPort!=null) {
                    deltaSum += s.natPort - lastNatPort;
                    deltaCn  += 1;
                }
                
                lastServerIP = s.serverIP;
                lastServerPort = s.serverPort;
                lastNatPort = s.natPort;
            }
            
            sb.append("NAT is address-sensitive: " 
                    + (addressSensitive == null ? "N/A" : addressSensitive)).append("\n");
            sb.append("NAT is port-sensitive: " 
                    + (portSensitive == null ? "N/A" : portSensitive)).append("\n");
            
            float deltaP = (float)deltaSum / (float)deltaCn;
            sb.append("Average deltaP = " + deltaP);
            if (callback!=null) callback.addMessage(sb.toString());
            
            return false;
    }

		public InetAddress getIaddress() {
			return iaddress;
		}

		public void setIaddress(InetAddress iaddress) {
			this.iaddress = iaddress;
		}

		public String getStunServer() {
			return stunServer;
		}

		public void setStunServer(String stunServer) {
			this.stunServer = stunServer;
		}

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public int getTimeoutInitValue() {
			return timeoutInitValue;
		}

		public void setTimeoutInitValue(int timeoutInitValue) {
			this.timeoutInitValue = timeoutInitValue;
		}

		public MappedAddress getMa() {
			return ma;
		}

		public void setMa(MappedAddress ma) {
			this.ma = ma;
		}

		public ChangedAddress getCa() {
			return ca;
		}

		public void setCa(ChangedAddress ca) {
			this.ca = ca;
		}

		public boolean isNodeNatted() {
			return nodeNatted;
		}

		public void setNodeNatted(boolean nodeNatted) {
			this.nodeNatted = nodeNatted;
		}

		public DatagramSocket getSocketTest1() {
			return socketTest1;
		}

		public void setSocketTest1(DatagramSocket socketTest1) {
			this.socketTest1 = socketTest1;
		}

		public DiscoveryInfo getDi() {
			return di;
		}

		public void setDi(DiscoveryInfo di) {
			this.di = di;
		}

		public MessageInterface getCallback() {
			return callback;
		}

		public void setCallback(MessageInterface callback) {
			this.callback = callback;
		}

		public GuiLogger getGuiLogger() {
			return guiLogger;
		}

		public void setGuiLogger(GuiLogger guiLogger) {
			this.guiLogger = guiLogger;
		}
	
		private void guiLog(String message){
			if (this.callback==null)return;
			callback.addMessage(message);
		}
	
}
