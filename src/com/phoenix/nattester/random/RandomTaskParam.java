package com.phoenix.nattester.random;

import com.phoenix.nattester.TaskAppConfig;

public class RandomTaskParam {
	TaskAppConfig cfg;
	int stunPorts=99;
	boolean noRecv=false;
	boolean noStun=false;
	int pause=0;

	public TaskAppConfig getCfg() {
		return cfg;
	}

	public void setCfg(TaskAppConfig cfg) {
		this.cfg = cfg;
	}

	public int getStunPorts() {
		return stunPorts;
	}

	public void setStunPorts(int stunPorts) {
		this.stunPorts = stunPorts;
	}

	public boolean isNoRecv() {
		return noRecv;
	}

	public void setNoRecv(boolean noRecv) {
		this.noRecv = noRecv;
	}

	public boolean isNoStun() {
		return noStun;
	}

	public void setNoStun(boolean noStun) {
		this.noStun = noStun;
	}

	public int getPause() {
		return pause;
	}

	public void setPause(int pause) {
		this.pause = pause;
	}

	@Override
	public String toString() {
		return "RandomTaskParam [cfg=" + cfg + "]";
	}
	
	
}
