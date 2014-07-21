package uk.me.geekylou.SMSForwarder;

import android.graphics.Bitmap;
import android.net.Uri;

public class InboxEntry {
	public String sender,message;
	public Bitmap bitmap;
	public int    id;
	public Uri uri;
	
	public String toString()
	{
		return sender + "\n" + message;
	}
}
