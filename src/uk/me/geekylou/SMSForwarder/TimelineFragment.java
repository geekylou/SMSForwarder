package uk.me.geekylou.SMSForwarder;

import java.util.Date;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

public class TimelineFragment extends InboxFragment
{	
	private InboxEntry baseEntry;
	
	public TimelineFragment()
	{
		
	}
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.timeline, container, false);
    }
	
    /** Called when the activity is first created. */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        Button buttonSend = (Button) getView().findViewById(R.id.buttonSend);
        buttonSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

            TextView messageText = (TextView) getView().findViewById(R.id.multiAutoCompleteTextViewSMS);
            Activity ctx = getActivity();
            String message = messageText.getText().toString();

            Spinner spinner = (Spinner) getView().findViewById(R.id.spinner1);
            
        	ProtocolHandler mProtocolHandler = new ProtocolHandler(ctx,0x100);
        	
			mProtocolHandler.sendSMSMessage(ctx,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_SEND,0, (String) spinner.getSelectedItem(),  message,new Date().getTime());

			//InboxEntry baseEntry = getItem(0); /* TODO this is a horrible hack and currently ignores the spinner entry. */
			InboxEntry entry     = new InboxEntry();
			
			entry.bitmap    = baseEntry.bitmap;
			entry.date      = new Date();
			entry.cacheId   = -1;
			entry.id        = -1;
			entry.message   = message;
			entry.sender    = baseEntry.sender;
			entry.senderRaw = baseEntry.senderRaw;
			entry.type      = ProtocolHandler.SMS_MESSAGE_TYPE_RESPONSE_SENT;
			mInboxEntriesAdapter.insert(entry, 0);
            }
        });
    }

    void messageToNewContact(MessageCache messages,String sender,String number,boolean threadView)
    {
    	super.setMessageCache(messages, sender, threadView);

    	InboxEntry baseEntry = new InboxEntry();
    	
    	baseEntry.sender = sender;
    	baseEntry.senderRaw = number;

        TextView textViewSender = (TextView)getView().findViewById(R.id.textViewSender);
        textViewSender.setText(sender);

        Spinner spinner = (Spinner) getView().findViewById(R.id.spinner1);
        
        ArrayAdapter<String> array = messages.getContactNos(sender);
        
        spinner.setAdapter(array);
        
        int count = array.getCount();
                
        for (int index=0;index<count;index++)
        {
        	if (PhoneNumberUtils.compare(array.getItem(index),baseEntry.senderRaw))
        	{
        		spinner.setSelection(index);
        	}
        }

    }
    
    void setMessageCache(MessageCache messages,String Sender,boolean threadView)
    {
    	super.setMessageCache(messages, Sender, threadView);
    	
        TextView textViewSender = (TextView)getView().findViewById(R.id.textViewSender);
        textViewSender.setText(Sender);
        
        Spinner spinner = (Spinner) getView().findViewById(R.id.spinner1);
        
        ArrayAdapter<String> array = messages.getContactNos(Sender);
        
        spinner.setAdapter(array);
        
        int count = array.getCount();
        
		baseEntry = getItem(0); /* TODO this is a horrible hack and currently ignores the spinner entry. */
        
        for (int index=0;index<count;index++)
        {
        	if (PhoneNumberUtils.compare(array.getItem(index),baseEntry.senderRaw))
        	{
        		spinner.setSelection(index);
        	}
        }
    }
}