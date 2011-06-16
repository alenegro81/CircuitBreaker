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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import junit.framework.TestCase;
import com.hubspot.utils.circuitbreaker.CircuitBreakerPolicy.CircuitBreakerState;

/**
 * Unit tests basic circuit breaker behavior
 */
public class CircuitBreakerInvocationHandlerTest extends TestCase {
	
	static interface MockInvocationTestInterface {
		@CircuitBreakerExceptionBlacklist(blacklist={NullPointerException.class})
		void breakerMethod(String s) throws CircuitBreakerException, NullPointerException;
		String nonBreakerMethod(String s);	
	}
	
	static interface AnotherMockInvocationTestInterface extends MockInvocationTestInterface {
		@CircuitBreakerExceptionBlacklist(blacklist={NullPointerException.class})
		void anotherBreakerMethod(String s) throws CircuitBreakerException, NullPointerException;
	}
	
	/**
	 * Basic object that will NPE for the first breakUntil invocations
	 * of the breakerMethod(), subsequent calls will succeed
	 */
	static class MockInvocationTestImpl implements AnotherMockInvocationTestInterface {
		
		int breakUntil = 0;
		int failureCnt = 0;
		
		public MockInvocationTestImpl(int breakUntil) {
			this.breakUntil = breakUntil;
		}
		
		@Override
		public void breakerMethod(String s) throws CircuitBreakerException,
				NullPointerException {
			failureCnt++;
			if( failureCnt < breakUntil ) {
				throw new NullPointerException();
			}
		}

		@Override
		public String nonBreakerMethod(String s) {
			return s;
		}

		@Override
		public void anotherBreakerMethod(String s)
				throws CircuitBreakerException, NullPointerException {
			throw new NullPointerException();
			
		}
	}
	
	static interface BadBreakerInterface {

		@CircuitBreakerExceptionBlacklist(blacklist={NullPointerException.class})
		void badBreakerMethod(String s) throws NullPointerException;
	}
	
	static class BadBreakerImpl implements BadBreakerInterface {

		@Override
		public void badBreakerMethod(String s) throws NullPointerException {
			
		} 
		
	}
	
	/**
	 * Mock dumb circuit breaker policy
	 */
	static class ZeroTimeoutPolicy implements CircuitBreakerPolicy {
		CircuitBreakerState state;
		
		@Override
		public void failedBlacklistedCall(Method m) {
			state = CircuitBreakerState.OPEN;
		}

		@Override
		public CircuitBreakerState getCurrentState() {
			return state;
		}

		@Override
		public boolean shouldAttemptReset() {
			return state == CircuitBreakerState.HALF_OPEN;
		}

		@Override
		public void successfulCall(Method m) {
			state = CircuitBreakerState.CLOSED;
		}
		
		public void setState(CircuitBreakerState state){
			this.state = state;
		}

		
	}
	
	static class MockNotificationHandler implements NotificationHandler<StateChange> {

		private StateChange lastEvent;
		
		@Override
		public void onChanged(StateChange event) {
			lastEvent = event;
		}
		
		public StateChange getLastEvent() {
			return lastEvent;
		}
	}
	

	/**
	 * Test the happy path for wrapped object invocations
	 */
	public void testBasicInvocations()  {
		MockInvocationTestInterface mi = new MockInvocationTestImpl(3);
		CircuitBreakerWrapper cbw = CircuitBreakerWrapper.getCircuitBreakerWrapperInstance();
		ZeroTimeoutPolicy policy = null;
		try {
			policy = new ZeroTimeoutPolicy();
			mi = cbw.wrap( mi, MockInvocationTestInterface.class, policy );
		} catch (CircuitBreakerWrappingException e) {
			fail();
		}
		
		// make sure non-breaker methods go through ok
		assertEquals(mi.nonBreakerMethod("HELLO"), "HELLO");
		assertEquals(mi.nonBreakerMethod("HELLO"), "HELLO");
		
		// initial call of breaker method with this policy should NPE
		try {
			mi.breakerMethod("HELLO");
		} catch( NullPointerException e) {
			// ok
		} catch ( CircuitBreakerException e) {
			fail("Circuit breaker should not be tripped at this point");
		}
		
		// subsequent call should be circuit-broken and throw CBE
		try {
			mi.breakerMethod("HELLO");
		} catch( NullPointerException e) {
			fail("Circuit breaker SHOULD be tripped at this point");
		} catch( CircuitBreakerException e) {
			// ok
		}
		
		// manually move to a half-open state (simulating a timeout in a BaseCircuitBreakerPolicy for instance)
		// and make sure we get another NPE
		policy.setState(CircuitBreakerState.HALF_OPEN);
		try {
			mi.breakerMethod("HELLO");
		} catch( NullPointerException e) {
			// ok
		} catch( CircuitBreakerException e) {
			fail("Circuit breaker should not be tripped at this point");
		}
		
		// finally, move to CLOSED and make sure we get an NPE
		policy.setState(CircuitBreakerState.CLOSED);
		try {
			mi.breakerMethod("HELLO");
		} catch( NullPointerException e) {
			// ok
		} catch( CircuitBreakerException e) {
			fail("Circuit breaker should not be tripped at this point");
		}
	}
	
