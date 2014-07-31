package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class BluetoothInterfaceService extends InterfaceBaseService
{
	UUID uuid = UUID.fromString("0aaaaf9a-c01e-4d2c-8e97-5995c1f6409e"); // Bluetooth magic UUID used for finding other instances of ourselves. 
	BluetoothAdapter mBluetoothAdapter;
	private BluetoothSocket mSocket;	
	String bluetoothID;
	
	public BluetoothInterfaceService()
	{
		key = "BluetoothInterfaceService";
		mServerSocketThread = new BluetoothSocketThread();
		mSocketThread       = new BluetoothSocketThread();
	}
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        Toast.makeText(this, "Service Started" + intent.getStringExtra("BT_ID") + Boolean.toString(intent.getBooleanExtra("CONNECT", true)), Toast.LENGTH_SHORT).show();
        
		if(mServerSocketThread.mInputThread == null)
		{
        	mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    		if (mBluetoothAdapter == null) {
    		    // Device does not support Bluetooth
    	        Toast.makeText(this, "Error Bluetooth not available!", Toast.LENGTH_SHORT).show();
    	        return START_STICKY;
    		}
    		
    		if (!mBluetoothAdapter.isEnabled()) {
    			// We shouldn't get here unless the user disable bluetooth after starting the app as this is checked for in
    			// main app startup.
    	        Toast.makeText(this, "Error Bluetooth not enabled!", Toast.LENGTH_SHORT).show();
    	        return START_STICKY;
    		}

    		initListeners(this,intent);
    		
    		// If the CONNECT value is either both available and set to true then connect to device identified by BT_ID.
            if (intent.getBooleanExtra("CONNECT", true))
            {
                SharedPreferences prefs = getSharedPreferences("BluetoothPreferences", MODE_PRIVATE);
        		bluetoothID = prefs.getString("BT_ID", null);
        		
        		if (bluetoothID == null)
        		{
        			Toast.makeText(this, "Can't connect to remote device as it's bluetooth device ID has not been set.", Toast.LENGTH_SHORT).show();
        			return START_STICKY;
        		}
            }
            
            NotificationCompat.Builder mBuilder =
    			    new NotificationCompat.Builder(this)
    			    .setSmallIcon(R.drawable.ic_launcher)
    			    .setContentTitle("SMS Forwarder")
    			    .setContentText("Bluetooth Service running.");
            
            startForeground(notificationId, mBuilder.build());
        	((BluetoothSocketThread)mServerSocketThread).startRunning(true);
 		}				
		/* We can send packet data when we start the intent.  This makes things like request inbox easier.*/
		mReceiver.onReceive(this,intent);

    	// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
	
    void startClientConnection()
    {
    	((BluetoothSocketThread)mSocketThread).startRunning(false);
    }
    
    class BluetoothSocketThread extends SocketThread
    {
    	
    	BluetoothServerSocket mBluetoothSocket;
    	BluetoothSocket       mSocket;
    	BluetoothAdapter 	  mAdapter;
    	   
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
				statusUpdate("Disconnected.  Waiting for connection.", CONNECTION_STATUS_WAITING_FOR_CONNECTION);
				mSocket = mBluetoothSocket.accept();
			}
			else
			{
				statusUpdate("Connecting.", CONNECTION_STATUS_CONNECTING);
        		mSocket = mBluetoothAdapter.getRemoteDevice(bluetoothID).createRfcommSocketToServiceRecord(uuid);        		
				mSocket.connect();
			}
			statusUpdate("Connected.", CONNECTION_STATUS_CONNECTED);
			
			out = new DataOutputStream(mSocket.getOutputStream());
			in  = new DataInputStream(mSocket.getInputStream());
    	}
    	
    	boolean isConnected()
    	{
    		if (mSocket != null)
    			return mSocket.isConnected();
    		return false;
    	}
    	
    	void close() throws IOException
    	{
    		if (mSocket != null) mSocket.close();
    		if (mBluetoothSocket != null) mBluetoothSocket.close();
    	}
    	
    	/* We can't override start and stop so you must use stopRunning and startRunning instead.*/
    	void startRunning(boolean server)
    	{
    		Log.i("BluetoothInterfaceService","startRunning");
    		synchronized(this)
    		{
	    		if(mInputThread	== null)
	    		{
	    			this.server=server;
    				start();	    			
	    		}
    		}
    	}
    }
}