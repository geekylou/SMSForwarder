package uk.me.geekylou.SMSForwarder;

import java.util.Comparator;
import java.util.Date;

import android.graphics.Bitmap;
import android.net.Uri;

public class InboxEntry implements Comparator<InboxEntry> {
	public String sender,senderRaw,message;
	public Bitmap bitmap;
	public int    id;
	public int    cacheId;
	public Date   date;
	public int type;
	
	public String toString()
	{
		return sender + "\n" + message;
	}

	@Override
	public int compare(InboxEntry lhs, InboxEntry rhs) {
		// TODO Auto-generated method stub
		return rhs.date.compareTo(lhs.date);
	}
}
