package com.phoenix.nattester.service;
import com.phoenix.nattester.service.ReceivedMessage;
interface IServerServiceCallback{
  void messageSent(int success);
  void messageReceived(in ReceivedMessage msg);
}
