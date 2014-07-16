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

public class BluetoothInterfaceService extends InterfaceBaseService
{
	UUID uuid = UUID.fromString("0aaaaf9a-c01e-4d2c-8e97-5995c1f6409e"); // Bluetooth magic UUID used for finding other instances of ourselves. 
	BluetoothAdapter mBluetoothAdapter;	
		
	public BluetoothInterfaceService()
	{
		mSocketThread = new BluetoothSocketThread();
	}
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        Toast.makeText(this, "Service Started" + intent.getStringExtra("BT_ID") + Boolean.toString(intent.getBooleanExtra("CONNECT", false)), Toast.LENGTH_SHORT).show();
        
		if(mSocketThread.running == SocketThread.THREAD_STOPPED)
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
    		
    		initListeners();
    		
    		// If the CONNECT value is either both available and set to true then connect to device identified by BT_ID.
            if (intent.getBooleanExtra("CONNECT", false))
            {
	        	try 
	        	{
	        		BluetoothSocket Socket = mBluetoothAdapter.getRemoteDevice(intent.getStringExtra("BT_ID")).createRfcommSocketToServiceRecord(uuid);
	
	        		Toast.makeText(this, "BT Connect", Toast.LENGTH_SHORT).show();
	        		
	        		((BluetoothSocketThread)mSocketThread).startRunning(Socket);
	        		
				} 
	        	catch (IOException e) 
				{
	        		// We shouldn't get here unless the caller is giving us invalid data.
	        		unregisterReceiver(mReceiver);
	        		unregisterReceiver(mSMSReceiver);
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
            else
            {
            	((BluetoothSocketThread)mSocketThread).startRunning(null);
            }    		
		}				
        
    	// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
		
    class BluetoothSocketThread extends SocketThread
    {
    	
    	BluetoothServerSocket mBluetoothSocket;
    	BluetoothSocket       mSocket;
    	DataOutputStream out = null;
    	DataInputStream  in;
    	BluetoothAdapter mAdapter;
    	
    	int running = 0; // Set to true before starting the thread and false when stopping the thread.
    	boolean isOpen = false;  // True when it is safe to write to the socket.
    
    	void initServerConnection()
    	{
    		try {
				mBluetoothSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("protocol-v2", uuid);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	
    	void acceptConnection(boolean server) throws IOException
    	{
			if (server)
			{
				statusUpdate("Disconnected.  Waiting for connection.");
				mSocket = mBluetoothSocket.accept();
			}
			else
			{
				statusUpdate("Connecting.");
				mSocket.connect();
			}
			statusUpdate("Connected.");
			
			out = new DataOutputStream(mSocket.getOutputStream());
			in  = new DataInputStream(mSocket.getInputStream());
    	}
    	
    	boolean isConnected()
    	{
    		return mSocket.isConnected();
    	}
    	
    	void close() throws IOException
    	{
    		if (mSocket != null) mSocket.close();
    		if (mBluetoothSocket != null) mBluetoothSocket.close();
    	}
    	
    	/* We can't override start and stop so you must use stopRunning and startRunning instead.*/
    	void startRunning(BluetoothSocket socket)
    	{
    		if(running == THREAD_STOPPED)
    		{
    			mSocket = socket;
    			if (socket != null)
    			{
    				server=false;
    			}
    			else
    			{
    				server=true;
    			}
    			running = THREAD_RUNNING;
    			start();
    		}
    	}
    }
}

class TCPIPInterfaceService extends InterfaceBaseService
{
	UUID uuid = UUID.fromString("0aaaaf9a-c01e-4d2c-8e97-5995c1f6409e"); // Bluetooth magic UUID used for finding other instances of ourselves. 
	BluetoothAdapter mBluetoothAdapter;	
		
	public TCPIPInterfaceService()
	{
		mSocketThread = new TCPIPSocketThread();
	}
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        Toast.makeText(this, "Service Started" + intent.getStringExtra("BT_ID") + Boolean.toString(intent.getBooleanExtra("CONNECT", false)), Toast.LENGTH_SHORT).show();
        
		if(mSocketThread.running == SocketThread.THREAD_STOPPED)
		{  		
    		initListeners();
    		
    		// If the CONNECT value is either both available and set to true then connect to device identified by BT_ID.
            if (intent.getBooleanExtra("CONNECT", false))
            {
	        	try 
	        	{
	        		InetAddress peer = InetAddress.getByName("192.168.0.101");
	        		
	        		((TCPIPSocketThread)mSocketThread).startRunning(peer);
	        		
				} 
	        	catch (IOException e) 
				{
	        		// We shouldn't get here unless the caller is giving us invalid data.
	        		unregisterReceiver(mReceiver);
	        		unregisterReceiver(mSMSReceiver);
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
            else
            {
            	((TCPIPSocketThread)mSocketThread).startRunning(null);
            }    		
		}				
        
    	// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
		
    class TCPIPSocketThread extends SocketThread
    {
    	
    	ServerSocket mBluetoothSocket;
    	Socket       mSocket;
    	DataOutputStream out = null;
    	DataInputStream  in;
    	
    	InetAddress mPeerAddress;
    	
    	int running = 0; // Set to true before starting the thread and false when stopping the thread.
    	boolean isOpen = false;  // True when it is safe to write to the socket.
    
    	void initServerConnection()
    	{
    	}
    	
    	void acceptConnection(boolean server) throws IOException
    	{
			if (server)
			{
				statusUpdate("Disconnected.  Waiting for connection.");
				mSocket = mBluetoothSocket.accept();
			}
			else
			{
				mSocket = new Socket(mPeerAddress,9100);
				statusUpdate("Connecting.");
			}
			statusUpdate("Connected.");
			
			out = new DataOutputStream(mSocket.getOutputStream());
			in  = new DataInputStream(mSocket.getInputStream());
    	}
    	
    	boolean isConnected()
    	{
    		return mSocket.isConnected();
    	}
    	
    	void close() throws IOException
    	{
    		if (mSocket != null) mSocket.close();
    		if (mBluetoothSocket != null) mBluetoothSocket.close();
    	}
    	
    	/* We can't override start and stop so you must use stopRunning and startRunning instead.*/
    	void startRunning(InetAddress peerAddress)
    	{
    		if(running == THREAD_STOPPED)
    		{
    			mPeerAddress = peerAddress;
    			if (mPeerAddress != null)
    			{
    				server=false;
    			}
    			else
    			{
    				server=true;
    			}
    			running = THREAD_RUNNING;
    			start();
    		}
    	}
    }
}


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
    			}
    			// processing done here¦.
    			Intent broadcastIntent = new Intent();
    			broadcastIntent.setAction(BluetoothInterfaceService.PACKET_RECEIVED);
    			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
    			broadcastIntent.putExtra("SOCK", new String(header));
    			sendBroadcast(broadcastIntent);
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