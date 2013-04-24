package com.phoenix.nattester.random;

import com.phoenix.nattester.TaskAppConfig;

public class RandomTaskParam {
	TaskAppConfig cfg;
	int stunPorts=99;

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

	@Override
	public String toString() {
		return "RandomTaskParam [cfg=" + cfg + "]";
	}
	
	
}
