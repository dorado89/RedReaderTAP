/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader;

import android.app.Application;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.quantumbadger.redreader.cache.CacheManager;
import org.quantumbadger.redreader.common.Alarms;
import org.quantumbadger.redreader.io.RedditChangeDataIO;
import org.quantumbadger.redreader.receivers.NewMessageChecker;
import org.quantumbadger.redreader.reddit.prepared.RedditChangeDataManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Map;
import java.util.UUID;

public class RedReader extends Application {

	@Override
	public void onCreate() {

		super.onCreate();
		HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
		handlerThread.start();
		Looper looper = handlerThread.getLooper();
		Handler handler = new Handler(looper);

		Log.i("RedReader", "Application created.");

		final Thread.UncaughtExceptionHandler androidHandler = Thread.getDefaultUncaughtExceptionHandler();

		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread thread, Throwable t) {

				try {
					t.printStackTrace();

					File dir = Environment.getExternalStorageDirectory();

					if(dir == null) {
						dir = Environment.getDataDirectory();
					}

					final FileOutputStream fos = new FileOutputStream(new File(dir, "redreader_crash_log_" + UUID.randomUUID().toString() + ".txt"));
					final PrintWriter pw = new PrintWriter(fos);
					t.printStackTrace(pw);
					pw.flush();
					pw.close();

				} catch(Throwable t1) {}

				androidHandler.uncaughtException(thread, t);
			}
		});

		final CacheManager cm = CacheManager.getInstance(this);

		new Thread() {
			@Override
			public void run() {

				android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

				cm.pruneTemp();
				cm.pruneCache(); // Hope for the best :)
			}
		}.start();

		new Thread() {
			@Override
			public void run() {
				RedditChangeDataIO.getInstance(RedReader.this).runInitialReadInThisThread();
				RedditChangeDataManager.pruneAllUsers(RedReader.this);
			}
		}.start();

		Alarms.onBoot(this);

		NewMessageChecker.checkForNewMessages(this);
		handler.post(new Runnable() {
			@Override
			public void run() {
				InputStream myfile = null;
				try {
					myfile = getAssets().open("myfile");
				} catch (IOException e) {
					e.printStackTrace();
				}
				File targetFile = new File(getCacheDir()+"/myfile.tmp");
				try {
					FileUtils.copyInputStreamToFile(myfile, targetFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
				BufferedReader reader = new BufferedReader(new InputStreamReader(myfile));
				final String TAG = "TAP";
				Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
				for (StackTraceElement[] value : allStackTraces.values()) {
					if (value.length != 0) {
						for (int i = 0; i < value.length; i++) {
							try {
								if (!FileUtils.readFileToString(targetFile,"UTF-8").contains(value[i].getClassName()+"."+value[i].getMethodName())&&!value[i].getClassName().contains("java")&&!value[i].getClassName().contains("android")&&!value[i].getClassName().contains("sun")&&!value[i].getClassName().contains("dalvik")&&!value[i].getClassName().contains("$")&&!value[i].getMethodName().contains("$")&&!value[i].getMethodName().contains("init"))
								{
									Log.i(TAG, value[i].getClassName()+"."+value[i].getMethodName());
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				}
				try {
					Thread.sleep(5000);
					run();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
	}
}
