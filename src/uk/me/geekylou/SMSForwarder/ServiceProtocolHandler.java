package uk.me.geekylou.SMSForwarder;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import android.app.NotificationManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;

public class ServiceProtocolHandler extends ProtocolHandler {
	HashMap<String,String> mHashmap = new HashMap<String,String>();
	private MessageCache mMessages;
	private MediaPlayer mMediaPlayer;

	static String[] mProjections = new String[] {
        ContactsContract.PhoneLookup.DISPLAY_NAME,
        ContactsContract.PhoneLookup._ID};

	ServiceProtocolHandler(Context ctx, int sourceAddress) {
		super(ctx, sourceAddress);
		mMessages 			= new MessageCache(ctx);
	}

    boolean handleSMSMessage(int type,int id,String sender, String message, Date date) 
    {
    	Cursor cursor;
    	
    	switch(type)
    	{
    	case SMS_MESSAGE_TYPE_SEND:
    		/* Sender is destination no. in the case of a send type.*/
    		/* [NOTE] disabled sending of text messages. */
    		SmsManager.getDefault().sendTextMessage(sender, null, message, null, null);
    		break;
    	case SMS_MESSAGE_TYPE_SEND_UUID:
    		byte[] bytes = new byte[]{0x11,0x22,0x33,0x44,0x55,0x66,0x77,0x66,0x55};
    		
    		SmsManager.getDefault().sendDataMessage(sender, null, (short) 9100, bytes, null, null);
    		break;    		
    	case SMS_MESSAGE_TYPE_PHONE_CALL:
    	case SMS_MESSAGE_TYPE_NOTIFICATION:	
	    	// define the columns to return for getting the name of the sender.
	    	String[] projection = new String[] {
	    	        ContactsContract.PhoneLookup.DISPLAY_NAME,
	    	        ContactsContract.PhoneLookup._ID};
	    	
	    	Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender));
	
	    	// query time
	    	cursor = ctx.getContentResolver().query(uri, projection, null, null, null);
	
	    	NotificationCompat.Builder mBuilder =
				    new NotificationCompat.Builder(ctx)
	    			.setSmallIcon(R.drawable.ic_launcher)
				    .setContentText(message);
	
