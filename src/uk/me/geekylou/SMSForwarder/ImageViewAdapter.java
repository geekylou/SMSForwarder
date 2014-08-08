package uk.me.geekylou.SMSForwarder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

class ImageViewAdapter extends ArrayAdapter<InboxEntry>
{
	private static LayoutInflater inflater=null;
	private int mTextViewResourceId;
	private boolean mThreadView;

	
	
	public ImageViewAdapter(Context context, int textViewResourceId,boolean threadView) {
		super(context, textViewResourceId);		
		mThreadView         = threadView;
		mTextViewResourceId = textViewResourceId;
		inflater            = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	public View getView(int position, View convertView, ViewGroup parent) {
		View vi=convertView;
        if(convertView==null)
            vi = inflater.inflate(mTextViewResourceId, null);
        
        InboxEntry entry = this.getItem(position);
        
        TextView textBody=(TextView)vi.findViewById(R.id.textViewBody);
        TextView textFooter=(TextView)vi.findViewById(R.id.textViewFooter);
        TextView textDate=(TextView)vi.findViewById(R.id.textViewDate);
        ImageView imageViewIcon = (ImageView)vi.findViewById(R.id.imageViewIcon);
        
        if (textBody != null) textBody.setText(entry.sender);
        
        if (entry.type == ProtocolHandler.SMS_MESSAGE_TYPE_RESPONSE_SENT)
        {
            textFooter.setText("Me:"+entry.message);
            if (!mThreadView)
            {
            	android.view.ViewGroup.LayoutParams frame = imageViewIcon.getLayoutParams();
            	frame.width = 0;
            	frame.height = 0;
            	imageViewIcon.setLayoutParams(frame);
            }
        }
        else
        {
            textFooter.setText(entry.message);
        }
        textDate.setText(entry.date.toLocaleString());        
       	imageViewIcon.setImageBitmap(entry.bitmap);        	

        return vi;
	}
}