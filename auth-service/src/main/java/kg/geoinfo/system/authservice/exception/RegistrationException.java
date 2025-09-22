package kg.geoinfo.system.authservice.exception;

import kg.geoinfo.system.authservice.type.ErrorLevel;

public class RegistrationException extends InformationException {

    public RegistrationException(String description) {
        super(description, null, ErrorLevel.ERROR);
    }

    public RegistrationException(String message, Throwable cause) {
        super(message, cause, ErrorLevel.ERROR);
    }
}
