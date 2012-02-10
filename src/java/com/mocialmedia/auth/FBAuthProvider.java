package com.mocialmedia.auth;

import org.jivesoftware.openfire.auth.AuthProvider;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.user.UserNotFoundException;

public class FBAuthProvider implements AuthProvider {

	public void authenticate(String username, String password)
			throws UnauthorizedException {
		 if (username == null || password == null) {
	            throw new UnauthorizedException();
	        }
		 if(!password.equals("1234"))
			 throw new UnauthorizedException();
	}

	public void authenticate(String username, String token, String digest)
			throws UnauthorizedException {
		 throw new UnauthorizedException("Digest authentication not supported.");
		  
	}
	
	public String getPassword(String username) throws UserNotFoundException,
			UnsupportedOperationException {
		return "1234";
	}

	public boolean isDigestSupported() {
		return false;
	}

	public boolean isPlainSupported() {
		return false;
	}
	public void setPassword(String username, String password)
			throws UserNotFoundException, UnsupportedOperationException {
		throw new UnsupportedOperationException("Unsupported Operation");

	}

	public boolean supportsPasswordRetrieval() {
		return false;
	}

}
