package uk.me.geekylou.SMSForwarder;

import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

public class TimelineFragment extends InboxFragment
{	
	private InboxEntry baseEntry;
	private boolean    fieldsSet = false;
	
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
		layout = R.layout.text_preview_item;
        
        ImageButton buttonSend = (ImageButton) getView().findViewById(R.id.imageButtonSend);
        buttonSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

            TextView messageText = (TextView) getView().findViewById(R.id.multiAutoCompleteTextViewSMS);
            Activity ctx = getActivity();
            String message = messageText.getText().toString();

            Spinner spinner = (Spinner) getView().findViewById(R.id.spinner1);
            
        	ProtocolHandler mProtocolHandler = new ProtocolHandler(ctx,0x100);
        	
        	mMessages.sendMessage(mProtocolHandler,(String) spinner.getSelectedItem(),  message);
			
        	//mProtocolHandler.sendSMSMessage(ctx,new Intent(),0x100,ProtocolHandler.SMS_MESSAGE_TYPE_SEND,0, (String) spinner.getSelectedItem(),  message,new Date().getTime());
	    	
			InboxEntry entry     = new InboxEntry();
			
			entry.bitmap    = baseEntry.bitmap;
			entry.date      = new Date();
			entry.cacheId   = -1;
			entry.id        = -1;
			entry.message   = message;
			entry.sender    = baseEntry.sender;
			entry.senderRaw = (String) spinner.getSelectedItem();
			entry.type      = ProtocolHandler.SMS_MESSAGE_TYPE_RESPONSE_SENT;
			mInboxEntriesAdapter.insert(entry, 0);
            }
        });
        
		refreshEntries();
    }

    // Now handled by NewContactTimeLineFragment.
/*    void messageToNewContact(MessageCache messages,String sender,String number,boolean threadView)
    {
    	super.setMessageCache(messages, sender, threadView);

    	baseEntry = new InboxEntry();
    	
    	baseEntry.sender    = sender;
    	baseEntry.senderRaw = number;
    	baseEntry.bitmap    = messages.getContactBitmap(baseEntry);
    	
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
    }*/
    
    void setMessageCache(MessageCache messages,String Sender,boolean threadView)
    {
    	super.setMessageCache(messages, Sender, threadView);
    	
    	if (Sender != null)
    	{
    		// Check if the fragment is loaded and view is available before setting the UI so we don't crash!
    		if (setFields())
    		{
    			refreshEntries();
    		}
    	}
    }
    
    protected void refreshEntriesDone()
    {
		setFields();    	
    }
    
    private boolean setFields()
    {
    	View view = getView();
    	
    	if (view == null)
    	{
    		fieldsSet = true;
    		return false;
    	}
        TextView textViewSender = (TextView)view.findViewById(R.id.textViewSender);
        
        if (textViewSender == null) // The fragment is not rendered yet so leave this until it is loaded.
        {
        	fieldsSet = true;
        	return false;
        }
        
        textViewSender.setText(mSender);
        
        Spinner spinner = (Spinner) getView().findViewById(R.id.spinner1);
        if (spinner == null)
        {
        	fieldsSet = true;
        	return false;
        }

        if (mMessages == null) {fieldsSet=true; return false;}
        ArrayAdapter<String> array = mMessages.getContactNos(mSender);
        
        spinner.setAdapter(array);
        
        int count = array.getCount();
        
		InboxEntry entry = getItem(0);
		if (entry != null)
		{
			baseEntry = entry;
		}
		else
		{
			fieldsSet=true;
			return true;
		}
        
        for (int index=0;index<count;index++)
        {
        	if (PhoneNumberUtils.compare(array.getItem(index),baseEntry.senderRaw))
        	{
        		spinner.setSelection(index);
        	}
        }
    	fieldsSet = false;
    	return true;
    }
}