package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

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