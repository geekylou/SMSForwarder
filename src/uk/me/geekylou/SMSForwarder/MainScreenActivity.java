package uk.me.geekylou.SMSForwarder;

import uk.me.geekylou.SMSForwarder.InboxActivity.InboxProtocolHandler;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class MainScreenActivity extends FragmentActivity {
	private ProtocolHandler mProtocolHandler = new ProtocolHandler(this,0x104);;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		Intent intent = getIntent();
		
		/* Start service with a request for the inbox on the peer.*/
        Intent bluetoothService = new Intent(this,BluetoothInterfaceService.class);
		
		String search = intent.getStringExtra("search");
		if(search == null) search="";
		
		InboxFragment detailFragment;
	
		detailFragment = (InboxFragment) (getFragmentManager().findFragmentById(R.id.detailFragment));
		
		detailFragment.setSender("Emily (Flat)");
		
    	mProtocolHandler.populateSMSMessageIntent(bluetoothService,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, search, "",0);
    	/* cludge to make TCPIP Service work.*/
    	mProtocolHandler.sendSMSMessage(this,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_REQUEST,0, search, "",0);
	
 		startService(bluetoothService);
		
 		
	}
}
