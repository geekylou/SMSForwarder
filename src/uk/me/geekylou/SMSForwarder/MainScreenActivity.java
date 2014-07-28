package uk.me.geekylou.SMSForwarder;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;

import uk.me.geekylou.SMSForwarder.InboxActivity.InboxProtocolHandler;
import uk.me.geekylou.SMSForwarder.InboxActivity.ResponseReceiver;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.AdapterView;

public class MainScreenActivity extends FragmentActivity {
	private CachingProtocolHandler mProtocolHandler;
	private MessageCache mMessages;
	private ResponseReceiver receiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		mMessages        = new MessageCache(this);
		mProtocolHandler = new CachingProtocolHandler(this,0x104,mMessages);
		
		Intent intent = getIntent();
		
		/* Start service with a request for the inbox on the peer.*/
        Intent bluetoothService = new Intent(this,BluetoothInterfaceService.class);
		
		String search = intent.getStringExtra("search");
		if(search == null) search="";
		
		/* Start listening for replies before doing anything else.*/
		IntentFilter filter = new IntentFilter(InterfaceBaseService.PACKET_RECEIVED);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
    	receiver = new ResponseReceiver();
		registerReceiver(receiver, filter);
		
    	mProtocolHandler.populateSMSMessageIntent(bluetoothService,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, search, "",0);
    	/* cludge to make TCPIP Service work.*/
    	mProtocolHandler.sendSMSMessage(this,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, search, "",0);
	
 		startService(bluetoothService);
		
 		
	}
	
	protected void onResume()
	{
		super.onResume();

		final InboxFragment listFragment;
		final InboxFragment detailFragment;
		
		listFragment = (InboxFragment) (getFragmentManager().findFragmentById(R.id.listFragment));
		listFragment.setMessageCache(mMessages,null,true);

		detailFragment = (InboxFragment) (getFragmentManager().findFragmentById(R.id.detailFragment));
		
		String sender = listFragment.getItem(0).sender;
		
		detailFragment.setMessageCache(mMessages,sender,true);

		listFragment.setOnClickListener(new AdapterView.OnItemClickListener() {

	      	  @Override
	      	  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {

	      		detailFragment.setMessageCache(mMessages,listFragment.getItem(position).sender,true);
	      	  }
	      	});
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
		   mProtocolHandler.decodePacket(intent.getByteArrayExtra("header"),intent.getByteArrayExtra("payload"));
	   }
	}
}

class CachingProtocolHandler extends ProtocolHandler {
	static String[] mProjections = new String[] {
        ContactsContract.PhoneLookup.DISPLAY_NAME,
        ContactsContract.PhoneLookup._ID};
	private MessageCache mMessages;
	HashMap<String,InboxEntry> mHashmap = new HashMap<String,InboxEntry>();

	CachingProtocolHandler(Context ctx, int sourceAddress,MessageCache messages) {
		super(ctx, sourceAddress);
		mMessages = messages;
		// TODO Auto-generated constructor stub
	}
	
	/* Called when all messages have been sent.*/
	void updateFinished()
	{
		
	}
	
    void handleSMSMessage(int type,int id,String sender, String message,Date date) 
    {
    	// [TODO] this should be a placeholder and this implementation implemented in a subclass.
    
    	Cursor cursor;
    	
    	switch(type)
    	{
    	case SMS_MESSAGE_TYPE_NOTIFICATION:
    		break;
    	case SMS_MESSAGE_TYPE_RESPONSE:
    	case SMS_MESSAGE_TYPE_RESPONSE_SENT:
    		InboxEntry entry = new InboxEntry();
    		
    		String senderRaw = sender;

        	if (mHashmap.containsKey(entry.senderRaw))
			{
				InboxEntry baseEntry = mHashmap.get(entry.senderRaw);
				
				sender = baseEntry.sender;
			}
			else
			{
	        	Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender));
	
	        	// query contact.
	        	cursor = ctx.getContentResolver().query(uri, mProjections, null, null, null);
	
	        	if (cursor.moveToFirst()) 
	        	{
	        		sender = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
	        	}
	    		cursor.close();
	    		/* if we are in thread view check if the item already exists. if it does update the entry if there is a more recent
	        	 * message.*/
			}

        	entry.type      = type;
        	entry.sender    = sender;
        	entry.senderRaw = senderRaw;
        	entry.message   = message;
        	entry.id        = id;
        	entry.date      = date;

        	mMessages.insertEntry(entry);
            break;
    	case SMS_MESSAGE_TYPE_DONE:
    		if(id == SMS_MESSAGE_TYPE_REQUEST)
    			sendSMSMessage(ctx, 0x100,SMS_MESSAGE_TYPE_REQUEST_SENT,0,"","",0);
    		else if(id == SMS_MESSAGE_TYPE_REQUEST_SENT)
    		{
    			updateFinished();
    			
    		}
    	}
	}
}