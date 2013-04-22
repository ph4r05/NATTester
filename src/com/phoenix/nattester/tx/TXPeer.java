package com.phoenix.nattester.tx;

// 127.0.0.1;56380;hta;default
public class TXPeer {
	private String IP;
	private int port;
	private String id;
	private String natType;
	public String getIP() {
		return IP;
	}
	public void setIP(String iP) {
		IP = iP;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	
	public String getNatType() {
		return natType;
	}
	public void setNatType(String natType) {
		this.natType = natType;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((IP == null) ? 0 : IP.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((natType == null) ? 0 : natType.hashCode());
		result = prime * result + port;
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TXPeer other = (TXPeer) obj;
		if (IP == null) {
			if (other.IP != null)
				return false;
		} else if (!IP.equals(other.IP))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (natType == null) {
			if (other.natType != null)
				return false;
		} else if (!natType.equals(other.natType))
			return false;
		if (port != other.port)
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "TXPeer [IP=" + IP + ", port=" + port + ", id=" + id + ", opt="
				+ natType + "]";
	}
	
	
}
