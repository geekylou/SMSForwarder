package uk.me.geekylou.SMSForwarder;


import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import uk.me.geekylou.SMSForwarder.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.view.LayoutInflater;


public class InboxActivity extends Activity {
	private ListView mInboxEntriesView;
	private ArrayAdapter<InboxEntry> mBluetoothDeviceArrayAdapter;
	static final int REQUEST_NEW_ENTRY = 1000;
	private BluetoothAdapter mBluetoothAdapter;
	private SharedPreferences prefs;
	InboxProtocolHandler  mProtocolHandler;
	private ResponseReceiver receiver;
	StatusReceiver mStatusReceiver;
	String search;
	
	static String[] mProjections = new String[] {
	        ContactsContract.PhoneLookup.DISPLAY_NAME,
	        ContactsContract.PhoneLookup._ID};
	
	boolean threadView=true;
	private TextView mStatusTextView;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     
        setContentView(R.layout.bluetooth_chooser);
        final Intent intent = getIntent();
        
        mProtocolHandler = new InboxProtocolHandler(this,0x104);
        mBluetoothDeviceArrayAdapter = new ImageViewAdapter(this, R.layout.text_preview_item);
		
        prefs = getSharedPreferences("BluetoothPreferences", MODE_PRIVATE);

        /* Start listening for replies before doing anything else.*/
		IntentFilter filter = new IntentFilter(InterfaceBaseService.PACKET_RECEIVED);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
    	receiver = new ResponseReceiver();
		registerReceiver(receiver, filter);

        /* Start listening for status doing anything else.*/
		filter = new IntentFilter(InterfaceBaseService.SERVICE_STATUS_UPDATE);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
    	mStatusReceiver = new StatusReceiver();
    	registerReceiver(mStatusReceiver, filter);
    	
        /* Start service with a request for the inbox on the peer.*/
        Intent bluetoothService = new Intent(this,BluetoothInterfaceService.class);
		
		search = intent.getStringExtra("search");
		if(search == null) search="";
    	mProtocolHandler.populateSMSMessageIntent(bluetoothService,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, search, "",0);
    	/* cludge to make TCPIP Service work.*/
    	mProtocolHandler.sendSMSMessage(this,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, search, "",0);
	
 		startService(bluetoothService);
		
		mInboxEntriesView = (ListView) findViewById(R.id.listView1);
        mInboxEntriesView.setAdapter(mBluetoothDeviceArrayAdapter);
        
        mStatusTextView = (TextView) findViewById(R.id.textViewStatus);
    }
    
    /* This sends a request to the service to send us the current connection status.*/
    protected void onResume()
	{
		super.onResume();
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(InterfaceBaseService.SEND_PACKET);
		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra("requestStatus", true);
		sendBroadcast(broadcastIntent);
	}
    
    protected void onDestroy()
    {
    	unregisterReceiver(mStatusReceiver);
    	unregisterReceiver(receiver);
    	super.onDestroy();
    }
    class InboxProtocolHandler extends ProtocolHandler {
    	HashMap<String,InboxEntry> mHashmap = new HashMap<String,InboxEntry>();
    	
		InboxProtocolHandler(Context ctx, int sourceAddress) {
			super(ctx, sourceAddress);
			// TODO Auto-generated constructor stub
		}
    	
	    void handleSMSMessage(int type,int id,String sender, String message,Date date) 
	    {
	    	// [TODO] this should be a placeholder and this implementation implemented in a subclass.
	    
	    	Cursor cursor;
	    	boolean addToTop = false; // Whether to add to the top of the screen or not.  Also affects if it is cached or not. */
	    	
	    	switch(type)
	    	{
	    	case SMS_MESSAGE_TYPE_NOTIFICATION:
	    		addToTop = true;
	    	case SMS_MESSAGE_TYPE_RESPONSE:
	    	case SMS_MESSAGE_TYPE_RESPONSE_SENT:
	    		InboxEntry entry = new InboxEntry();
	    		
	        	Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender));

	        	// query time
	        	cursor = ctx.getContentResolver().query(uri, mProjections, null, null, null);

	        	if (cursor.moveToFirst()) 
	        	{
	        		sender = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
	        			            	
	            	do{	
		        	    String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));

		        	    Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(contactId));
		        		InputStream bitmapStream = ContactsContract.Contacts.openContactPhotoInputStream(ctx.getContentResolver(), photoUri);
		        		
		            	entry.bitmap = BitmapFactory.decodeStream(bitmapStream);

					}while(cursor.moveToNext() && entry.bitmap == null);
	        	}
	    		cursor.close();
	    		/* if we are in thread view check if the item already exists. if it does update the entry if there is a more recent
	        	 * message.*/
	        	if (threadView)
        		{
        			if (mHashmap.containsKey(sender))
        			{
        				InboxEntry entryToCheck = mHashmap.get(sender);
        				
        				if (entryToCheck.date.getTime() < date.getTime())
        					entry = entryToCheck;
            			else
            				break;        				
        			}
        		}

	        	if (entry.bitmap == null)
            		entry.bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

	        	entry.type    = type;
	        	entry.sender = sender;
	        	entry.message = message;
	        	entry.id      = id;
	        	entry.date    = date;
	        	
	        	if (addToTop)
	        	{
	        		if (threadView)
		        		mBluetoothDeviceArrayAdapter.remove(entry);	        		

	        		mBluetoothDeviceArrayAdapter.insert(entry, 0);	        		
	        	}
	        	else
	        	{
	        		if (threadView)
	        		{
	        			if (!mHashmap.containsKey(sender))
	        			{
	        				mHashmap.put(sender, entry);
	        				mBluetoothDeviceArrayAdapter.add(entry);
	        			}
	        			else
	        			{
	        				mBluetoothDeviceArrayAdapter.notifyDataSetChanged();
	        			}
	        		}
	        		else if (!addEntryToCache(entry))
	        			mBluetoothDeviceArrayAdapter.add(entry);
	        	}
	            cursor.close();
	            break;
	    	case SMS_MESSAGE_TYPE_DONE:
	    		if(id == SMS_MESSAGE_TYPE_REQUEST)
	    			sendSMSMessage(ctx, 0x100,SMS_MESSAGE_TYPE_REQUEST_SENT,0,search,"",0);
	    	}
    	}

	    
		private boolean addEntryToCache(InboxEntry entry) {
			// TODO Auto-generated method stub
			return false;
		}
    }
    class ResponseReceiver extends BroadcastReceiver {
		ResponseReceiver()
		{
			super();
		}
	   @Override
	    public void onReceive(Context context, Intent intent) 
	    {
		   String status = intent.getStringExtra("STATUS");
	       if (status != null) mStatusTextView.setText(status);
		   mProtocolHandler.decodePacket(intent.getByteArrayExtra("header"),intent.getByteArrayExtra("payload"));
	    }
	}
    class StatusReceiver extends BroadcastReceiver {
 		StatusReceiver()
 		{
 			super();
 		}
 	   @Override
 	    public void onReceive(Context context, Intent intent) 
 	    {
 		   String status = intent.getStringExtra("STATUS");
 	       if (status != null) mStatusTextView.setText("Status:"+status);
 	    }
 	}
}