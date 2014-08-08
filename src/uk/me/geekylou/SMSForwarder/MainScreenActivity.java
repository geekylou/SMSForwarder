package uk.me.geekylou.SMSForwarder;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;

import uk.me.geekylou.SMSForwarder.InboxActivity.InboxProtocolHandler;
import uk.me.geekylou.SMSForwarder.InboxActivity.ResponseReceiver;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;

public class MainScreenActivity extends FragmentActivity {
	protected static final int PICK_CONTACT = 0;
	private MainScreenProtocolHandler mProtocolHandler;
	private MessageCache mMessages;
	private ResponseReceiver receiver;
	
	private InboxFragment listFragment;
	private TimelineFragment detailFragment;

	String  sender;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		mMessages        = new MessageCache(this);
		mProtocolHandler = new MainScreenProtocolHandler(this,0x104);
		
		//Intent intent = getIntent();
		
		/* Start service with a request for the inbox on the peer.*/
        //Intent bluetoothService = new Intent(this,BluetoothInterfaceService.class);
		
		/* Start listening for replies before doing anything else.*/
		IntentFilter filter = new IntentFilter(InterfaceBaseService.PACKET_RECEIVED);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
    	receiver = new ResponseReceiver();
		registerReceiver(receiver, filter);
	}

	protected void onDestroy()
	{
		super.onDestroy();
		mProtocolHandler.closeConnection();
		unregisterReceiver(receiver);
	}
	
	protected void onPause()
	{
		super.onPause();
		mProtocolHandler.closeConnection();		
	}
	
	protected void onResume()
	{
		super.onResume();
		
		listFragment = (InboxFragment) (getFragmentManager().findFragmentById(R.id.listFragment));
		listFragment.setMessageCache(mMessages,null,true);

		detailFragment = (TimelineFragment) (getFragmentManager().findFragmentById(R.id.detailFragment));
		
		InboxEntry item = listFragment.getItem(0);
		if (item != null && sender == null)
		{
			sender = listFragment.getItem(0).sender;
		}
			
		detailFragment.setMessageCache(mMessages,sender,false);
		
		listFragment.setOnClickListener(new AdapterView.OnItemClickListener() {

	      	  @Override
	      	  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {

	      		detailFragment.setMessageCache(mMessages,listFragment.getItem(position).sender,false);
	      	  }
	      	});
		
		listFragment.setNewContactListener(new View.OnClickListener() {
            public void onClick(View v) {
            	@SuppressWarnings("deprecation")
				Intent intent = new Intent(Intent.ACTION_PICK, Contacts.Phones.CONTENT_URI);
            	startActivityForResult(intent, PICK_CONTACT); 
            }
        });
		
		mProtocolHandler.sendInboxRequest(true);
	}
	
	public void onActivityResult (int requestCode, int resultCode, Intent intent) 
	{
		if (resultCode != Activity.RESULT_OK || requestCode != PICK_CONTACT) return;
		Cursor c = managedQuery(intent.getData(), null, null, null, null);
		if (c.moveToFirst()) 
		{
		  sender  = c.getString(c.getColumnIndexOrThrow(Contacts.Phones.DISPLAY_NAME));
		  String phone = c.getString(c.getColumnIndexOrThrow(Contacts.Phones.NUMBER));
		  
		  detailFragment.messageToNewContact(mMessages,sender,phone,true);
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
		   mProtocolHandler.decodePacket(intent.getByteArrayExtra("header"),intent.getByteArrayExtra("payload"));
	   }
	}
	
	class MainScreenProtocolHandler extends ProtocolHandler
	{
		static final String openKey = "uk.me.geekylou.MainScreenActivity";
		
		MainScreenProtocolHandler(Context ctx, int sourceAddress) {
			super(ctx, sourceAddress);
		}
		
		void sendInboxRequest(boolean launch)
		{
			long latestMessageDate = mMessages.getLatestMessageDate();
			Log.d("SMSForwarder", "CachingProtocolHandler latestMessageDate(" + latestMessageDate + ") ");

			if (launch)
			{
				/* Start service with a request for the inbox on the peer.*/
		        Intent bluetoothService = new Intent(ctx,BluetoothInterfaceService.class);
		        bluetoothService.putExtra("openKey",openKey);
		        
		    	populateSMSMessageIntent(bluetoothService,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, "", "", latestMessageDate);
		    	/* Kludge to make TCPIP Service work.*/
			
		 		ctx.startService(bluetoothService);
			}
	 		
	    	sendSMSMessage(ctx,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, "", "", latestMessageDate);
		}
		
		void closeConnection()
		{
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(InterfaceBaseService.SEND_PACKET);
			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
			broadcastIntent.putExtra("closeKey", openKey);
			sendBroadcast(broadcastIntent);
		}
		
	    boolean handleSMSMessage(int type,int id,String sender, String message,Date date) 
	    {
	    	Cursor cursor;
	    	
	    	switch(type)
	    	{
	    	case SMS_MESSAGE_TYPE_NOTIFICATION:
	    		/* If we receive a text message then send a resync request to get the updated inbox with correct ID.*/
	    		sendInboxRequest(false);
	    		break;
	    	case SMS_MESSAGE_TYPE_DONE:
				updateFinished();
	    	}
	        return false;
		}

		void updateFinished()
		{
			detailFragment.refreshEntries();
			listFragment.refreshEntries();
		}		
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    // Inflate the menu items for use in the action bar
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main, menu);
	    return super.onCreateOptionsMenu(menu);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle presses on the action bar items
	    switch (item.getItemId()) {
	        case R.id.itemDebug:
            	Intent intent = new Intent(MainScreenActivity.this, MainActivity.class);
				intent.setAction(intent.ACTION_INSERT);
				startActivityForResult(intent,0);
	        	return true;
	        case R.id.action_settings:
            	intent = new Intent(MainScreenActivity.this, BluetoothChooserActivity.class);
				intent.setAction(intent.ACTION_CHOOSER);
				startActivityForResult(intent,0);
	        	return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
}