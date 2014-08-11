package uk.me.geekylou.SMSForwarder;

import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Contacts;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

public class NewContactTimelineFragment extends InboxFragment
{	
	private InboxEntry baseEntry;
	private OnClickListener mOnClickListener;
	
	public NewContactTimelineFragment()
	{
	}
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.new_contact_timeline, container, false);
    }
	
    /** Called when the activity is first created. */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
		layout = R.layout.text_preview_item;
        
        ImageButton buttonSend = (ImageButton) getView().findViewById(R.id.imageButtonSend);
        buttonSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

            TextView messageText = (TextView) getView().findViewById(R.id.multiAutoCompleteTextViewSMS);
            TextView addressTextView = (TextView) getView().findViewById(R.id.editTextAddress);
            
            Activity ctx = getActivity();
            String message = messageText.getText().toString();

        	ProtocolHandler mProtocolHandler = new ProtocolHandler(ctx,0x100);
        	
        	Intent bluetoothService = new Intent(ctx,BluetoothInterfaceService.class);
			mProtocolHandler.sendSMSMessage(ctx,new Intent(),0x100,ProtocolHandler.SMS_MESSAGE_TYPE_SEND,0, (String) addressTextView.getText().toString(),  message,new Date().getTime());
	    	
			InboxEntry entry     = new InboxEntry();
			
			if (baseEntry != null)
			{
				entry.bitmap    = baseEntry.bitmap;
				entry.sender    = baseEntry.sender;
			}
			
			entry.date      = new Date();
			entry.cacheId   = -1;
			entry.id        = -1;
			entry.message   = message;
			entry.senderRaw = addressTextView.getText().toString();
			entry.type      = ProtocolHandler.SMS_MESSAGE_TYPE_RESPONSE_SENT;
			mInboxEntriesAdapter.insert(entry, 0);
            }
        });
        
    	ImageButton buttonSearch = (ImageButton) getView().findViewById(R.id.imageButtonSearch);
        buttonSearch.setOnClickListener(mOnClickListener);

        mInboxEntriesAdapter = new ImageViewAdapter(getActivity(), layout, mThreadView);
		mInboxEntriesView.setAdapter(mInboxEntriesAdapter);
    }

    void setSearchOnClickListener(View.OnClickListener onClickListener)
    {
    	mOnClickListener = onClickListener;

    	View view = getView();
    	if (view == null) return;
    	
    	ImageButton buttonSearch = (ImageButton) view.findViewById(R.id.imageButtonSearch);
    	
    	if (buttonSearch == null) return;
    	
        buttonSearch.setOnClickListener(onClickListener);
        
    }
    
    void messageToNewContact(MessageCache messages,String sender,String number,boolean threadView)
    {
    	super.setMessageCache(messages, sender, threadView);

    	baseEntry = new InboxEntry();
    	
    	baseEntry.sender    = sender;
    	baseEntry.senderRaw = number;
    	baseEntry.bitmap    = messages.getContactBitmap(baseEntry);
    	
        TextView addressTextView = (TextView) getView().findViewById(R.id.editTextAddress);
        addressTextView.setText(number);	        
    }
    
    void setMessageCache(MessageCache messages,String Sender,boolean threadView)
    {
    	super.setMessageCache(messages, Sender, threadView);
    }
}