/** Copyright 2011 HubSpot, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 **/

package com.hubspot.utils.circuitbreaker;

/**
 * Interface defining an object that notifies a set of handlers about events
 */
public interface Notifier<T> {
	/**
	 * Ensures that the supplied NotificationHandler will be notified when appropriate 
	 */
	void attachHandler(NotificationHandler<T> notifiable);
	
	/**
	 * Removes the specified object from the set of objects that will be notified
	 */
	void detachHandler(NotificationHandler<T> notifiable);
	
	/**
	 * Notifies a set of interested parties that a change occurred
	 */
	void notifyHandlers(T change);
}
