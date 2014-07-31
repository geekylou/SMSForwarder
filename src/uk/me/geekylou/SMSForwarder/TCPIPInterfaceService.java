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
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

public class TCPIPInterfaceService extends InterfaceBaseService
{
	BluetoothAdapter mBluetoothAdapter;	
		
	public TCPIPInterfaceService()
	{
		key = "TCPIPInterfaceService";		
		mSocketThread = new TCPIPSocketThread();
	}
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        Toast.makeText(this, "IP Service Started", Toast.LENGTH_SHORT).show();
        
		if(mSocketThread != null)
		{  		
    		initListeners(this,intent);
    		
    		// If the CONNECT value is either both available and set to true then connect to device identified by BT_ID.
            if (intent.getBooleanExtra("CONNECT", false))
            {
                SharedPreferences prefs = getSharedPreferences("BluetoothPreferences", MODE_PRIVATE);
        		String bluetoothID = prefs.getString("PEER_IP_ADDRESS", null);

	        	try 
	        	{
	        		InetAddress peer = InetAddress.getByName(prefs.getString("PEER_IP_ADDRESS", null));

	                Toast.makeText(this, "Connect to"+prefs.getString("PEER_IP_ADDRESS", null), Toast.LENGTH_SHORT).show();

	        		
	        		((TCPIPSocketThread)mSocketThread).startRunning(peer,intent.getIntExtra("PEER_PORT", 9100));
	        		
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
            	((TCPIPSocketThread)mSocketThread).startRunning(null,intent.getIntExtra("PEER_PORT", 9100));
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
    	InetAddress  mPeerAddress;
    	int			 mPort;
    	    
    	void initServerConnection() throws IOException
    	{    		
			mBluetoothSocket = new ServerSocket(mPort);
    	}
    	
    	void acceptConnection(boolean server) throws IOException
    	{
			if (server)
			{
				statusUpdate("Disconnected.  Waiting for connection.", 0);
				mSocket = mBluetoothSocket.accept();
			}
			else
			{
				mSocket = new Socket(mPeerAddress,mPort);
				statusUpdate("Connecting.", 0);
			}
			statusUpdate("Connected.", 0);
			
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
    	void startRunning(InetAddress peerAddress, int port)
    	{
    		if(mInputThread	== null)
    		{
    			mPort        = port;
    			mPeerAddress = peerAddress;
    			if (mPeerAddress != null)
    			{
    				server=false;
    			}
    			else
    			{
    				server=true;
    			}
    			start();
    		}
    	}
    }

	@Override
	void startClientConnection() {
		// TODO Auto-generated method stub
		
	}
}