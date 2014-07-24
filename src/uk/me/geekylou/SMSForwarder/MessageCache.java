package uk.me.geekylou.SMSForwarder;

import java.io.IOException;
import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.ArrayAdapter;
import android.util.Log;

public class MessageCache extends SQLiteOpenHelper 
{
    private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "timeline.db";
	
	private static final String TABLE_NAME = "entries";

	MessageCache(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

	public void onCreate(SQLiteDatabase db) {
		
		db.execSQL("CREATE TABLE " + TABLE_NAME + " ("+
                "_id PRIMARY KEY," +
				"type INTEGER," +
				"sender CHAR(40)" +
                "message TEXT," +
                "date INTEGER);");
    }

	
	@Override
	public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub
		
	}
	
	public void insertEntry(InboxEntry entry)
	{
		SQLiteDatabase db = getWritableDatabase();
		
		/* Store timeline entry. */
		ContentValues values = new ContentValues();
		
		values.put("_id", entry.id);
		values.put("type", entry.type);
		values.put("sender", entry.sender);
		values.put("message",entry.message);
		values.put("date", entry.date.getTime());
		db.insert("entries", null, values);
			
		db.close();
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

		db.delete("entries","_id='"+entry.id+"'", null);
	}
	
	public ArrayAdapter<InboxEntry> GetTimeline(ArrayAdapter<InboxEntry> mTimelineArrayAdapter,String sender)
	{
		SQLiteDatabase db = getWritableDatabase();
		
		Cursor c;
		String args[];
				
		c = db.rawQuery("SELECT * FROM entries", null);
		
		while (c.moveToNext())
		{
			String json = c.getString(c.getColumnIndex("json"));
			
			Log.d("BlobFreeformMicroblogActivity", "TimelineStorageHelper.GetTimeline " + json);

			boolean addEntry = true;
			InboxEntry entry = new InboxEntry();
			entry.id   = c.getInt(c.getColumnIndex("_id"));
			entry.date = new Date(c.getLong(c.getColumnIndex("date")));

			entry.sender = c.getString(c.getColumnIndex("sender"));
			entry.message = c.getString(c.getColumnIndex("message"));

			Log.d("SMSForwarder", "MessageCache " + c.getLong(c.getColumnIndex("date")));
			
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