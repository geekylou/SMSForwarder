package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.http.util.ByteArrayBuffer;

import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Telephony;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import android.support.v4.app.NotificationCompat;

abstract class InterfaceBaseService extends Service
{
	int  notificationId = 1001;
	String key;

	BluetoothAdapter mBluetoothAdapter;
	ResponseReceiver mReceiver;
	SMSResponseReceiver mSMSReceiver;
	String mStatusString= "";
	int    mStatusCode  = CONNECTION_STATUS_STOPPED;
	
	/* connectionLocks is used to safely close the connection (ensure that no other service or activity is using it when it's closed.
	   When the connection is opened we add a lock string provided by the caller to the connectionLocks object.
	   When the caller no longer needs the connection it sends a close command with the lock string which causes us to remove the relevent
	   entry from the map.  If the map is empty then we close the connection to the peer.*/
	TreeMap<String,String> connectionLocks = new TreeMap<String,String>();

	SocketThread  mServerSocketThread;
	SocketThread  mSocketThread;
	private StatusReceiver mStatusReceiver;
	
	public static final String SEND_PACKET     = "uk.me.geekylou.GPSTest.SEND_PACKET";
	public static final String PACKET_RECEIVED = "uk.me.geekylou.GPSTest.PACKET_RECEIVED";
	public static final String SERVICE_STATUS_UPDATE = "uk.me.geekylou.GPSTest.SERVICE_STATUS_UPDATE";

	public static final int CONNECTION_STATUS_STOPPED 				 = 0;
	public static final int CONNECTION_STATUS_WAITING_FOR_CONNECTION = 1;
	public static final int CONNECTION_STATUS_CONNECTING             = 2;
	public static final int CONNECTION_STATUS_CONNECTED 			 = 3;
	
	void initListeners(Context context, Intent intent)
	{
		// Register intents for both IPC from other Activity to activity on other host and system events
		// we want to notify the other host of.
		IntentFilter filter = new IntentFilter(SEND_PACKET);
		mReceiver = new ResponseReceiver();
		filter.addCategory(Intent.CATEGORY_DEFAULT);
		registerReceiver(mReceiver, filter);

		filter = new IntentFilter(SERVICE_STATUS_UPDATE);
		mStatusReceiver = new StatusReceiver();
		filter.addCategory(Intent.CATEGORY_DEFAULT);
		registerReceiver(mStatusReceiver, filter);

		
		filter = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
		mSMSReceiver = new SMSResponseReceiver();
		registerReceiver(mSMSReceiver, filter);
		
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(new CallStateListener(), PhoneStateListener.LISTEN_CALL_STATE);
 	}
	
    @SuppressWarnings("deprecation")
	@Override
    public void onDestroy() {
        // Tell the user we stopped.
        Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show();

        connectionLocks.clear(); /* not ideal but we a pulling the rug out from any callers anyway so... */
        if (mServerSocketThread != null) mServerSocketThread.stopRunning();
        if (mSocketThread != null)       mSocketThread.stopRunning();
        if (mReceiver != null)           unregisterReceiver(mReceiver);
        if (mStatusReceiver != null)     unregisterReceiver(mStatusReceiver);
        if (mSMSReceiver != null)        unregisterReceiver(mSMSReceiver);
    }

	void statusUpdate(String statusString, int statusCode)
	{
		mStatusString = statusString;
		
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(BluetoothInterfaceService.SERVICE_STATUS_UPDATE);
		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra("senderId", key);
		broadcastIntent.putExtra("STATUS", new String(statusString));
		broadcastIntent.putExtra("DBG_KEYS", connectionLocks.toString());
		sendBroadcast(broadcastIntent);

	}
	
	class PacketQueueItem
	{
		byte packetBuffer[];
	}
	
