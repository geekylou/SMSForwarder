package com.example.gpstest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.example.gpstest.BluetoothInterfaceService.IPSocketThread;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

public class TCPPacketHandler 
{
	IPSocketThread  mIPSocketThread = new IPSocketThread();
	Context ctx;
	public static final String ACTION_RESP = "uk.me.geekylou.GPSTest.PACKET_RECEIVED";
	
	public TCPPacketHandler(Context ctx,int port)
	{
		this.ctx = ctx;
	}
	
	class IPSocketThread extends Thread
	{
		public Object lock = new Object();
		String msg = "";
		ServerSocket mServerSocket;
		Socket       mSocket;
		DataOutputStream out = null;
		DataInputStream  in;
		
		boolean running = false, isOpen = false;
		public void run()
		{
			byte buffer[] = new byte[256];
			try {
				mSocket = new Socket(InetAddress.getByName("192.168.0.75"),9100);
				
				out = new DataOutputStream(mSocket.getOutputStream());
				in  = new DataInputStream(mSocket.getInputStream());
				synchronized(this) 
				{
				isOpen = true;
				}
				while(running)
				{
					if (in.read(buffer, 0, 6) > 0)
					{
						in.read(buffer, 6, buffer[5] + 1);
					}
					// processing done here¦.
					//Intent broadcastIntent = new Intent();
					//broadcastIntent.setAction(ACTION_RESP);
					//broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
					//broadcastIntent.putExtra("SOCK", resultTxt);
					//ctx.sendBroadcast(broadcastIntent);
				}
				in.close();
				out.close();
					
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
		//	} catch (InterruptedException e) {
				// TODO Auto-generated catch block
		//		e.printStackTrace();
			}
			out = null;
		}
		//private void synchronized(IPSocketThread ipSocketThread) {
			// TODO Auto-generated method stub
			
		
	}
	class ResponseReceiver extends BroadcastReceiver 
	{	
		public static final String ACTION_RESP = "uk.me.geekylou.GPSTest.SEND_PACKET";

		ResponseReceiver()
		{
			super();
		}
	   @Override
	    public void onReceive(Context context, Intent intent) 
	   	{	     
    	   if (mIPSocketThread.isOpen)
			{
				try {
					mIPSocketThread.out.write(intent.getByteArrayExtra ("packetData"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}	
	   	}
	}
	void start()
	{
		mIPSocketThread.start();
	}
	
	void stop()
	{
		mIPSocketThread.running = false;
		
        try {
           	if (mIPSocketThread.mSocket != null) mIPSocketThread.mSocket.close();
        	if (mIPSocketThread.mServerSocket != null) mIPSocketThread.mServerSocket.close();
			try {
				mIPSocketThread.join(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        } catch(IOException e) 
        	{
        		e.printStackTrace();
        	}
	}
}
