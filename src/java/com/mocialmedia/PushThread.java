package com.mocialmedia;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javapns.Push;
import javapns.notification.Payload;
import javapns.notification.PayloadPerDevice;

public class PushThread extends Thread{
	private static final Logger Log = LoggerFactory
			.getLogger(BoomurangPlugin.class);
	
	ArrayList<PayloadPerDevice> payloadPerDevices;
	boolean production=false; //if this is production or dev
	int threadCount=1; //number of threads to run it
	//List<String> devices; // "37d1fa821e4fd1399a43c320ee278037b678bbc136c7a3add35d5347c8597513"
	
	String certFilePass="BoomurangX3@51";
	InputStream  in;
	//Plugin boomurang;
	
	
	public PushThread(ArrayList<PayloadPerDevice> payloadPerDevices,boolean production, int threadCount) {
	//public PushThread(ArrayList<PayloadPerDevice> payloadPerDevices,boolean production, int threadCount,Plugin plugin) {
		// TODO Auto-generated constructor stub
		this.payloadPerDevices=payloadPerDevices;
		this.production=production;
		this.threadCount=threadCount;
		//this.devices=devices;
		//this.boomurang=plugin;
		String certFile;
		if (production){
			certFile="Production.p12";
		}
		else{
			certFile="Development.p12";
		}
		//in=XMPPServer.getInstance().getPluginManager().getPluginClassloader(boomurang).getResourceAsStream(certFile);
		
		
		in = this.getClass().getClassLoader().getResourceAsStream(certFile); // this also works
		
		
	}
	
	public void run()  {
		// TODO Auto-generated method stub
			try {
				//org.jivesoftware.openfire
				//Push.payload(payload, in, certFilePass, production, threadCount, devices);
				Push.payloads(in, certFilePass, production,threadCount, payloadPerDevices);
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				Log.info(e.getMessage(),e);
			}
		
	}

}

