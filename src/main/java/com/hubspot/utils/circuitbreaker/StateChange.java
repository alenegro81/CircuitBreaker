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

import java.lang.reflect.Method;

import com.hubspot.utils.HubSpotObject;
import com.hubspot.utils.circuitbreaker.CircuitBreakerPolicy.CircuitBreakerState;

/**
 * Simple object passed to circuitbreaker notification handlers when a state change occurs
 *
 */
public class StateChange extends HubSpotObject {
	
	private CircuitBreakerState newState;
	private CircuitBreakerState oldState;
	private Method method;
	
	public StateChange(CircuitBreakerState oldState,
			CircuitBreakerState newState, Method method) {
		this.oldState = oldState;
		this.newState = newState;
		this.method = method;
	}
	
	public CircuitBreakerState getNewState() {
		return newState;
	}
	
	public CircuitBreakerState getOldState() {
		return oldState;
	}
	
	public Method getMethod() {
		return method;
	}

}
