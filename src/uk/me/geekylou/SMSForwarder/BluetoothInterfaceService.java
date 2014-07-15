package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import android.provider.Telephony;
import android.provider.Telephony.Sms.Intents;

public class BluetoothInterfaceService extends Service
{
	UUID uuid = UUID.fromString("0aaaaf9a-c01e-4d2c-8e97-5995c1f6409e"); // Bluetooth magic UUID used for finding other instances of ourselves. 
	BluetoothAdapter mBluetoothAdapter;
	ResponseReceiver mReceiver;
	SMSResponseReceiver mSMSReceiver;
	
	BluetoothSocketThread  mIPSocketThread = new BluetoothSocketThread();
	Context ctx;
	
	public static final String SEND_PACKET     = "uk.me.geekylou.GPSTest.SEND_PACKET";
	public static final String PACKET_RECEIVED = "uk.me.geekylou.GPSTest.PACKET_RECEIVED";
	public static final String SERVICE_STATUS_UPDATE = "uk.me.geekylou.GPSTest.SERVICE_STATUS_UPDATE";

	
	public BluetoothInterfaceService(Context ctx,int port)
	{
		this.ctx = ctx;
	}
	
	public BluetoothInterfaceService()
	{
		this.ctx = this;
	}
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        Toast.makeText(this, "Service Started" + intent.getStringExtra("BT_ID") + Boolean.toString(intent.getBooleanExtra("CONNECT", false)), Toast.LENGTH_SHORT).show();
        
