package uk.me.geekylou.SMSForwarder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

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

public class ProtocolHandler 
{
	private int              sourceAddress;
	Context                  ctx;
	
	static final byte PACKET_ID_DBG               = (byte) 0x88;
	static final byte PACKET_ID_SMS               = (byte) 0x8a;
	static final byte PACKET_ID_BUTTON_PRESS      = (byte) 0x90;
	static final byte PACKET_ID_SETLED_INTENSITY8 = (byte) 0x0C;
	
	static final int SMS_MESSAGE_TYPE_SEND		   = 0x1; /* a message to be sent by the destination device. */
	static final int SMS_MESSAGE_TYPE_NOTIFICATION = 0x2; /* message just received by the source to be displayed by the destination.*/
	static final int SMS_MESSAGE_TYPE_REQUEST	   = 0x3; /* Request for message ID = id.*/
	static final int SMS_MESSAGE_TYPE_RESPONSE	   = 0x4; /* Response to a request for message ID.*/
			
	ProtocolHandler(Context ctx,int sourceAddress)
	{
		this.sourceAddress  = sourceAddress;
		this.ctx            = ctx;
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

	static byte[] CreateSMSPacket(int type,int id,String sender,String payload) throws IOException
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
	
	void sendSMSMessage(Context ctx, int destination,int type,int id,String sender, String payload)
	{
		try {			
			byte[] buf = CreatePacket(sourceAddress,destination,CreateSMSPacket(type,id,sender,payload));
			
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(TCPPacketHandler.SEND_PACKET);
			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
			broadcastIntent.putExtra("packetData", buf);
			ctx.sendBroadcast(broadcastIntent);
			
		} catch(IOException e) {    	
			Log.e("ProtocolHandler", "Unexpected io exception "+e.toString());
		};
	}
	
    void sendButtonPress(Context ctx, int destination,int buttonID,int pageNo)
	{
		try {			
			byte[] buf = CreatePacket(sourceAddress,destination,CreateButtonPressPacket(buttonID,pageNo));
			
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(TCPPacketHandler.SEND_PACKET);
			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
			broadcastIntent.putExtra("packetData", buf);
			ctx.sendBroadcast(broadcastIntent);
		} catch(IOException e) {    	
			Log.e("ProtocolHandler", "Unexpected io exception "+e.toString());
		};
	}
    
    void decodePacket(byte header[],byte payload[])
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
    		
    		handleSMSMessage(type,id,new String(senderBytes,"UTF-8"),new String(messageBytes,"UTF-8"));
    		
    		
    	default:
    		Log.i("ProtocolHandler", "unknown packet ID " + payload[0]);
    		
    	}    	
			in.close();
	    	inStr.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    void handleSMSMessage(int type,int id,String sender, String message) 
    {
    	// [TODO] this should be a placeholder and this implementation implemented in a subclass.
    	
    	switch(type)
    	{
    	case SMS_MESSAGE_TYPE_NOTIFICATION:
	    	// define the columns to return for getting the name of the sender.
	    	String[] projection = new String[] {
	    	        ContactsContract.PhoneLookup.DISPLAY_NAME,
	    	        ContactsContract.PhoneLookup._ID};
	    	
	    	Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender));
	
	    	// query time
	    	Cursor cursor = ctx.getContentResolver().query(uri, projection, null, null, null);
	
	    	NotificationCompat.Builder mBuilder =
				    new NotificationCompat.Builder(ctx)
	    			.setSmallIcon(R.drawable.ic_launcher)
				    .setContentText(message);
	
	    	if (cursor.moveToFirst()) 
	    	{
	    	    String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));
	    		sender = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
	    		
	    	    Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(contactId));
	    		InputStream bitmapStream = ContactsContract.Contacts.openContactPhotoInputStream(ctx.getContentResolver(), photoUri);
	    		
	        	Bitmap bitmap = BitmapFactory.decodeStream(bitmapStream);
	        	
	        	if (bitmap != null)
	        		mBuilder.setLargeIcon(bitmap);
	    	}
	    	
	    	mBuilder.setContentTitle(sender);
			
			// Sets an ID for the notification
			int mNotificationId = 001;
			// Gets an instance of the NotificationManager service
			NotificationManager mNotifyMgr = 
			        (NotificationManager) ctx.getSystemService(ctx.NOTIFICATION_SERVICE);
			// Builds the notification and issues it.
			mNotifyMgr.notify(mNotificationId, mBuilder.build());
			break;
    	case SMS_MESSAGE_TYPE_SEND:
    		/* Sender is destination no. in the case of a send type.*/
    		SmsManager.getDefault().sendTextMessage(sender, null, message, null, null);
    	}		
	}

	/* Overide this to handle button press events.*/
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
