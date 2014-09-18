package com.bustr.activities;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Random;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bustr.R;
import com.bustr.utilities.BustrMath;
import com.bustr.utilities.CameraPreview;
import com.bustr.utilities.ImagePacket;
import com.bustr.utilities.ResourceProvider;

public class CameraActivity extends Activity implements LocationListener {

   private final static String LOGTAG = "BUSTR";
   private SharedPreferences sharedPrefs;
   private SharedPreferences.Editor prefEditor;
   private int cam;

   private Camera mCamera;
   private CameraPreview mPreview;
   private ShutterCallback shutterCallback;
   private PictureCallback pictureCallbackJPG;
   private LocationManager locationManager;

   // GUI elements -------------------------------------------------------------
   private TextView lat_long_view;
   private Button btn_snap;
   private Button btn_flip;
   private Button btn_keep;
   private Button btn_discard;

   @TargetApi(Build.VERSION_CODES.GINGERBREAD)
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_camera);

      sharedPrefs = PreferenceManager
            .getDefaultSharedPreferences(getBaseContext());
      prefEditor = sharedPrefs.edit();
      cam = sharedPrefs.getInt("camera", Camera.CameraInfo.CAMERA_FACING_BACK);

      locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
            0, this);

      // Wire GUI elements -----------------------------------------------------
      Typeface tf = ResourceProvider.instance(getApplicationContext())
            .getFont();
      lat_long_view = (TextView) findViewById(R.id.lat_long_view);
      btn_snap = (Button) findViewById(R.id.btn_snap);
      btn_flip = (Button) findViewById(R.id.btn_flip);
      btn_keep = (Button) findViewById(R.id.btn_keep);
      btn_discard = (Button) findViewById(R.id.btn_discard);
      btn_snap.setTypeface(tf);
      btn_keep.setTypeface(tf);
      btn_discard.setTypeface(tf);

      // Camera callback -------------------------------------------------------
      shutterCallback = new ShutterCallback() {
         @Override
         public void onShutter() {
            Toast.makeText(getBaseContext(), "CLICK", Toast.LENGTH_SHORT)
                  .show();
         }
      };

      pictureCallbackJPG = new PictureCallback() {
         @Override
         public void onPictureTaken(final byte[] bytes, Camera cam) {

            btn_keep.setVisibility(View.VISIBLE);
            btn_discard.setVisibility(View.VISIBLE);
            btn_snap.setVisibility(View.GONE);
            Location loc = locationManager
                  .getLastKnownLocation(locationManager.GPS_PROVIDER);
            final float lat = BustrMath.gridDimension(loc.getLatitude());
            final float lng = BustrMath.gridDimension(loc.getLongitude());
            OnClickListener listener = new OnClickListener() {

               @Override
               public void onClick(View v) {
                  if (v.getId() == R.id.btn_keep) {
                     Thread thread = new Thread() {
                        @Override
                        public void run() {
                           try {
                              Socket socket = new Socket(
                                    InetAddress.getByName("50.173.32.127"),
                                    8000);
                              ObjectOutputStream output = new ObjectOutputStream(
                                    socket.getOutputStream());
                              ObjectInputStream input = new ObjectInputStream(
                                    socket.getInputStream());
                              Random rand = new Random();
                              String randomName = Long
                                    .toString(rand.nextLong()) + ".jpg";
                              output.writeObject(new ImagePacket(randomName,
                                    bytes, lat, lng));
                           } catch (Exception e) {
                              e.printStackTrace();
                           }
                        }
                     };
                     thread.start();
                     finish();
                  } else if (v.getId() == R.id.btn_discard) {
                     btn_keep.setVisibility(View.GONE);
                     btn_discard.setVisibility(View.GONE);
                     btn_snap.setVisibility(View.VISIBLE);
                     mCamera.startPreview();
                  }

               }
            };
            btn_keep.setOnClickListener(listener);
            btn_discard.setOnClickListener(listener);
            // Bitmap bitmapPicture = BitmapFactory.decodeByteArray(bytes, 0,
            // bytes.length);

         }
      };

      // Setup snap button -----------------------------------------------------
      btn_snap.setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View v) {
            mCamera.takePicture(shutterCallback, null, pictureCallbackJPG);
         }
      });

      btn_flip.setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View v) {
            switchCamera();
         }
      });

   }

   public void switchCamera() {
      // mPreview.stopEverything();
      if (cam == Camera.CameraInfo.CAMERA_FACING_BACK) {
         prefEditor.putInt("camera", Camera.CameraInfo.CAMERA_FACING_FRONT)
               .commit();
      } else {
         prefEditor.putInt("camera", Camera.CameraInfo.CAMERA_FACING_BACK)
               .commit();
      }
      startActivity(new Intent(this, CameraActivity.class));
      finish();
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {

      // Inflate the menu; this adds items to the action bar if it is present.
      getMenuInflater().inflate(R.menu.camera, menu);
      return true;
   }

   @Override
   protected void onStop() {
      super.onStop();
      // mCamera.release();
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      // Handle action bar item clicks here. The action bar will
      // automatically handle clicks on the Home/Up button, so long
      // as you specify a parent activity in AndroidManifest.xml.
      int id = item.getItemId();
      if (id == R.id.action_settings) {
         return true;
      }
      return super.onOptionsItemSelected(item);
   }

   public static Camera getCameraInstance() {
      Camera c = null;
      try {
         c = Camera.open();
      } catch (Exception e) {
         Log.d(LOGTAG, e.toString());
      }
      return c;
   }

   @Override
   protected void onPause() {
      super.onPause();
      mPreview.stopEverything();
   }

   @Override
   protected void onResume() {
      super.onResume();
      mCamera = Camera.open(cam);
      mPreview = new CameraPreview(this, mCamera);
      FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
      CameraPreview.setCameraDisplayOrientation(this, cam, mCamera);
      preview.addView(mPreview);

   }

   @Override
   public void onLocationChanged(Location loc) {
      String lat_long = "lat: " + loc.getLatitude() + "\nlong: "
            + loc.getLongitude();
      Log.d(LOGTAG, lat_long);
      lat_long_view.setText(lat_long);
   }

   @Override
   public void onProviderDisabled(String arg0) {
      // TODO Auto-generated method stub

   }

   @Override
   public void onProviderEnabled(String arg0) {
      // TODO Auto-generated method stub

   }

   @Override
   public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      // TODO Auto-generated method stub

   }

}
