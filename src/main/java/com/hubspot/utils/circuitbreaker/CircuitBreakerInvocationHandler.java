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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;

import com.hubspot.utils.HubSpotObject;
import com.hubspot.utils.circuitbreaker.CircuitBreakerPolicy.CircuitBreakerState;

/**
 * Invocation handler that transparently wraps an object and will trip a method "circuit breaker" should
 * an exception be thrown that exceeds the defined policy for that method.
 *
 */
public class CircuitBreakerInvocationHandler extends HubSpotObject implements InvocationHandler {

	private Object realObj;
	private Map<Method, Class[]> blacklist;    	// map of method-->Exception types that may trip the breaker
	private CircuitBreakerPolicy policy;		// policy that determines when we move between states
	
	/**
	 * Constructor
	 * 
	 * @param realObj: Object/resource we're wrapping
	 * @param blacklist: map of <method, blacklisted exception list> pairs
	 * @param policy: Instance of BaseCircuitPolicy that tells us when to trip
	 */
	protected CircuitBreakerInvocationHandler(Object realObj,
										   Map<Method, Class[]> blacklist,
										   CircuitBreakerPolicy policy) {
		if( realObj == null || blacklist == null || policy == null )  {
			throw new IllegalArgumentException("Constructor parameters cannot be null");
		}
		
		this.realObj = realObj;
		this.blacklist = blacklist;
		this.policy = policy;
	}
	
	/**
	 *  Called from a Proxy instance to invoke a method on the realObj stored by this handler.
	 *  
	 *  If a "blacklisted" exception is thrown, we inform our CircuitBreakerPolicy let it
	 *  tell us whether we should move states.
	 */
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		getLog().debug("circuit breaker wrapped method invocation = " + method.toGenericString());

        Throwable fromInvocation = null;
        Object ret = null;
        try {
        	if (blacklist.containsKey(method) &&
        	    policy.getCurrentState() == CircuitBreakerState.OPEN &&
        	   !policy.shouldAttemptReset() ) {
        		// breaker is open, just throw our standard CircuitBreakerException
        		throw new CircuitBreakerException();
        	}
        	
        	// circuit breaker is either open or half-open, do our invocation
        	ret = method.invoke(realObj, args);
        } catch (InvocationTargetException e) {
            // The underlying method was called successfully, but threw an exception.
            // we want to pass that exception on.
            fromInvocation = e.getCause();
        } catch (IllegalArgumentException e) {
            // "In a properly constructed proxy, this should never happen."
        	getLog().debug("Illegal argument exception in circuit breaker handler"+ e);
            throw e;
            
        } catch (IllegalAccessException e) {
            // "In a properly constructed proxy, this should never happen."
        	getLog().debug("Illegal access exception in circuit breaker handler"+ e);
            throw e;
        }
        
        // exception was thrown, determine if it was blacklisted and if we should trip
        if (fromInvocation != null) {
        	if( blacklist.containsKey(method)) {
        		Class[] blacklistedExceptionTypes = blacklist.get(method);
        		if(ArrayUtils.contains(blacklistedExceptionTypes, fromInvocation.getClass()) ) {
        			policy.failedBlacklistedCall(method);
        		}
        	} else {
        		policy.successfulCall(method);
        	}
            throw fromInvocation;
        }
        
        if(blacklist.containsKey(method)) 
        	policy.successfulCall(method);
        
        return ret;
    }

}
