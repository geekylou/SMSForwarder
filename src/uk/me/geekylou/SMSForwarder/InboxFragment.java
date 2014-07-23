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
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.view.LayoutInflater;

/*
class ImageViewAdapter extends ArrayAdapter<InboxEntry>
{
	private static LayoutInflater inflater=null;
	
	public ImageViewAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);		
		
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	public View getView(int position, View convertView, ViewGroup parent) {
		View vi=convertView;
        if(convertView==null)
            vi = inflater.inflate(R.layout.text_preview_item, null);
        
        InboxEntry entry = this.getItem(position);
        
        TextView textBody=(TextView)vi.findViewById(R.id.textViewBody);
        TextView textFooter=(TextView)vi.findViewById(R.id.textViewFooter);
        TextView textDate=(TextView)vi.findViewById(R.id.textViewDate);
        ImageView imageViewIcon = (ImageView)vi.findViewById(R.id.imageViewIcon);
        
        textBody.setText(entry.sender);
        if (entry.type == ProtocolHandler.SMS_MESSAGE_TYPE_RESPONSE_SENT)
        {
            textFooter.setText("Me:"+entry.message);        	
        }
        else
        {
            textFooter.setText(entry.message);
        }
        	
        textDate.setText(entry.date.toLocaleString());
        
       	imageViewIcon.setImageBitmap(entry.bitmap);

        return vi;
	}
}
*/

public class InboxFragment extends Fragment {
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
	
	public InboxFragment()
	{
		
	}
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.bluetooth_chooser, container, false);
    }
	
	
    /** Called when the activity is first created. */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
     
        Activity ctx = getActivity();
        
        final Intent intent = ctx.getIntent();
        
        mProtocolHandler = new InboxProtocolHandler(getActivity(),0x104);
        mBluetoothDeviceArrayAdapter = new ImageViewAdapter(ctx, R.layout.text_preview_item);
		
        prefs = ctx.getSharedPreferences("BluetoothPreferences", ctx.MODE_PRIVATE);

        /* Start listening for replies before doing anything else.*/
		IntentFilter filter = new IntentFilter(InterfaceBaseService.PACKET_RECEIVED);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
    	receiver = new ResponseReceiver();
		ctx.registerReceiver(receiver, filter);

        /* Start listening for status doing anything else.*/
		filter = new IntentFilter(InterfaceBaseService.SERVICE_STATUS_UPDATE);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
    	mStatusReceiver = new StatusReceiver();
    	ctx.registerReceiver(mStatusReceiver, filter);
 		
		mInboxEntriesView = (ListView) getView().findViewById(R.id.listView1);
        mInboxEntriesView.setAdapter(mBluetoothDeviceArrayAdapter);        
    }
        
    public void onDestroy()
    {
        Activity ctx = getActivity();
        
    	ctx.unregisterReceiver(mStatusReceiver);
    	ctx.unregisterReceiver(receiver);
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
	    			sendSMSMessage(ctx, 0x100,SMS_MESSAGE_TYPE_REQUEST_SENT,0,"","",0);
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
 	    }
 	}
}