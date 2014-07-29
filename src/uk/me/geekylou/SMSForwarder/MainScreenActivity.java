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
		
    	/*mProtocolHandler.populateSMSMessageIntent(bluetoothService,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, search, "",0);
    	/* cludge to make TCPIP Service work.*/
    	/*mProtocolHandler.sendSMSMessage(this,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, search, "",0);
	
 		startService(bluetoothService);*/
		
		mProtocolHandler.sendInboxRequest();
	}

	protected void onDestroy()
	{
		super.onDestroy();
		unregisterReceiver(receiver);
	}
	
	protected void onResume()
	{
		super.onResume();
		
		listFragment = (InboxFragment) (getFragmentManager().findFragmentById(R.id.listFragment));
		listFragment.setMessageCache(mMessages,null,true);

		detailFragment = (TimelineFragment) (getFragmentManager().findFragmentById(R.id.detailFragment));
		
		//String sender = listFragment.getItem(0).sender;
		
		//detailFragment.setMessageCache(mMessages,sender,true);

		listFragment.setOnClickListener(new AdapterView.OnItemClickListener() {

	      	  @Override
	      	  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {

	      		detailFragment.setMessageCache(mMessages,listFragment.getItem(position).sender,true);
	      	  }
	      	});
		
		listFragment.setNewContactListener(new View.OnClickListener() {
            public void onClick(View v) {
            	@SuppressWarnings("deprecation")
				Intent intent = new Intent(Intent.ACTION_PICK, Contacts.Phones.CONTENT_URI);
            	startActivityForResult(intent, PICK_CONTACT); 
            }
        });
	}
	
	public void onActivityResult (int requestCode, int resultCode, Intent intent) 
	{
		if (resultCode != Activity.RESULT_OK || requestCode != PICK_CONTACT) return;
		Cursor c = managedQuery(intent.getData(), null, null, null, null);
		if (c.moveToFirst()) 
		{
		  String name  = c.getString(c.getColumnIndexOrThrow(Contacts.Phones.DISPLAY_NAME));
		  String phone = c.getString(c.getColumnIndexOrThrow(Contacts.Phones.NUMBER));
		 
		  detailFragment.messageToNewContact(mMessages,name,phone,true);
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
		MainScreenProtocolHandler(Context ctx, int sourceAddress) {
			super(ctx, sourceAddress);
		}
		
		void sendInboxRequest()
		{
			long latestMessageDate = mMessages.getLatestMessageDate();
			Log.d("SMSForwarder", "CachingProtocolHandler latestMessageDate(" + latestMessageDate + ") ");

			/* Start service with a request for the inbox on the peer.*/
	        Intent bluetoothService = new Intent(ctx,BluetoothInterfaceService.class);
	        bluetoothService.putExtra("openKey","uk.me.geekylou.MainScreenActivity");
	        
	    	populateSMSMessageIntent(bluetoothService,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, "", "", latestMessageDate);
	    	/* Kludge to make TCPIP Service work.*/
	    	sendSMSMessage(ctx,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, "", "", latestMessageDate);
		
	 		ctx.startService(bluetoothService);		
		}
		
	    boolean handleSMSMessage(int type,int id,String sender, String message,Date date) 
	    {
	    	// [TODO] this should be a placeholder and this implementation implemented in a subclass.
	    
	    	Cursor cursor;
	    	
	    	switch(type)
	    	{
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
}