package uk.me.geekylou.SMSForwarder;

import java.util.Date;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class TimelineFragment extends InboxFragment
{	 
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
            
        	ProtocolHandler mProtocolHandler = new ProtocolHandler(ctx,0x104);
			mProtocolHandler.sendSMSMessage(ctx,0x100,ProtocolHandler.SMS_MESSAGE_TYPE_SEND,0, getItem(0).senderRaw,  message,new Date().getTime());

            }
        });
    }
    
    void setMessageCache(MessageCache messages,String Sender,boolean threadView)
    {
    	super.setMessageCache(messages, Sender, threadView);
    	
        TextView textViewSender = (TextView)getView().findViewById(R.id.textViewSender);
        textViewSender.setText(Sender);
    }
    
}