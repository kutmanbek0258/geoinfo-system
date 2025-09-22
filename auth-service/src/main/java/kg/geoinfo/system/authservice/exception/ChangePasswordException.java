package kg.geoinfo.system.authservice.exception;


import kg.geoinfo.system.authservice.type.ErrorLevel;

public class ChangePasswordException extends InformationException {

    public ChangePasswordException(String description) {
        super(description, null, ErrorLevel.ERROR);
    }

    public ChangePasswordException(String message, Throwable cause) {
        super(message, cause, ErrorLevel.ERROR);
    }
}
