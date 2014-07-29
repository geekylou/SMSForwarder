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

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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

/* This is the first activity initiated after launch.  It allows the user to choose the peer device and launch the inbox on the remote device.*/
public class BluetoothChooserActivity<BluetoothChooser> extends Activity {
	private ListView mTimeLineView;
	private ArrayAdapter<BluetoothChooserEntry> mBluetoothDeviceArrayAdapter;
	static final int REQUEST_NEW_ENTRY = 1000;
	private BluetoothAdapter mBluetoothAdapter;
	private SharedPreferences prefs;
	private Intent mBluetoothService;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     
        setContentView(R.layout.bluetooth_chooser);
		
        final Intent intent = getIntent();
        String action = intent.getAction();
        
		mBluetoothService = new Intent(this,BluetoothInterfaceService.class);

        prefs = getSharedPreferences("BluetoothPreferences", MODE_PRIVATE);
        
        mBluetoothDeviceArrayAdapter = new ArrayAdapter<BluetoothChooserEntry>(this, R.layout.itemb);
        
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter != null) {
		    // Device does not support Bluetooth
		
			if (!mBluetoothAdapter.isEnabled()) 
			{
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				int REQUEST_ENABLE_BT=45;
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
			else
			{
		        final Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
		        // If there are paired devices
		               
		        if (pairedDevices.size() > 0) 
		        {
			        // Loop through paired devices
			        for (BluetoothDevice device : pairedDevices) {
			        	// Add the name and address to an array adapter to show in a ListView
			        	BluetoothChooserEntry entry = new BluetoothChooserEntry();
			        	entry.device = device;
			        	
			            mBluetoothDeviceArrayAdapter.add(entry);
			        }
				}
		        if (prefs.getString("BT_ID", null) != null)
		        {
		        	if (prefs.getBoolean("LAUNCH_TO_INBOX", false))
		        	{
						startActivity(new Intent(BluetoothChooserActivity.this,MainScreenActivity.class));
						finish();
		        	}
		        	else
		        		startService(mBluetoothService);
		        }
			}
		}

		Button mPostEntryButton = (Button) findViewById(R.id.buttonQuit);
        mPostEntryButton.setOnClickListener(new OnClickListener()
        {

			@Override
			public void onClick(View v) {
				stopService(mBluetoothService);
				finish();
			}
        	
        });
        
        mPostEntryButton = (Button) findViewById(R.id.buttonInbox);
        mPostEntryButton.setOnClickListener(new OnClickListener()
        {

			@Override
			public void onClick(View v) {			
				startActivity(new Intent(BluetoothChooserActivity.this,MainScreenActivity.class));
				finish();
			}
        	
        });

        mPostEntryButton = (Button) findViewById(R.id.buttonDebug);
        mPostEntryButton.setOnClickListener(new OnClickListener()
        {

			@Override
			public void onClick(View v) {			
				startActivity(new Intent(BluetoothChooserActivity.this,MainActivity.class));
				finish();
			}
        	
        });

        mTimeLineView = (ListView) findViewById(R.id.listView1);
        mTimeLineView.setAdapter(mBluetoothDeviceArrayAdapter);
        mTimeLineView.setOnItemClickListener (new AdapterView.OnItemClickListener() {

        	  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
        		arg1.setSelected(true);
    		    SharedPreferences.Editor mPreferencesEditor = prefs.edit();

    		    mPreferencesEditor.putString("BT_ID"  , mBluetoothDeviceArrayAdapter.getItem(position).device.getAddress());
    		    mPreferencesEditor.putString("BT_NAME", mBluetoothDeviceArrayAdapter.getItem(position).device.getName());
        		mPreferencesEditor.commit();

        		stopService(mBluetoothService);
        		startService(mBluetoothService);
        	  }
        	});
   }    
}