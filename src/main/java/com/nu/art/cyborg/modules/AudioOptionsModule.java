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

package com.nu.art.cyborg.modules;

import android.annotation.TargetApi;
import android.content.Intent;
import android.media.AudioManager;

import com.nu.art.core.generics.Processor;
import com.nu.art.core.interfaces.Condition;
import com.nu.art.cyborg.core.CyborgModule;
import com.nu.art.cyborg.core.CyborgReceiver;
import com.nu.art.reflection.tools.ReflectiveTools;

import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static android.media.AudioManager.STREAM_ALARM;
import static android.media.AudioManager.STREAM_DTMF;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_SYSTEM;
import static android.media.AudioManager.STREAM_VOICE_CALL;

/**
 * Created by matankoby on 2/4/18.
 */

public class AudioOptionsModule
	extends CyborgModule {

	public interface OnAudioOptionsChanged {

		void onDeviceVolumeChanged();

		void onStreamMuteChanged();
	}

	private AudioManager audioManager;

	@Override
	protected void init() {
		audioManager = cyborg.getSystemService(AudioService);
	}

	public void registerForVolumeChanges() {
		registerReceiver(AudioSettingsReceiver.class);
	}

	public void unregisterForVolumeChanges() {
		unregisterReceiver(AudioSettingsReceiver.class);
	}

	private int _getStreamVolume(int streamId) {
		return audioManager.getStreamVolume(streamId);
	}

	private void _setStreamVolume(int streamId, int volume, int flags) {
		audioManager.setStreamVolume(streamId, volume, flags);
	}

	/**
	 * Ringer mode is the device's global Volume/Vibrate/Silent mode.
	 */
	public void setRingerMode(MuteState wantedState) {
		audioManager.setRingerMode(wantedState.audioManagerConstant);
	}

	/**
	 * @return One of MuteState's 3 enum values.
	 */
	public MuteState getRingerMode() {
		return MuteState.getTypeByValue(audioManager.getRingerMode());
	}

	/**
	 * Each sound stream might have different max volume.
	 */
	public int getStreamMaxVolume(int streamId) {
		return audioManager.getStreamMaxVolume(streamId);
	}

	public int getStreamVolume(AudioStreamType stream) {
		int volume = _getStreamVolume(stream.streamId);
		logInfo("AudioStream Get volume: " + stream.name() + " <= " + volume);
		return volume;
	}

	public int getStreamVolume(int streamId) {
		int volume = audioManager.getStreamVolume(streamId);
		logInfo("AudioStream Get volume: " + streamId + " <= " + volume);
		return volume;
	}

	public void setStreamVolume(int streamId, int volume) {
		setStreamVolume(streamId, volume, 0);
	}

	public void setStreamVolume(int streamId, int volume, int flags) {
		audioManager.setStreamVolume(streamId, volume, flags);
		logInfo("AudioStream Set volume: " + streamId + " " + _getStreamVolume(streamId));
	}

	public void setStreamVolume(AudioStreamType stream, int volume) {
		setStreamVolume(stream, volume, 0);
	}

	public void setStreamVolume(AudioStreamType stream, int volume, int flags) {
		audioManager.setStreamVolume(stream.streamId, volume, flags);
		logInfo("AudioStream Set volume: " + stream + " " + _getStreamVolume(stream.streamId));
	}

	public void muteStream(int streamId, boolean toMute) {
		audioManager.adjustStreamVolume(streamId, toMute ? android.media.AudioManager.ADJUST_MUTE : android.media.AudioManager.ADJUST_UNMUTE, 0);
	}

	@TargetApi(23)
	public boolean isStreamMute(int streamId) {
		return audioManager.isStreamMute(streamId);
	}

	/**
	 * The device's global Ringer state, Volume, Vibrate-only or Silent.
	 */
	public enum MuteState {
		Volume(RINGER_MODE_NORMAL),
		Vibrate(RINGER_MODE_VIBRATE),
		Silent(RINGER_MODE_SILENT);

		public final int audioManagerConstant;

		MuteState(int audioManagerConstant) {
			this.audioManagerConstant = audioManagerConstant;
		}

		public static MuteState getTypeByValue(final int audioManagerConstant) {
			return ReflectiveTools.findMatchingEnumItem(MuteState.class, new Condition<MuteState>() {
				@Override
				public boolean checkCondition(MuteState item) {
					return item.audioManagerConstant == audioManagerConstant;
				}
			});
		}
	}

	public enum AudioStreamType {
		VoiceCall(STREAM_VOICE_CALL),
		System(STREAM_SYSTEM),
		Ring(STREAM_RING),
		Music(STREAM_MUSIC),
		Alarm(STREAM_ALARM),
		Notification(STREAM_NOTIFICATION),
		DTMF(STREAM_DTMF);

		public final int streamId;

		AudioStreamType(int streamId) {
			this.streamId = streamId;
		}

		public static AudioStreamType getTypeByValue(final int streamId) {
			return ReflectiveTools.findMatchingEnumItem(AudioStreamType.class, new Condition<AudioStreamType>() {
				@Override
				public boolean checkCondition(AudioStreamType item) {
					return item.streamId == streamId;
				}
			});
		}

	}

	public static final class AudioSettingsReceiver
		extends CyborgReceiver<AudioOptionsModule> {

		public static final String IntentAction__VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
		public static final String IntentAction__STREAM_MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION";

		private static final String[] INTENT_ACTIONS = {
			IntentAction__VOLUME_CHANGED,
			IntentAction__STREAM_MUTE_CHANGED
		};

		private AudioSettingsReceiver() {
			super(AudioOptionsModule.class, INTENT_ACTIONS);
		}

		@Override
		protected void onReceive(Intent intent, AudioOptionsModule module) {
			if (intent.getAction() == null)
				return;

			switch (intent.getAction()) {
				case IntentAction__VOLUME_CHANGED:
					module.onVolumeChanged();
					break;

				case IntentAction__STREAM_MUTE_CHANGED:
					module.onStreamMuteChanged();
					break;
			}
		}
	}

	private void onVolumeChanged() {
		dispatchEvent("Device volume changed", OnAudioOptionsChanged.class, new Processor<OnAudioOptionsChanged>() {
			@Override
			public void process(OnAudioOptionsChanged listener) {
				listener.onDeviceVolumeChanged();
			}
		});
	}

	private void onStreamMuteChanged() {
		dispatchEvent("Stream mute changed", OnAudioOptionsChanged.class, new Processor<OnAudioOptionsChanged>() {
			@Override
			public void process(OnAudioOptionsChanged listener) {
				listener.onStreamMuteChanged();
			}
		});
	}
}
