package uk.me.geekylou.SMSForwarder;

import uk.me.geekylou.SMSForwarder.R;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
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
import android.database.Cursor;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class MainActivity extends Activity {

	protected static final int CONTACT = 0;
	protected static final int PICK_CONTACT = 0;
	Intent          mBluetoothService;
	ResponseReceiver receiver;
	ProtocolHandler  mProtocolHandler;
	
	TextView txtGPS;
	TextView txtLOC;
	TextView txtSock;
	private SharedPreferences prefs;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_debug);

		mBluetoothService = new Intent(this,BluetoothInterfaceService.class);
		mProtocolHandler  = new ProtocolHandler(this,0x104);
		
		txtSock = (TextView)findViewById(R.id.textView1);
		txtGPS = (TextView)findViewById(R.id.textView2);
		txtLOC = (TextView)findViewById(R.id.textView3);
		
        prefs = getSharedPreferences("BluetoothPreferences", MODE_PRIVATE);

		txtLOC.setText(prefs.getString("BT_ID", "")+":"+prefs.getString("BT_NAME",""));
		
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
		    // Device does not support Bluetooth
			finish();
		}

		if (!mBluetoothAdapter.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    int REQUEST_ENABLE_BT=45;
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		
		Button butStart= (Button)findViewById(R.id.buttonStart);
		butStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                StartLocation();
            }
        });

		Button but1= (Button)findViewById(R.id.button1);
		but1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
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
            	
            	@SuppressWarnings("deprecation")
				Intent intent = new Intent(Intent.ACTION_PICK, Contacts.Phones.CONTENT_URI);
            	startActivityForResult(intent, CONTACT); 
            }
        });
		Button but4 = (Button)findViewById(R.id.button4);
		but4.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	mProtocolHandler.sendSMSMessage(MainActivity.this,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_SEND,0, "+447968975566", "This is a test sms message triggered using a button");
            	//mProtocolHandler.sendButtonPress(MainActivity.this, 0x100,3,0);
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
        		intent.putExtra("Tags","note");
				startActivityForResult(intent,0);
				// TODO Auto-generated method stub
            }
        });	
		
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(InterfaceBaseService.SEND_PACKET);
		broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
		broadcastIntent.putExtra("STATUS", true);
		sendBroadcast(broadcastIntent);
		
	}

	void StartLocation()
	{	
		if (receiver == null)
		{
			CheckBox checkConnect = (CheckBox)findViewById(R.id.checkBoxConnect);
			mBluetoothService.putExtra("CONNECT", checkConnect.isChecked());
			mBluetoothService.putExtra("BT_ID", prefs.getString("BT_ID", ""));
			
			IntentFilter filter = new IntentFilter(BluetoothInterfaceService.SERVICE_STATUS_UPDATE);
			filter.addCategory(Intent.CATEGORY_DEFAULT);
			receiver = new ResponseReceiver();
			registerReceiver(receiver, filter);
		}
		startService(mBluetoothService);
	}
	
	void StopLocation()
	{
		if (receiver != null)
		{
			unregisterReceiver(receiver);
			receiver = null;
		}
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
		       String text = intent.getStringExtra("GPS");
		       if (text != null) txtGPS.setText(text);
		       text = intent.getStringExtra("NET");
		       if (text != null) txtLOC.setText(text);
		       text = intent.getStringExtra("STATUS");
		       if (text != null) txtSock.setText(text);
		       
		    }
		}
	public void onActivityResult (int requestCode, int resultCode, Intent intent) 
	{
		  if (resultCode != Activity.RESULT_OK || requestCode != CONTACT) return;
		  Cursor c = managedQuery(intent.getData(), null, null, null, null);
		  if (c.moveToFirst()) {
		     String phone = c.getString(c.getColumnIndexOrThrow(Contacts.Phones.NUMBER));
		     // yay
		  }
	}
}
