package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

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