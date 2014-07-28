package uk.me.geekylou.SMSForwarder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;

import uk.me.geekylou.SMSForwarder.R;

import android.app.NotificationManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.TextView;

public class ProtocolHandler 
{
	private int              sourceAddress;
	Context                  ctx;
	HashMap<String,String> mHashmap = new HashMap<String,String>();
	private MessageCache mMessages;

	static String[] mProjections = new String[] {
        ContactsContract.PhoneLookup.DISPLAY_NAME,
        ContactsContract.PhoneLookup._ID};

	static final byte PACKET_ID_DBG               = (byte) 0x88;
	static final byte PACKET_ID_SMS               = (byte) 0x8a;
	static final byte PACKET_ID_BUTTON_PRESS      = (byte) 0x90;
	static final byte PACKET_ID_SETLED_INTENSITY8 = (byte) 0x0C;
	
	static final int SMS_MESSAGE_TYPE_SEND		      = 0x1; /* a message to be sent by the destination device. */
	static final int SMS_MESSAGE_TYPE_NOTIFICATION    = 0x2; /* message just received by the source to be displayed by the destination.*/
	static final int SMS_MESSAGE_TYPE_REQUEST	      = 0x3; /* Request for all messages in inbox.*/
	static final int SMS_MESSAGE_TYPE_RESPONSE	      = 0x4; /* Response to a request for message.*/
	static final int SMS_MESSAGE_TYPE_REQUEST_SENT    = 0x5; /* Request for messages in sent*/
	static final int SMS_MESSAGE_TYPE_RESPONSE_SENT   = 0x6; /* Response to a request for message.*/
	static final int SMS_MESSAGE_TYPE_DONE            = 0x7; /* message ID = REQUEST_TYPE_* has finished.*/

	static final int SMS_MESSAGE_TYPE_PHONE_CALL      = 0x8; /* voice call uses the same mechanism as SMS messages.*/

	ProtocolHandler(Context ctx,int sourceAddress)
	{
		this.sourceAddress  = sourceAddress;
		this.ctx            = ctx;
		mMessages 			= new MessageCache(ctx);
	}
	
	static byte getCRC(byte[] instr,int j)
	{
	   byte CRC=0;
	   byte genPoly = 0x07;
	   for (int x=0; x < j; x++)
	   {
	      byte i;
	      CRC ^= instr[x];
	      
	      for (i=0;i<8;i++)
	      {
	         if((CRC & 0x80) != 0 )
	        	 CRC = (byte) ((byte) (CRC << 1) ^ genPoly);
		 else
		   CRC <<= 1;
	      }
	   }
	   return CRC;
	}
	
	/* Replaced by decodePacket.
	int ReceivePacketFromBuffer(byte[] packetData)
	{
		ByteArrayInputStream outStr = new ByteArrayInputStream(packetData, 0, packetData.length);
		DataInputStream      dataOut = new DataInputStream(outStr);
		return 0;
	}*/

	byte []CreateDBGPacket(String str) throws IOException
	{
		byte[] byteArray = str.getBytes();
		ByteArrayOutputStream outStr = new ByteArrayOutputStream(64);
		
		outStr.write(PACKET_ID_DBG);
		outStr.write(byteArray.length+1);
		outStr.write(byteArray);
		outStr.write(0);
		
		return outStr.toByteArray();
	}
	
	static byte[] CreateButtonPressPacket(int buttonID,int pageNo) throws IOException
	{
		ByteArrayOutputStream outStr = new ByteArrayOutputStream(64);
		
		outStr.write(PACKET_ID_BUTTON_PRESS);
		outStr.write(buttonID);
		outStr.write(pageNo);
		
		return outStr.toByteArray();
	}

	static byte[] CreateSMSPacket(int type,int id,String sender,String payload,long date) throws IOException
	{
		ByteArrayOutputStream outStr = new ByteArrayOutputStream(payload.length()+sender.length()+64);
		DataOutputStream      dataOut = new DataOutputStream(outStr);
		
		ByteBuffer senderByteBuffer = Charset.forName("UTF-8").encode(sender);
		ByteBuffer payloadByteBuffer = Charset.forName("UTF-8").encode(payload);
		byte[] senderByteArr = senderByteBuffer.array();
		byte[] payloadByteArr = payloadByteBuffer.array();
		
		dataOut.write(PACKET_ID_SMS);
		dataOut.write(type);
		dataOut.writeInt(id);
		dataOut.write(senderByteBuffer.limit());
		dataOut.write(senderByteArr,0,senderByteBuffer.limit());
		dataOut.writeShort(payloadByteBuffer.limit());
		dataOut.write(payloadByteArr,0,payloadByteBuffer.limit());
		dataOut.writeLong(date);
		
		return outStr.toByteArray();
	}
	
	static byte []CreatePacket(int i,int j,byte[] packetData) throws IOException
	{
		ByteArrayOutputStream outStr = new ByteArrayOutputStream(64);
		DataOutputStream      dataOut = new DataOutputStream(outStr);
		dataOut.writeShort(j);
		dataOut.writeShort(i);
		dataOut.writeShort(packetData.length); // Extended protocol type (payload can be larger then 256byte.
		dataOut.write(packetData);
		dataOut.writeByte(getCRC(outStr.toByteArray(),outStr.size()));
		
		return outStr.toByteArray();
	}
	
