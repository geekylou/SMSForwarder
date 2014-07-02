package com.example.gpstest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ProtocolHandler 
{
	private DataInputStream  in;
	private DataOutputStream out;
	
	static final int PACKET_ID_DBG               = 0x88;
	static final int PACKET_ID_BUTTON_PRESS      = 0x90;
	static final int PACKET_ID_SETLED_INTENSITY8 = 0x0C;
	
	ProtocolHandler(DataInputStream in, DataOutputStream out)
	{
		this.in  = in;
		this.out = out;
	}
	
	byte getCRC(byte[] instr,int j)
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
	
	byte[] CreateButtonPressPacket(int buttonID,int pageNo) throws IOException
	{
		ByteArrayOutputStream outStr = new ByteArrayOutputStream(64);
		
		outStr.write(PACKET_ID_BUTTON_PRESS);
		outStr.write(buttonID);
		outStr.write(pageNo);
		
		return outStr.toByteArray();
	}

	byte []CreatePacket(short source,short destination,byte[] packetData) throws IOException
	{
		ByteArrayOutputStream outStr = new ByteArrayOutputStream(64);
		DataOutputStream      dataOut = new DataOutputStream(outStr);
		dataOut.writeShort(destination);
		dataOut.writeShort(source);
		dataOut.writeByte(packetData.length);
		
		out.write(outStr.toByteArray());
		out.writeByte(getCRC(outStr.toByteArray(),outStr.size()));
		
		return outStr.toByteArray();
	}
}
