package uk.me.geekylou.SMSForwarder;

import java.util.Date;
import java.util.HashMap;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

abstract public class CachingProtocolHandler extends ProtocolHandler {
	static String[] mProjections = new String[] {
        ContactsContract.PhoneLookup.DISPLAY_NAME,
        ContactsContract.PhoneLookup._ID};
	private MessageCache mMessages;

	CachingProtocolHandler(Context ctx, int sourceAddress,MessageCache messages) {
		super(ctx, sourceAddress);
		mMessages = messages;
		// TODO Auto-generated constructor stub
	}
	
	/* Called when all messages have been sent.*/
	abstract void updateFinished();
	
	void sendInboxRequest()
	{
		long latestMessageDate = mMessages.getLatestMessageDate();
		Log.d("SMSForwarder", "CachingProtocolHandler latestMessageDate(" + latestMessageDate + ") ");

		/* Start service with a request for the inbox on the peer.*/
        Intent bluetoothService = new Intent(ctx,BluetoothInterfaceService.class);
        
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
}