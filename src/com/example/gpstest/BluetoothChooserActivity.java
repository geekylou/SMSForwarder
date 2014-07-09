package com.example.gpstest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

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

public class BluetoothChooserActivity<BluetoothChooser> extends Activity {
	private ListView mTimeLineView;
	private ArrayAdapter<Entry> mBluetoothDeviceArrayAdapter;
	static final int REQUEST_NEW_ENTRY = 1000;
	private BluetoothAdapter mBluetoothAdapter;
	private SharedPreferences prefs;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     
        setContentView(R.layout.bluetooth_chooser);
		
        final Intent intent = getIntent();
        String action = intent.getAction();
        
        prefs = getSharedPreferences("BluetoothPreferences", MODE_PRIVATE);
        
        mBluetoothDeviceArrayAdapter = new ArrayAdapter<Entry>(this, R.layout.itemb);
        
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
		    // Device does not support Bluetooth
		}
		
		if (!mBluetoothAdapter.isEnabled()) {
			finish();
		}
		
        final Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
               
        if (pairedDevices.size() > 0) {
        // Loop through paired devices
        for (BluetoothDevice device : pairedDevices) {
        	// Add the name and address to an array adapter to show in a ListView
        	Entry entry = new Entry();
        	entry.device = device;
        	
            mBluetoothDeviceArrayAdapter.add(entry);
         }
     }

// TODO: Do we want to remove OK/Cancel buttons for selecting Bluetooth device to connect to.
/*        
        Button mPostEntryButton = (Button) findViewById(R.id.buttonSelect);
        mPostEntryButton.setOnClickListener(new OnClickListener()
        {

			@Override
			public void onClick(View v) {
	
			}
        	
        });
        
        mPostEntryButton = (Button) findViewById(R.id.buttonCancel);
        mPostEntryButton.setOnClickListener(new OnClickListener()
        {

			@Override
			public void onClick(View v) {			
						
			}
        	
        });
*/
        mTimeLineView = (ListView) findViewById(R.id.listView1);
        mTimeLineView.setAdapter(mBluetoothDeviceArrayAdapter);
        mTimeLineView.setOnItemClickListener (new AdapterView.OnItemClickListener() {

        	  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
        		
    		    SharedPreferences.Editor mPreferencesEditor = prefs.edit();

    		    mPreferencesEditor.putString("BT_ID"  , mBluetoothDeviceArrayAdapter.getItem(position).device.getAddress());
    		    mPreferencesEditor.putString("BT_NAME", mBluetoothDeviceArrayAdapter.getItem(position).device.getName());
        		mPreferencesEditor.commit();
        		
      			intent.putExtra("DeviceID",mBluetoothDeviceArrayAdapter.getItem(position).device.getAddress());
				setResult(RESULT_OK);
				finish();
        	  }
        	});
   }    
}