	    	if (cursor.moveToFirst()) 
	    	{
	    		Bitmap bitmap;
	    		sender = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
	    		
	    		do{	
	        	    String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));

	        	    Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(contactId));
	        		InputStream bitmapStream = ContactsContract.Contacts.openContactPhotoInputStream(ctx.getContentResolver(), photoUri);
	        		
	            	bitmap = BitmapFactory.decodeStream(bitmapStream);

				}while(cursor.moveToNext() && bitmap == null);
	        	
	        	if (bitmap != null)
	        		mBuilder.setLargeIcon(bitmap);
	    	}
	    	
	    	if (type == SMS_MESSAGE_TYPE_PHONE_CALL)
	    	{
	    		mBuilder.setContentTitle("Call from");
	    		mBuilder.setContentText(sender);
	    	}
	    	else
	    		mBuilder.setContentTitle(sender);
	    		
			// Sets an ID for the notification
			int mNotificationId = 001;
			// Gets an instance of the NotificationManager service
			NotificationManager mNotifyMgr = 
			        (NotificationManager) ctx.getSystemService(ctx.NOTIFICATION_SERVICE);
			// Builds the notification and issues it.
			mNotifyMgr.notify(mNotificationId, mBuilder.build());
			break;
    	case SMS_MESSAGE_TYPE_REQUEST:
			{
				Log.i("ProtocolHandler", "handleSMSMessage - SMS_MESSAGE_TYPE_REQUEST\n");
				long requestDate = date.getTime();
				String search[] = {sender};
				String msgData = "";
				if (!sender.equals(""))
				{
					cursor = ctx.getContentResolver().query(Uri.parse("content://sms/inbox"), null, "address=?", search, null);
				}
				else
				{
					cursor = ctx.getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);					
				}
				
				if (!cursor.moveToFirst())
					break;
				do{	
					long dateField = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
					
					if (dateField >= requestDate)
					{
						sendSMSMessage(ctx, new Intent(),0x100,SMS_MESSAGE_TYPE_RESPONSE,
								cursor.getInt(cursor.getColumnIndexOrThrow("_id")),
								cursor.getString(cursor.getColumnIndexOrThrow("address")), 
								cursor.getString(cursor.getColumnIndexOrThrow("body")),
								dateField);
					}
					
				}while(cursor.moveToNext());

				sendSMSMessage(ctx, new Intent(),0x100,SMS_MESSAGE_TYPE_DONE,SMS_MESSAGE_TYPE_REQUEST,"","",0);				
			}
			break;
    	case SMS_MESSAGE_TYPE_RESPONSE:
    	case SMS_MESSAGE_TYPE_RESPONSE_SENT:
    		InboxEntry entry = new InboxEntry();
    		
    		String senderRaw = sender;

        	if (mHashmap.containsKey(entry.senderRaw))
			{
				sender = mHashmap.get(entry.senderRaw);
			}
			else
			{
	        	Uri uriPhone = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender));
	
	        	// query contact.
	        	cursor = ctx.getContentResolver().query(uriPhone, mProjections, null, null, null);
	
	        	if (cursor.moveToFirst()) 
	        	{
	        		sender = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
	        		mHashmap.put(senderRaw, sender);
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

    		return true;
    	case SMS_MESSAGE_TYPE_REQUEST_SENT:
			{
				Log.i("ProtocolHandler", "handleSMSMessage - SMS_MESSAGE_TYPE_REQUEST_SENT\n");
				long requestDate = date.getTime();
				String search[] = {sender};
				String msgData = "";
				if (!sender.equals(""))
				{
					cursor = ctx.getContentResolver().query(Uri.parse("content://sms/sent"), null, "address=?", search, null);
				}
				else
				{
					cursor = ctx.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, null);					
				}
				
				if (!cursor.moveToFirst())
					break;
				do{	
					long dateField = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
					
					if (dateField >= requestDate)
					{
						sendSMSMessage(ctx, new Intent(),0x100,SMS_MESSAGE_TYPE_RESPONSE_SENT,
								cursor.getInt(cursor.getColumnIndexOrThrow("_id")),
								cursor.getString(cursor.getColumnIndexOrThrow("address")), 
								cursor.getString(cursor.getColumnIndexOrThrow("body")),
								dateField);
					}
				}while(cursor.moveToNext());
				
				sendSMSMessage(ctx, new Intent(), 0x100,SMS_MESSAGE_TYPE_DONE,SMS_MESSAGE_TYPE_REQUEST_SENT,"","",0);
			}
			break;
    	case SMS_MESSAGE_TYPE_DONE:
			if(id == SMS_MESSAGE_TYPE_REQUEST)
    			sendSMSMessage(ctx, new Intent(), 0x100,SMS_MESSAGE_TYPE_REQUEST_SENT,0,"","",0);
    	}
		return false;
    }
	/* Override this to handle button press events.*/
    void handleButtonPress(int buttonID,int pageNo)
    {
    	switch(buttonID)
    	{
    	case 1:
    		if(mMediaPlayer != null)
    		{
    			mMediaPlayer.stop();
    			mMediaPlayer = null;
    		}
    		try {
    			Uri alert =  RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    			
    			mMediaPlayer = new MediaPlayer();
    			mMediaPlayer.setDataSource(ctx, alert);
    			final AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    			if (audioManager.getStreamVolume(AudioManager.STREAM_RING) != 0) {
    				mMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
    				mMediaPlayer.setLooping(true);
    				mMediaPlayer.prepare();
    				mMediaPlayer.start();
    			}
    		} catch(Exception e) {
    		}
    		break;
    	case 2:
    		if(mMediaPlayer != null)
    		{
    			mMediaPlayer.stop();
    			mMediaPlayer = null;
    		}
    		break;
    	default:
	    	Uri soundURI = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
	    	
	    	NotificationCompat.Builder mBuilder =
				    new NotificationCompat.Builder(ctx)
				    .setSmallIcon(R.drawable.ic_launcher)
				    .setContentTitle("Notification")
				    .setContentText("User generated alert!");
//					.setSound(soundURI);
			// Sets an ID for the notification
			int mNotificationId = 001;
			// Gets an instance of the NotificationManager service
			NotificationManager mNotifyMgr = 
			        (NotificationManager) ctx.getSystemService(ctx.NOTIFICATION_SERVICE);
			// Builds the notification and issues it.
			mNotifyMgr.notify(mNotificationId, mBuilder.build());
    	}
    }
}
