// (C) Copyright IBM Corp. 2001, 2002  All Rights Reserved.
package com.ibm.wsif.naming;

import javax.naming.*;

/**
 * This is a lightweight object which provides a reference for a WSIFService. 
 * When passed to Context.bind(), the getReference() method is invoked and the 
 * resulting Reference object is stored in the directory by JNDI.
 *  
 * @author Owen Burroughs
 */
public class WSIFServiceRef implements Referenceable {

    String wsdlLoc;
    String serviceNS;
    String serviceName;
    String portTypeNS;
    String portTypeName;

    /**
     * Constructor that takes all necessary information needed to create a 
     * WSIFService.
     * @param wsdl The location of the wsdl file
     * @param sNS The namespace for the service as specified in the wsdl
     * @param sName The name of the service required, as specified in the wsdl
     * @param ptNS The namespace of the port type required, as specified in the wsdl
     * @param ptName The name of the port type required, as specified in the wsdl
     */
    public WSIFServiceRef(
    		String wsdl,
    		String sNS,
    		String sName,
    		String ptNS,
    		String ptName) {
        wsdlLoc = wsdl;
        serviceNS = sNS;
        serviceName = sName;
        portTypeNS = ptNS;
        portTypeName = ptName;
    }

    /**
     * Method to create and return a Reference object for the service.
     * @return A Reference object containing the information required to create a 
     * WSIFService and return it, when a lookup is performed on the service using
     * JNDI.
     */
    public Reference getReference() throws NamingException {
        Reference ref =
            new Reference(
                WSIFServiceRef.class.getName(),
                WSIFServiceObjectFactory.class.getName(),
                null);
        ref.add(new StringRefAddr("wsdlLoc", wsdlLoc));
        ref.add(new StringRefAddr("serviceNS", serviceNS));
        ref.add(new StringRefAddr("serviceName", serviceName));
        ref.add(new StringRefAddr("portTypeNS", portTypeNS));
        ref.add(new StringRefAddr("portTypeName", portTypeName));
        return ref;
    }

    /**
     * Standard toString method overriding.
     * @return A String representation of this object
     */
    public String toString() {
        String s = "WSIFServiceRef: " + wsdlLoc + ", " + serviceNS + ", " + serviceName
        	+ ", " + portTypeNS + ", " + portTypeName;
        return s;
    }
}