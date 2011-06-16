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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.List;

import com.hubspot.utils.HubSpotObject;

/**
 * Basic implementation of a CircuitBreakerPolicy. It takes a "# of trips" threshold and 
 * a threshold window in seconds. When the failures per window interval is exceeded, 
 * the policy moves the breaker to the OPEN state. It also takes a halfOpenTimeout which
 * determines when to attempt a retry on the wrapped resource.
 * 
 */
public class BaseCircuitBreakerPolicyImpl extends HubSpotObject implements CircuitBreakerPolicy, Notifier<StateChange>{

	// interval in seconds after which the breaker will move to HALF_OPEN
	protected int halfOpenTimeout;
	
	// number of failures per thresholdWindow that will cause the circuit breaker to trip
	protected int tripThreshold;
	
	// interval in seconds over which we compute our failure rate
	protected int thresholdWindow;
	
	// tracks when the circuit breaker tripped so we can compute when to 
	// move to HALF_OPEN after halfOpenTimeout seconds have elapsed
	protected Date trippedTimestamp; 
	
	// collection of failure timestamps used to compute our failure rate
	protected Deque<Date> failures = new ArrayDeque<Date>(); 
	
	// current state of the circuit breaker
	protected CircuitBreakerState currentState = CircuitBreakerState.CLOSED;
	
	// list of parties interested in receiving state change notifications
	List<NotificationHandler<StateChange>> notificationChain = new ArrayList<NotificationHandler<StateChange>>();
	
	/**
	 * Constructor
	 * 
	 * @param halfOpenTimeout: Determines when the circuit breaker will attempt a 
	 * retry on the wrapped resource and potentially move back to the CLOSED state
	 * 
	 * @param tripThreshold: Determines how many blacklisted exceptions will move the
	 * breaker to the OPEN state.
	 * 
	 * @param thresholdWindow: Window over which to count failures (in seconds); i.e, 5 failures per
	 * 600 seconds will trip the breaker
	 * 
	 * @throws CircuitBreakerWrappingException
	 */
	public BaseCircuitBreakerPolicyImpl(int tripThreshold,
										int halfOpenTimeout,
										int thresholdWindow,
										List<NotificationHandler<StateChange>> notificationChain) throws CircuitBreakerWrappingException {
		// parameter check
		if( tripThreshold <= 0 ) {
			throw new CircuitBreakerWrappingException("Invalid trip threshold.");
		}
		
		if( halfOpenTimeout <= 0 ) {
			throw new CircuitBreakerWrappingException("Invalid half-open circuit breaker timeout.");
		}
		
		if (thresholdWindow <= 0) {
			throw new CircuitBreakerWrappingException("Invalid reset timeout");
		}
		
		this.tripThreshold = tripThreshold;
		this.halfOpenTimeout = halfOpenTimeout;
		this.thresholdWindow = thresholdWindow;
		if( notificationChain != null ) {
			this.notificationChain = notificationChain;
		}
	}
	
	/**
	 * Constructor
	 * @param tripThreshold
	 * @param halfOpenTimeout
	 * @param thresholdWindow
	 * @throws CircuitBreakerWrappingException
	 */
	public BaseCircuitBreakerPolicyImpl(int tripThreshold,
			int halfOpenTimeout,
			int thresholdWindow) throws CircuitBreakerWrappingException {
		this(tripThreshold, halfOpenTimeout, thresholdWindow, null);
	}
	
	protected BaseCircuitBreakerPolicyImpl() {}

	/**
	 * Moves the breaker to a CLOSED state
	 */
	@Override
	public synchronized void successfulCall(Method m) {
		if( currentState != CircuitBreakerState.CLOSED && m != null) {
			getLog().info("Circuit breaker moving to CLOSED from "+currentState+" due to successful invocation of "+m.getDeclaringClass().getName()+"."+m.getName());
			notifyHandlers(new StateChange(currentState, CircuitBreakerState.CLOSED, m));
		}
		currentState = CircuitBreakerState.CLOSED;	
	}
	
	/**
	 * Determines if we should move to an OPEN state from a CLOSED
	 * state after a failed *blacklisted* exception thrown by our
	 * wrapped method.
	 */
	@Override
	public synchronized void failedBlacklistedCall(Method m) {
		failedBlacklistedCall(new Date(), m);
	}
	
	public void failedBlacklistedCall(Date timestamp, Method m) {
		// add the latest failure timestamp to our list of failures
		failures.push(timestamp);

		// pop off failures that have exited our threshold window
		Calendar c = Calendar.getInstance();
		c.add(Calendar.SECOND, -thresholdWindow);
		Date cutoffThreshold = c.getTime();
		
		while(failures.size() > 0 && failures.peekLast().before(cutoffThreshold)) {
			failures.pollLast();
		}
		
		// if we have any more failures in the window, check if we're over
		// the trip threshold
		if( failures.size() >= tripThreshold && 
				(currentState == CircuitBreakerState.CLOSED ||
				 currentState == CircuitBreakerState.HALF_OPEN) )
		{
			if (m != null) {
				getLog().info("Circuit breaker moving to OPEN from "+currentState+" due to failed call of "+m.getDeclaringClass().getName()+"."+m.getName());
			}
			notifyHandlers(new StateChange(currentState, CircuitBreakerState.OPEN, m));
			currentState = CircuitBreakerState.OPEN;
			trippedTimestamp = new Date();
		} 
	}

	/**
	 * Determines if the circuit breaker should return to an CLOSED state from
	 * OPEN. 
	 * 
	 * This implementation simply looks at the time elapsed since the
	 * breaker tripped and compares it to the halfOpenTimeout.
	 */
	@Override
	public synchronized boolean shouldAttemptReset() {
		return shouldAttemptReset(new Date());
	}
	
	public boolean shouldAttemptReset(Date timestamp) {
		if (currentState != CircuitBreakerState.OPEN)
			return false;
		
		// figure out if we're past the reset timeout and
		// possibly move our state to HALF_OPEN
		Calendar c = Calendar.getInstance();
		c.setTime(trippedTimestamp);
		c.add(Calendar.SECOND, halfOpenTimeout);
	
		if(timestamp.after(c.getTime())) {
			notifyHandlers(new StateChange(currentState, CircuitBreakerState.HALF_OPEN, null));
			currentState = CircuitBreakerState.HALF_OPEN;
			return true;
		} 
		return false;
	}

	/**
	 * Returns the current state of the breaker
	 */
	@Override
	public CircuitBreakerState getCurrentState() {
		return currentState;
	}

	/**
	 * Adds the supplied object to the list of objects to be 
	 * notified on a state change.
	 */
	@Override
	public void attachHandler(NotificationHandler<StateChange> n) {
		if( !notificationChain.contains(n)) {
			notificationChain.add(n);
		}
	}

	/**
	 * Removes a handler from the list of entities to be notified on
	 * a state change
	 */
	@Override
	public void detachHandler(NotificationHandler<StateChange> n) {
		notificationChain.remove(n);
	}

	/**
	 * Notifies all entities in the notification chain of a state change
	 */
	@Override
	public void notifyHandlers(StateChange change) {
		for( NotificationHandler<StateChange> handler : notificationChain ) {
			try {
				handler.onChanged(change);
			} catch( Exception e ) {
				getLog().error("Error while notifying of circuit breaker state change", e);
			}
		}
	}
}