    abstract class SocketThread
    {
    	// We use the running variable not only to stop the thread but also so we know when it's safe to restart it.
    	// (starting a thread which is already started will cause an exception).  So we know it's safe to restart the
    	// thread we set running=THREAD_CLOSING when we want it to shutdown.  It will then set running=THREAD_STOPPED
    	// when it is finished running and it is now safe to restart the thread.
    	static final int THREAD_STOPPED 	  = 0;
    	static final int THREAD_RUNNING 	  = 1;
    	static final int THREAD_CLOSING 	  = 2;
    	static final int THREAD_STOP_DEFERRED = 3;
    	
    	public Object lock = new Object();
    	String msg = "";
    	DataOutputStream out = null;
    	DataInputStream  in;
    	ProtocolHandler  handler = new ServiceProtocolHandler(InterfaceBaseService.this,0x104); 
    	LinkedList<PacketQueueItem> mPacketQueue = new LinkedList<PacketQueueItem>();
    	
    	boolean isOpen = false,server=false;  // True when it is safe to write to the socket.
    
    	abstract void initServerConnection() throws IOException;
    	
    	abstract void acceptConnection(boolean server) throws IOException;    	
    	abstract boolean isConnected();
    	abstract void close() throws IOException;
		private OutputThread mOutputThread = null;
    	InputThread  mInputThread = null;
		
    	class InputThread extends Thread
    	{
        	int running = 0; // Set to true before starting the thread and false when stopping the thread.

        	public void run()
        	{
        		running = THREAD_RUNNING;
        		mOutputThread = new OutputThread();
    			mOutputThread.start();

        		if (server)
        		{    			
        			try 
        			{
        			initServerConnection();
        			} catch(IOException e)
        			{
        				e.printStackTrace();
        				statusUpdate("Disconnected. Can't initilise connection.", CONNECTION_STATUS_WAITING_FOR_CONNECTION);

        			}
    				statusUpdate("Disconnected. Waiting for connection.", CONNECTION_STATUS_WAITING_FOR_CONNECTION);
        		}
        		
        		while(running == THREAD_RUNNING)
        		{
        			try {
        				acceptConnection(server);
        				
        				if (running != THREAD_RUNNING) break;

        				synchronized(mOutputThread.waitObj)
        				{
        					mOutputThread.waitObj.notify();
        				}
        				
        				handleConnection();

        			} catch (IOException e) {
        				e.printStackTrace();
        				isOpen = false;
        			}

        			if (!server && running == THREAD_RUNNING)
    				{
        				if (mOutputThread.status == THREAD_STOP_DEFERRED)
        				{
        					mOutputThread.status = THREAD_STOPPED;
        					running              = THREAD_STOPPED;
        				}
        				else
        				{
        					statusUpdate("Waiting to reconnect.", CONNECTION_STATUS_WAITING_FOR_CONNECTION);
        					try {
        						sleep(4000);
        					} catch (InterruptedException e) 
        					{
        						/* There's not anything worth doing here if we get an interrupted exception.*/
        					}
        				}
    				}		

        			isOpen = false;

            		out = null;
    				statusUpdate("Disconnected.", CONNECTION_STATUS_WAITING_FOR_CONNECTION);
        		}
        	}
        	
        	void handleConnection() throws IOException
        	{
        		byte header[] = new byte[6];

        		synchronized(this) 
        		{
        		isOpen = true;
        		}

        		while(running == THREAD_RUNNING)
        		{
        			int retval = 0;
        			int source = in.readShort();
        			int dest   = in.readShort();
        			int length = in.readShort();
        			
        			//Log.i("BluetoothInterfaceService", "in.read " + retval + " PKT len: " + (int)length);
        			
        			if(!isConnected())
        			{
        			break;
        			}
        			
        			if (length > 0 && length < 65536)
        			{
        				byte[] payload = new byte[length + 1];
        				retval = in.read(payload, 0, length + 1);
        				
        				if(retval != length + 1) {running = THREAD_CLOSING; break;}
        				
        				//Log.i("BluetoothInterfaceService", "in.read(PKT body) " + retval);
        				
        				if (!handler.decodePacket(header, payload))
        				{
        					// processing done here¦.
        					Intent broadcastIntent = new Intent();
        					broadcastIntent.setAction(BluetoothInterfaceService.PACKET_RECEIVED);
        					broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        					broadcastIntent.putExtra("header", header);
        					broadcastIntent.putExtra("payload", payload);
        					sendBroadcast(broadcastIntent);
        				}
        			}
        		}
        		synchronized(this) 
        		{
        		isOpen = false;
        		}

        		in.close();
        		out.close();
	    		statusUpdate("Connection closed.", 0);
        	}    		
    	}
    	
