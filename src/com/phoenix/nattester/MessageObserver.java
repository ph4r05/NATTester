package com.phoenix.nattester;

import com.phoenix.nattester.service.ReceivedMessage;

public interface MessageObserver {
	void onNewMessageReceived(ReceivedMessage msg);
}
