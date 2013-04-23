package com.phoenix.nattester;

public class TaskAppConfigNATTimeout {
	private int testType=1;
	private TaskAppConfig cfg;

	public int getTestType() {
		return testType;
	}

	public void setTestType(int testType) {
		this.testType = testType;
	}

	public TaskAppConfig getCfg() {
		return cfg;
	}

	public void setCfg(TaskAppConfig cfg) {
		this.cfg = cfg;
	}

	@Override
	public String toString() {
		return "TaskAppConfigNATTimeout [testType=" + testType + ", cfg=" + cfg
				+ "]";
	}

	
	
	
}