	void sendSMSMessage(Context ctx, int destination,int type,int id,String sender, String payload,long date)
	{
		Intent intent  = populateSMSMessageIntent(new Intent(), destination, type, id, sender, payload, date);
		if (intent != null) ctx.sendBroadcast(intent);
	}
	
	Intent populateSMSMessageIntent(Intent broadcastIntent,int destination,int type,int id,String sender, String payload,long date)
	{
		try {			
			byte[] buf = CreatePacket(sourceAddress,destination,CreateSMSPacket(type,id,sender,payload,date));
			
			broadcastIntent.setAction(InterfaceBaseService.SEND_PACKET);
			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
			broadcastIntent.putExtra("packetData", buf);
					
			return broadcastIntent;
		} catch(IOException e) {    	
			Log.e("ProtocolHandler", "Unexpected io exception "+e.toString());
		};
		return null;
	}
	
    void sendButtonPress(Context ctx, int destination,int buttonID,int pageNo)
	{
		try {			
			byte[] buf = CreatePacket(sourceAddress,destination,CreateButtonPressPacket(buttonID,pageNo));
			
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(InterfaceBaseService.SEND_PACKET);
			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
			broadcastIntent.putExtra("packetData", buf);
			ctx.sendBroadcast(broadcastIntent);
		} catch(IOException e) {    	
			Log.e("ProtocolHandler", "Unexpected io exception "+e.toString());
		};
	}
    
    boolean decodePacket(byte header[],byte payload[])
    {
    	try {
		ByteArrayInputStream inStr = new ByteArrayInputStream(payload);
		DataInputStream      in  = new DataInputStream(inStr);

    	switch((byte)in.read())
    	{
    	case PACKET_ID_BUTTON_PRESS:
    		handleButtonPress(payload[1],payload[2]);
    		break;
    	case PACKET_ID_SMS:
    		int type		 = in.read();
    		int id           = in.readInt();
    		int senderLength = in.read();
    		byte[] senderBytes = new byte[senderLength];
    		
    		in.read(senderBytes);
    		
    		int messageLength = in.readShort();
    		byte[] messageBytes = new byte[messageLength];
    		
    		in.read(messageBytes);
    		
    		Date date = new Date(in.readLong());
    		
    		return handleSMSMessage(type,id,new String(senderBytes,"UTF-8"),new String(messageBytes,"UTF-8"),date);
    	default:
    		Log.i("ProtocolHandler", "unknown packet ID " + payload[0]);
    		
    	}    	
			in.close();
	    	inStr.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return false;
    }
    
    boolean handleSMSMessage(int type,int id,String sender, String message, Date date) 
    {
    	// [TODO] this should be a placeholder and this implementation implemented in a subclass.
    
    	Cursor cursor;
    	
    	switch(type)
    	{
    	case SMS_MESSAGE_TYPE_SEND:
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
    	//case SMS_MESSAGE_TYPE_SEND:
    		/* Sender is destination no. in the case of a send type.*/
    		/* [NOTE] disabled sending of text messages. */
    		//SmsManager.getDefault().sendTextMessage(sender, null, message, null, null);
    	//	break;
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
						sendSMSMessage(ctx, 0x100,SMS_MESSAGE_TYPE_RESPONSE,
								cursor.getInt(cursor.getColumnIndexOrThrow("_id")),
								cursor.getString(cursor.getColumnIndexOrThrow("address")), 
								cursor.getString(cursor.getColumnIndexOrThrow("body")),
								dateField);
					}
					
				}while(cursor.moveToNext());

				sendSMSMessage(ctx, 0x100,SMS_MESSAGE_TYPE_DONE,SMS_MESSAGE_TYPE_REQUEST,"","",0);				
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
						sendSMSMessage(ctx, 0x100,SMS_MESSAGE_TYPE_RESPONSE_SENT,
								cursor.getInt(cursor.getColumnIndexOrThrow("_id")),
								cursor.getString(cursor.getColumnIndexOrThrow("address")), 
								cursor.getString(cursor.getColumnIndexOrThrow("body")),
								dateField);
					}
				}while(cursor.moveToNext());
				
				sendSMSMessage(ctx, 0x100,SMS_MESSAGE_TYPE_DONE,SMS_MESSAGE_TYPE_REQUEST_SENT,"","",0);
			}
			break;
    	case SMS_MESSAGE_TYPE_DONE:
			if(id == SMS_MESSAGE_TYPE_REQUEST)
    			sendSMSMessage(ctx, 0x100,SMS_MESSAGE_TYPE_REQUEST_SENT,0,"","",0);
    	}
		return false;
    }
	/* Override this to handle button press events.*/
    void handleButtonPress(int buttonID,int pageNo)
    {
    	// [TODO] this should be a placeholder and this implementation implemented in a subclass.
    	// Also this is purely a test implementation so should do something sensible.
    	Log.i("ProtocolHandler", "unimplemented handleButtonPress(" + buttonID + "," + pageNo);
    	
    	NotificationCompat.Builder mBuilder =
			    new NotificationCompat.Builder(ctx)
			    .setSmallIcon(R.drawable.ic_launcher)
			    .setContentTitle("My notification")
			    .setContentText("Hello World!");
		
		// Sets an ID for the notification
		int mNotificationId = 001;
		// Gets an instance of the NotificationManager service
		NotificationManager mNotifyMgr = 
		        (NotificationManager) ctx.getSystemService(ctx.NOTIFICATION_SERVICE);
		// Builds the notification and issues it.
		mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }
}
