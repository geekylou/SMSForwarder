package uk.me.geekylou.SMSForwarder;

import android.bluetooth.BluetoothDevice;

public class BluetoothChooserEntry {
	BluetoothDevice device;
	
	public String toString()
	{
		return device.getName() + "\n" + device.getAddress();
	}
}
