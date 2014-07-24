package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.util.Log;

public class LocalTimeline extends SQLiteOpenHelper 
{
    private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "timeline.db";
	
	private static final String TABLE_NAME = "entries";

	LocalTimeline(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

	private void createEntriesTable(SQLiteDatabase db)
	{
		db.execSQL("CREATE TABLE " + TABLE_NAME + " ("+
                "uuid CHAR(40) PRIMARY KEY," +
                "json TEXT," +
                "date INTEGER);");
	}
	public void onCreate(SQLiteDatabase db) {
		
		createEntriesTable(db);
        db.execSQL("CREATE TABLE " + "tags (" +
                "uuid CHAR(40)," +
        		"tag  CHAR(40), PRIMARY KEY (uuid, tag));");
        
		db.execSQL("CREATE TABLE " + "media (" +
                "uuid CHAR(40), " +
				"shasum CHAR(64)," +
        		"uri  CHAR(256), PRIMARY KEY (uuid,uri));");
    }

	/* Regenerate the tags table from the records. */
	public void regenerateTags()
	{
		SQLiteDatabase db = getWritableDatabase();
		
		db.execSQL("DROP TABLE tags;");
		db.execSQL("CREATE TABLE " + "tags (" +
                "uuid CHAR(40), " +
        		"tag  CHAR(40), PRIMARY KEY (uuid, tag));");
		
		/* Now work through every item in the database and create a new
		 * tag entry.
		 */
		
		Cursor c = db.rawQuery("SELECT * FROM entries", null);
		
		while (c.moveToNext())
		{
			String json = c.getString(c.getColumnIndex("json"));
			
			try {
				InboxEntry entry = new InboxEntry(new JSONObject(json),false);
				
				ContentValues values;
				
				String args[] = entry.tags.split(",");
				
				for (int count=0; count< args.length; count++)
				{
					values = new ContentValues();
					
					values.put("uuid", entry.uuid.toString());
					values.put("tag", args[count].trim().toLowerCase());
					db.insert("tags", null, values);
				}
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		db.close();
	}
	
	public InboxEntry GetEntryByUUID(String uuid)
	{
		SQLiteDatabase db = getWritableDatabase();
		
		Cursor c;
		String args[];
		Entry entry = null;
		
		
		c = db.rawQuery("SELECT * FROM entries WHERE uuid='"+uuid+"'", null);
		
		if (c.moveToNext())
		{
			String json = c.getString(c.getColumnIndex("json"));
			
			Log.d("BlobFreeformMicroblogActivity", "TimelineStorageHelper.GetEntryByUUID " + json);
			try {
				boolean addEntry = true;
				entry = new Entry(new JSONObject(json),false);
				entry.timestamp = new Date(c.getLong(c.getColumnIndex("date")));
				
				Log.d("BlobFreeformMicroblogActivity", "TimelineStorageHelper.GetEntryByUUID " + c.getLong(c.getColumnIndex("date")));
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		db.close();
		
		return entry;
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
		
		values.put("uuid", entry.uuid.toString());
		values.put("json", entry.toJSON());
		values.put("date", entry.timestamp.getTime());
		db.insert("entries", null, values);
	
		String args[] = entry.tags.split(",");
		
		for (int count=0; count< args.length; count++)
		{
			values = new ContentValues();
			
			values.put("uuid", entry.uuid.toString());
			values.put("tag", args[count].trim().toLowerCase());
			db.insert("tags", null, values);
		}
		
		
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
	public void deleteEntry(Entry entry)
	{
		SQLiteDatabase db = getWritableDatabase();

		db.delete("entries","uuid='"+entry.uuid.toString()+"'", null);
		db.delete("tags","uuid='"+entry.uuid.toString()+"'", null);
	}
	
	public ArrayAdapter<InboxEntry> GetTimeline(ArrayAdapter<Entry> mTimelineArrayAdapter,String sender)
	{
		SQLiteDatabase db = getWritableDatabase();
		
		Cursor c;
		String args[];
		
		args = (tags != null) ? args = tags.split(",") : new String[0];
		
		// TODO add proper SQL based searching on all matching tags.
		/*if (args.length > 0 && args[0] != "")
		{
			String[] columns = {"entries.json"};
			String selection = "entries.uuid = tags.uuid and tags.tag='diary'";
			
			c = db.rawQuery("SELECT * FROM entries,tags WHERE entries.uuid=tags.uuid AND tags.tag='"+args[0].trim()+"'", null);
			//query (String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy)
			//c = db.query("tags,entries", columns, selection, args, null, null, "date", null);
		}
		else*/
		
		c = db.rawQuery("SELECT * FROM entries", null);
		
		while (c.moveToNext())
		{
			String json = c.getString(c.getColumnIndex("json"));
			
			Log.d("BlobFreeformMicroblogActivity", "TimelineStorageHelper.GetTimeline " + json);
			try {
				boolean addEntry = true;
				Entry entry = new Entry(new JSONObject(json),false);
				entry.timestamp = new Date(c.getLong(c.getColumnIndex("date")));
				
				Log.d("BlobFreeformMicroblogActivity", "TimelineStorageHelper.GetTimeline " + c.getLong(c.getColumnIndex("date")));
				
				/* Do the tag searching ourselves for now.  This searches to make sure
				 * the entry has all the tags we selected on our search criteria (args)
				 */
				if(args.length > 0 && args[0] != "")
				{
					String[] entryTags = entry.tags.split(","); 
					
					for (int count=0;count < args.length && addEntry; count++)
					{
						boolean match = false;
						
						/* '@' is a special character denoting threads so a match also occurs if the entries uuid eqauls this valus. */
						if (args[count].startsWith("@"))
            			{
							match = args[count].equalsIgnoreCase("@"+entry.uuid.toString());
            			}
						
						for (int countInner=0;countInner < entryTags.length && !match; countInner++)
							if (entryTags[countInner].trim().equalsIgnoreCase( args[count].trim()))
								match = true;
						
						if (!match)
							addEntry = false;
					}
				}
				if (addEntry)
					mTimelineArrayAdapter.add(entry);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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