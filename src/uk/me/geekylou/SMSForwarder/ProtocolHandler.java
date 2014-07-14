package uk.me.geekylou.SMSForwarder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import uk.me.geekylou.SMSForwarder.R;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class ProtocolHandler 
{
	private int              sourceAddress;
	Context                  ctx;
	
	static final byte PACKET_ID_DBG               = (byte) 0x88;
	static final byte PACKET_ID_SMS               = (byte) 0x8a;
	static final byte PACKET_ID_BUTTON_PRESS      = (byte) 0x90;
	static final byte PACKET_ID_SETLED_INTENSITY8 = (byte) 0x0C;
	
	ProtocolHandler(Context ctx,int sourceAddress)
	{
		this.sourceAddress  = sourceAddress;
		this.ctx            = ctx;
	}
	
	static byte getCRC(byte[] instr,int j)
	{
	   byte CRC=0;
	   byte genPoly = 0x07;
	   byte chr;
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
	
	int ReceivePacketFromBuffer(byte[] packetData)
	{
		ByteArrayInputStream outStr = new ByteArrayInputStream(packetData, 0, packetData.length);
		DataInputStream      dataOut = new DataInputStream(outStr);
		return 0;
	}

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

	static byte[] CreateSMSPacket(String sender,String payload) throws IOException
	{
		ByteArrayOutputStream outStr = new ByteArrayOutputStream(payload.length()+sender.length()+64);
		DataOutputStream      dataOut = new DataOutputStream(outStr);
		
		byte[] senderByteArr = Charset.forName("UTF-8").encode(sender).array();
		byte[] payloadByteArr = Charset.forName("UTF-8").encode(payload).array();
		
		dataOut.write(PACKET_ID_SMS);
		dataOut.write(senderByteArr.length);
		dataOut.write(senderByteArr);
		dataOut.writeShort(payloadByteArr.length);
		dataOut.write(payloadByteArr);
		
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
	
	void sendSMSMessage(Context ctx, int destination,String sender, String payload)
	{
		try {			
			byte[] buf = CreatePacket(sourceAddress,destination,CreateSMSPacket(sender,payload));
			
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
    		int senderLength = in.read();
    		byte[] senderBytes = new byte[senderLength];
    		
    		in.read(senderBytes);
    		
    		int messageLength = in.readShort();
    		byte[] messageBytes = new byte[messageLength];
    		
    		in.read(messageBytes);
    		
    		handleSMSMessage(new String(senderBytes,"UTF-8"),new String(messageBytes,"UTF-8"));
    		
    		
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
    
    void handleSMSMessage(String string, String string2) {

    	NotificationCompat.Builder mBuilder =
			    new NotificationCompat.Builder(ctx)
			    .setSmallIcon(R.drawable.ic_launcher)
			    .setContentTitle(string)
			    .setContentText(string2);
		
		// Sets an ID for the notification
		int mNotificationId = 001;
		// Gets an instance of the NotificationManager service
		NotificationManager mNotifyMgr = 
		        (NotificationManager) ctx.getSystemService(ctx.NOTIFICATION_SERVICE);
		// Builds the notification and issues it.
		mNotifyMgr.notify(mNotificationId, mBuilder.build());
		
	}

	/* Overide this to handle button press events.*/
    void handleButtonPress(int buttonID,int pageNo)
    {
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
