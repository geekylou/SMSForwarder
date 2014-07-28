package uk.me.geekylou.SMSForwarder;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.BitmapFactory;
import android.widget.ArrayAdapter;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

public class MessageCache extends SQLiteOpenHelper 
{
	private Context ctx;
    private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "message_cache.db";
	
	private static final String TABLE_NAME = "entries";

	static String[] mProjections = new String[] {
        ContactsContract.PhoneLookup.DISPLAY_NAME,
        ContactsContract.PhoneLookup._ID};

	MessageCache(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
		ctx = context;
    }

	public void onCreate(SQLiteDatabase db) {
		
		db.execSQL("CREATE TABLE " + TABLE_NAME + " ("+
                "_id INTEGER PRIMARY KEY," +
				"originalID INTEGER," +
				"type INTEGER," +
				"sender CHAR(40)," +
				"senderRaw CHAR(40)," +
                "message TEXT," +
                "date INTEGER);");
    }

	
	@Override
	public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub
		
	}
	
	public long insertEntry(InboxEntry entry)
	{
		long returnValue = -1;
		SQLiteDatabase db = getWritableDatabase();
		
		/* Store timeline entry. */
		ContentValues values = new ContentValues();
		
		values.put("originalID", entry.id);
		values.put("type", entry.type);
		values.put("sender", entry.sender);
		values.put("senderRaw", entry.senderRaw);
		values.put("message",entry.message);
		values.put("date", entry.date.getTime());
		
		String[] searchValues = new String[] {
		        Integer.toString(entry.id),
		        Integer.toString(entry.type)};
		
		Cursor c = db.rawQuery("SELECT * FROM entries WHERE originalID=? and type=?", searchValues);
		
		if(!c.moveToNext())
		{
			returnValue = db.insert(TABLE_NAME, null, values);
			//Log.d("SMSForwarder", "MessageCache insertEntry(" + returnValue + ") "+entry.message);
		}
		
		db.close();

//		Log.d("SMSForwarder", "MessageCache insertEntry(" + returnValue + ") "+entry.message);
		
		return returnValue;
	}
	
	/*
	public void updateEntry(Entry entry)
	{
		SQLiteDatabase db = getWritableDatabase();
		
		/* Store timeline entry. /
		ContentValues values = new ContentValues();
		String[] args = {entry.uuid.toString()};
		
		values.put("json", entry.toJSON());

		db.update("entries", values, "uuid='"+entry.uuid.toString()+"'", null);
		
		//("entries", null, values);
	
		args = entry.tags.split(",");
				
		db.close();
	}
*/
	public void deleteEntry(InboxEntry entry)
	{
		SQLiteDatabase db = getWritableDatabase();

		db.delete(TABLE_NAME,"_id='"+entry.id+"'", null);
	}
	
	public ArrayAdapter<InboxEntry> GetTimeline(ArrayAdapter<InboxEntry> mTimelineArrayAdapter,String sender,boolean threadView)
	{
		HashMap<String,InboxEntry> mHashmap;
		SQLiteDatabase db = getWritableDatabase();
		
		Cursor c;
		String args[] = new String[]{sender};
		
		if (sender == null)
		{
			mHashmap = new HashMap<String,InboxEntry>();
			c = db.rawQuery("SELECT * FROM entries ORDER BY date DESC", null);
		}
		else
		{
			mHashmap = null;
			threadView = false; /* Thread view makes no sense for a single contact.*/
			c = db.rawQuery("SELECT * FROM entries WHERE sender=? ORDER BY date DESC", args);
		}
			
		while (c.moveToNext())
		{
			boolean addEntry = true;
			InboxEntry entry = new InboxEntry();
			entry.id   = c.getInt(c.getColumnIndex("_id"));
			entry.type = c.getInt(c.getColumnIndex("type"));
			entry.date = new Date(c.getLong(c.getColumnIndex("date")));

			entry.sender    = c.getString(c.getColumnIndex("sender"));
			entry.senderRaw = c.getString(c.getColumnIndex("senderRaw"));
			entry.message   = c.getString(c.getColumnIndex("message"));

			//Log.d("SMSForwarder", "MessageCache " + c.getLong(c.getColumnIndex("date")));

			/*
        	Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(entry.senderRaw));

        	// query contact.
        	Cursor cursor = ctx.getContentResolver().query(uri, mProjections, null, null, null);

        	if (cursor.moveToFirst()) 
        	{
            	do{	
	        	    String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));

	        	    Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(contactId));
	        		InputStream bitmapStream = ContactsContract.Contacts.openContactPhotoInputStream(ctx.getContentResolver(), photoUri);
	        		
	            	entry.bitmap = BitmapFactory.decodeStream(bitmapStream);

				}while(cursor.moveToNext() && entry.bitmap == null);
        	}
    		cursor.close();
    		/* if we are in thread view check if the item already exists. if it does update the entry if there is a more recent
        	 * message.*/
			
        	/*if (entry.bitmap == null)
        		entry.bitmap = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_launcher);*/

			if (threadView)
    		{
    			if (!mHashmap.containsKey(entry.senderRaw))
    			{
    				mHashmap.put(entry.senderRaw, entry);
    				mTimelineArrayAdapter.add(entry);
    			}
    		}
    		else
    			mTimelineArrayAdapter.add(entry);
		}
		db.close();
		
		return mTimelineArrayAdapter;
	}
	
	/* Debug function to dump the contents of the database to a file. */
	/*void dumpDB(Context context)
	{
		SQLiteDatabase db = getWritableDatabase();
		
		Cursor c;
	
		c = db.rawQuery("SELECT * FROM entries", null);
		
		FileOutputStream os;
		try {
			os = new FileOutputStream("/sdcard/db_dump.json",true);
			DataOutputStream writer = new DataOutputStream(os);
			
			while (c.moveToNext())
			{
				/* Make sure the timestamp value is being stored in the correct format.The timestamp database column is 
				 * always correct so we use that one to correct the json one.
				 /
				Entry entry = new Entry(new JSONObject(c.getString(c.getColumnIndex("json"))), false);
				entry.timestamp = new Date(c.getLong(c.getColumnIndex("date")));
				
				writer.writeUTF(entry.toJSON());
			}
			
			os.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
	
	void restoreDB(String filename)
	{
		SQLiteDatabase db = getWritableDatabase();
		
		db.execSQL("DROP TABLE entries;");
		createEntriesTable(db);
		
		/* Will be reborn as export functions :) /
		FileInputStream os;
		try {
			os = new FileInputStream(filename);
			DataInputStream reader = new DataInputStream(os);
			String str;
			
			while ((str = reader.readUTF()) != null)
			{
				Entry entry = new Entry(new JSONObject(str),false);
				insertEntry(entry);
			}
			
			os.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	*/
}










