package uk.me.geekylou.SMSForwarder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
import android.widget.ListView;
import android.widget.TextView;
import android.view.LayoutInflater;

public class InboxActivity<BluetoothChooser> extends Activity {
	private ListView mInboxEntriesView;
	private ArrayAdapter<InboxEntry> mBluetoothDeviceArrayAdapter;
	static final int REQUEST_NEW_ENTRY = 1000;
	private BluetoothAdapter mBluetoothAdapter;
	private SharedPreferences prefs;
	InboxProtocolHandler  mProtocolHandler;
	private ResponseReceiver receiver;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     
        setContentView(R.layout.bluetooth_chooser);

        mBluetoothDeviceArrayAdapter = new ArrayAdapter<InboxEntry>(this, R.layout.text_preview_item);
		        
		mProtocolHandler  = new InboxProtocolHandler(this,0x104);
    	mProtocolHandler.sendSMSMessage(this,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, "", "");
        
		IntentFilter filter = new IntentFilter(InterfaceBaseService.PACKET_RECEIVED);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
    	receiver = new ResponseReceiver();
		registerReceiver(receiver, filter);
		
		mInboxEntriesView = (ListView) findViewById(R.id.listView1);
        mInboxEntriesView.setAdapter(mBluetoothDeviceArrayAdapter);
        
     
    }
    class InboxProtocolHandler extends ProtocolHandler {

		InboxProtocolHandler(Context ctx, int sourceAddress) {
			super(ctx, sourceAddress);
			// TODO Auto-generated constructor stub
		}
    	
	    void handleSMSMessage(int type,int id,String sender, String message) 
	    {
	    	// [TODO] this should be a placeholder and this implementation implemented in a subclass.
	    
	    	Cursor cursor;
	    	
	    	switch(type)
	    	{
	    	case SMS_MESSAGE_TYPE_RESPONSE:
	    		InboxEntry entry = new InboxEntry();
	        	entry.sender  = sender;
	        	entry.message = message;
	        	entry.id      = id;
	        	
	            mBluetoothDeviceArrayAdapter.add(entry);
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