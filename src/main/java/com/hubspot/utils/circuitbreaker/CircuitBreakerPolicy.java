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

/**
 * Interface defining the transition between circuit breaker states.
 */
public interface CircuitBreakerPolicy {

	public enum CircuitBreakerState {
		CLOSED,    // working normally, calls are transparently passing through
		OPEN,      // method calls are being intercepted and CircuitBreakerExceptions are being thrown instead
		HALF_OPEN  // method calls are passing through; if another blacklisted exception is thrown, reverts back to OPEN
	}

	/**
	 * Invoked when a wrapped method is successfully invoked (i.e., no blacklisted exception is thrown)
	 */
	void successfulCall(Method m);
	
	/**
	 * Invoked when a blacklisted exception is thrown; this should determine whether the breaker transitions
	 * to OPEN
	 */
	void failedBlacklistedCall(Method m);
	
	/**
	 * Invoked when the caller needs to know whether to do a retry attempt
	 */
	boolean shouldAttemptReset();
	
	/**
	 * Gets the current breaker state
	 * @return
	 */
	CircuitBreakerState getCurrentState();
	
}
