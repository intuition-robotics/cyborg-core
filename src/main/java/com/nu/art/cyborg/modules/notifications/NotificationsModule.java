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

package com.nu.art.cyborg.modules.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat.Builder;

import com.nu.art.core.exceptions.runtime.MUST_NeverHappenException;
import com.nu.art.cyborg.annotations.ModuleDescriptor;
import com.nu.art.cyborg.core.CyborgModule;
import com.nu.art.reflection.utils.GenericMap;

@ModuleDescriptor(usesPermissions = {})
public final class NotificationsModule
	extends CyborgModule
	implements NotificationKeys {

	private GenericMap<NotificationHandler> notificationHandlers = new GenericMap<>();

	private NotificationManager notificationManager;

	@Override
	protected void init() {
		notificationManager = getSystemService(NotificationService);
	}

	@Deprecated
	public final <Type extends NotificationHandler> void addNotificationHandler(Class<Type> handlerType) {
		logWarning("addNotificationHandler METHOD DOES NOTHING!!!");
	}

	@SuppressWarnings( {
		                   "unchecked",
		                   "SuspiciousMethodCalls"
	                   })
	public final <HandlerType extends NotificationHandler> HandlerType getNotificationHandler(Class<HandlerType> handlerType) {
		HandlerType notificationHandler = (HandlerType) notificationHandlers.get(handlerType);
		if (notificationHandler == null)
			notificationHandlers.put(handlerType, notificationHandler = createModuleItem(handlerType));

		return notificationHandler;
	}

	public final void disposeNotification(short notificationId) {
		notificationManager.cancel(notificationId);
	}

	final PendingIntent createPendingIntent(NotificationHandler notificationHandler, int notificationId, String action, Bundle notificationData, int flags) {
		Context context = cyborg.getApplicationContext();
		Intent intent = new Intent(context, NotificationReceiver.class);
		intent.putExtra(ExtraKey_Id, notificationId);
		intent.putExtra(ExtraKey_DataBundle, notificationData);
		intent.putExtra(ExtraKey_HandlerType, notificationHandler.getClass().getName());
		intent.putExtra(ExtraKey_Action, action);
		return PendingIntent.getBroadcast(context, CyborgModule.getNextRandomPositiveShort(), intent, flags);
	}

	@SuppressWarnings("unchecked")
	<HandlerType extends NotificationHandler> void processNotification(Intent intent) {
		String handlerTypeClassName = intent.getStringExtra(ExtraKey_HandlerType);
		HandlerType notificationHandler;
		try {
			Class<HandlerType> handlerType = (Class<HandlerType>) Class.forName(handlerTypeClassName);
			logInfo("Received notification event of type: " + handlerType);
			notificationHandler = getNotificationHandler(handlerType);
		} catch (Exception e) {
			MUST_NeverHappenException exception = new MUST_NeverHappenException("Cannot find class type: " + handlerTypeClassName, e);
			if (isDebug())
				throw exception;

			logError(exception);
			return;
		}

		try {
			Bundle bundle = intent.getBundleExtra(ExtraKey_DataBundle);
			int notificationId = intent.getIntExtra(ExtraKey_Id, -1);
			String action = intent.getStringExtra(ExtraKey_Action);
			notificationHandler.processNotification((short) notificationId, action, bundle);
		} catch (Exception e) {
			logError("Error processing notification event", e);
		}
	}

	final Notification postNotification(Builder builder, int notificationId) {
		Notification notification;
		if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN)
			notification = builder.build();
		else
			notification = builder.getNotification();

		notificationManager.notify(notificationId, notification);
		return notification;
	}
}
