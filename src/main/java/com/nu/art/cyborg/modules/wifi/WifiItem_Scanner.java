/*
 * cyborg-core is an extendable  module based framework for Android.
 *
 * Copyright (C) 2018  Adam van der Kruk aka TacB0sS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nu.art.cyborg.modules.wifi;

import android.Manifest.permission;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.support.annotation.Nullable;

import com.nu.art.core.generics.Processor;
import com.nu.art.core.tools.ArrayTools;
import com.nu.art.cyborg.core.CyborgModuleItem;
import com.nu.art.cyborg.core.CyborgReceiver;
import com.nu.art.cyborg.errorMessages.ExceptionGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by TacB0sS on 12/07/2017.
 */

@SuppressWarnings("MissingPermission")
public class WifiItem_Scanner
	extends CyborgModuleItem {

	public interface OnWifiUIListener {

		void onScanCompleted();
	}

	public enum WifiSecurityMode {
		WEP,
		PSK,
		EAP,
		OPEN,
		//
		;

		static WifiSecurityMode getSecurityMode(String capabilities) {
			for (WifiSecurityMode mode : values()) {
				if (capabilities.contains(mode.name()))
					return mode;
			}

			return OPEN;
		}
	}

	public enum WifiStrength {
		VeryWeak,
		Weak,
		Medium,
		Strong,
		VeryStrong,
		//
		;
	}

	public enum Frequency {
		_5GHZ,
		_2_4GHZ,
		//
		;
	}

	public static class ScannedWifiInfo {

		public WifiStrength strength;

		public ScanResult scanResult;

		public Frequency frequency;

		public final String getName() {
			return scanResult.SSID;
		}

		public final String getBssid() { return scanResult.BSSID; }

		public WifiSecurityMode getSecurity() {
			return WifiSecurityMode.getSecurityMode(scanResult.capabilities);
		}
	}

	private final ArrayList<ScannedWifiInfo> scanResults = new ArrayList<>();

	private boolean scanning;

	private WifiManager wifiManager;

	@Override
	protected void init() {
		wifiManager = getSystemService(WifiService);
	}

	private void startScan() {
		try {
			wifiManager.startScan();
		} catch (SecurityException e) {
			throw ExceptionGenerator.missingPermissionsToPerformAction("Start Wifi Network scan", permission.CHANGE_WIFI_STATE, e);
		} catch (Exception e) {
			logError("Low level Android error when trying to start scanning for wifi... will not SCAN", e);
		}
	}

	boolean hasAccessPoint(String wifiName) {
		return getAccessPoint(wifiName) != null;
	}

	ScannedWifiInfo getAccessPoint(String wifiName, Frequency frequency) {
		logDebug("getAccessPoint for "+wifiName+" frequency "+frequency);
		ScannedWifiInfo resultNotMatchingFrequency = null;
		synchronized (scanResults) {
			for (ScannedWifiInfo scanResult : scanResults) {
				if (scanResult.getName().equals(wifiName)) {
					resultNotMatchingFrequency = scanResult;
					if (frequency == null) {
						return resultNotMatchingFrequency;
					} else if (scanResult.frequency == frequency) {
						return scanResult;
					}
				}
			}
		}
		return resultNotMatchingFrequency;
	}

	ScannedWifiInfo getAccessPoint(String wifiName) {
		return getAccessPoint(wifiName, null);
	}

	@Nullable
	WifiStrength getAccessPointStrength(String wifiName) {
		ScannedWifiInfo accessPoint = getAccessPoint(wifiName);
		if (accessPoint == null)
			return null;

		return accessPoint.strength;
	}

	void onScanCompleted() {
		logInfo("On wifi scan completed");

		List<ScanResult> results = wifiManager.getScanResults();
		HashMap<String, ScannedWifiInfo> scannedWifis = new HashMap<>();

		WifiStrength[] values = WifiStrength.values();
		for (ScanResult result : results) {
			if (result.SSID.trim().length() == 0)
				continue;

			ScannedWifiInfo scannedWifi = new ScannedWifiInfo();
			scannedWifi.frequency = getFrequency(result);
			scannedWifi.strength = values[WifiManager.calculateSignalLevel(result.level, values.length)];
			scannedWifi.scanResult = result;
			scannedWifis.put(result.SSID + "_"+ scannedWifi.frequency.name(), scannedWifi);
		}

		synchronized (scanResults) {
			scanResults.clear();
			scanResults.addAll(scannedWifis.values());
			Collections.sort(scanResults, new Comparator<ScannedWifiInfo>() {
				@Override
				public int compare(ScannedWifiInfo o1, ScannedWifiInfo o2) {
					if (o1.strength.ordinal() == o2.strength.ordinal())
						return 0;

					return o1.strength.ordinal() < o2.strength.ordinal() ? 1 : -1;
				}
			});
		}

		dispatchGlobalEvent("Wifi Scan Completed", OnWifiUIListener.class, new Processor<OnWifiUIListener>() {
			@Override
			public void process(OnWifiUIListener listener) {
				listener.onScanCompleted();
			}
		});
	}

	private Frequency getFrequency(ScanResult result) {
		if (result.frequency > 4900)
			return Frequency._5GHZ;

		return Frequency._2_4GHZ;
	}

	public ScannedWifiInfo[] getScanResults() {
		return ArrayTools.asArray(scanResults, ScannedWifiInfo.class);
	}

	public boolean isScanning() {
		return scanning;
	}

	void enable(boolean enable) {
		if (scanning != enable) {
			if (enable) {
				cyborg.registerReceiver(WifiNetworksReceiver.class, WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
			} else
				cyborg.unregisterReceiver(WifiNetworksReceiver.class);

			scanning = enable;
		}

		if (enable)
			startScan();
	}

	public static class WifiNetworksReceiver
		extends CyborgReceiver<WifiModule> {

		protected WifiNetworksReceiver() {
			super(WifiModule.class);
		}

		@Override
		protected void onReceive(Intent intent, WifiModule module) {
			String action = intent.getAction();
			if (action == null)
				return;

			switch (action) {
				case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
					module.onScanCompleted();
					break;
			}
		}
	}
}
