package de.ahlfeld.bluetoothexample2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String TAG = "bluetooth2";

	
	private static final int BLUETOOTH_INTENT_CODE = 2;
	
	/**
	 * The Buttons
	 */
	Button btnOn;
	Button btnOff;

	/**
	 * Text view to display stuff from the arduino
	 */
	TextView txtArduino;
	
	/**
	 * We need this object to send messages between to different threads.
	 */
	Handler mHandler;

	/**
	 * Status for handler
	 */
	final int RECIEVE_MESSAGE = 1;
	/*
	 * Bluetooth adapter
	 */
	private BluetoothAdapter btAdapter = null;
	/**
	 * Bluetooth socket.
	 */
	private BluetoothSocket btSocket = null;
	/**
	 * Use a stringbuilder for performance.
	 */
	private StringBuilder sb = new StringBuilder();

	/**
	 * Thread which handles the connections.
	 */
	private ConnectedThread mConnectedThread;

	/**
	 *  SPP UUID servic. Hint: If you are connecting to a Bluetooth serial board 
	 *  then try using the well-known SPP UUID 00001101-0000-1000-8000-00805F9B34FB. 
	 *  However if you are connecting to an Android peer then please generate your own unique UUID.
	 */
	private static final UUID MY_UUID = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// Change this adress to your device mac
	private static String ADDRESS = "00:12:05:08:80:60";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		/**
		 * Init views
		 */
		btnOn = (Button) findViewById(R.id.btnOn);
		btnOff = (Button) findViewById(R.id.btnOff);
		txtArduino = (TextView) findViewById(R.id.txtArduino);

		/**
		 * Create a handler for updating the ui by incomming bytes.
		 */
		mHandler = new Handler() {
			public void handleMessage(android.os.Message msg) {
				switch (msg.what) {
				case RECIEVE_MESSAGE: // if receive massage
					byte[] readBuf = (byte[]) msg.obj;
					String strIncom = new String(readBuf, 0, msg.arg1); //create string from bytes
					sb.append(strIncom); // append string
					int endOfLineIndex = sb.indexOf("\r\n"); // determine the end-of-line
					if (endOfLineIndex > 0) { // if end-of-line,
						String sbprint = sb.substring(0, endOfLineIndex); // extract string
						sb.delete(0, sb.length()); // and clear
						txtArduino.setText("Data from Arduino: " + sbprint); // update TextView
						btnOff.setEnabled(true);
						btnOn.setEnabled(true);
					}
					Log.d(TAG, "...String:" + sb.toString() + "Byte:"
							+ msg.arg1 + "...");
					break;
				}
			};
		};

		/**
		 * get the bluetooth adapter from the device.
		 */
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		/**
		 * Check the bluetooth state.
		 */
		checkBTState();

		/**
		 * Set up onClick listener for buttons.
		 */
		btnOn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				btnOn.setEnabled(false);
				mConnectedThread.write("1"); // Send "1" via Bluetooth
				// Toast.makeText(getBaseContext(), "Turn on LED",
				// Toast.LENGTH_SHORT).show();
			}
		});

		btnOff.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				btnOff.setEnabled(false);
				mConnectedThread.write("0"); // Send "0" via Bluetooth
				// Toast.makeText(getBaseContext(), "Turn off LED",
				// Toast.LENGTH_SHORT).show();
			}
		});
	}

	/**
	 * Create a bluetoothsocket from a given device
	 * 
	 * @param device
	 * @return Returns a bluetooth socket..
	 * @throws IOException
	 */
	private BluetoothSocket createBluetoothSocket(BluetoothDevice device)
			throws IOException {
		/**
		 * Devices is running on honeycomb (API level 10) or higher?
		 * We can use createInsecureRfcommSocketToServiceRecord
		 */
		if (Build.VERSION.SDK_INT >= 10) {
			try {
				final Method m = device.getClass().getMethod(
						"createInsecureRfcommSocketToServiceRecord",
						new Class[] { UUID.class });
				return (BluetoothSocket) m.invoke(device, MY_UUID);
			} catch (Exception e) {
				Log.e(TAG, "Could not create Insecure RFComm Connection", e);
			}
		}
		/**
		 * Device is running with an android lower honeycomb
		 */
		return device.createRfcommSocketToServiceRecord(MY_UUID);
	}

	@Override
	public void onResume() {
		super.onResume();

		Log.d(TAG, "...onResume - try connect...");

		BluetoothDevice device = btAdapter.getRemoteDevice(ADDRESS);

		/**
		 * We need to things. the MACADRESS of the device. And the UUID for our service.
		 */

		try {
			btSocket = createBluetoothSocket(device);
		} catch (final IOException e) {
			Log.e(TAG, e.getMessage());
			exitWithErrorMessage("Fatal Error", "In onResume() and socket create failed: "
					+ e.getMessage() + ".");
		}

		//Cancel discovery because it need to much ressources
		btAdapter.cancelDiscovery();

		// Establish the connection. This will block until it connects.
		Log.d(TAG, "...Connecting...");
		try {
			btSocket.connect();
			Log.d(TAG, "....Connection ok...");
		} catch (final IOException e) {
			Log.e(TAG, e.getLocalizedMessage());
			try {
				btSocket.close();
			} catch (final IOException e2) {
				Log.e(TAG, e2.getMessage());
				exitWithErrorMessage("Fatal Error",
						"In onResume() and unable to close socket during connection failure"
								+ e2.getMessage() + ".");
			}
		}

		// Create a data stream so we can talk to server.
		Log.d(TAG, "...Create Socket...");
		if (btSocket != null) {
			mConnectedThread = new ConnectedThread(btSocket);
			mConnectedThread.start();
		}
	}

	/**
	 * Try to close the btSocket. We don't need it anymore
	 */
	@Override
	public void onPause() {
		super.onPause();

		Log.d(TAG, "...In onPause()...");

		try {
			btSocket.close();
		} catch (final IOException e2) {
			Log.e(TAG, e2.getMessage());
			exitWithErrorMessage("Fatal Error", "In onPause() and failed to close socket."
					+ e2.getMessage() + ".");
		}
	}

	/**
	 * Check for Bluetooth support and then check to make sure it is turned
	 */
	private void checkBTState() {
		if (btAdapter == null) {
			Log.e(TAG, "Bluetooth adapter is null");
			exitWithErrorMessage("Fatal Error", "Bluetooth not support");
		} else {
			if (btAdapter.isEnabled()) {
				Log.d(TAG, "...Bluetooth ON...");
			} else {
				// If bluetooth is not enabled start the activity
				final Intent enableBtIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, BLUETOOTH_INTENT_CODE);
			}
		}
	}

	/**
	 * Make a toast for the with a title and a message.
	 * @param title The title for the toast.
	 * @param message The message for the toast.
	 */
	private void exitWithErrorMessage(String title, String message) {
		Toast.makeText(getBaseContext(), title + " - " + message,
				Toast.LENGTH_LONG).show();
		finish();
	}

	/**
	 * Thread which handles the data connection.
	 * 
	 * @author Björn
	 * 
	 */
	private class ConnectedThread extends Thread {
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				if (socket != null) {
					
					tmpIn = socket.getInputStream();
					tmpOut = socket.getOutputStream();
				}
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
			
		}

		public void run() {
			byte[] buffer = new byte[256]; // buffer store for the stream
			int bytes; // bytes returned from read()

			//While true, for listening on incomming bytes.
			while (true) {
				try {
					// Read from the InputStream
					if (mmInStream != null) {
						bytes = mmInStream.read(buffer); // Get number of bytes and message in "buffer"
						Log.d(TAG, "Received " + bytes + " bytes");
						//Send message to handler, which will handle the ui update.
						mHandler.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer)
								.sendToTarget(); 
					} else {
						Log.w(TAG, "Stream is null");
					}
				} catch (final IOException e) {
					break;
				}
			}
		}

		
		/**
		 * Methode to write a message in the output stream. Call this methode from the activity / ui thread
		 * @param message
		 */
		public void write(final String message) {
			Log.d(TAG, "...Data to send: " + message + "...");
			byte[] msgBuffer = message.getBytes();
			try {
				mmOutStream.write(msgBuffer);
			} catch (IOException e) {
				Log.d(TAG, "...Error data send: " + e.getMessage() + "...");
			}
		}
	}

}