    	/* TODO: move output and connection management here.*/
    	class OutputThread extends Thread
    	{
    		Object waitObj = new Object();
    		
    		int status = THREAD_RUNNING;
    		public void run()
    		{
    			while(!isInterrupted() && (status == THREAD_RUNNING || status == THREAD_STOP_DEFERRED))
    			{	
    				/* Send out anything currently queued before doing anything else.
    				 * This is safe to do in the sender thread as we don't wait for anything.
    				 */
    				PacketQueueItem item;
    				synchronized(mPacketQueue)
    				{
    					if(isOpen)
    					try {
    						
    						while(!mPacketQueue.isEmpty())
    						{
    							Log.i("BluetoothInterfaceService", "in.write "+server);
    							item = mPacketQueue.removeFirst();
    							
    							//Log.i("BluetoothInterfaceService", "in.write "+item.packetBuffer);
    							
    							out.write(item.packetBuffer);
    						}
    					} catch(NoSuchElementException e) {
    						e.printStackTrace();
    					} catch (IOException e) {
    						e.printStackTrace();
						}
    	   			}
    				
    				try {
    					synchronized(waitObj)
    					{
    						waitObj.wait(10000);
    						Log.i("BluetoothInterfaceService", "THREAD notified.");
    					}
					} catch (InterruptedException e) {
						return;
					}
    			
					if (status == THREAD_STOP_DEFERRED && mPacketQueue.isEmpty()) /* Can only do a deferred shutdown if the output
																					 queue is empty.*/
					{
						Log.i("BluetoothInterfaceService", "THREAD_STOP_DEFERRED");
						stopRunning();
						status = THREAD_STOPPED;
						return;
					}
					/*if (!isConnected())
					{
						return;
					}*/
    			}
    		}
    	}
    	
    	void stopRunningDeffered()
    	{
    		if (mOutputThread != null)
    			mOutputThread.status = THREAD_STOP_DEFERRED;
    	}
    	
    	void cancelStopRunningDeffered()
    	{
    		if (mOutputThread != null && mOutputThread.status == THREAD_STOP_DEFERRED)
    			mOutputThread.status = THREAD_RUNNING;
    	}
    	
    	/* We can't override start and stop so you must use stopRunning and startRunning instead.*/
    	synchronized void stopRunning()
    	{   
    		if (mInputThread != null)
    		{
	    		mInputThread.running = THREAD_CLOSING;/* Could use isInterrupted instead however we need a value to store whether the thread has 
	    		shutdown so we might as we continue using the status value.*/
	    		
	            try {
	            	mInputThread.interrupt();
	            	mOutputThread.interrupt();
	            	close();
	            } catch(IOException e) 
	            	{
	            		Log.i("InterfaceBaseClass","stopRunning EXCEPTION IOException.");
	            		e.printStackTrace();
	            	}
	    		Log.i("InterfaceBaseClass","stopRunning");
	    		mOutputThread.status = THREAD_STOPPED;
	    		mInputThread.running = THREAD_STOPPED;/* Could use isInterrupted instead however we need a value to store whether the thread has */
	    		mInputThread  = null;
	    		mOutputThread = null;
    		}
    	}
    	