	/** 
	 * Makes sure that an interface that violates our circuit breaker
	 * restrictions fails to wrap
	 */
	public void testInvocationOfImproperBlacklist() {
		BadBreakerInterface mi = new BadBreakerImpl();
		CircuitBreakerWrapper cbw = CircuitBreakerWrapper.getCircuitBreakerWrapperInstance();
		ZeroTimeoutPolicy policy = null;
		try {
			policy = new ZeroTimeoutPolicy();
			mi = cbw.wrap( mi, BadBreakerInterface.class, policy );
		} catch (CircuitBreakerWrappingException e) {
			// ok 
			return;
		}
		
		fail("Interface should not have been wrapped");
	}
	
	/**
	 * Tests that inherited interface methods that contain a blacklist trip the 
	 * breaker properly
	 */
	public void testInheritedInterfaceBlacklist() throws Exception {
		AnotherMockInvocationTestInterface obj = new MockInvocationTestImpl(100);
		CircuitBreakerWrapper cbw = CircuitBreakerWrapper.getCircuitBreakerWrapperInstance();
		ZeroTimeoutPolicy policy = null;
		try {
			policy = new ZeroTimeoutPolicy();
			obj = cbw.wrap( obj, AnotherMockInvocationTestInterface.class, policy );
			obj.anotherBreakerMethod("hello");
		} catch (NullPointerException e) {
			// ok
		} catch (CircuitBreakerException e) {
			fail("Circuit breaker should not be tripped at this point");
		}
		
		try {
			obj.anotherBreakerMethod("hello");
		} catch (NullPointerException e) {
			fail("Circuit breaker SHOULD be tripped at this point");
		} catch (CircuitBreakerException e) {
			//ok
		}
		
		policy.setState(CircuitBreakerState.CLOSED);
		try {
			obj.breakerMethod("hello");
		} catch (NullPointerException e) {
			// ok
		} catch (CircuitBreakerException e) {
			fail("Circuit breaker should not be tripped at this point");
		}
		
		try {
			obj.breakerMethod("hello");
		} catch (NullPointerException e) {
			fail("Circuit breaker SHOULD be tripped at this point");
		} catch (CircuitBreakerException e) {
			//ok
		}
	}
	
	/**
	 * Tests that when the circuit breaker is tripped for blacklisted methods,
	 * it still allows non-blacklisted methods to succeed (i.e., a tripped database connection
	 * should still allow .toString() to be called on itself)
	 */
	public void testNonBlacklistMethodsSucceeed() throws Exception {
		AnotherMockInvocationTestInterface obj = new MockInvocationTestImpl(100);
		CircuitBreakerWrapper cbw = CircuitBreakerWrapper.getCircuitBreakerWrapperInstance();
		ZeroTimeoutPolicy policy = null;
		try {
			policy = new ZeroTimeoutPolicy();
			obj = cbw.wrap( obj, AnotherMockInvocationTestInterface.class, policy );
			obj.anotherBreakerMethod("hello");
		} catch (NullPointerException e) {
			// ok
		} catch (CircuitBreakerException e) {
			fail();
		}
		
		try {
			obj.anotherBreakerMethod("hello");
		} catch (NullPointerException e) {
			fail();
		} catch (CircuitBreakerException e) {
			// ok
		}
		
		obj.toString();
		obj.hashCode();
		
		try {
			obj.anotherBreakerMethod("hello");
		} catch (NullPointerException e) {
			fail();
		} catch (CircuitBreakerException e) {
			// ok
		}
	}
	
	/**
	 * Tests the threshold window by simulating fake blacklisted calls outside of the 
	 * threshold window; i.e., if the trip threshold is 1 fail per 10 seconds, simulate
	 * a set of failures 11 seconds ago (outside of the failure window) and make sure they don't trip us.
	 */
	public void testPolicyIntervalWindowForFailures() throws Exception {
		int thresholdWindow = 10;
		int deltaOutsideWindow = -(thresholdWindow + 1);
		int deltaInsideWindow = -(thresholdWindow - 1);
		
		BaseCircuitBreakerPolicyImpl p = new BaseCircuitBreakerPolicyImpl(2, 600, thresholdWindow);
		assertEquals(p.getCurrentState(), CircuitBreakerState.CLOSED);
		
		// simulate failed blacklist calls hitting OUTSIDE of the threshold window
		// and ensure we don't trip
		Calendar c = Calendar.getInstance();
		c.add(Calendar.SECOND, deltaOutsideWindow);
		for( int i = 0; i < 100; ++i )
			p.failedBlacklistedCall(c.getTime(), null);
		
		assertEquals(p.getCurrentState(), CircuitBreakerState.CLOSED);
		
		// now simulate failed blacklist calls in the window and make sure we trip
		// after our failure threshold is hit
		c = Calendar.getInstance();
		c.add(Calendar.SECOND, deltaInsideWindow);
		p.failedBlacklistedCall(c.getTime(), null);
		assertEquals(p.getCurrentState(), CircuitBreakerState.CLOSED);
		
		p.failedBlacklistedCall(c.getTime(), null);
		assertEquals(p.getCurrentState(), CircuitBreakerState.OPEN);
	}
	
