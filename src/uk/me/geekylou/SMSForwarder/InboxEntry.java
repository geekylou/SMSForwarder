package uk.me.geekylou.SMSForwarder;

import java.util.Date;

import android.graphics.Bitmap;
import android.net.Uri;

public class InboxEntry {
	public String sender,message;
	public Bitmap bitmap;
	public int    id;
	public Date   date;
	public int type;
	
	public String toString()
	{
		return sender + "\n" + message;
	}
}
