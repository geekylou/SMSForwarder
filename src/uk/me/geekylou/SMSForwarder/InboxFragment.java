package uk.me.geekylou.SMSForwarder;

import uk.me.geekylou.SMSForwarder.R;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.view.LayoutInflater;

public class InboxFragment extends Fragment {
	protected ListView mInboxEntriesView = null;
	protected int layout; 
	static final int REQUEST_NEW_ENTRY = 1000;
	String search;

	private TextView mStatusTextView;
	private String mSender;
	private MessageCache mMessages;
	private ResponseReceiver mResponseReceiver;
	protected boolean mThreadView;
	ArrayAdapter<InboxEntry> mInboxEntriesAdapter;
	
	public InboxFragment()
	{
	}
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.inbox_fragment, container, false);
    }
	
	
    /** Called when the activity is first created. */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
     
        Activity ctx = getActivity();
		layout = R.layout.thread_preview_item;
        
        //final Intent intent = ctx.getIntent();
        		
        /* Start listening for status doing anything else.*/
		IntentFilter filter = new IntentFilter(InterfaceBaseService.SERVICE_STATUS_UPDATE);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
    	mResponseReceiver = new ResponseReceiver();
    	ctx.registerReceiver(mResponseReceiver, filter);
 		
		mInboxEntriesView = (ListView) getView().findViewById(R.id.listView1);      		
    }
        
    public void onDestroy()
    {
        Activity ctx = getActivity();
        
    	ctx.unregisterReceiver(mResponseReceiver);
    	super.onDestroy();
    }
    
    void setMessageCache(MessageCache messages,String Sender,boolean threadView)
    {
    	mThreadView = threadView;
    	mSender     = Sender;
    	mMessages   = messages;
    	
    	Activity ctx = getActivity();

    	mInboxEntriesAdapter = mMessages.getTimeline(new ImageViewAdapter(ctx, layout, threadView), mSender,threadView);
	
		mInboxEntriesView.setAdapter(mInboxEntriesAdapter);
    }
    
    void setOnClickListener(AdapterView.OnItemClickListener onClickListener)
    {
		mInboxEntriesView.setOnItemClickListener(onClickListener);
    }
    
    InboxEntry getItem(int position)
    {
    	if (mInboxEntriesAdapter.getCount() > 0)
    	{
    		return mInboxEntriesAdapter.getItem(position);
    	}
    	else
    	{
    		return null;
    	}
    }
    
    void refreshEntries()
    {
    	Activity ctx = getActivity();
    	if (mMessages != null)
    	{
    		/*mInboxEntriesAdapter = mMessages.getTimeline(new ImageViewAdapter(ctx, layout, mThreadView), mSender,mThreadView);
    	
    		mInboxEntriesView.setAdapter(mInboxEntriesAdapter);*/
    		new BackgroundRefresh().execute(new ImageViewAdapter(ctx, layout, mThreadView));
    	}
    }
    
    private class BackgroundRefresh extends AsyncTask<ArrayAdapter<InboxEntry>, ArrayAdapter<InboxEntry>, ArrayAdapter<InboxEntry>> {

        @Override
        protected ArrayAdapter<InboxEntry> doInBackground(ArrayAdapter<InboxEntry>... adapter) {
        	return mMessages.getTimeline(adapter[0],mSender,mThreadView);
        }

        @Override
        protected void onPostExecute(ArrayAdapter<InboxEntry> values) {
    		mInboxEntriesView.setAdapter(mInboxEntriesAdapter);
        }
    }
    
    class ResponseReceiver extends BroadcastReceiver {
		ResponseReceiver()
		{
			super();
		}
	   @Override
	    public void onReceive(Context context, Intent intent) 
	    {
		   //String status = intent.getStringExtra("STATUS");
	       //if (status != null) mStatusTextView.setText(status);
	    }
	}
}