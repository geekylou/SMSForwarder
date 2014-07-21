package uk.me.geekylou.SMSForwarder;

public class InboxEntry {
	String sender,message;
	int    id;
	
	public String toString()
	{
		return sender + "\n" + message;
	}
}
