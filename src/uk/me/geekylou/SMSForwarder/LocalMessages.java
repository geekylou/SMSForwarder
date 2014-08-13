package uk.me.geekylou.SMSForwarder;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.SmsManager;
import android.widget.ArrayAdapter;

public class LocalMessages extends MessageCache
{
	private Context ctx;
	LocalMessages(Context context) {
		super(context);
		ctx = context;
		// TODO Auto-generated constructor stub
	}
	
	public ArrayAdapter<InboxEntry> getTimeline(ArrayAdapter<InboxEntry> mTimelineArrayAdapter,String sender,boolean threadView)
	{
		HashMap<String,InboxEntry> mHashmap = new HashMap<String,InboxEntry>();
		SQLiteDatabase db = getWritableDatabase();
		Bitmap bitmap = null;
		Cursor c;
		String args[] = new String[]{sender};
		Bitmap defaultBitmap = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_launcher);
		
		c = ctx.getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);					
			
		bitmap = populateAdapter(mHashmap, c, sender, mTimelineArrayAdapter, threadView, bitmap, defaultBitmap,false);

		c.close();
		c = ctx.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, null);					
		
		bitmap = populateAdapter(mHashmap, c, sender, mTimelineArrayAdapter, threadView, bitmap, defaultBitmap,true);

		c.close();
		mTimelineArrayAdapter.sort(new InboxEntry());
		
		db.close();

		return mTimelineArrayAdapter;
	}
	
	private Bitmap populateAdapter(HashMap<String,InboxEntry> mHashmap,Cursor c,String sender,ArrayAdapter<InboxEntry> mTimelineArrayAdapter,boolean threadView,Bitmap bitmap, Bitmap defaultBitmap, boolean sent)
	{
		while (c.moveToNext())
		{
			boolean addEntry = true;
			InboxEntry entry = new InboxEntry();
			entry.id   = c.getInt(c.getColumnIndex("_id"));
			entry.type = c.getInt(c.getColumnIndex("type"));
			entry.date = new Date(c.getLong(c.getColumnIndex("date")));

			if (sent)
				entry.type  = ProtocolHandler.SMS_MESSAGE_TYPE_RESPONSE_SENT;
			else
				entry.type  = ProtocolHandler.SMS_MESSAGE_TYPE_RESPONSE;
				
			entry.senderRaw = c.getString(c.getColumnIndex("address"));
			entry.message   = c.getString(c.getColumnIndex("body"));

			//Log.d("SMSForwarder", "MessageCache " + c.getLong(c.getColumnIndex("date")));

			if (mHashmap.containsKey(entry.senderRaw))
			{
				InboxEntry baseEntry = mHashmap.get(entry.senderRaw);
				
				entry.bitmap = baseEntry.bitmap;
				entry.sender = baseEntry.sender;
			}
			else
			{
				entry.sender = getContactName(entry.senderRaw);

				Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(entry.senderRaw));

				if (sender == null || entry.sender.equals(sender))
				{	
		        	// query contact.
		        	Cursor cursor = ctx.getContentResolver().query(uri, mProjections, null, null, null);
		
		        	if (cursor.moveToFirst()) 
		        	{
		            	do{	
			        	    String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));
		
			        	    Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(contactId));
			        		InputStream bitmapStream = ContactsContract.Contacts.openContactPhotoInputStream(ctx.getContentResolver(), photoUri);
			        		
			        		if (threadView || bitmap == null)
			        			entry.bitmap = bitmap = BitmapFactory.decodeStream(bitmapStream);
			        		else
			        			entry.bitmap = bitmap;
			        		
						}while(cursor.moveToNext() && entry.bitmap == null);
		        	}
		    		cursor.close();
				}
			}
			
			if (sender == null || entry.sender.equals(sender))
			{
	    		/* if we are in thread view check if the item already exists. if it does update the entry if there is a more recent
	        	 * message.*/
	    		
	        	if (entry.bitmap == null)
	        		entry.bitmap = defaultBitmap;
	
				if (threadView)
	    		{
	    			if (!mHashmap.containsKey(entry.senderRaw))
	    			{
	    				mHashmap.put(entry.senderRaw, entry);
	        			mTimelineArrayAdapter.add(entry);    				
	    			}
	    		}
	    		else
	    		{
	    			if (!mHashmap.containsKey(entry.senderRaw))
	    			{
	    				mHashmap.put(entry.senderRaw, entry);
	    			}
					mTimelineArrayAdapter.add(entry);
	    		}
			}
		}
		return bitmap;
	}
	
	public void sendMessage(ProtocolHandler protocolHandler, String address, String message)
	{
		SmsManager.getDefault().sendTextMessage(address, null, message, null, null);
	}
}
