# Copyright 2011 HubSpot, Inc.
#
#   Licensed under the Apache License, Version 2.0 (the 
# "License"); you may not use this file except in compliance 
# with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, 
# software distributed under the License is distributed on an 
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
# either express or implied.  See the License for the specific 
# language governing permissions and limitations under the 
# License.

This project contains an implementation of the circuit breaker stability pattern as documented by Michael Nygard in "Release It!" (http://www.amazon.com/gp/product/0978739213?ie=UTF8&tag=michaelnygard-20&linkCode=as2&camp=1789&creative=9325&creativeASIN=0978739213). 

There are three key components to this implementation:

*A lightweight interception layer that will wrap an appropriately defined interface (appropriate will be defined later on) in a Circuit Breaker, very similar in spirit and implementation to Hydra.
*An instance of the CircuitBreakerPolicy interface that determines when to move between CLOSED, OPEN and HALF_OPEN.
*An interface to be wrapped that declares what methods will be monitored and a list of exceptions thrown by those methods that should be considered bad.

For example:

MyInterface objectToWrap = new MyInterfaceImpl(); 
CircuitBreakerWrapper wrapper = CircuitBreakerWrapper.getInstance();  
CircuitBreakerPolicy policy = new BaseCircuitBreakerPolicyImpl(x, y, z);  
objectToWrap = wrapper.wrap(objectToWrap, MyInterface.class, policy); 

Any interface can be passed to

CircuitBreakerWrapper.wrap() 

and calls will just be passed through.

For anything useful to happen, the interface must declare which methods will be monitored and which exceptions on those methods will potentially cause a trip of the breaker.

To do this, just add the CircuitBreakerExceptionBlacklist annotation to a method that should be monitored, like so:

public interface MyInterface {       
     
    void doSomething(); // <-- Has no annotation, not monitored      
    
    @CircuitBreakerExceptionBlacklist(blacklist = {SQLException.class})      
    void doSomethingExpensive() throws SQLException, BlahException;  
    // has annotation, so any throw of SQLException will potentially cause a trip, but BlahException will not     
} 

In the above example, a list of exceptions is included in the annotation that, when thrown, will move the circuit breaker towards an OPEN state. Whether or not the circuit breaker eventually trips and moves to OPEN is determined by the CircuitBreakerPolicy instance that's passed in at wrap time.

A default policy implementation has been provided named BaseCircuitBreakerPolicyImpl that trips based on two parameters:

tripThreshold: how many instances of a blacklisted exception should move the breaker to an OPEN state
thresholdWindow: time period over to measure the number of blacklisted exceptions, i.e., 10 SQLExceptions per 60 seconds will cause a trip
This base implementation also provides hooks to register your state change notification handler, and configurable timeout for checking when in the HALF_OPEN state.