    	/* We can't override start and stop so you must use stopRunning and startRunning instead.*/
    	void start()
    	{
			mInputThread = new InputThread();
			mInputThread.start();
	}
    	
    	boolean sendPacket(byte[] packetData,boolean buffer)
    	{
    		if (isOpen && packetData != null)
			{
				try {
					out.write(packetData);
					return true;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
    		else if (buffer && packetData != null)
    		{
    			synchronized(mPacketQueue)
    			{
    	    		PacketQueueItem item = new PacketQueueItem();
    	    		item.packetBuffer = packetData;
    	    		mPacketQueue.addLast(item);
    	    		Log.i("BluetoothInterfaceService", "add to queue ");
    			}
    		}
			return false;
    	}
    }

	class SMSResponseReceiver extends BroadcastReceiver {
		ProtocolHandler  mProtocolHandler;
		static final String openKey = "me.uk.geekylou.InterfaceBaseService.SMSResponseReceiver";
		SMSResponseReceiver()
		{
			super();
			mProtocolHandler = new ServiceProtocolHandler(InterfaceBaseService.this,0x104);

		}
	   @Override
	    public void onReceive(Context context, Intent intent) 
	   	{	     
		   // Retrieves a map of extended data from the intent.
	       final Bundle bundle = intent.getExtras();
	 
	       try {
	             
	           if (bundle != null) {
	                 
	        	   final Object[] pdusObj = (Object[]) bundle.get("pdus");
	                 
	               for (int i = 0; i < pdusObj.length; i++) {
	                     
	            	   SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
	            	   String phoneNumber = currentMessage.getDisplayOriginatingAddress();
	                     
	            	   String senderNum = phoneNumber;
	            	   String message = currentMessage.getDisplayMessageBody();
	 
	            	   Log.i("SmsReceiver", "senderNum: "+ senderNum + "; message: " + message);
	                     
	 
                   		// Show Alert
	            	   int duration = Toast.LENGTH_LONG;
	            	   Toast toast = Toast.makeText(InterfaceBaseService.this, 
	            			   "senderNum: "+ senderNum + ", message: " + message, duration);
	            	   toast.show();
	                 
	            	   
       					try {
       						// No point in just looping back an intent to be handled by ourselves so create
       						// a message ourselves and inject it into the output stream.
       		            	byte buf[] = ProtocolHandler.CreatePacket(0x100,0x104,
										  ProtocolHandler.CreateSMSPacket(ProtocolHandler.SMS_MESSAGE_TYPE_NOTIFICATION,
										  0, /* id not used on this type of request. */
										  senderNum, 
										  message,
										  new Date().getTime()));
       		            	sendPacket(buf,openKey);
       		            	closeConnection(openKey);
       					} catch (IOException e) {
       						// TODO Auto-generated catch block
       						e.printStackTrace();
       					}
	
	               } // end for loop
	          } // bundle is null
	        } catch (Exception e) {
	            Log.e("SmsReceiver", "Exception smsReceiver" +e);
	         
	        }
	   	}
	}    
    
	void sendPacket(byte[] packetData,String closeKey)
	{
	   if (closeKey != null)
	   {
		   connectionLocks.put(closeKey, closeKey);
	   }
 	   if (mSocketThread.isOpen)
			{
				mSocketThread.sendPacket(packetData,false);
			} 
 	   else if(mServerSocketThread.isOpen)
			{
				mServerSocketThread.sendPacket(packetData,false);
			}
 	   else
 	   {
 		   // Neither a connection to us to the peer or a connection from the peer is active so queue the message and create one.
 		   mSocketThread.sendPacket(packetData,true);    		   
 		   startClientConnection();
 	   }		
	}
	
	/* Safe close connection.  Only close the connection if no other activity is using it.*/
	void closeConnection(String closeKey)
	{
		connectionLocks.remove(closeKey);
		if (connectionLocks.isEmpty())
		{
			mSocketThread.stopRunningDeffered();
		}
		else
		{
			mSocketThread.cancelStopRunningDeffered();
		}
	}
	
	abstract void startClientConnection();
	
	/**
	    * Listener to detect incoming calls. 
	    */
    private class CallStateListener extends PhoneStateListener {
	  static final String openKey = "me.uk.geekylou.InterfaceBaseService.PhoneStateListener";

      @Override
      public void onCallStateChanged(int state, String incomingNumber) {
          switch (state) {
              case TelephonyManager.CALL_STATE_RINGING:
              // called when someone is ringing to this phone

        	  
    		  try {
					// No point in just looping back an intent to be handled by ourselves so create
					// a message ourselves and inject it into the output stream.
	            	byte buf[] = ProtocolHandler.CreatePacket(0x100,0x104,
								 ProtocolHandler.CreateSMSPacket(ProtocolHandler.SMS_MESSAGE_TYPE_PHONE_CALL,
								 0, /* id not used on this type of request. */
								 incomingNumber, 
								 "",
								 new Date().getTime()));
	            	sendPacket(buf,openKey);
	            	closeConnection(openKey);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
       
              break;
          }
      }
    }
	
	class ResponseReceiver extends BroadcastReceiver 
	{	
		ResponseReceiver()
		{
			super();
		}
	   @Override
	    public void onReceive(Context context, Intent intent) 
	   	{
		   try {
		   if (intent.getBooleanExtra("requestStatus", false))
		   {
				Intent broadcastIntent = new Intent();
				broadcastIntent.setAction(BluetoothInterfaceService.SERVICE_STATUS_UPDATE);
				broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
				broadcastIntent.putExtra("STATUS", new String(mStatusString));
				broadcastIntent.putExtra("StatusCode", mStatusCode);
				broadcastIntent.putExtra("DBG_KEYS", connectionLocks.toString());				
				sendBroadcast(broadcastIntent);
		   }
		   
		   byte[] packetData = intent.getByteArrayExtra ("packetData");
		   if (packetData != null)
		   {
			   sendPacket(packetData, intent.getStringExtra("openKey"));
		   }
    	   if (intent.getBooleanExtra("forceConnect", false))
           {
    		   /* This handle force connect intents where we have nothing to send but want to open the channel anyway.*/
    		   startClientConnection();
           }
		   } catch(Exception e){e.printStackTrace();}

		   String openKey = intent.getStringExtra("openKey");
		   if (openKey != null) connectionLocks.put(openKey, openKey);
		   
		   String closeKey = intent.getStringExtra("closeKey");

		   if (closeKey != null)
		   {
			   connectionLocks.remove(closeKey);
		   }
		   // No one has the connection open so close the connection after the timeout.
		   // Timeout gives us enough time to make sure everything we need to send is sent.
		   if (connectionLocks.isEmpty())
		   {
			   mSocketThread.stopRunningDeffered();
		   }
		   else
		   {
			   mSocketThread.cancelStopRunningDeffered();
		   }
	   	}
	}

	class StatusReceiver extends BroadcastReceiver 
	{	
		StatusReceiver()
		{
			super();
		}
	   @Override
	    public void onReceive(Context context, Intent intent) 
	   	{
		   String keyArg = intent.getStringExtra("senderId");
		   
		    if (keyArg != null && keyArg.equals(key))
		    {
			    NotificationCompat.Builder mBuilder =
					    new NotificationCompat.Builder(InterfaceBaseService.this)
					    .setSmallIcon(R.drawable.ic_launcher)
					    .setContentTitle("SMS Forwarder")
					    .setContentText(intent.getStringExtra("STATUS"));
		        
		        NotificationManager mNotifyMgr = 
				        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			    
				mNotifyMgr.notify(notificationId, mBuilder.build());
		    }
	   	}
	}

	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}