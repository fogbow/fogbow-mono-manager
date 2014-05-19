package org.fogbowcloud.manager.core;

import java.util.Timer;
import java.util.TimerTask;

public class ManagerTimer {

	private Timer timer;
	private boolean scheduled;

	public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
		timer = new Timer();
		timer.scheduleAtFixedRate(task, delay, period);
		scheduled = true;
	}

	public void cancel() {
		if (timer != null) {
			timer.cancel();
		}
		scheduled = false;
	}

	public boolean isScheduled() {
		return scheduled;
	}

}
