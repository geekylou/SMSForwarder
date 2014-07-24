package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.apache.http.util.ByteArrayBuffer;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Telephony;
import android.telephony.PhoneStateListener;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

abstract class InterfaceBaseService extends Service
{
	BluetoothAdapter mBluetoothAdapter;
	ResponseReceiver mReceiver;
	SMSResponseReceiver mSMSReceiver;
	String mStatusString= "";
	int    mStatusCode  = CONNECTION_STATUS_STOPPED;

	SocketThread  mServerSocketThread;
	SocketThread  mSocketThread;
	
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

        if (mServerSocketThread != null) mServerSocketThread.stopRunning();
        if (mSocketThread != null) mSocketThread.stopRunning();
        if (mReceiver != null)     unregisterReceiver(mReceiver);
        if (mSMSReceiver != null)  unregisterReceiver(mSMSReceiver);
    }

	void statusUpdate(String statusString, int statusCode)
	{
		mStatusString = statusString;
		
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(BluetoothInterfaceService.SERVICE_STATUS_UPDATE);
		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra("STATUS", new String(statusString));
		sendBroadcast(broadcastIntent);

	}
	
	class PacketQueueItem
	{
		byte packetBuffer[];
	}
	
    abstract class SocketThread extends Thread
    {
    	// We use the running variable not only to stop the thread but also so we know when it's safe to restart it.
    	// (starting a thread which is already started will cause an exception).  So we know it's safe to restart the
    	// thread we set running=THREAD_CLOSING when we want it to shutdown.  It will then set running=THREAD_STOPPED
    	// when it is finished running and it is now safe to restart the thread.
    	static final int THREAD_STOPPED = 0;
    	static final int THREAD_RUNNING = 1;
    	static final int THREAD_CLOSING = 2;
    	
    	public Object lock = new Object();
    	String msg = "";
    	DataOutputStream out = null;
    	DataInputStream  in;
    	ProtocolHandler  handler = new ProtocolHandler(InterfaceBaseService.this,0x104);; 
    	LinkedList<PacketQueueItem> mPacketQueue = new LinkedList<PacketQueueItem>();
    	
    	int running = 0; // Set to true before starting the thread and false when stopping the thread.
    	boolean isOpen = false,server=false;  // True when it is safe to write to the socket.
		private Object mSocket;
    
    	abstract void initServerConnection() throws IOException;
    	
    	abstract void acceptConnection(boolean server) throws IOException;    	
    	abstract boolean isConnected();
    	abstract void close() throws IOException;
    	
    	public void run()
    	{
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
    				
    				handleConnection();

    			} catch (IOException e) {
    				e.printStackTrace();
    				isOpen = false;
    			}

    			if (!server)
				{
					statusUpdate("Waiting to reconnect.", CONNECTION_STATUS_WAITING_FOR_CONNECTION);
					try {
						sleep(4000);
					} catch (InterruptedException e) 
					{
						/* There's not anything worth doing here if we get an interrupted exception.*/
					}
				}		

    			isOpen = false;

        		out = null;
    		}
    		running = THREAD_STOPPED;
    	}
    	
    	void handleConnection() throws IOException
    	{
    		byte header[] = new byte[6];

    		synchronized(this) 
    		{
    		isOpen = true;
    		}
    		
			/* Send out anything currently queued before doing anything else.
			 * This is safe to do in the sender thread as we don't wait for anything.
			 */
			PacketQueueItem item;
			synchronized(mPacketQueue)
			{
				try {
					while(!mPacketQueue.isEmpty())
					{
						Log.i("BluetoothInterfaceService", "in.write "+server);
						item = mPacketQueue.removeFirst();
						//Log.i("BluetoothInterfaceService", "in.write "+item.packetBuffe);
						
						out.write(item.packetBuffer);
					}
				} catch(NoSuchElementException e) {
					e.printStackTrace();
				}
				
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
    				
    				handler.decodePacket(header, payload);
    				
        			// processing done here¦.
        			Intent broadcastIntent = new Intent();
        			broadcastIntent.setAction(BluetoothInterfaceService.PACKET_RECEIVED);
        			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        			broadcastIntent.putExtra("header", header);
        			broadcastIntent.putExtra("payload", payload);
        			sendBroadcast(broadcastIntent);
    			}
    		}
    		synchronized(this) 
    		{
    		isOpen = false;
    		}

    		in.close();
    		out.close();
    	}
    	    	
    	/* We can't override start and stop so you must use stopRunning and startRunning instead.*/
    	void stopRunning()
    	{
    		running = THREAD_CLOSING;/* Could use isInterrupted instead however we need a value to store whether the thread has 
    		shutdown so we might as we continue using the status value.*/
    		
    		
            try {
            	interrupt();
            	close();
    			try {
    				join(10000);
    			} catch (InterruptedException e) {
    				e.printStackTrace();
    			}
            } catch(IOException e) 
            	{
            		e.printStackTrace();
            	}
    		statusUpdate("Service stopped.", 0);
    	}
    	
    	/* We can't override start and stop so you must use stopRunning and startRunning instead.*/
    	void startRunning(boolean mServer)
    	{
    		if(running == THREAD_STOPPED)
    		{
    			this.server = mServer;
    			running = THREAD_RUNNING;
    			start();
    		}
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
		
		SMSResponseReceiver()
		{
			super();
			mProtocolHandler = new ProtocolHandler(InterfaceBaseService.this,0x104);

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
       		            	sendPacket(buf);
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
    
	void sendPacket(byte[] packetData)
	{
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
	
	abstract void startClientConnection();
	
	/**
	    * Listener to detect incoming calls. 
	    */
    private class CallStateListener extends PhoneStateListener {
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
	            	sendPacket(buf);
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
				
				sendBroadcast(broadcastIntent);
		   }
		   
		   sendPacket(intent.getByteArrayExtra ("packetData"));
    	   
    	   if (intent.getBooleanExtra("forceConnect", false))
           {
    		   /* This handle force connect intents where we have nothing to send but want to open the channel anyway.*/
    		   startClientConnection();
           }
		   } catch(Exception e){e.printStackTrace();}
	   	}
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}