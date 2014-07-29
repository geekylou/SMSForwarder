package uk.me.geekylou.SMSForwarder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

class ImageViewAdapter extends ArrayAdapter<InboxEntry>
{
	private static LayoutInflater inflater=null;
	
	public ImageViewAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);		
		
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	public View getView(int position, View convertView, ViewGroup parent) {
		View vi=convertView;
        if(convertView==null)
            vi = inflater.inflate(R.layout.text_preview_item, null);
        
        InboxEntry entry = this.getItem(position);
        
        TextView textBody=(TextView)vi.findViewById(R.id.textViewBody);
        TextView textFooter=(TextView)vi.findViewById(R.id.textViewFooter);
        TextView textDate=(TextView)vi.findViewById(R.id.textViewDate);
        ImageView imageViewIcon = (ImageView)vi.findViewById(R.id.imageViewIcon);
        
        textBody.setText(entry.sender);
        if (entry.type == ProtocolHandler.SMS_MESSAGE_TYPE_RESPONSE_SENT)
        {
            textFooter.setText("Me:"+entry.message);        	
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