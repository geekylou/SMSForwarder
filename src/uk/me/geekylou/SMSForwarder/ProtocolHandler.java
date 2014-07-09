package uk.me.geekylou.SMSForwarder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ProtocolHandler 
{
	private int              sourceAddress;
	
	static final byte PACKET_ID_DBG               = (byte) 0x88;
	static final byte PACKET_ID_BUTTON_PRESS      = (byte) 0x90;
	static final byte PACKET_ID_SETLED_INTENSITY8 = (byte) 0x0C;
	
	ProtocolHandler(Context ctx,int sourceAddress)
	{
		this.sourceAddress  = sourceAddress;
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

	static byte []CreatePacket(int i,int j,byte[] packetData) throws IOException
	{
		ByteArrayOutputStream outStr = new ByteArrayOutputStream(64);
		DataOutputStream      dataOut = new DataOutputStream(outStr);
		dataOut.writeShort(j);
		dataOut.writeShort(i);
		dataOut.writeByte(packetData.length);
		dataOut.write(packetData);
		dataOut.writeByte(getCRC(outStr.toByteArray(),outStr.size()));
		
		return outStr.toByteArray();
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
		} catch(IOException e) {};
	}
    
    void decodePacket(byte header[],byte payload[])
    {
    	switch(payload[0])
    	{
    	case PACKET_ID_BUTTON_PRESS:
    		handleButtonPress(payload[1],payload[2]);
    		break;
    	default:
    		Log.i("ProtocolHandler", "unknown packet ID " + payload[0]);
    		
    	}
    }
    
    /* Overide this to handle button press events.*/
    void handleButtonPress(int buttonID,int pageNo)
    {
    	Log.i("ProtocolHandler", "unimplemented handleButtonPress(" + buttonID + "," + pageNo);
    }
}
