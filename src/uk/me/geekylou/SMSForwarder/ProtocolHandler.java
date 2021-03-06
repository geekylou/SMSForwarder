package uk.me.geekylou.SMSForwarder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Date;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ProtocolHandler 
{
	private int              sourceAddress;
	Context                  ctx;

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

	static final int SMS_MESSAGE_TYPE_SEND_UUID       = 0x9; /* Data message to send to the recipient our unique UUID.*/
	
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
	
	void sendSMSMessage(Context ctx, Intent intent, int destination,int type,int id,String sender, String payload,long date)
	{
		intent  = populateSMSMessageIntent(intent, destination, type, id, sender, payload, date);
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
    
    void handleButtonPress(int buttonID,int pageNo)
    {
    }
    
    boolean handleSMSMessage(int type,int id,String sender, String message, Date date) 
    {
    	return false;
    }
}
