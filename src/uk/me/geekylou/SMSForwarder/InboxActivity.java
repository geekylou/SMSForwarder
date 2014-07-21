package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import uk.me.geekylou.SMSForwarder.R;
import uk.me.geekylou.SMSForwarder.MainActivity.ResponseReceiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.view.LayoutInflater;

class ImageViewAdapter extends ArrayAdapter<InboxEntry>
{
	private static LayoutInflater inflater=null;
	
	public ImageViewAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);		
		
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	public View getView(int position, View convertView, ViewGroup parent) {
		View vi=convertView;
        if(convertView==null)
            vi = inflater.inflate(R.layout.text_preview_item, null);
        
        InboxEntry entry = this.getItem(position);
        
        TextView textBody=(TextView)vi.findViewById(R.id.textViewBody);
        TextView textFooter=(TextView)vi.findViewById(R.id.textViewFooter);
        TextView textDate=(TextView)vi.findViewById(R.id.textViewDate);
        ImageView imageViewIcon = (ImageView)vi.findViewById(R.id.imageViewIcon);
        
        textBody.setText(entry.sender);
        textFooter.setText(entry.message);
        textDate.setText(entry.date.toLocaleString());
        
//        if (entry.bitmap != null)
        	imageViewIcon.setImageBitmap(entry.bitmap);

        //vi.setBackgroundColor(Color.GREEN);
        return vi;
	}
}

public class InboxActivity extends Activity {
	private ListView mInboxEntriesView;
	private ArrayAdapter<InboxEntry> mBluetoothDeviceArrayAdapter;
	static final int REQUEST_NEW_ENTRY = 1000;
	private BluetoothAdapter mBluetoothAdapter;
	private SharedPreferences prefs;
	InboxProtocolHandler  mProtocolHandler;
	private ResponseReceiver receiver;
	static String[] mProjections = new String[] {
	        ContactsContract.PhoneLookup.DISPLAY_NAME,
	        ContactsContract.PhoneLookup._ID};;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     
        setContentView(R.layout.bluetooth_chooser);

        mBluetoothDeviceArrayAdapter = new ImageViewAdapter(this, R.layout.text_preview_item);
		        
		mProtocolHandler  = new InboxProtocolHandler(this,0x104);
    	mProtocolHandler.sendSMSMessage(this,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, "", "",0);
        
		IntentFilter filter = new IntentFilter(InterfaceBaseService.PACKET_RECEIVED);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
    	receiver = new ResponseReceiver();
		registerReceiver(receiver, filter);
		
		mInboxEntriesView = (ListView) findViewById(R.id.listView1);
        mInboxEntriesView.setAdapter(mBluetoothDeviceArrayAdapter);
    }
    
    protected void onDestroy()
    {
    	unregisterReceiver(receiver);
    	super.onDestroy();
    }
    class InboxProtocolHandler extends ProtocolHandler {

		InboxProtocolHandler(Context ctx, int sourceAddress) {
			super(ctx, sourceAddress);
			// TODO Auto-generated constructor stub
		}
    	
	    void handleSMSMessage(int type,int id,String sender, String message,Date date) 
	    {
	    	// [TODO] this should be a placeholder and this implementation implemented in a subclass.
	    
	    	Cursor cursor;
	    	
	    	switch(type)
	    	{
	    	case SMS_MESSAGE_TYPE_RESPONSE:
	    		InboxEntry entry = new InboxEntry();
	    		
	        	Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender));

	        	// query time
	        	cursor = ctx.getContentResolver().query(uri, mProjections, null, null, null);

	        	if (cursor.moveToFirst()) 
	        	{
	        	    String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));
	        		sender = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
	        		
	        	    Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(contactId));
	        		InputStream bitmapStream = ContactsContract.Contacts.openContactPhotoInputStream(ctx.getContentResolver(), photoUri);
	        		
	            	entry.bitmap = BitmapFactory.decodeStream(bitmapStream);	            	
	        	}

	        	if (entry.bitmap == null)
            		entry.bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

            	entry.sender  = sender;
	        	entry.message = message;
	        	entry.id      = id;
	        	entry.date    = date;
	        	
	            mBluetoothDeviceArrayAdapter.add(entry);

	            cursor.close();
	    	}
    	}
    }
    class ResponseReceiver extends BroadcastReceiver {
		ResponseReceiver()
		{
			super();
		}
	   @Override
	    public void onReceive(Context context, Intent intent) 
	    {
		   mProtocolHandler.decodePacket(intent.getByteArrayExtra("header"),intent.getByteArrayExtra("payload"));
	    }
	}
}