package com.mocialmedia;



public class MsgSort implements Comparable<MsgSort> {
	long logTime;
	String body;
	String roomName;
	String sender;
	String msg;
	
	public MsgSort(long logTime,String body,String roomName,String sender,String msg) {
		// TODO Auto-generated constructor stub
		this.logTime=logTime;
		this.body=body;
		this.roomName=roomName;
		this.sender=sender;
		this.msg=msg;
	} 
	
	@Override
	public int compareTo(MsgSort m) {
		// TODO Auto-generated method stub
		return m.logTime> this.logTime? 1 : m.logTime < this.logTime ? -1 : 0;
	}
	

}
