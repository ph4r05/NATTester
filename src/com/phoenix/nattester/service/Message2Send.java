package com.phoenix.nattester.service;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

public class Message2Send {
	public InetAddress ip;
	public int dstPort;
	public byte[] aMessage;
	
	public List<Message2Send> blockMessages = null;
	public Message2Send(InetAddress ip, int dstPort, byte[] aMessage) {
		super();
		this.ip = ip;
		this.dstPort = dstPort;
		this.aMessage = aMessage;
	}
	@Override
	public String toString() {
		return "Message2Send [ip=" + ip + ", dstPort=" + dstPort
				+ ", aMessage=" + Arrays.toString(aMessage) + "]";
	}
	
	
}
