package cz.brmlab.brmgps;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

public class GPSService extends Service {
	private static final String TAG = "brmGPSService";
	private ScheduledExecutorService scheduler;
	private Thread listenerThread;
	private ServerSocket serverSocket;
	private Socket clientSocket;
	private boolean shouldRun;
	private SensorManager sensorMgr;
	private LocationManager locationMgr; 
	private PowerManager powerMgr;
	private PowerManager.WakeLock wl;

	
    private float[] mValues;
    private Location lastKnownLocation;

	
    private final SensorListener mListener = new SensorListener() {

        public void onSensorChanged(int sensor, float[] values) {
            //Log.d(TAG, "sensorChanged (" + values[0] + ", " + values[1] + ", " + values[2] + ")");
            mValues = values;
        }

        public void onAccuracyChanged(int sensor, int accuracy) {
            // TODO Auto-generated method stub

        }
    };
    
    private final LocationListener lListener = new LocationListener() {

		@Override
		public void onLocationChanged(Location location) {
			lastKnownLocation = location;
			Log.d(TAG, "locationChanged (" + location + ")");
		}

		@Override
		public void onProviderDisabled(String provider) {
			lastKnownLocation= null;
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
    	
    };

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		Toast.makeText(this, "GPS Service Created", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onCreate");
		
        sensorMgr = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

		
		scheduler = Executors.newScheduledThreadPool(1);
	
		listenerThread = new Thread(new Runnable(){
			@Override
			public void run() {
				try {
					do {
						Socket s = serverSocket.accept();
						OutputStream o = s.getOutputStream();
						o.write("brmgps\n".getBytes());
						o.flush();
						if (clientSocket != null) {
							try {
								clientSocket.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						clientSocket = s;
					}while(shouldRun);
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
		});
	}

	@Override
	public void onDestroy() {
		Toast.makeText(this, "GPS Service Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		shouldRun = false;
		listenerThread.interrupt();
		sensorMgr.unregisterListener(mListener);
		locationMgr.removeUpdates(lListener);
		if (wl != null) {
			wl.release();
			wl = null;
		}
		if (serverSocket != null) {
			try {
				serverSocket.close();
				
			} catch (IOException e) {
				e.printStackTrace();
			}finally {
				serverSocket = null;
			}
		}
		if (clientSocket != null) {
			try {
				clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				clientSocket = null;
			}
		}

	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		Log.d(TAG, "onStart");
		if (!shouldRun) {
			shouldRun = true;
			locationMgr = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			List<String> providers = locationMgr.getAllProviders();
			Log.d(TAG, "Location Providers:");
			for (String provider : providers) {
				Log.d(TAG, provider);
			}

			sensorMgr.registerListener(mListener, SensorManager.SENSOR_ORIENTATION, SensorManager.SENSOR_DELAY_GAME);
			locationMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1l, 1l, lListener);
			
			powerMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wl = powerMgr.newWakeLock(PowerManager.FULL_WAKE_LOCK, "brmGPS");
			wl.acquire();
			try {
				serverSocket = new ServerSocket(5000);
				Toast.makeText(this, "GPS Service Started " + serverSocket.getLocalSocketAddress(), Toast.LENGTH_LONG).show();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			listenerThread.start();
			scheduler.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					if (clientSocket != null) {
						try {
							String line ="";
							OutputStream os = clientSocket.getOutputStream();
							if (mValues != null) {
								line +="COMPAS:" + mValues[0] +"|";
							}

							if (lastKnownLocation != null) {
								double lat = lastKnownLocation.getLatitude();
								double lon = lastKnownLocation.getLongitude();
								double alt = lastKnownLocation.getAltitude();
								float speed = lastKnownLocation.getSpeed();
								float acc = lastKnownLocation.getAccuracy();
								line+="GPS:" + lat + "|" + lon + "|" + alt + "|" + speed + "|" + acc;    
							}else{
								line+="GPS:MISSINGINFO";
							}
							line+="\n";
							Log.d(TAG, line);
							os.write(line.getBytes());
							os.flush();
						} catch (Exception e) {
							e.printStackTrace();
							clientSocket = null;
						}
					}
				}
			}, 0, 1000, TimeUnit.MILLISECONDS);
			
		}else{
			Toast.makeText(this, "GPS Service already started", Toast.LENGTH_LONG).show();
		}
	}
	
	
	
	
}