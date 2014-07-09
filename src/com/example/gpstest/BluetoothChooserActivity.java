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

public class BluetoothChooserActivity extends Activity {
	private ListView mTimeLineView;
	private ArrayAdapter<Entry> mBluetoothDeviceArrayAdapter;
	static final int REQUEST_NEW_ENTRY = 1000;
	BluetoothAdapter mBluetoothAdapter;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
     
        setContentView(R.layout.bluetooth_chooser);
		
        final Intent intent = getIntent();
        String action = intent.getAction();
        
        mBluetoothDeviceArrayAdapter = new ArrayAdapter<Entry>(this, R.layout.itemb);
        
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
		    // Device does not support Bluetooth
		}
		
		if (!mBluetoothAdapter.isEnabled()) {
			
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

        mTimeLineView = (ListView) findViewById(R.id.listView1);
        mTimeLineView.setAdapter(mBluetoothDeviceArrayAdapter);
        mTimeLineView.setOnItemClickListener (new AdapterView.OnItemClickListener() {

        	  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
        		  
      			intent.putExtra("DeviceID",mBluetoothDeviceArrayAdapter.getItem(position).device.getAddress());
				setResult(RESULT_OK);
				finish();
        	  }
        	});
   }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent returnedIntent) { 
	    super.onActivityResult(requestCode, resultCode, returnedIntent); 

	    switch(requestCode) { 
	      case REQUEST_NEW_ENTRY:
	        //if(resultCode == RESULT_OK){  
	        //	reload();   
	        //}
	    }
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        		
        switch (item.getItemId()) {

            default:
                return super.onContextItemSelected(item);
        }
    }
    
    /**
     * Called when your activity's options menu needs to be created.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
//        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
/*        case R.id.itemEvent:
  		  	Intent intent = new Intent(BluetoothChooserActivity.this, EditEventEntryActivity.class);
			  
  		  	intent.setAction(Intent.ACTION_INSERT);
  		  	startActivityForResult(intent,REQUEST_NEW_ENTRY);
  		  	return true;
        case R.id.itemDumpDB:
        	return true;*/
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
    }
    
    class DeleteEntryDialogClickListener implements DialogInterface.OnClickListener 
    {
    	Entry entry;
    	
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
            case DialogInterface.BUTTON_POSITIVE:
                break;

            case DialogInterface.BUTTON_NEGATIVE:
                //No button clicked
                break;
            }
        }
    };
}