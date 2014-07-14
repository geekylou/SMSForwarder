package uk.me.geekylou.SMSForwarder;

import uk.me.geekylou.SMSForwarder.R;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class MainActivity extends Activity {

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
		setContentView(R.layout.activity_main);

		mBluetoothService = new Intent(this,BluetoothInterfaceService.class);
		mProtocolHandler  = new ProtocolHandler(this,0x104);
		
		txtSock = (TextView)findViewById(R.id.textView1);
		txtGPS = (TextView)findViewById(R.id.textView2);
		txtLOC = (TextView)findViewById(R.id.textView3);
		
        prefs = getSharedPreferences("BluetoothPreferences", MODE_PRIVATE);

		txtLOC.setText(prefs.getString("BT_ID", "")+":"+prefs.getString("BT_NAME",""));
		
		
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
            }
        });
		Button but4 = (Button)findViewById(R.id.button4);
		but4.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	mProtocolHandler.sendSMSMessage(MainActivity.this, 0x100, "Siobhán Keane", "This is a test sms message triggered using a button");
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
	}

	void StartLocation()
	{	
		if (receiver == null)
		{
			CheckBox checkConnect = (CheckBox)findViewById(R.id.checkBoxConnect);
			mBluetoothService.putExtra("CONNECT", checkConnect.isChecked());
			mBluetoothService.putExtra("BT_ID", prefs.getString("BT_ID", ""));
			
			IntentFilter filter = new IntentFilter(BluetoothInterfaceService.PACKET_RECEIVED);
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
		public static final String ACTION_RESP =
			      "uk.me.geekylou.GPSTest.MESSAGE_PROCESSED";
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
		       text = intent.getStringExtra("SOCK");
		       if (text != null) txtSock.setText(text);
		       
		    }
		}
}