/*
InboxEntry entry = new InboxEntry();

Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender));

// query time
cursor = ctx.getContentResolver().query(uri, mProjections, null, null, null);

if (cursor.moveToFirst()) 
{
	sender = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
		            	
	do{	
	    String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));

	    Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(contactId));
		InputStream bitmapStream = ContactsContract.Contacts.openContactPhotoInputStream(ctx.getContentResolver(), photoUri);
		
    	entry.bitmap = BitmapFactory.decodeStream(bitmapStream);

	}while(cursor.moveToNext() && entry.bitmap == null);
}

/*
cursor.close();
/* if we are in thread view check if the item already exists. if it does update the entry if there is a more recent
 * message.

if (threadView)
{
	if (mHashmap.containsKey(sender))
	{
		InboxEntry entryToCheck = mHashmap.get(sender);
		
		if (entryToCheck.date.getTime() < date.getTime())
			entry = entryToCheck;
		else
			break;        				
	}
}

if (entry.bitmap == null)
	entry.bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

entry.type    = type;
entry.sender = sender;
entry.message = message;
entry.id      = id;
entry.date    = date;

if (addToTop)
{
	if (threadView)
		mBluetoothDeviceArrayAdapter.remove(entry);	        		

	if (mSender == null || mSender.equals(sender))
		mBluetoothDeviceArrayAdapter.insert(entry, 0);	        		
}
else
{
	if (threadView)
	{
		if (!mHashmap.containsKey(sender))
		{
			mHashmap.put(sender, entry);
			mBluetoothDeviceArrayAdapter.add(entry);
		}
		else
		{
			mBluetoothDeviceArrayAdapter.notifyDataSetChanged();
		}
	}
	else 
	{
		if (mSender == null || mSender.equals(sender))
			mBluetoothDeviceArrayAdapter.add(entry);
	}
}
*/