package kg.geoinfo.system.authservice.exception;

import kg.geoinfo.system.authservice.type.ErrorLevel;

public class ResetPasswordException extends InformationException {

    public ResetPasswordException(String description) {
        super(description, null, ErrorLevel.ERROR);
    }

    public ResetPasswordException(String message, Throwable cause) {
        super(message, cause, ErrorLevel.ERROR);
    }
}
