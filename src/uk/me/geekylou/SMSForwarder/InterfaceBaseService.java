package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

abstract class InterfaceBaseService extends Service
{
	UUID uuid = UUID.fromString("0aaaaf9a-c01e-4d2c-8e97-5995c1f6409e"); // Bluetooth magic UUID used for finding other instances of ourselves. 
	BluetoothAdapter mBluetoothAdapter;
	ResponseReceiver mReceiver;
	SMSResponseReceiver mSMSReceiver;
	String status;
	
	SocketThread  mSocketThread;
	
	public static final String SEND_PACKET     = "uk.me.geekylou.GPSTest.SEND_PACKET";
	public static final String PACKET_RECEIVED = "uk.me.geekylou.GPSTest.PACKET_RECEIVED";
	public static final String SERVICE_STATUS_UPDATE = "uk.me.geekylou.GPSTest.SERVICE_STATUS_UPDATE";
	
	void initListeners()
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
	}
	
    @SuppressWarnings("deprecation")
	@Override
    public void onDestroy() {
        // Tell the user we stopped.
        Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show();

        mSocketThread.stopRunning();
		unregisterReceiver(mReceiver);
		unregisterReceiver(mSMSReceiver);
    }

	void statusUpdate(String status)
	{
		this.status = status;
		
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(BluetoothInterfaceService.SERVICE_STATUS_UPDATE);
		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra("STATUS", new String(status));
		sendBroadcast(broadcastIntent);

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
    	
    	int running = 0; // Set to true before starting the thread and false when stopping the thread.
    	boolean isOpen = false,server=false;  // True when it is safe to write to the socket.
		private Object mSocket;
    
    	abstract void initServerConnection();
    	
    	abstract void acceptConnection(boolean server) throws IOException;    	
    	abstract boolean isConnected();
    	abstract void close() throws IOException;
    	
    	public void run()
    	{
    		if (server)
    		{    			
    			initServerConnection();
				statusUpdate("Disconnected.  Waiting for connection.");
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
					statusUpdate("Waiting to reconnect.");
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
    		while(running == THREAD_RUNNING)
    		{
    			int retval = 0;
    			int source = in.readShort();
    			int dest   = in.readShort();
    			int length = in.readShort();
    			
    			Log.i("BluetoothInterfaceService", "in.read " + retval + " PKT len: " + (int)length);
    			
    			if(!isConnected())
    			{
    				break;
    			}
    			
    			if (length > 0 && length < 65536)
    			{
    				byte[] payload = new byte[length + 1];
    				retval = in.read(payload, 0, length + 1);
    				
    				if(retval != length + 1) {running = THREAD_CLOSING; break;}
    				
    				Log.i("BluetoothInterfaceService", "in.read(PKT body) " + retval);
    				
    				handler.decodePacket(header, payload);
    				
        			// processing done here�.
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
    		running = THREAD_CLOSING;
    		
            try {
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
    		running = THREAD_STOPPED;
    		statusUpdate("Service stopped.");
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
	                 
	            	   if (mSocketThread.isOpen)
	       				{
	       					try {
	       						// No point in just looping back an intent to be handled by ourselves so create
	       						// a message ourselves and inject it into the output stream.
	       		            	byte buf[] = ProtocolHandler.CreatePacket(0x100,0x104,
											  ProtocolHandler.CreateSMSPacket(ProtocolHandler.SMS_MESSAGE_TYPE_NOTIFICATION,
											  0, /* id not used on this type of request. */
											  senderNum, 
											  message));
	       		            	mSocketThread.out.write(buf);
	       					} catch (IOException e) {
	       						// TODO Auto-generated catch block
	       						e.printStackTrace();
	       					}
	       				}	
	               } // end for loop
	          } // bundle is null
	        } catch (Exception e) {
	            Log.e("SmsReceiver", "Exception smsReceiver" +e);
	         
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
		   if (intent.getBooleanExtra("requestStatus", false))
		   {
				Intent broadcastIntent = new Intent();
				broadcastIntent.setAction(BluetoothInterfaceService.SERVICE_STATUS_UPDATE);
				broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
				broadcastIntent.putExtra("STATUS", new String(status));
				sendBroadcast(broadcastIntent);
		   }
    	   if (mSocketThread.isOpen)
			{
				try {
					mSocketThread.out.write(intent.getByteArrayExtra ("packetData"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}	
	   	}
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}