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

import junit.framework.TestCase;
/**
 * Unit tests for circuit breaker wrapping logic.
 */
public class CircuitBreakerWrapperTest extends TestCase {
	
	static interface BadMockWrappedInterface {
		@CircuitBreakerExceptionBlacklist(blacklist={Exception.class})
		String doSomething(String s);
	}

	static class BadWrapObjectImpl implements BadMockWrappedInterface {
		@Override
		public String doSomething(String s) {
			return s;
		}
	}


	static interface MockWrappedInterface {
		@CircuitBreakerExceptionBlacklist(blacklist={Exception.class})
		String doSomething(String s) throws Exception, CircuitBreakerException;
	}

	static class MockWrappedInterfaceImpl implements MockWrappedInterface {
		@Override
		public String doSomething(String s)  throws Exception, CircuitBreakerException {
			// TODO Auto-generated method stub
			return s;
		}
	}


	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	public void setUp() throws Exception {
		super.setUp();
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	@Override
	public void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for {@link com.hubspot.utils.circuitbreaker.CircuitBreakerWrapper#wrap(java.lang.Object, java.lang.Class, com.hubspot.utils.circuitbreaker.CircuitBreakerPolicy)}.
	 */
	public void testWrap() throws Exception {
		MockWrappedInterface mw = new MockWrappedInterfaceImpl();
		
		CircuitBreakerWrapper cbw = CircuitBreakerWrapper.getCircuitBreakerWrapperInstance();
		try {
			mw = cbw.wrap(mw, MockWrappedInterface.class, new BaseCircuitBreakerPolicyImpl(1,1,10));
		} catch (Exception e) {
			fail();
		}
		assertNotNull(mw);
		assertEquals(mw.doSomething("HELLO"), "HELLO");
	}
	
	/**
	 * Test that wrapping an object that violates our wrapping conditions fails
	 */
	public void testBadWrap() {
		BadMockWrappedInterface mw = new BadWrapObjectImpl();
		
		CircuitBreakerWrapper cbw = CircuitBreakerWrapper.getCircuitBreakerWrapperInstance();
		try {
			mw = cbw.wrap(mw, BadMockWrappedInterface.class, new BaseCircuitBreakerPolicyImpl(1,1, 10));
		} catch (CircuitBreakerWrappingException e) {
			return;
		}
		fail();
	}
	
	/**
	 * Test that we can't double wrap an object
	 */
	public void testDoubleWrapIsBadWrap() {
		MockWrappedInterface mw = new MockWrappedInterfaceImpl();
		CircuitBreakerWrapper cbw = CircuitBreakerWrapper.getCircuitBreakerWrapperInstance();
		try {
			mw = cbw.wrap(mw, MockWrappedInterface.class, new BaseCircuitBreakerPolicyImpl(1,1, 10));
		} catch (CircuitBreakerWrappingException e) {
			fail();
		}
		
		// try wrapping the object in a circuit breaker one more time--should fail
		try {
			mw = cbw.wrap(mw, MockWrappedInterface.class, new BaseCircuitBreakerPolicyImpl(1,1, 10));
		} catch (CircuitBreakerWrappingException e) {
			return;
		}
		
		fail();
	}

	/**
	 * Test method for {@link com.hubspot.utils.circuitbreaker.CircuitBreakerWrapper#getCircuitBreakerWrapperInstance()}.
	 */
	public void testGetCircuitBreakerWrapperInstance() {
		CircuitBreakerWrapper w = CircuitBreakerWrapper.getCircuitBreakerWrapperInstance();
		assertNotNull(w);
	}

}
