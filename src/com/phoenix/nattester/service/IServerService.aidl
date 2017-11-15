package com.phoenix.nattester.service;
import com.phoenix.nattester.service.IServerServiceCallback;
import com.phoenix.nattester.service.Message2SendParc;
//import com.phoenix.nattester.service.SvcCallback;
interface IServerService{
  int sendMessage(in String aIP, int dstPort, in byte[] aMessage);
  int sendMessageList(in String aIP, in List<Message2SendParc> lst); 
  int clearSenderQueue();
  void setCallback(IServerServiceCallback callback);
  
  
  //void setCallback(in SvcCallback callback);
  void startServer();
  void stopServer();
}