	/**
	 * Tests that the Basic policy moves to HALF_OPEN when appropriate
	 */
	public void testPolicyIntervalWindowForReset() throws Exception {
		int retryTimeout = 10;
		int deltaOutsideTimeout = retryTimeout + 1;
		int deltaInsideTimeout = retryTimeout - 1;
		
		BaseCircuitBreakerPolicyImpl p = new BaseCircuitBreakerPolicyImpl(1, retryTimeout, retryTimeout);
		assertEquals(p.getCurrentState(), CircuitBreakerState.CLOSED);
		try {
			p.failedBlacklistedCall(null);
		} catch(Exception e){}

		assertEquals(p.getCurrentState(), CircuitBreakerState.OPEN);
		
		// try to move to HALF_OPEN when we're still inside the retry timeout
		// we should remain OPEN
		Calendar c = Calendar.getInstance();
		c.add(Calendar.SECOND, deltaInsideTimeout);
		p.shouldAttemptReset(c.getTime());
		assertEquals(p.getCurrentState(), CircuitBreakerState.OPEN);
		
		// try again, this time with a timestamp outside the retry timeout
		// we should move to HALF_OPEN
		c = Calendar.getInstance();
		c.add(Calendar.SECOND, deltaOutsideTimeout);
		p.shouldAttemptReset(c.getTime());
		assertEquals(p.getCurrentState(), CircuitBreakerState.HALF_OPEN);
	}
		
	/**
	 * Tests that we get notifications on state changes
	 */
	public void testPolicyNotifications() throws Exception {
		MockNotificationHandler notificationHandler = new MockNotificationHandler();
		
		int retryTimeout = 1000;
		int deltaOutsideTimeout = retryTimeout + 1;
		int deltaInsideTimeout = retryTimeout - 1;
		
		BaseCircuitBreakerPolicyImpl p = new BaseCircuitBreakerPolicyImpl(1, retryTimeout, retryTimeout);
		p.attachHandler(notificationHandler);

		assertEquals(p.getCurrentState(), CircuitBreakerState.CLOSED);
		try {
			p.failedBlacklistedCall(null);
		} catch(Exception e){}

		assertEquals(notificationHandler.getLastEvent().getNewState(), CircuitBreakerState.OPEN);
		assertEquals(notificationHandler.getLastEvent().getOldState(), CircuitBreakerState.CLOSED);
		
		// try to move to HALF_OPEN when we're still inside the retry timeout
		// we should remain OPEN
		Calendar c = Calendar.getInstance();
		c.add(Calendar.SECOND, deltaInsideTimeout);
		p.shouldAttemptReset(c.getTime());
		assertEquals(notificationHandler.getLastEvent().getNewState(), CircuitBreakerState.OPEN);
		assertEquals(notificationHandler.getLastEvent().getOldState(), CircuitBreakerState.CLOSED);
		
		// try again, this time with a timestamp outside the retry timeout
		// we should move to HALF_OPEN
		c = Calendar.getInstance();
		c.add(Calendar.SECOND, deltaOutsideTimeout);
		p.shouldAttemptReset(c.getTime());
		assertEquals(notificationHandler.getLastEvent().getNewState(), CircuitBreakerState.HALF_OPEN);
		assertEquals(notificationHandler.getLastEvent().getOldState(), CircuitBreakerState.OPEN);
		
		// test the constructor overload that takes a notification chain input
		List<NotificationHandler<StateChange>> notificationChain = new ArrayList<NotificationHandler<StateChange>>();
		notificationChain.add(notificationHandler);
		p = new BaseCircuitBreakerPolicyImpl(1, retryTimeout, retryTimeout, notificationChain);
		assertEquals(p.getCurrentState(), CircuitBreakerState.CLOSED);
		try {
			p.failedBlacklistedCall(null);
		} catch(Exception e){}

		assertEquals(notificationHandler.getLastEvent().getNewState(), CircuitBreakerState.OPEN);
		assertEquals(notificationHandler.getLastEvent().getOldState(), CircuitBreakerState.CLOSED);
		
	}
	
}
	