		if(mIPSocketThread.running == BluetoothSocketThread.THREAD_STOPPED)
		{
        	mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    		if (mBluetoothAdapter == null) {
    		    // Device does not support Bluetooth
    			// [TODO] Handle this gracefully.
    	        Toast.makeText(this, "Error Bluetooth not available!", Toast.LENGTH_SHORT).show();
    	        return START_STICKY;
    		}
    		
    		if (!mBluetoothAdapter.isEnabled()) {
    			// We shouldn't get here unless the user disable bluetooth after starting the app as this is checked for in
    			// main app startup.
    	        Toast.makeText(this, "Error Bluetooth not enabled!", Toast.LENGTH_SHORT).show();
    	        return START_STICKY;
    		}
    		
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

    		// If the CONNECT value is either both available and set to true then connect to device identified by BT_ID.
            if (intent.getBooleanExtra("CONNECT", false))
            {
	        	try 
	        	{
	        		BluetoothSocket Socket = mBluetoothAdapter.getRemoteDevice(intent.getStringExtra("BT_ID")).createRfcommSocketToServiceRecord(uuid);
	
	        		Toast.makeText(this, "BT Connect", Toast.LENGTH_SHORT).show();
	        		
	        		mIPSocketThread.startRunning(Socket);
	        		
				} 
	        	catch (IOException e) 
				{
	        		// We shouldn't get here unless the caller is giving us invalid data.
	        		unregisterReceiver(mReceiver);
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
            else
            {
            	mIPSocketThread.startRunning(null);
            }    		
		}				
        
    	// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
	
    @SuppressWarnings("deprecation")
	@Override
    public void onDestroy() {
        // Tell the user we stopped.
        Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show();

        mIPSocketThread.stopRunning();
		unregisterReceiver(mReceiver);        
    }

	void statusUpdate(String status)
	{
		// processing done here¦.
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(BluetoothInterfaceService.SERVICE_STATUS_UPDATE);
		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra("STATUS", new String(status));
		ctx.sendBroadcast(broadcastIntent);

	}
	
    class BluetoothSocketThread extends Thread
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
    	BluetoothServerSocket mBluetoothSocket;
    	BluetoothSocket       mSocket;
    	DataOutputStream out = null;
    	DataInputStream  in;
    	ProtocolHandler  handler = new ProtocolHandler(BluetoothInterfaceService.this,0x104);; 
    	BluetoothAdapter mAdapter;
    	
    	int running = 0; // Set to true before starting the thread and false when stopping the thread.
    	boolean isOpen = false;  // True when it is safe to write to the socket.
    	
    	public void run()
    	{
    		boolean server = false;
    		
    		if (mSocket == null)
    		{
    			server = true;
    			try {
    				mBluetoothSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("protocol-v2", uuid);
    			} catch (IOException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}			
				statusUpdate("Disconnected.  Waiting for connection.");
    		}
    		
    		while(running == THREAD_RUNNING)
    		{
    			try {
    				
    				if (server)
    				{
    					statusUpdate("Disconnected.  Waiting for connection.");
    					mSocket = mBluetoothSocket.accept();
    				}
    				else
    				{
						if (running != THREAD_RUNNING) break;
						statusUpdate("Connecting.");
    					mSocket.connect();
    				}
					statusUpdate("Connected.");

    				out = new DataOutputStream(mSocket.getOutputStream());
    				in  = new DataInputStream(mSocket.getInputStream());
    				
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
    			
    			if(!mSocket.isConnected())
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
    			}
    			// processing done here¦.
    			Intent broadcastIntent = new Intent();
    			broadcastIntent.setAction(BluetoothInterfaceService.PACKET_RECEIVED);
    			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
    			broadcastIntent.putExtra("SOCK", new String(header));
    			ctx.sendBroadcast(broadcastIntent);
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
               	if (mSocket != null) mSocket.close();
            	if (mBluetoothSocket != null) mBluetoothSocket.close();
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
    	void startRunning(BluetoothSocket socket)
    	{
    		if(running == THREAD_STOPPED)
    		{
    			mSocket = socket;
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
			mProtocolHandler = new ProtocolHandler(ctx,0x104);

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
	            	   Toast toast = Toast.makeText(BluetoothInterfaceService.this, 
	            			   "senderNum: "+ senderNum + ", message: " + message, duration);
	            	   toast.show();
	                 
	            	   if (mIPSocketThread.isOpen)
	       				{
	       					try {
	       						// No point in just looping back an intent to be handled by ourselves so create
	       						// a message ourselves and inject it into the output stream.
	       		            	byte buf[] = ProtocolHandler.CreatePacket(0x100,0x104,ProtocolHandler.CreateSMSPacket(senderNum, message));
	       		            	mIPSocketThread.out.write(buf);
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
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}

















/*
public class BluetoothInterfaceService extends Service {
	public static final String ACTION_RESP =
		      "uk.me.geekylou.GPSTest.MESSAGE_PROCESSED";
	public static final String PARAM_OUT_MSG = "GPS";
	MyLocationListener mLocListener=null,mLocListener2=null;
	LocationManager mLocManager;
	IPSocketThread  mIPSocketThread = new IPSocketThread();
	SmsManager sms = SmsManager.getDefault();
	ResponseReceiver mReceiver;
	
	Object          mLock[]=new Object[1];
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return (IBinder) BluetoothInterfaceService.this;
	}

	public static void sendMessage(Context ctx,String message)
	{
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(ACTION_RESP);
		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra("MSG", message);
		ctx.sendBroadcast(broadcastIntent);
	}
	
	@Override
    public void onCreate() {
	    Toast.makeText(this, "Service Created", Toast.LENGTH_SHORT).show();
        }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();

    	mLocManager = (LocationManager) 
    	        getSystemService(Context.LOCATION_SERVICE);

        if (mLocListener == null)
        {
        	mLocListener = new MyLocationListener("GPS",mLock);
        
        	mLocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
    	            0, mLocListener);
        }
        if (mLocListener2 == null)
        {
        	mLocListener2 = new MyLocationListener("NET",mLock);        
        
        	mLocManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0,
    	            0, mLocListener2);
        }    	
        
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(new CallStateListener(), PhoneStateListener.LISTEN_CALL_STATE);
        
        mLock[0] = mIPSocketThread.lock;
        if(!mIPSocketThread.running) 
        	{
        	mIPSocketThread.running = true;
        	mIPSocketThread.start();
        	}
        
		IntentFilter filter = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
		ResponseReceiver mReceiver = new ResponseReceiver();
		registerReceiver(mReceiver, filter);
        
    	// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
	
    @SuppressWarnings("deprecation")
	@Override
    public void onDestroy() {
        // Tell the user we stopped.
        Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show();

		if (mLocManager != null && mLocListener != null)  
		{
			mLocManager.removeUpdates(mLocListener);
			mLocListener = null;
		}
		if (mLocManager != null && mLocListener2 != null)
		{
			mLocManager.removeUpdates(mLocListener2);
			mLocListener2 = null;
		}
		unregisterReceiver(mReceiver);
		mIPSocketThread.running = false;
		
        try {
        	synchronized(mIPSocketThread.lock)
        	{
        	//	mIPSocketThread.lock.notify();
        	}
        	if (mIPSocketThread.mSocket != null) mIPSocketThread.mSocket.close();
        	if (mIPSocketThread.mServerSocket != null) mIPSocketThread.mServerSocket.close();
			mIPSocketThread.join(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    /**
    * Listener to detect incoming calls. 
    *//*
    private class CallStateListener extends PhoneStateListener {
      @Override
      public void onCallStateChanged(int state, String incomingNumber) {
          switch (state) {
              case TelephonyManager.CALL_STATE_RINGING:
              // called when someone is ringing to this phone

        	  if (mIPSocketThread.isOpen)
    			{
    				try {
    					mIPSocketThread.out.writeBytes("PHONE:"+incomingNumber+"\n");
    				} catch (IOException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
    			}
       
              break;
          }
      }
    }
    
	class MyLocationListener implements LocationListener
	{
		String   header;
		Object   locks[];
		public MyLocationListener(String header,Object lock[])
		{
			this.locks = lock;
			this.header = header;
		}
		@Override
		public void onLocationChanged(Location arg0) {
			// TODO Auto-generated method stub
			String resultTxt = header+": SPD:"+arg0.getSpeed()+"\nLocX:"+arg0.getLongitude()+ "\nLocY:"+arg0.getLatitude()
					+ "\nAlt:"+arg0.getAltitude()+"\nAcc:"+arg0.getAccuracy();
			
			if (mIPSocketThread.isOpen)
			{
				try {
					mIPSocketThread.out.writeBytes(resultTxt+"\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			// processing done here¦.
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(ACTION_RESP);
			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
			broadcastIntent.putExtra(header, resultTxt);
			sendBroadcast(broadcastIntent);
		}

		@Override
		public void onProviderDisabled(String arg0) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onProviderEnabled(String arg0) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
			// TODO Auto-generated method stub
			
		}
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
			try {
				mServerSocket = new ServerSocket(10025);
				mSocket = mServerSocket.accept();
				
				out = new DataOutputStream(mSocket.getOutputStream());
				in  = new DataInputStream(mSocket.getInputStream());
				synchronized(this) 
				{
				isOpen = true;
				}
				while(running)
				{
					
					String resultTxt = in.readLine();
					// processing done here¦.
					Intent broadcastIntent = new Intent();
					broadcastIntent.setAction(ACTION_RESP);
					broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
					broadcastIntent.putExtra("SOCK", resultTxt);
					sendBroadcast(broadcastIntent);
				}
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
	

}

*/