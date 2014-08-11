package uk.me.geekylou.SMSForwarder;

import java.util.Date;

import uk.me.geekylou.SMSForwarder.R;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.ContactsContract;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.support.v7.app.ActionBarActivity;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {
	protected static final String key = "uk.me.geekylou.SMSForwarder.MainActivity";
	protected static final int CONTACT = 0;
	protected static final int PICK_CONTACT   = 10;
	protected static final int PICK_BT_DEVICE = 11;
	Intent          mBluetoothService;
	ResponseReceiver receiver;
	ProtocolHandler  mProtocolHandler;
	
	TextView txtGPS;
	TextView txtLOC;
	TextView txtSock;
	private SharedPreferences prefs;
	private Intent mIPService;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_debug);

		mBluetoothService = new Intent(this,BluetoothInterfaceService.class);
		mIPService = new Intent(this,TCPIPInterfaceService.class);
		
		mProtocolHandler  = new ProtocolHandler(this,0x104);
		
		txtSock = (TextView)findViewById(R.id.textViewDate);
		txtGPS = (TextView)findViewById(R.id.textView2);
		txtLOC = (TextView)findViewById(R.id.textView3);
		
        prefs = getSharedPreferences("BluetoothPreferences", MODE_PRIVATE);

        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        
        txtGPS.setText(ip);
        
		txtLOC.setText(prefs.getString("BT_ID", "")+":"+prefs.getString("BT_NAME",""));
		
		final TextView txtIPPeer = (TextView)findViewById(R.id.editTextPeerIP);
		txtIPPeer.setText(prefs.getString("PEER_IP_ADDRESS", ""));
		
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter != null) {
		    // Device does not support Bluetooth
		
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				int REQUEST_ENABLE_BT=45;
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}
		
		Button butStart= (Button)findViewById(R.id.buttonStart);
		butStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                StartLocation();
            }
        });

		Button butStartTCP= (Button)findViewById(R.id.buttonStartIP);
		butStartTCP.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	CheckBox checkConnect = (CheckBox)findViewById(R.id.checkBoxIPConnect);
            	
            	Editor prefsEdit = prefs.edit();
            	
            	prefsEdit.putString("PEER_IP_ADDRESS",txtIPPeer.getText().toString());
            	prefsEdit.commit();
            	
            	mIPService.putExtra("CONNECT", checkConnect.isChecked());

        		startService(mIPService);
            }
        });
		
		Button butStopTCP= (Button)findViewById(R.id.buttonStopIP);
		butStopTCP.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

        		stopService(mIPService);
            }
        });
		
		Button but1= (Button)findViewById(R.id.button1);
		but1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
        		Intent broadcastIntent = new Intent();
        		broadcastIntent.setAction(InterfaceBaseService.SEND_PACKET);
        		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        		broadcastIntent.putExtra("requestStatus", true);
        		sendBroadcast(broadcastIntent);

            	mProtocolHandler.sendButtonPress(MainActivity.this, 0x100,0,0);
            }
        });
		Button but2= (Button)findViewById(R.id.button2);
		but2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	mProtocolHandler.sendButtonPress(MainActivity.this, 0x100,1,0);            }
        });
		Button but3= (Button)findViewById(R.id.button3);
		but3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	mProtocolHandler.sendButtonPress(MainActivity.this, 0x100,2,0);            	
            }
        });
		Button but4 = (Button)findViewById(R.id.button4);
		but4.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	mProtocolHandler.sendButtonPress(MainActivity.this, 0x100,3,0);
            }
        });

		Button butStop= (Button)findViewById(R.id.buttonStop);
		butStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                StopLocation();
            }
        });
		
		Button butChooser = (Button)findViewById(R.id.buttonChooser);
		butChooser.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	Intent intent = new Intent(MainActivity.this, BluetoothChooserActivity.class);
				intent.setAction(intent.ACTION_INSERT);
				startActivityForResult(intent,PICK_BT_DEVICE);
				// TODO Auto-generated method stub
            }
        });	
		
		Button butDisconnect = (Button)findViewById(R.id.buttonDisconnect);
		butDisconnect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	Intent broadcastIntent = new Intent();
    			broadcastIntent.setAction(InterfaceBaseService.SEND_PACKET);
    			broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
    			broadcastIntent.putExtra("closeKey", key);
    			sendBroadcast(broadcastIntent);
            }
        });
		
		CheckBox checkLaunchToInbox = (CheckBox)findViewById(R.id.checkBoxLaunchInbox);
		checkLaunchToInbox.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
    		    SharedPreferences.Editor mPreferencesEditor = prefs.edit();

    		    mPreferencesEditor.putBoolean("LAUNCH_TO_INBOX"  , ((CheckBox)v).isChecked());
        		mPreferencesEditor.commit();
				
			}});
		
		IntentFilter filter = new IntentFilter(BluetoothInterfaceService.SERVICE_STATUS_UPDATE);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
		receiver = new ResponseReceiver();
		registerReceiver(receiver, filter);
		
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(InterfaceBaseService.SEND_PACKET);
		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra("requestStatus", true);
		sendBroadcast(broadcastIntent);
		
	}

	protected void onResume()
	{
		super.onResume();
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(InterfaceBaseService.SEND_PACKET);
		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra("requestStatus", true);
		sendBroadcast(broadcastIntent);
	}
	
	void StartLocation()
	{	
		CheckBox checkConnect = (CheckBox)findViewById(R.id.checkBoxConnect);
		mBluetoothService.putExtra("CONNECT", checkConnect.isChecked());
		mBluetoothService.putExtra("openKey", key);
		mBluetoothService.putExtra("BT_ID", prefs.getString("BT_ID", ""));			

		startService(mBluetoothService);
	}
	
	protected void onDestroy()
	{
		unregisterReceiver(receiver);
		super.onDestroy();
	}
	
	void StopLocation()
	{
		stopService(mBluetoothService);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	class ResponseReceiver extends BroadcastReceiver {
			ResponseReceiver()
			{
				super();
			}
		   @Override
		    public void onReceive(Context context, Intent intent) {
		       String text = intent.getStringExtra("DBG_KEYS");
		       if (text != null) txtGPS.setText(text);
		       text = intent.getStringExtra("NET");
		       if (text != null) txtLOC.setText(text);
		       text = intent.getStringExtra("STATUS");
		       if (text != null) txtSock.setText(text);
		       
		    }
		}
	public void onActivityResult (int requestCode, int resultCode, Intent intent) 
	{
		  if (resultCode != Activity.RESULT_OK || requestCode != PICK_CONTACT) return;
		  Cursor c = managedQuery(intent.getData(), null, null, null, null);
		  if (c.moveToFirst()) {
		     String phone = c.getString(c.getColumnIndexOrThrow(Contacts.Phones.NUMBER));
		     // yay
		     
         	mProtocolHandler.sendSMSMessage(MainActivity.this,new Intent(),0x100,ProtocolHandler.SMS_MESSAGE_TYPE_NOTIFICATION,0, phone, "This is a test sms message triggered using a button",new Date().getTime());

		  }
	}
}
