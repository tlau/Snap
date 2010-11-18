package org.cygx1.snap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.cygx1.snap.SnapPreferences;
import org.cygx1.snap.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Toast;

/**
 * Snap: take a snapshot and send it to someone with just one tap
 * 
 * @author tlau
 * 
 * Camera bugs:
 * - http://stackoverflow.com/questions/1910608/android-action-image-capture-intent
 * - http://code.google.com/p/android/issues/detail?id=1480
 * 
 * Sending email without user interaction using JavaMail:
 * - http://nilvec.com/sending-email-without-user-interaction-in-android/
 * 
 * TODO:
 * send mail from a separate thread so it doesn't block the UI
 * detect camera orientation and tag the JPEG with the right orientation
 * make sure camera preview works on all devices
 * prompt for preferences on startup if they're not set
 * delete the image after sending
 */

public class Snap extends Activity {
	private static final String TAG = "Snap";
	private static final int PREFS_ID = 0;
	private Preview mPreview;
	int mRequestCode = 1;
	static File outputFile = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// If we haven't set our preferences yet, start with that view
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String email = prefs.getString(getString(R.string.emailPref), null);
        if (email == null) {
        	startActivity(new Intent(this, SnapPreferences.class));
        	return;
        }

		// TL: the code below tries to invoke the built-in camera app
		// It doesn't exactly work

		if (false) {
			// Launch the camera activity
			Intent i = new Intent(
					android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

			// Figure out where to put the tmp file
			File snapDirectory = new File("/sdcard/cygx1/snap/");
			// have the object build the directory structure, if needed.
			snapDirectory.mkdirs();
			// create a File object for the output file
			outputFile = new File(snapDirectory, String.format("p%d.jpg",
					System.currentTimeMillis()));
			i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri
					.fromFile(outputFile));
			// On my N1, this line causes nothing to happen when you tap the
			// Camera's OK button to finish
			// i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
			// android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

			startActivityForResult(i, mRequestCode);
		}

		// TL: the code below uses our own Camera Activity, which probably works
		// better on more devices, but doesn't have as many amenities as the
		// built-in Camera app
		
		// Declare the layout to be fullscreen with no title bar
		getWindow().setFormat(PixelFormat.TRANSLUCENT);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new Preview(this);
		setContentView(mPreview);

		// Set up a tap handler
		mPreview.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(getApplicationContext(), "Taking snapshot...",
						Toast.LENGTH_SHORT).show();
				mPreview.mCamera.takePicture(null, null, jpegCallback);
			}
		});

	}

	// Callback when the camera activity finishes
	// Currently not used; this goes with the code above to launch the built-in camera activity
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (requestCode == mRequestCode) {
			Log.d(TAG, "Activity result, sending file!");
			// Retrieve the image from the static filename we defined earlier ... there
			// should be a better way to pass this information
			sendIt(outputFile);
			Log.d(TAG, "Finished sending!");
			this.finish();
		}
	}

	/**
	 * Callback with jpeg data taken from camera
	 */
	PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			try {
				// create a File object for the parent directory
				File snapDirectory = new File("/sdcard/cygx1/snap/");
				// have the object build the directory structure, if needed.
				snapDirectory.mkdirs();
				// create a File object for the output file
				final File outputFile = new File(snapDirectory, String.format(
						"p%d.jpg", System.currentTimeMillis()));
				// now attach the OutputStream to the file object, instead of a
				// String representation
				FileOutputStream fos = new FileOutputStream(outputFile);
				fos.write(data);
				fos.close();

				Toast.makeText(getApplicationContext(), "Snapshot saved",
						Toast.LENGTH_SHORT).show();

				//	Send the picture - asynchronously
				new Thread() {
					public void run() {
						sendIt(outputFile);
					}
				}.start();

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
			}
			Log.d(TAG, "onPictureTaken - jpeg");

			Snap.this.finish();
		}
	};

	/**
	 *  Send a file attachment via email
	 * @param outputFile - the File containing the attachment to send
	 */
	public void sendIt(File outputFile) {
		// Now send it via email
		
		// Get my email address out of preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String email = prefs.getString(getString(R.string.emailPref), null);
        String password = prefs.getString(getString(R.string.passwordPref), null);
        String recipient = prefs.getString(getString(R.string.recipientPref), null);
        String subject = prefs.getString(getString(R.string.subjectPref), null);

		Mail m = new Mail(email, password);
		String[] toArr = { recipient };
		m.setTo(toArr);
		m.setFrom(email);
		m.setSubject(subject);
		m.setBody("");
		try {
			m.addAttachment(outputFile.getAbsolutePath());
			if (m.send()) {
				Toast.makeText(getApplicationContext(),
						"Email sent successfully", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(getApplicationContext(), "Error sending email",
						Toast.LENGTH_SHORT).show();
			}
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), "Error attaching photo",
					Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Handle the preferences menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, PREFS_ID, Menu.NONE, "Prefs")
				.setIcon(android.R.drawable.ic_menu_preferences)
				.setAlphabeticShortcut('p');
		return (super.onCreateOptionsMenu(menu));
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case PREFS_ID:
			startActivity(new Intent(this, SnapPreferences.class));
			return (true);
		}
		return (super.onOptionsItemSelected(item));
	}

}