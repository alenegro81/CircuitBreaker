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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.apache.commons.lang.ArrayUtils;

import com.hubspot.utils.HubSpotObject;

/**
 * Wraps an interface instance in a circuit breaker.
 *
 */
public class CircuitBreakerWrapper extends HubSpotObject {
	
	CircuitBreakerWrapper() {
	}
	
	
	/**
	 * Wraps the supplied object toWrap in a CircuitBreaker conforming to the supplied CircuitBreakerPolicy.
	 */
	public <T, W extends T> T wrap(W toWrap, Class<T> interfaceToProxy, CircuitBreakerPolicy policy) throws CircuitBreakerWrappingException  {
		sanityCheck(toWrap, interfaceToProxy, policy);
		
		// walk the chain of interfaces implemented by T and check for their blacklisted methods
		Stack<Class<?>> implementedInterfaces = new Stack<Class<?>>();
		implementedInterfaces.addAll(Arrays.asList(interfaceToProxy.getInterfaces()));
		implementedInterfaces.add(interfaceToProxy);
		
        Map<Method, Class[]> blacklist = new HashMap();
        while( !implementedInterfaces.isEmpty() ) {
        	Class<?> implementedInterface = implementedInterfaces.pop();
        	
	        for( Method m : implementedInterface.getDeclaredMethods() ) {
	        	// check that the blacklisted method throws CircuitBreakerException
	        	if (m.isAnnotationPresent(CircuitBreakerExceptionBlacklist.class)) {
	        		if(!ArrayUtils.contains(m.getExceptionTypes(),
	        								 CircuitBreakerException.class)) {
	        			throw new CircuitBreakerWrappingException("Wrapped methods must throw CircuitBreakerException");
	        		}
	
	            	CircuitBreakerExceptionBlacklist a = (CircuitBreakerExceptionBlacklist)m.getAnnotation(CircuitBreakerExceptionBlacklist.class);
	            	blacklist.put(m,a.blacklist());
	        	} 
	        }
	        
	        implementedInterfaces.addAll(Arrays.asList(implementedInterface.getInterfaces()));
        }


        Class<?>[] interfaces = new Class<?>[] { interfaceToProxy };
        InvocationHandler handler = new CircuitBreakerInvocationHandler(toWrap, blacklist, policy);
        T newProxyInstance = (T) Proxy.newProxyInstance(getClass().getClassLoader(), interfaces, handler);
        return newProxyInstance;
    }

	/**
	 * Ensures that the object we're wrapping and it's base interface conform to our restrictions
	 * @throws CircuitBreakerWrappingException
	 */
	private <T, W extends T> void sanityCheck(W toWrap, Class<T> interfaceToProxy, CircuitBreakerPolicy policy) throws CircuitBreakerWrappingException {
		if (toWrap == null) {
			throw new CircuitBreakerWrappingException("Cannot wrap a null object");
		}
        if (interfaceToProxy == null) {
            throw new CircuitBreakerWrappingException("Cannot proxy a null interface");
        }
		if ( !interfaceToProxy.isInterface() ) {
			throw new CircuitBreakerWrappingException("interfaceToProxy must be an interface");
		}
		
		try {
            if (Proxy.isProxyClass(toWrap.getClass())
                    && Proxy.getInvocationHandler(toWrap) instanceof CircuitBreakerInvocationHandler) {
                throw new CircuitBreakerWrappingException("Object is already wrapped in a circuit breaker.");
            }
        } catch (IllegalArgumentException ex) {
            // "this should never happen", but if it does, we'll log our own logic error
            // before throwing the bad wrap
            getLog().error("IllegalArgumentException when trying to check for previous wrap", ex);
            throw new CircuitBreakerWrappingException("Trouble detecting whether object was already proxied");
        }
	}

	/**
	 * Returns an instance of a CircuitBreakerWrapper
	 */
	public static CircuitBreakerWrapper getCircuitBreakerWrapperInstance() {
		return createWrapper();
	}
	
	private static CircuitBreakerWrapper createWrapper() {
		return new CircuitBreakerWrapper();
	}
}
